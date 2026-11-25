# DataForge: Architecture & System Design

DataForge is a lightweight, zero-dependency synthetic test data generation and analytics engine built entirely in **Core Java 17+**. It incorporates multi-threaded row synthesis, transactional database persistence with MySQL via JDBC, and an interactive real-time statistical visualization suite.

---

## 🏛️ System Overview

```text
               +-------------------------------------------+
               |        Web UI (HTML5 / CSS3 / Vanilla JS) |
               +-------------------------------------------+
                                     |
                                REST API
                                (HTTP / JSON)
                                     v
+--------------------------------------------------------------------------+
|                       Built-in Java HttpServer                           |
|                      (com.sun.net.httpserver)                            |
+--------------------------------------------------------------------------+
       |                        |                         |
       v                        v                         v
+---------------+      +------------------+      +-------------------+
|  REST Routes  |      | GeneratorService |      |   StatsService    |
|  - /generate  |      |  - 4 Worker      |      |  - Distributions  |
|  - /progress  |      |    Threads       |      |  - Quartiles/IQR  |
|  - /history   |      |  - Synchronized  |      |  - Histogram Bins |
|  - /job       |      |    Progress      |      |  - Category Share |
|  - /stats     |      +------------------+      +-------------------+
|  - /export    |               |                          |
+---------------+               v                          v
       |               +------------------+      +-------------------+
       |               | GeneratedDataDAO |      |   ExportService   |
       |               |  - Batch Inserts |      |  - CSV Streaming  |
       |               |  - Transactions  |      |  - SQL Generation |
       v               +------------------+      +-------------------+
+--------------------------------------------------------------------+
|                           MySQL Database                           |
|      (jobs table: metadata  |  generated_rows: partitioned data)    |
+--------------------------------------------------------------------+
```

---

## ⚡ Concurrency & Multithreading Model

### 1. Workload Partitioning
When generating $N$ rows, `GeneratorService` divides the workload across $T$ worker threads (default: 4):
$$\text{Rows Per Thread} = \lfloor N / T \rfloor, \quad \text{Remainder added to final thread}$$

Each thread runs an isolated `Runnable` task generating its local row slice, eliminating contention during data synthesis.

### 2. Synchronization & Progress State
DataForge protects the shared accumulation pipeline using synchronized methods:
- `synchronized void addChunk(List<List<String>> chunk)`: Atomically appends generated batches and increments the counter.
- `synchronized int getProgress()`: Thread-safe accessor for real-time frontend polling via `GET /progress`.
- `synchronized int getProgressPercentage()`: Normalizes progress for smooth progress bar rendering.

### 3. Thread Coordination
The main orchestrator thread invokes `worker.join()` on each thread instance, ensuring all partitioned tasks terminate cleanly before persisting data to MySQL or initiating streaming responses.

---

## 💾 Database Persistence & JDBC Optimizations

### 1. ACID Transactions
All generation jobs are persisted inside a strict transactional boundary:
```java
conn.setAutoCommit(false);
try {
    // 1. Insert Job Record
    // 2. Batch Insert Generated Rows
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
    throw e;
} finally {
    conn.setAutoCommit(true);
}
```
This guarantees atomicity: if any error occurs during batch insertion, the entire operation is rolled back, preventing orphaned records.

### 2. High-Performance Batch Insertion
Instead of issuing individual `INSERT` statements across the network, DataForge uses `PreparedStatement.addBatch()` and flushes in chunks of 500 rows (`executeBatch()`), dramatically reducing socket round-trips and optimizing MySQL InnoDB buffer pool writes.

---

## 📊 Visual Analytics & Statistical Engine

The `StatsService` inspects generated datasets using the Java 8+ Stream API:
- **Numerical Distribution**: Computes Mean, Median, Min, Max, 25th percentile ($Q_1$), 75th percentile ($Q_3$), and Standard Deviation ($\sigma$).
- **Histogram Binning**: Partitions continuous numeric values into dynamic ranges for frequency visualization.
- **Categorical Proportions**: Groups values by frequency count and percentage share using `Collectors.groupingBy()`.
- **Duplicate Detection**: Identifies unique vs duplicate keys in $O(N)$ time using `HashSet`.

The frontend dynamically visualizes these metrics using pure SVG:
1. **Histogram Bar Chart**: Binned distribution with hover counts.
2. **Donut Share Chart**: Percentage breakdown with interactive slice highlights.
3. **Density / Area Curve**: Distribution shape and spread across categories.
4. **Box & Whisker Plot**: Statistical quartile spread ($Q_1$, Median, $Q_3$, Min, Max, and Mean marker).
