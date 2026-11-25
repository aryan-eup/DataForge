package model;

import java.util.List;

// Model representing a complete generation job including its schema, headers, and rows
public class JobRecord {

    private final int id;
    private final String schemaName;
    private final int rowCount;
    private final String createdAt;
    private final List<String> headers;
    private final List<List<String>> rows;

    public JobRecord(int id, String schemaName, int rowCount, String createdAt,
                     List<String> headers, List<List<String>> rows) {
        this.id = id;
        this.schemaName = schemaName;
        this.rowCount = rowCount;
        this.createdAt = createdAt;
        this.headers = headers;
        this.rows = rows;
    }

    public int getId() {
        return id;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }
}
