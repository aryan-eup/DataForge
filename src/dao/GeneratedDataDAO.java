package dao;

import model.JobRecord;
import model.JobSummary;
import util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// CONCEPT: CRUD Operations
// GeneratedDataDAO handles complete persistence for generation jobs and rows using JDBC.
public class GeneratedDataDAO {

    private static final String HEADER_PREFIX = "__HEADER__:";
    private static final String DELIMITER = "\t";

    /**
     * CREATE: Saves a job and its generated rows inside a single database transaction using batch insert.
     */
    // CONCEPT: Database Transaction (commit/rollback) & Batch Insert
    public int saveJob(String schemaName, int rowCount, List<String> headers, List<List<String>> rows)
            throws SQLException {

        Connection conn = null;
        PreparedStatement stmtJob = null;
        PreparedStatement stmtRow = null;
        ResultSet rsKeys = null;

        try {
            // CONCEPT: JDBC DriverManager connection
            conn = DBConnection.getConnection();

            // CONCEPT: Database Transaction - begin transaction
            conn.setAutoCommit(false);

            // 1. Insert into jobs table
            // CONCEPT: PreparedStatement (parameterized query)
            String insertJobSql = "INSERT INTO jobs (schema_name, row_count) VALUES (?, ?)";
            stmtJob = conn.prepareStatement(insertJobSql, Statement.RETURN_GENERATED_KEYS);
            stmtJob.setString(1, schemaName);
            stmtJob.setInt(2, rowCount);
            stmtJob.executeUpdate();

            // Retrieve generated primary key
            // CONCEPT: ResultSet
            rsKeys = stmtJob.getGeneratedKeys();
            int jobId;
            if (rsKeys.next()) {
                jobId = rsKeys.getInt(1);
            } else {
                throw new SQLException("Failed to retrieve generated job ID.");
            }

            // 2. Insert headers as metadata row, followed by data rows
            // CONCEPT: Batch Insert (addBatch, executeBatch)
            String insertRowSql = "INSERT INTO generated_rows (job_id, row_data) VALUES (?, ?)";
            stmtRow = conn.prepareStatement(insertRowSql);

            // Store column headers as the first entry
            stmtRow.setInt(1, jobId);
            stmtRow.setString(2, HEADER_PREFIX + String.join(DELIMITER, headers));
            stmtRow.addBatch();

            // Batch insert data rows
            int batchSize = 0;
            for (List<String> row : rows) {
                stmtRow.setInt(1, jobId);
                stmtRow.setString(2, String.join(DELIMITER, row));
                stmtRow.addBatch();
                batchSize++;

                // Execute batch in chunks of 500 for optimal memory and network performance
                if (batchSize % 500 == 0) {
                    stmtRow.executeBatch();
                }
            }

            stmtRow.executeBatch();

            // CONCEPT: Database Transaction - commit on success
            conn.commit();
            return jobId;

        } catch (SQLException e) {
            // CONCEPT: Database Transaction - rollback on any failure
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            // CONCEPT: Exception Handling (try-catch-finally resource cleanup)
            if (rsKeys != null) try { rsKeys.close(); } catch (SQLException ignored) {}
            if (stmtJob != null) try { stmtJob.close(); } catch (SQLException ignored) {}
            if (stmtRow != null) try { stmtRow.close(); } catch (SQLException ignored) {}
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * READ: Retrieves history of all past jobs.
     */
    public List<JobSummary> getHistory() throws SQLException {
        List<JobSummary> list = new ArrayList<>();
        String sql = "SELECT id, schema_name, row_count, created_at FROM jobs ORDER BY id DESC";

        // CONCEPT: try-with-resources
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             // CONCEPT: ResultSet reading
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String schema = rs.getString("schema_name");
                int count = rs.getInt("row_count");
                Timestamp ts = rs.getTimestamp("created_at");
                String createdStr = (ts != null) ? ts.toString() : "";
                list.add(new JobSummary(id, schema, count, createdStr));
            }
        }
        return list;
    }

    /**
     * READ: Retrieves a specific job and all its generated rows.
     */
    public JobRecord getJobById(int jobId) throws SQLException {
        String jobSql = "SELECT id, schema_name, row_count, created_at FROM jobs WHERE id = ?";
        String rowsSql = "SELECT row_data FROM generated_rows WHERE job_id = ? ORDER BY id ASC";

        try (Connection conn = DBConnection.getConnection()) {
            String schemaName = null;
            int rowCount = 0;
            String createdAt = "";

            try (PreparedStatement psJob = conn.prepareStatement(jobSql)) {
                psJob.setInt(1, jobId);
                try (ResultSet rs = psJob.executeQuery()) {
                    if (rs.next()) {
                        schemaName = rs.getString("schema_name");
                        rowCount = rs.getInt("row_count");
                        Timestamp ts = rs.getTimestamp("created_at");
                        createdAt = (ts != null) ? ts.toString() : "";
                    } else {
                        return null; // Job not found
                    }
                }
            }

            List<String> headers = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            try (PreparedStatement psRows = conn.prepareStatement(rowsSql)) {
                psRows.setInt(1, jobId);
                try (ResultSet rs = psRows.executeQuery()) {
                    boolean isFirst = true;
                    while (rs.next()) {
                        String data = rs.getString("row_data");
                        if (isFirst && data.startsWith(HEADER_PREFIX)) {
                            String headerLine = data.substring(HEADER_PREFIX.length());
                            headers.addAll(Arrays.asList(headerLine.split(DELIMITER, -1)));
                            isFirst = false;
                        } else {
                            rows.add(Arrays.asList(data.split(DELIMITER, -1)));
                        }
                    }
                }
            }

            return new JobRecord(jobId, schemaName, rowCount, createdAt, headers, rows);
        }
    }

    /**
     * UPDATE: Renames a job's schema name label.
     */
    public boolean renameJob(int jobId, String newName) throws SQLException {
        String sql = "UPDATE jobs SET schema_name = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setInt(2, jobId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * DELETE: Deletes a job and its associated rows inside a transaction.
     */
    public boolean deleteJob(int jobId) throws SQLException {
        String deleteRowsSql = "DELETE FROM generated_rows WHERE job_id = ?";
        String deleteJobSql = "DELETE FROM jobs WHERE id = ?";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement psRows = conn.prepareStatement(deleteRowsSql)) {
                psRows.setInt(1, jobId);
                psRows.executeUpdate();
            }

            int affected;
            try (PreparedStatement psJob = conn.prepareStatement(deleteJobSql)) {
                psJob.setInt(1, jobId);
                affected = psJob.executeUpdate();
            }

            conn.commit();
            return affected > 0;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }
}
