-- DataForge: Test Data Generator
-- Database Schema Definition

CREATE DATABASE IF NOT EXISTS dataforge;
USE dataforge;

-- Table to track generation jobs
CREATE TABLE IF NOT EXISTS jobs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    schema_name VARCHAR(100) NOT NULL,
    row_count INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table to store generated rows (JSON or comma-separated string)
CREATE TABLE IF NOT EXISTS generated_rows (
    id INT AUTO_INCREMENT PRIMARY KEY,
    job_id INT NOT NULL,
    row_data TEXT NOT NULL,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);
