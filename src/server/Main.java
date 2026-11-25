package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dao.GeneratedDataDAO;
import exception.InvalidSchemaException;
import model.Field;
import model.JobRecord;
import model.JobSummary;
import model.Schema;
import service.ExportService;
import service.GeneratorService;
import service.StatsService;
import util.Repository;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

// Main application class: boots the built-in Java HttpServer and registers all REST endpoints.
public class Main {

    private static final int PORT = 8080;
    private static final String WEB_DIR = "web";

    private static final GeneratorService generatorService = new GeneratorService();
    private static final GeneratedDataDAO dao = new GeneratedDataDAO();
    private static final ExportService exportService = new ExportService();
    private static final StatsService statsService = new StatsService();

    // CONCEPT: Generics - generic in-memory repository to cache active/recent jobs
    private static final Repository<JobSummary> inMemoryRepo = new Repository<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Register API endpoint routes
        server.createContext("/generate", new GenerateHandler());
        server.createContext("/progress", new ProgressHandler());
        server.createContext("/history", new HistoryHandler());
        server.createContext("/job", new JobHandler());
        server.createContext("/export", new ExportHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/schemas", new SchemasHandler());

        // Static file handler for frontend UI (serves index.html, style.css, script.js)
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println("==================================================");
        System.out.println("  DataForge: Test Data Generator Server Running   ");
        System.out.println("  URL: http://localhost:" + PORT + "/               ");
        System.out.println("==================================================");
    }

    // -------------------------------------------------------------
    // HTTP Handlers
    // -------------------------------------------------------------

    /**
     * GET /generate?schema=students&rows=100&cols=name,email,age
     */
    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleOptions(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String schemaName = params.getOrDefault("schema", "students").toLowerCase();
            int rows = 100;
            try {
                if (params.containsKey("rows")) {
                    rows = Integer.parseInt(params.get("rows"));
                }
            } catch (NumberFormatException ignored) {}

            String colsParam = params.getOrDefault("cols", "");
            List<String> chosenCols;
            if (colsParam.isBlank()) {
                chosenCols = List.of("id", "name", "email");
            } else {
                chosenCols = Arrays.stream(colsParam.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            Map<String, Schema> schemas = Schema.getPredefinedSchemas();
            Schema targetSchema = schemas.get(schemaName);

            try {
                if (targetSchema == null) {
                    throw new InvalidSchemaException("Schema '" + schemaName + "' is not supported. Choose students, employees, or products.");
                }

                // Generate data using 4 worker threads
                List<List<String>> generated = generatorService.generateData(targetSchema, chosenCols, rows, 4);

                // Persist to database
                int jobId = dao.saveJob(schemaName, generated.size(), chosenCols, generated);

                // Cache in generic in-memory repository
                inMemoryRepo.add(new JobSummary(jobId, schemaName, generated.size(), new Date().toString()));

                // Return JSON response
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"jobId\":").append(jobId).append(",");
                json.append("\"schemaName\":").append(escapeJson(schemaName)).append(",");
                json.append("\"header\":").append(toJsonStringArray(chosenCols)).append(",");
                json.append("\"rows\":").append(toJsonMatrix(generated));
                json.append("}");

                sendResponse(exchange, 200, "application/json", json.toString());

            } catch (InvalidSchemaException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (SQLException e) {
                sendError(exchange, 500, "Database error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /progress
     */
    static class ProgressHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleOptions(exchange)) return;

            int current = generatorService.getProgress();
            int total = generatorService.getTotalRows();
            int percentage = generatorService.getProgressPercentage();

            StringBuilder json = new StringBuilder();
            json.append("{")
                .append("\"current\":").append(current).append(",")
                .append("\"total\":").append(total).append(",")
                .append("\"percentage\":").append(percentage)
                .append("}");

            sendResponse(exchange, 200, "application/json", json.toString());
        }
    }

    /**
     * GET /history
     */
    static class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleOptions(exchange)) return;

            try {
                List<JobSummary> history = dao.getHistory();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < history.size(); i++) {
                    JobSummary js = history.get(i);
                    json.append("{")
                        .append("\"id\":").append(js.getId()).append(",")
                        .append("\"schemaName\":").append(escapeJson(js.getSchemaName())).append(",")
                        .append("\"rowCount\":").append(js.getRowCount()).append(",")
                        .append("\"createdAt\":").append(escapeJson(js.getCreatedAt()))
                        .append("}");
                    if (i < history.size() - 1) json.append(",");
                }
                json.append("]");
                sendResponse(exchange, 200, "application/json", json.toString());
            } catch (SQLException e) {
                sendError(exchange, 500, "Database error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /job?id=5  (optional query param: sort=colName&asc=true/false)
     * DELETE /job?id=5
     * PUT /job?id=5&name=NewName
     */
    static class JobHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleOptions(exchange)) return;

            String method = exchange.getRequestMethod().toUpperCase();
            Map<String, String> params = parseQueryParams(exchange.getRequestURI());

            int jobId;
            try {
                jobId = Integer.parseInt(params.getOrDefault("id", "0"));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Missing or invalid job ID parameter.");
                return;
            }

            try {
                if ("GET".equals(method)) {
                    JobRecord record = dao.getJobById(jobId);
                    if (record == null) {
                        sendError(exchange, 404, "Job not found with ID: " + jobId);
                        return;
                    }

                    // Optional sorting
                    List<List<String>> rows = new ArrayList<>(record.getRows());
                    String sortCol = params.get("sort");
                    if (sortCol != null && !sortCol.isBlank()) {
                        boolean asc = !"false".equalsIgnoreCase(params.get("asc"));
                        generatorService.sortRows(rows, record.getHeaders(), sortCol, asc);
                    }

                    StringBuilder json = new StringBuilder();
                    json.append("{")
                        .append("\"jobId\":").append(record.getId()).append(",")
                        .append("\"schemaName\":").append(escapeJson(record.getSchemaName())).append(",")
                        .append("\"rowCount\":").append(record.getRowCount()).append(",")
                        .append("\"createdAt\":").append(escapeJson(record.getCreatedAt())).append(",")
                        .append("\"header\":").append(toJsonStringArray(record.getHeaders())).append(",")
                        .append("\"rows\":").append(toJsonMatrix(rows))
                        .append("}");

                    sendResponse(exchange, 200, "application/json", json.toString());

                } else if ("DELETE".equals(method)) {
                    boolean deleted = dao.deleteJob(jobId);
                    if (deleted) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Job deleted successfully\"}");
                    } else {
                        sendError(exchange, 404, "Job not found or already deleted: " + jobId);
                    }

                } else if ("PUT".equals(method)) {
                    String newName = params.get("name");
                    if (newName == null || newName.isBlank()) {
                        sendError(exchange, 400, "Missing 'name' parameter for renaming job.");
                        return;
                    }
                    boolean updated = dao.renameJob(jobId, newName.trim());
                    if (updated) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Job renamed successfully\"}");
                    } else {
                        sendError(exchange, 404, "Job not found for ID: " + jobId);
                    }

                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
            } catch (SQLException e) {
                sendError(exchange, 500, "Database error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /export?id=5&format=csv|sql
     */
    static class ExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleOptions(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            int jobId;
            try {
                jobId = Integer.parseInt(params.getOrDefault("id", "0"));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid job ID.");
                return;
            }

            String format = params.getOrDefault("format", "csv").toLowerCase();

            try {
                JobRecord record = dao.getJobById(jobId);
                if (record == null) {
                    sendError(exchange, 404, "Job not found with ID: " + jobId);
                    return;
                }

                File tempFile = File.createTempFile("dataforge_" + jobId + "_", "." + format);
                tempFile.deleteOnExit();

                if ("csv".equals(format)) {
                    exportService.exportToCsv(record.getHeaders(), record.getRows(), tempFile);
                    exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"dataforge_job_" + jobId + ".csv\"");
                } else if ("sql".equals(format)) {
                    exportService.exportToSql(record.getSchemaName(), record.getHeaders(), record.getRows(), tempFile);
                    exchange.getResponseHeaders().set("Content-Type", "application/sql; charset=UTF-8");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"dataforge_job_" + jobId + ".sql\"");
                } else {
                    sendError(exchange, 400, "Unsupported export format: " + format + ". Choose csv or sql.");
                    return;
                }

                byte[] fileBytes = Files.readAllBytes(tempFile.toPath());
                exchange.sendResponseHeaders(200, fileBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes);
                }

            } catch (SQLException e) {
                sendError(exchange, 500, "Database error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /stats?id=5&col=age
     */
    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleOptions(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            int jobId;
            try {
                jobId = Integer.parseInt(params.getOrDefault("id", "0"));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid job ID.");
                return;
            }

            String col = params.get("col");
            if (col == null || col.isBlank()) {
                sendError(exchange, 400, "Missing 'col' parameter.");
                return;
            }

            try {
                JobRecord record = dao.getJobById(jobId);
                if (record == null) {
                    sendError(exchange, 404, "Job not found with ID: " + jobId);
                    return;
                }

                StatsService.ColumnStats stats = statsService.computeStats(record.getHeaders(), record.getRows(), col);

                StringBuilder json = new StringBuilder();
                json.append("{")
                    .append("\"column\":").append(escapeJson(stats.getColumnName())).append(",")
                    .append("\"total\":").append(stats.getTotalCount()).append(",")
                    .append("\"distinct\":").append(stats.getDistinctCount()).append(",")
                    .append("\"hasDuplicates\":").append(stats.hasDuplicates()).append(",")
                    .append("\"isNumeric\":").append(stats.isNumeric()).append(",")
                    .append("\"average\":").append(stats.getAverage() != null ? String.format(Locale.US, "%.2f", stats.getAverage()) : "null").append(",")
                    .append("\"min\":").append(stats.getMin() != null ? String.format(Locale.US, "%.2f", stats.getMin()) : "null").append(",")
                    .append("\"max\":").append(stats.getMax() != null ? String.format(Locale.US, "%.2f", stats.getMax()) : "null").append(",")
                    .append("\"median\":").append(stats.getMedian() != null ? String.format(Locale.US, "%.2f", stats.getMedian()) : "null").append(",")
                    .append("\"q1\":").append(stats.getQ1() != null ? String.format(Locale.US, "%.2f", stats.getQ1()) : "null").append(",")
                    .append("\"q3\":").append(stats.getQ3() != null ? String.format(Locale.US, "%.2f", stats.getQ3()) : "null").append(",")
                    .append("\"stdDev\":").append(stats.getStdDev() != null ? String.format(Locale.US, "%.2f", stats.getStdDev()) : "null").append(",")
                    .append("\"topFrequent\":[");

                List<StatsService.FrequencyItem> topList = stats.getTopFrequent();
                for (int i = 0; i < topList.size(); i++) {
                    StatsService.FrequencyItem item = topList.get(i);
                    json.append("{")
                        .append("\"value\":").append(escapeJson(item.getValue())).append(",")
                        .append("\"count\":").append(item.getCount()).append(",")
                        .append("\"percentage\":").append(item.getPercentage())
                        .append("}");
                    if (i < topList.size() - 1) json.append(",");
                }
                json.append("],\"histogramBins\":[");

                List<StatsService.HistogramBin> bins = stats.getHistogramBins();
                for (int i = 0; i < bins.size(); i++) {
                    StatsService.HistogramBin bin = bins.get(i);
                    json.append("{")
                        .append("\"label\":").append(escapeJson(bin.getLabel())).append(",")
                        .append("\"minVal\":").append(String.format(Locale.US, "%.2f", bin.getMinVal())).append(",")
                        .append("\"maxVal\":").append(String.format(Locale.US, "%.2f", bin.getMaxVal())).append(",")
                        .append("\"count\":").append(bin.getCount())
                        .append("}");
                    if (i < bins.size() - 1) json.append(",");
                }
                json.append("]}");

                sendResponse(exchange, 200, "application/json", json.toString());

            } catch (SQLException e) {
                sendError(exchange, 500, "Database error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /schemas (returns metadata of predefined schemas and available fields)
     */
    static class SchemasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (handleOptions(exchange)) return;

            Map<String, Schema> schemas = Schema.getPredefinedSchemas();
            StringBuilder json = new StringBuilder("{");
            int sIdx = 0;
            for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
                Schema s = entry.getValue();
                json.append(escapeJson(entry.getKey())).append(":{");
                json.append("\"name\":").append(escapeJson(s.getName())).append(",");
                json.append("\"fields\":[");
                List<Field> fields = s.getFields();
                for (int fIdx = 0; fIdx < fields.size(); fIdx++) {
                    Field f = fields.get(fIdx);
                    json.append("{")
                        .append("\"name\":").append(escapeJson(f.getName())).append(",")
                        .append("\"type\":").append(escapeJson(f.getType()))
                        .append("}");
                    if (fIdx < fields.size() - 1) json.append(",");
                }
                json.append("]}");
                if (sIdx < schemas.size() - 1) json.append(",");
                sIdx++;
            }
            json.append("}");

            sendResponse(exchange, 200, "application/json", json.toString());
        }
    }

    /**
     * Serves static frontend assets from the web/ directory.
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isBlank()) {
                path = "/index.html";
            }

            // Prevent path traversal
            if (path.contains("..")) {
                sendError(exchange, 403, "Forbidden");
                return;
            }

            Path filePath = Paths.get(WEB_DIR, path.substring(1));
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendError(exchange, 404, "File not found: " + path);
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=UTF-8";
            else if (path.endsWith(".png")) contentType = "image/png";

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // -------------------------------------------------------------
    // Helper Methods & Manual JSON Builder (No JSON library used)
    // -------------------------------------------------------------

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String json = "{\"error\":" + escapeJson(message) + "}";
        sendResponse(exchange, status, "application/json", json);
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> map = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = (idx < pair.length() - 1)
                        ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                        : "";
                map.put(key, val);
            }
        }
        return map;
    }

    // Manual JSON String escaper
    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String toJsonStringArray(List<String> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(escapeJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonMatrix(List<List<String>> matrix) {
        if (matrix == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < matrix.size(); i++) {
            sb.append(toJsonStringArray(matrix.get(i)));
            if (i < matrix.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
