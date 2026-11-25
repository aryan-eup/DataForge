package service;

import exception.InvalidSchemaException;
import model.Field;
import model.Schema;
import rules.GenerationRule;

import java.util.*;

// Service responsible for validating schema parameters and generating rows using worker threads.
public class GeneratorService {

    // CONCEPT: Synchronization (shared state protected with synchronized methods)
    private int currentProgress = 0;
    private int totalRows = 0;
    private final List<List<String>> sharedRows = new ArrayList<>();

    // CONCEPT: Synchronization - synchronized reset
    public synchronized void resetProgress(int total) {
        this.currentProgress = 0;
        this.totalRows = total;
        this.sharedRows.clear();
    }

    // CONCEPT: Synchronization - atomic chunk addition and counter increment
    public synchronized void addChunk(List<List<String>> chunk) {
        this.sharedRows.addAll(chunk);
        this.currentProgress += chunk.size();
    }

    // CONCEPT: Synchronization - thread-safe read of progress counter
    public synchronized int getProgress() {
        return this.currentProgress;
    }

    public synchronized int getTotalRows() {
        return this.totalRows;
    }

    public synchronized int getProgressPercentage() {
        if (totalRows <= 0) return 100;
        return (int) Math.min(100, Math.round((currentProgress * 100.0) / totalRows));
    }

    /**
     * Generates rows by partitioning workload across multiple worker threads.
     *
     * @param schema        The schema template (Students, Employees, Products)
     * @param chosenColumns List of columns selected by the user
     * @param rowCount      Total number of rows to generate
     * @param threadCount   Number of worker threads to use
     * @return List of generated rows (each row is a List of string values)
     * @throws InvalidSchemaException if parameters fail validation
     */
    public List<List<String>> generateData(Schema schema, List<String> chosenColumns, int rowCount, int threadCount)
            throws InvalidSchemaException {

        // CONCEPT: Exception handling (validation check throwing custom InvalidSchemaException)
        if (schema == null) {
            throw new InvalidSchemaException("Schema cannot be null.");
        }
        if (rowCount < 1 || rowCount > 100000) {
            throw new InvalidSchemaException("Row count must be between 1 and 100,000. Provided: " + rowCount);
        }
        if (chosenColumns == null || chosenColumns.isEmpty()) {
            throw new InvalidSchemaException("At least one column must be selected.");
        }

        // CONCEPT: Collections (List) & Polymorphism (List<GenerationRule> processed uniformly)
        List<GenerationRule> activeRules = new ArrayList<>();
        Map<String, Field> fieldMap = new HashMap<>();
        for (Field f : schema.getFields()) {
            fieldMap.put(f.getName().toLowerCase(), f);
        }

        for (String col : chosenColumns) {
            Field f = fieldMap.get(col.trim().toLowerCase());
            if (f == null) {
                throw new InvalidSchemaException("Unknown column '" + col + "' for schema '" + schema.getName() + "'.");
            }
            activeRules.add(f.getRule());
        }

        resetProgress(rowCount);

        // Determine thread allocation
        int numThreads = Math.max(1, Math.min(threadCount, Math.max(1, rowCount / 2)));
        int rowsPerThread = rowCount / numThreads;
        int remainder = rowCount % numThreads;

        List<Thread> workers = new ArrayList<>();

        // CONCEPT: Multithreading (Runnable via lambda expression, Thread.join)
        for (int t = 0; t < numThreads; t++) {
            final int countForThisThread = rowsPerThread + (t == numThreads - 1 ? remainder : 0);

            // Thread lambda implementation of Runnable
            Thread worker = new Thread(() -> {
                List<List<String>> localChunk = new ArrayList<>(countForThisThread);
                for (int i = 0; i < countForThisThread; i++) {
                    List<String> row = new ArrayList<>(activeRules.size());
                    // CONCEPT: Polymorphism & Abstraction
                    // Each rule generates its specific column data through the common interface
                    for (GenerationRule rule : activeRules) {
                        row.add(rule.generate());
                    }
                    localChunk.add(row);
                }
                addChunk(localChunk);
            }, "GeneratorWorker-" + t);

            workers.add(worker);
            worker.start();
        }

        // CONCEPT: Multithreading - Wait for all worker threads to complete
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InvalidSchemaException("Generation interrupted: " + e.getMessage(), e);
            }
        }

        synchronized (this) {
            return new ArrayList<>(sharedRows);
        }
    }

    /**
     * Sorts rows by a specified column index using Comparator.
     *
     * @param rows        List of rows to sort
     * @param headers     List of column headers
     * @param colName     Column to sort by
     * @param ascending   True for ascending, false for descending
     */
    // CONCEPT: Comparable / Comparator
    public void sortRows(List<List<String>> rows, List<String> headers, String colName, boolean ascending) {
        if (rows == null || headers == null || colName == null) return;
        int colIndex = -1;
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(colName.trim())) {
                colIndex = i;
                break;
            }
        }
        if (colIndex == -1) return;

        final int idx = colIndex;
        // CONCEPT: Comparator with lambda expression
        Comparator<List<String>> comparator = (r1, r2) -> {
            String v1 = r1.get(idx);
            String v2 = r2.get(idx);

            // Attempt numeric comparison first
            try {
                long n1 = Long.parseLong(v1);
                long n2 = Long.parseLong(v2);
                return Long.compare(n1, n2);
            } catch (NumberFormatException ignored) {
                // CONCEPT: Comparable - natural String lexicographical comparison
                return v1.compareToIgnoreCase(v2);
            }
        };

        if (!ascending) {
            comparator = comparator.reversed();
        }

        rows.sort(comparator);
    }
}
