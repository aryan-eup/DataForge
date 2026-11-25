# ⚡ DataForge: High-Performance Synthetic Test Data Generator & Analytics Platform

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Database](https://img.shields.io/badge/MySQL-8.0%2F9.0-blue?logo=mysql)](https://www.mysql.com/)
[![Dependencies](https://img.shields.io/badge/Dependencies-Zero%20External-success)](#prerequisites)
[![Architecture](https://img.shields.io/badge/Architecture-Multi--Threaded-blueviolet)](#architecture)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**DataForge** is a high-performance synthetic test data generation engine and interactive analytics suite engineered in **Core Java 17+**. It generates mock datasets across multiple domains, partitions workload concurrently across worker threads, stores records in **MySQL** via JDBC transactions and batch inserts, and provides an interactive visual analytics dashboard featuring 4 distinct statistical graph plots.

Built without external dependencies, web frameworks, or ORMs — demonstrating low-level concurrency, transactional database architecture, and custom SVG visual rendering.

---

## 🌟 Key Features

- **⚡ Multi-Threaded Data Engine**: Concurrent row synthesis partitioned across 4 worker threads with `Thread.join()` coordination and synchronized progress tracking.
- **💾 Full Transactional JDBC CRUD**:
  - **Create**: High-speed batch inserts (`addBatch`/`executeBatch` in 500-row chunks) wrapped in atomic transactions (`commit`/`rollback`).
  - **Read**: Dynamic querying of past generation logs and dataset previews.
  - **Update**: In-place dataset renaming via `PUT` endpoints.
  - **Delete**: Transactional cascade deletion of datasets and stored records.
- **📊 Visual Analytics Studio (Pure SVG Plots)**:
  - 📊 **Histogram Bar Chart**: Binned distribution with hover counts and percentages.
  - 🍩 **Donut Share Chart**: Categorical proportions with interactive legend.
  - 📈 **Density / Area Curve**: Value spread and distribution shape visualization.
  - 📦 **Box & Whisker Plot**: Numerical quartile breakdown ($Min$, $Q_1$, $Median$, $Mean$, $Q_3$, $Max$).
- **🔍 Real-Time Dataset Search & Sorting**: Instant client-side row filtering and server-side multi-column sorting.
- **📥 Multi-Format Export**: One-click download of datasets as **CSV** or valid **SQL INSERT** script files, plus quick-copy to clipboard as JSON or CSV.
- **🔌 Zero External Frameworks**: Uses Java's built-in `com.sun.net.httpserver.HttpServer` and a handcrafted manual JSON serializer.

---

## 🏗️ Architecture

For an in-depth breakdown of the concurrency pipeline, synchronization mechanics, and JDBC optimizations, see **[ARCHITECTURE.md](ARCHITECTURE.md)**.

```text
┌─────────────────────────────────────────────────────────┐
│               Single Page Application (SPA)             │
│         HTML5  •  CSS3 (Dark Forge)  •  Vanilla JS      │
└────────────────────────────┬────────────────────────────┘
                             │ Fetch API / JSON
                             ▼
┌─────────────────────────────────────────────────────────┐
│               Java HttpServer (Port 8080)               │
├───────────────┬─────────────────────────┬───────────────┤
│ REST Handlers │ GeneratorService        │ StatsService  │
│ - /generate   │ - 4 Worker Threads      │ - Quartiles   │
│ - /progress   │ - Thread.join()         │ - Histograms  │
│ - /history    │ - Atomic Progress       │ - Categories  │
│ - /stats      │                         │               │
└───────┬───────┴────────────┬────────────┴───────┬───────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐   ┌─────────────────┐   ┌───────────────┐
│   Main.java   │   │ GeneratedDataDAO│   │ ExportService │
│ JSON Builder  │   │ - Batch Inserts │   │ - CSV Writer  │
│ Repository<T> │   │ - ACID Rollback │   │ - SQL Writer  │
└───────────────┘   └────────┬────────┘   └───────────────┘
                             │ JDBC
                             ▼
                    ┌─────────────────┐
                    │ MySQL Database  │
                    │ - jobs          │
                    │ - generated_rows│
                    └─────────────────┘
```

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- **JDK 17 or higher** (e.g. OpenJDK 17, 21 LTS).
- **MySQL Server** (8.0+ or 9.0+).

Verify your Java installation:
```bash
java -version
javac -version
```

### 2. Database Initialization
Execute the included `schema.sql` script to create the database and tables:
```bash
mysql -u root -p < schema.sql
```

Default credentials connect to `127.0.0.1:3306` (or `3307`). You can customize credentials at runtime via environment variables:
```bash
export DB_HOST="127.0.0.1"
export DB_PORT="3306"
export DB_NAME="dataforge"
export DB_USER="root"
export DB_PASS="your_password"
```

### 3. Compile and Run from Terminal

```bash
# 1. Clone repository
git clone https://github.com/aryan-eup/DataForge.git
cd DataForge

# 2. Compile source files with MySQL Connector in classpath
mkdir -p bin
javac -d bin \
  -cp "src:lib/*" \
  src/exception/*.java \
  src/rules/*.java \
  src/model/*.java \
  src/util/*.java \
  src/dao/*.java \
  src/service/*.java \
  src/server/*.java

# 3. Launch server
java -cp "bin:lib/*" server.Main
```

Open your browser and navigate to:
👉 **`http://localhost:8080/`**

---

## 📡 REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/generate?schema=students&rows=1000&cols=id,name,email` | Generates data, saves to MySQL, returns JSON |
| `GET` | `/progress` | Returns live generation progress counter |
| `GET` | `/history` | Returns list of past jobs stored in MySQL |
| `GET` | `/job?id=1` | Retrieves records for a specific job |
| `GET` | `/job?id=1&sort=age&asc=true` | Retrieves job records sorted by column |
| `GET` | `/stats?id=1&col=age` | Computes statistical metrics and chart distribution |
| `GET` | `/export?id=1&format=csv` | Streams dataset as downloadable CSV file |
| `GET` | `/export?id=1&format=sql` | Streams dataset as SQL INSERT script |
| `PUT` | `/job?id=1&name=NewTitle` | Renames a job record in MySQL |
| `DELETE` | `/job?id=1` | Deletes job and its associated rows from MySQL |
| `GET` | `/schemas` | Returns metadata of available domain schemas |

---

## 📁 Repository Layout

```text
DataForge/
├── src/
│   ├── model/          Field, Schema, JobSummary, JobRecord
│   ├── rules/          GenerationRule (interface), AbstractRule, domain rules
│   ├── service/        GeneratorService, ExportService, StatsService
│   ├── dao/            GeneratedDataDAO (JDBC transactions & batch insert)
│   ├── exception/      InvalidSchemaException
│   ├── util/           DBConnection, Repository<T>
│   └── server/         Main (HttpServer & JSON builder)
├── web/
│   ├── index.html      Single Page Web Application
│   ├── style.css       Dark Forge Responsive Design System
│   └── script.js       Interactive SVG Plotting Engine & Fetch API Client
├── lib/
│   └── mysql-connector-j-8.3.0.jar
├── schema.sql          MySQL Table Definitions
├── ARCHITECTURE.md     Deep-dive technical documentation
└── README.md           Project Overview & Quickstart
```

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
