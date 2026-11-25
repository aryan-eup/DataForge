package model;

import java.sql.Timestamp;

// Model representing a high-level summary of a generation job in history
public class JobSummary {

    private final int id;
    private final String schemaName;
    private final int rowCount;
    private final String createdAt;

    public JobSummary(int id, String schemaName, int rowCount, String createdAt) {
        this.id = id;
        this.schemaName = schemaName;
        this.rowCount = rowCount;
        this.createdAt = createdAt;
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
}
