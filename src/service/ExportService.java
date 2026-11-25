package service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

// CONCEPT: File Handling (BufferedWriter, FileWriter, try-with-resources)
// ExportService formats and writes generated records to CSV or SQL files on disk.
public class ExportService {

    /**
     * Exports headers and rows to a CSV file.
     */
    public void exportToCsv(List<String> headers, List<List<String>> rows, File targetFile) throws IOException {
        // Ensure parent directories exist
        if (targetFile.getParentFile() != null) {
            targetFile.getParentFile().mkdirs();
        }

        // CONCEPT: try-with-resources with BufferedWriter and FileWriter
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile, StandardCharsets.UTF_8))) {
            // Write CSV header
            // CONCEPT: Lambda expressions & Stream API (map, collect with Collectors.joining)
            String headerLine = headers.stream()
                    .map(this::escapeCsvField)
                    .collect(Collectors.joining(","));
            writer.write(headerLine);
            writer.newLine();

            // Write CSV rows
            for (List<String> row : rows) {
                String rowLine = row.stream()
                        .map(this::escapeCsvField)
                        .collect(Collectors.joining(","));
                writer.write(rowLine);
                writer.newLine();
            }
            writer.flush();
        }
    }

    /**
     * Exports records as standard SQL INSERT statements.
     */
    public void exportToSql(String tableName, List<String> headers, List<List<String>> rows, File targetFile)
            throws IOException {
        if (targetFile.getParentFile() != null) {
            targetFile.getParentFile().mkdirs();
        }

        String safeTable = (tableName != null && !tableName.isBlank()) ? tableName : "generated_table";
        String columnList = String.join(", ", headers);

        // CONCEPT: try-with-resources with BufferedWriter and FileWriter
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile, StandardCharsets.UTF_8))) {
            writer.write("-- DataForge Generated SQL Export");
            writer.newLine();
            writer.write("-- Table: " + safeTable + " | Total Rows: " + rows.size());
            writer.newLine();
            writer.newLine();

            for (List<String> row : rows) {
                // CONCEPT: Lambda Expressions & Stream API (map, joining)
                String valueList = row.stream()
                        .map(this::escapeSqlValue)
                        .collect(Collectors.joining(", "));

                String sql = String.format("INSERT INTO %s (%s) VALUES (%s);", safeTable, columnList, valueList);
                writer.write(sql);
                writer.newLine();
            }
            writer.flush();
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private String escapeSqlValue(String value) {
        if (value == null) return "NULL";
        return "'" + value.replace("'", "''") + "'";
    }
}
