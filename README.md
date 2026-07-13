# 📊 Spring Boot — Prometheus & Grafana Observability

> A complete, production-style observability example built with **Spring Boot 3.3.5**, **Micrometer**, **Prometheus**, and **Grafana**.  
> Covers JVM metrics, HTTP metrics, and custom business metrics — all auto-scraped and visualised in a pre-built dashboard.

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Quick Start](#quick-start)
5. [API Reference with cURL & Expected Output](#api-reference)
   - [Orders API](#1-orders-api)
   - [Inventory API](#2-inventory-api)
   - [User / Session API](#3-user--session-api)
   - [External API Simulation](#4-external-api-simulation)
   - [Actuator Endpoints](#5-actuator--metrics-endpoints)
6. [Prometheus — Querying Metrics](#prometheus--querying-metrics)
7. [Grafana — Dashboard Guide](#grafana--dashboard-guide)
8. [Metrics Deep Dive](#metrics-deep-dive)
   - [JVM Memory](#jvm-memory)
   - [CPU & Threads](#cpu--threads)
   - [Garbage Collection](#garbage-collection)
   - [HTTP Request Metrics](#http-request-metrics)
   - [Custom Business Metrics](#custom-business-metrics)
9. [Load Testing](#load-testing)
10. [What Behaviour to Expect](#what-behaviour-to-expect)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Network: observability             │
│                                                             │
│  ┌──────────────────┐   scrape every 5s    ┌─────────────┐ │
│  │  Spring Boot App │◄─────────────────────│ Prometheus  │ │
│  │  :8080           │  /actuator/prometheus │ :9090       │ │
│  │                  │                       └──────┬──────┘ │
│  │  ┌────────────┐  │                             │datasrc  │
│  │  │ Micrometer │  │                       ┌──────▼──────┐ │
│  │  │ Registry   │  │                       │   Grafana   │ │
│  │  └────────────┘  │                       │   :3000     │ │
│  └──────────────────┘                       └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**How it works:**
- The Spring Boot app exposes metrics at `/actuator/prometheus` in **OpenMetrics / Prometheus text format**
- Prometheus **scrapes** (pulls) that endpoint every 5 seconds and stores time-series data
- Grafana connects to Prometheus as a **datasource** and renders the pre-built dashboard

---

## Tech Stack

| Component | Version | Role |
|---|---|---|
| Spring Boot | 3.3.5 | Application framework |
| Micrometer | 1.12.x (bundled) | Metrics instrumentation API |
| micrometer-registry-prometheus | 1.12.x | Prometheus format exporter |
| spring-boot-starter-actuator | 3.3.5 | Exposes `/actuator/prometheus` |
| Prometheus | 2.51.2 | Metrics scraping & storage |
| Grafana | 10.4.2 | Metrics visualisation |
| Java | 17 | Runtime |
| Maven | 3.x | Build tool |

---

## Project Structure

```
prom-grafana/
├── Dockerfile                              # Multi-stage build (JDK build → JRE runtime)
├── docker-compose.yml                      # Spins up app + Prometheus + Grafana
├── prometheus.yml                          # Scrape config (5s interval, /actuator/prometheus)
├── pom.xml                                 # Spring Boot + actuator + micrometer deps
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/prometheus.yml      # Auto-registers Prometheus as datasource
│   │   └── dashboards/dashboard.yml        # Tells Grafana where to find dashboard JSON
│   └── dashboards/
│       └── spring-boot-observability.json  # 15-panel pre-built dashboard
└── src/main/java/com/prom_grafana/
    ├── PromGrafanaApplication.java
    ├── config/
    │   └── MetricsConfig.java              # Applies common tags to ALL metrics
    ├── model/
    │   └── Order.java
    ├── service/
    │   ├── MetricsService.java             # Central hub: Counters, Gauges, Timers
    │   └── LoadSimulatorService.java       # Auto-generates traffic (no manual curls needed)
    └── controller/
        ├── OrderController.java            # POST/GET/DELETE /api/orders
        ├── InventoryController.java        # GET/PUT /api/inventory
        ├── UserController.java             # POST /api/users/login|logout
        └── ExternalApiController.java      # POST /api/external/payment (simulates gateway)
```

---

## Quick Start

### Prerequisites

- Docker + Docker Compose installed
- Port `8080`, `9090`, `3000` free

### Start the full stack

```bash
git clone https://github.com/tapeshchavle/prometheus-grafana.git
cd prometheus-grafana

docker compose up --build
```

> First run downloads images + builds the JAR (~2-3 min). Subsequent runs start in ~15s.

### Service URLs

| Service | URL | Credentials |
|---|---|---|
| Spring Boot App | http://localhost:8080 | — |
| Prometheus UI | http://localhost:9090 | — |
| Grafana UI | http://localhost:3000 | `admin` / `admin` |
| Raw Metrics | http://localhost:8080/actuator/prometheus | — |
| Health Check | http://localhost:8080/actuator/health | — |

### Run locally (without Docker)

```bash
./mvnw spring-boot:run
# App starts at http://localhost:8080
```

---

## API Reference

> All examples below assume the app is running at `http://localhost:8080`.  
> The **Load Simulator** already calls these automatically every few seconds — you can also call them manually.

---

### 1. Orders API

#### `POST /api/orders` — Create an order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"product": "widget", "quantity": 3}' | jq
```

**Expected Output (Success — 85% of the time):**
```json
{
  "id": "a3f2c1d4-7e89-4b2a-9c3d-1e4f5a6b7c8d",
  "product": "widget",
  "quantity": 3,
  "status": "COMPLETED",
  "createdAt": "2024-07-13T16:45:01.123Z"
}
```
HTTP Status: `201 Created`

**Expected Output (Failure — ~15% random chance):**
```json
{
  "error": "Validation failed: invalid product code"
}
```
HTTP Status: `400 Bad Request`

> 💡 **What metrics this fires:**
> - `orders_created_total` ↑ (on success)
> - `orders_failed_total` ↑ (on failure)
> - `order_processing_duration_seconds` — histogram recorded every time
> - `orders_active` — gauge spikes to 1 during processing, drops back to 0
> - `inventory_items_total` ↓ by quantity consumed
> - `inventory_low_stock_alerts_total` ↑ if stock drops below 20

---

#### `GET /api/orders` — List all orders

```bash
curl -s http://localhost:8080/api/orders | jq
```

**Expected Output:**
```json
[
  {
    "id": "a3f2c1d4-7e89-4b2a-9c3d-1e4f5a6b7c8d",
    "product": "widget",
    "quantity": 3,
    "status": "COMPLETED",
    "createdAt": "2024-07-13T16:45:01.123Z"
  },
  {
    "id": "b9e1d2f3-4a5c-6b7d-8e9f-0a1b2c3d4e5f",
    "product": "gadget",
    "quantity": 1,
    "status": "COMPLETED",
    "createdAt": "2024-07-13T16:45:04.456Z"
  }
]
```

> 💡 Fires: `http_server_requests_seconds_count{uri="/api/orders",method="GET"}`

---

#### `GET /api/orders/{id}` — Get single order

```bash
curl -s http://localhost:8080/api/orders/a3f2c1d4-7e89-4b2a-9c3d-1e4f5a6b7c8d | jq
```

**Expected Output (found):**
```json
{
  "id": "a3f2c1d4-7e89-4b2a-9c3d-1e4f5a6b7c8d",
  "product": "widget",
  "quantity": 3,
  "status": "COMPLETED",
  "createdAt": "2024-07-13T16:45:01.123Z"
}
```

**Expected Output (not found):**
```
HTTP 404 Not Found  (empty body)
```

---

#### `DELETE /api/orders/{id}` — Cancel an order

```bash
curl -s -X DELETE http://localhost:8080/api/orders/a3f2c1d4-7e89-4b2a-9c3d-1e4f5a6b7c8d -v
```

**Expected Output:**
```
HTTP 204 No Content
```

---

### 2. Inventory API

#### `GET /api/inventory` — Current stock level

```bash
curl -s http://localhost:8080/api/inventory | jq
```

**Expected Output (Healthy):**
```json
{
  "itemsInStock": 91,
  "level": "HEALTHY",
  "lowStockThreshold": 20
}
```

**Expected Output (Low stock):**
```json
{
  "itemsInStock": 12,
  "level": "LOW",
  "lowStockThreshold": 20
}
```

> Stock levels: `< 20` = LOW 🔴 | `20–50` = MEDIUM 🟡 | `> 50` = HEALTHY 🟢

---

#### `PUT /api/inventory/consume` — Consume stock (triggers low-stock alert)

```bash
curl -s -X PUT http://localhost:8080/api/inventory/consume \
  -H "Content-Type: application/json" \
  -d '{"quantity": 30}' | jq
```

**Expected Output:**
```json
{
  "consumed": 30,
  "itemsInStock": 61,
  "alert": "OK"
}
```

**When stock drops below 20:**
```json
{
  "consumed": 30,
  "itemsInStock": 5,
  "alert": "LOW STOCK!"
}
```

> 💡 **What metrics this fires:**
> - `inventory_items_total` ↓ (gauge drops)
> - `inventory_low_stock_alerts_total` ↑ (if stock < 20)

---

#### `PUT /api/inventory/restock` — Restock items

```bash
curl -s -X PUT http://localhost:8080/api/inventory/restock \
  -H "Content-Type: application/json" \
  -d '{"quantity": 50}' | jq
```

**Expected Output:**
```json
{
  "restocked": 50,
  "itemsInStock": 55
}
```

> 💡 `inventory_items_total` gauge spikes back up — visible as a sharp upward tick in Grafana

---

### 3. User / Session API

#### `POST /api/users/login` — Login (creates a session)

```bash
# Successful login
curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123"}' | jq
```

**Expected Output (success):**
```json
{
  "message": "Login successful",
  "token": "alice-token-1720884901234",
  "user": "alice"
}
```
HTTP Status: `200 OK`

```bash
# Failed login
curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "wrongpassword"}' | jq
```

**Expected Output (failure):**
```json
{
  "error": "Invalid credentials"
}
```
HTTP Status: `401 Unauthorized`

> **Valid credentials for testing:**
> | Username | Password |
> |---|---|
> | alice | password123 |
> | bob | securepass |
> | carol | mypassword |

> 💡 **What metrics this fires:**
> - `user_login_total{status="success"}` ↑
> - `user_login_total{status="failure"}` ↑
> - `active_user_sessions` ↑ (gauge increments on success)

---

#### `POST /api/users/logout` — Logout (destroys session)

```bash
curl -s -X POST http://localhost:8080/api/users/logout \
  -H "Content-Type: application/json" \
  -d '{"token": "alice-token-1720884901234"}' | jq
```

**Expected Output:**
```json
{
  "message": "Logged out successfully"
}
```

> 💡 `active_user_sessions` gauge ↓ (drops by 1)

---

#### `GET /api/users/sessions` — Active session count

```bash
curl -s http://localhost:8080/api/users/sessions | jq
```

**Expected Output:**
```json
{
  "activeSessions": 2,
  "tokens": [
    "alice-token-1720884901234",
    "bob-token-1720884905678"
  ]
}
```

---

### 4. External API Simulation

#### `POST /api/external/payment` — Simulate payment gateway call

```bash
curl -s -X POST http://localhost:8080/api/external/payment \
  -H "Content-Type: application/json" \
  -d '{"amount": 99.99}' | jq
```

**Expected Output (success — ~80% of time, ~100–800ms delay):**
```json
{
  "status": "APPROVED",
  "transactionId": "TXN-1720884901234",
  "amount": 99.99
}
```
HTTP Status: `200 OK`

**Expected Output (gateway error — ~20% of time):**
```json
{
  "error": "Payment gateway timeout",
  "gateway": "payment-gateway"
}
```
HTTP Status: `502 Bad Gateway`

> 💡 **What metrics this fires:**
> - `external_api_calls_total{service="payment-gateway"}` ↑ every call
> - `external_api_errors_total{service="payment-gateway"}` ↑ on 502
> - `external_api_duration_seconds` — histogram captures 100–800ms latency

---

#### `GET /api/external/status` — Gateway health check

```bash
curl -s http://localhost:8080/api/external/status | jq
```

**Expected Output:**
```json
{
  "gateway": "payment-gateway",
  "status": "UP",
  "latencyMs": 23
}
```

---

### 5. Actuator / Metrics Endpoints

#### Raw Prometheus metrics

```bash
curl -s http://localhost:8080/actuator/prometheus
```

**Expected Output (excerpt — thousands of lines total):**
```
# HELP orders_created_total Total number of orders successfully created
# TYPE orders_created_total counter
orders_created_total{application="prom-grafana",env="dev",team="platform",type="order"} 14.0

# HELP orders_active Number of orders currently being processed
# TYPE orders_active gauge
orders_active{application="prom-grafana",env="dev",team="platform"} 0.0

# HELP order_processing_duration_seconds Time taken to process an order end-to-end
# TYPE order_processing_duration_seconds summary
order_processing_duration_seconds{application="prom-grafana",...,quantile="0.5"}  0.157286912
order_processing_duration_seconds{application="prom-grafana",...,quantile="0.95"} 0.285212672
order_processing_duration_seconds{application="prom-grafana",...,quantile="0.99"} 0.301989888
order_processing_duration_seconds_count{...} 14
order_processing_duration_seconds_sum{...}   2.401038042

# HELP jvm_memory_used_bytes
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",...}     45234176.0
jvm_memory_used_bytes{area="nonheap",...}  68120576.0

# HELP process_cpu_usage
# TYPE process_cpu_usage gauge
process_cpu_usage{...}  0.023847
```

---

#### Filter specific metrics

```bash
# Only custom business metrics
curl -s http://localhost:8080/actuator/prometheus | grep -E "^(orders_|inventory_|active_user|external_api|user_login)"

# JVM memory only
curl -s http://localhost:8080/actuator/prometheus | grep "^jvm_memory"

# HTTP request metrics only
curl -s http://localhost:8080/actuator/prometheus | grep "^http_server_requests"

# GC metrics only
curl -s http://localhost:8080/actuator/prometheus | grep "^jvm_gc"
```

---

#### Health endpoint

```bash
curl -s http://localhost:8080/actuator/health | jq
```

**Expected Output:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 494384795648,
        "free": 279703588864,
        "threshold": 10485760,
        "path": "/app/.",
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

---

#### All available actuator endpoints

```bash
curl -s http://localhost:8080/actuator | jq '.["_links"] | keys'
```

**Expected Output:**
```json
[
  "beans",
  "conditions",
  "configprops",
  "env",
  "health",
  "heapdump",
  "info",
  "loggers",
  "mappings",
  "metrics",
  "prometheus",
  "scheduledtasks",
  "threaddump"
]
```

---

## Prometheus — Querying Metrics

Open **http://localhost:9090** → click the **Graph** tab → paste these queries.

### HTTP Metrics

```promql
# Request rate per second (all endpoints)
rate(http_server_requests_seconds_count{application="prom-grafana"}[1m])

# Error rate (4xx + 5xx)
rate(http_server_requests_seconds_count{application="prom-grafana", status=~"[45].."}[1m])

# 5xx error rate only
rate(http_server_requests_seconds_count{application="prom-grafana", status=~"5.."}[1m])

# P50 latency (median response time)
histogram_quantile(0.50, rate(http_server_requests_seconds_bucket{application="prom-grafana"}[1m]))

# P95 latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{application="prom-grafana"}[1m]))

# P99 latency
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{application="prom-grafana"}[1m]))
```

### Business Metrics

```promql
# Orders created per second
rate(orders_created_total{application="prom-grafana"}[1m])

# Orders failed per second
rate(orders_failed_total{application="prom-grafana"}[1m])

# Order failure ratio
rate(orders_failed_total[1m]) / rate(orders_created_total[1m])

# Active (in-flight) orders right now
orders_active{application="prom-grafana"}

# Order processing P95 latency
histogram_quantile(0.95, rate(order_processing_duration_seconds_bucket{application="prom-grafana"}[1m]))

# Current inventory level
inventory_items_total{application="prom-grafana"}

# Low stock alert rate
rate(inventory_low_stock_alerts_total{application="prom-grafana"}[5m])

# Active user sessions
active_user_sessions{application="prom-grafana"}

# Login success vs failure rate
rate(user_login_total{application="prom-grafana", status="success"}[1m])
rate(user_login_total{application="prom-grafana", status="failure"}[1m])

# External API call rate
rate(external_api_calls_total{application="prom-grafana"}[1m])

# External API error rate
rate(external_api_errors_total{application="prom-grafana"}[1m])

# External API error ratio (%)
100 * rate(external_api_errors_total[1m]) / rate(external_api_calls_total[1m])

# External API P95 latency
histogram_quantile(0.95, rate(external_api_duration_seconds_bucket{application="prom-grafana"}[1m]))
```

### JVM Metrics

```promql
# Heap memory used (in MB)
jvm_memory_used_bytes{application="prom-grafana", area="heap"} / 1024 / 1024

# Heap memory committed
jvm_memory_committed_bytes{application="prom-grafana", area="heap"} / 1024 / 1024

# Heap usage percentage
100 * jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# Non-heap (Metaspace) used
jvm_memory_used_bytes{application="prom-grafana", area="nonheap"} / 1024 / 1024

# Live thread count
jvm_threads_live_threads{application="prom-grafana"}

# Daemon thread count
jvm_threads_daemon_threads{application="prom-grafana"}

# Peak thread count
jvm_threads_peak_threads{application="prom-grafana"}

# GC pause rate (seconds/second spent in GC)
rate(jvm_gc_pause_seconds_sum{application="prom-grafana"}[1m])

# CPU usage (0.0 to 1.0)
process_cpu_usage{application="prom-grafana"}

# System CPU load
system_cpu_usage{application="prom-grafana"}

# App uptime in seconds
process_uptime_seconds{application="prom-grafana"}

# Classes loaded
jvm_classes_loaded_classes{application="prom-grafana"}
```

---

## Grafana — Dashboard Guide

### Opening the Dashboard

1. Go to **http://localhost:3000**
2. Login with `admin` / `admin`
3. Click **Dashboards** (left sidebar) → **Spring Boot Observability** → **prom-grafana**
4. The dashboard opens with all 15 panels auto-populated

### Dashboard Panels Explained

| # | Panel | Metric Used | What to Look For |
|---|---|---|---|
| 1 | HTTP Request Rate | `http_server_requests_seconds_count` | Should be steady ~0.3 req/s from load simulator |
| 2 | HTTP Error Rate (5xx) | filtered by `status=~"5.."` | Spikes when payment gateway returns 502 |
| 3 | Orders Created (total) | `orders_created_total` | Should grow steadily |
| 4 | Active Orders (gauge) | `orders_active` | Spikes to 1 during processing, drops to 0 |
| 5 | HTTP Latency P50/P95/P99 | `http_server_requests_seconds_bucket` | P95 should be ~300ms for /api/orders |
| 6 | HTTP Request Rate by Endpoint | per `uri` + `method` label | Shows breakdown across all 4 controllers |
| 7 | Orders Created vs Failed | both counters as rates | Failed should be ~15% of created |
| 8 | Order Processing Latency | `order_processing_duration_seconds_bucket` | 50–300ms range |
| 9 | Inventory Level | `inventory_items_total` | Sawtooth pattern: gradual drop, sudden restock spike |
| 10 | Active User Sessions | `active_user_sessions` | Stays at 0–3, sessions auto-login from simulator |
| 11 | Login Success vs Failure | `user_login_total` by status tag | ~80% success rate |
| 12 | External API Calls & Errors | both counters | ~20% error rate expected |
| 13 | External API Latency | `external_api_duration_seconds_bucket` | 100–800ms range |
| 14 | JVM Heap Memory | `jvm_memory_used_bytes{area="heap"}` | Sawtooth: rises → GC fires → drops |
| 15 | CPU Usage & GC Pauses | `process_cpu_usage` + GC sum rate | CPU spikes during order processing |

### Changing the Time Range

- Top right → select `Last 5 minutes`, `Last 15 minutes`, `Last 1 hour`
- Click the 🔄 refresh icon → set **Auto-refresh: 5s** to watch live

---

## Metrics Deep Dive

### JVM Memory

The JVM divides memory into two main areas, both visible in Prometheus:

```
jvm_memory_used_bytes{area="heap"}    ← Objects your app creates live here
jvm_memory_used_bytes{area="nonheap"} ← JVM internals: class bytecode (Metaspace), JIT code cache
```

**What you'll observe in Grafana:**

```
Heap (MB)
 80 │     ╭──╮        ╭──╮        ╭──╮
 60 │    ╱    ╲      ╱    ╲      ╱    ╲
 40 │───╱      ╲────╱      ╲────╱      ╲────
 20 │           ╲GC/        ╲GC/        ╲GC/
    └─────────────────────────────────────── time
```

- Heap **climbs** as orders/sessions create objects
- Heap **drops sharply** when Garbage Collector runs (G1GC by default)
- Non-heap (Metaspace) is **flat** — it only grows when new classes are loaded, which happens at startup

**Useful Prometheus queries:**
```promql
# Heap usage %
100 * jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```
A healthy app stays below 80% heap. If it consistently hits 90%+, increase heap with `-Xmx`.

---

### CPU & Threads

#### CPU

```
process_cpu_usage  → only this JVM process (0.0–1.0)
system_cpu_usage   → entire machine (0.0–1.0)
```

**What you'll observe:**

| Event | CPU behaviour |
|---|---|
| Idle | 0.01–0.03 (1–3%) |
| Order created (50–300ms sleep simulation) | 0.03–0.08 |
| Load simulator firing (3 schedulers) | 0.05–0.12 |
| GC cycle running | Brief spike 0.15–0.30 |
| JVM startup | 0.30–0.80 (class loading, JIT compilation) |

> The load simulator uses 3 background threads (`@Scheduled`) firing every 3–7 seconds, so CPU will never be completely idle.

#### Threads

```
jvm_threads_live_threads   → All live threads (including daemon)
jvm_threads_daemon_threads → Background threads (GC, scheduler, etc.)
jvm_threads_peak_threads   → Maximum observed since JVM start
```

**Expected thread counts at runtime:**

| Thread group | Count | Examples |
|---|---|---|
| HTTP Server (Tomcat) | 10–20 | `http-nio-8080-exec-1..10` |
| Scheduler | 3–4 | `scheduling-1` (load simulator) |
| JVM internals | 10–15 | GC threads, JIT compiler, Signal handler |
| Spring Actuator | 1–2 | Background metric collection |
| **Total** | **~25–40** | |

```promql
# Watch thread count live
jvm_threads_live_threads{application="prom-grafana"}
```

**Sawtooth thread pattern** — you may see threads spike during burst requests (Tomcat creates threads up to its max pool of 200) then settle back to base count when requests finish.

---

### Garbage Collection

Spring Boot 3 uses **G1GC** (Garbage-First GC) by default on JDK 17+. There are two types of GC events:

| GC Type | Metric | Frequency | Pause | What it collects |
|---|---|---|---|---|
| Young GC (Minor) | `jvm_gc_pause_seconds{action="end of minor GC"}` | Every few seconds | 5–20ms | Eden + Survivor space |
| Full GC (Major) | `jvm_gc_pause_seconds{action="end of major GC"}` | Rarely | 50–200ms | Entire heap |

**Prometheus queries to observe GC:**

```promql
# GC pause time rate (seconds of GC per second of wall time — should be < 0.05)
rate(jvm_gc_pause_seconds_sum{application="prom-grafana"}[1m])

# Total GC pause count rate
rate(jvm_gc_pause_seconds_count{application="prom-grafana"}[1m])

# Average GC pause duration
rate(jvm_gc_pause_seconds_sum[1m]) / rate(jvm_gc_pause_seconds_count[1m])
```

**What healthy GC looks like:**
- Young GC fires every 5–30 seconds, pauses for < 20ms
- Full GC should be rare (once in minutes or not at all)
- GC pause rate should stay below `0.05` (app spending < 5% of time in GC)

**To trigger visible GC activity:**
```bash
# Rapid order creation causes rapid object allocation → more GC
for i in {1..50}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d '{"product":"widget","quantity":1}' > /dev/null
done
```

---

### HTTP Request Metrics

Spring Boot Actuator auto-instruments **every HTTP request** through a Micrometer filter. No code needed.

**Labels on `http_server_requests_seconds`:**

| Label | Example values | Purpose |
|---|---|---|
| `uri` | `/api/orders`, `/api/users/login` | Which endpoint |
| `method` | `GET`, `POST`, `PUT`, `DELETE` | HTTP verb |
| `status` | `200`, `201`, `400`, `401`, `404`, `502` | Response status |
| `outcome` | `SUCCESS`, `CLIENT_ERROR`, `SERVER_ERROR` | Grouped outcome |
| `application` | `prom-grafana` | Common tag |

**SLO (Service Level Objective) queries:**

```promql
# Availability = non-5xx requests / total requests (target: > 99.5%)
100 * (
  rate(http_server_requests_seconds_count{status!~"5.."}[5m])
  /
  rate(http_server_requests_seconds_count[5m])
)

# Apdex score (target P95 < 300ms = satisfied, < 1200ms = tolerating)
(
  rate(http_server_requests_seconds_bucket{le="0.3"}[5m])
  + 0.5 * rate(http_server_requests_seconds_bucket{le="1.2"}[5m])
) / rate(http_server_requests_seconds_count[5m])
```

---

### Custom Business Metrics

#### Metric Types Used

| Micrometer Type | Prometheus output | When to use |
|---|---|---|
| `Counter` | `_total` suffix, monotonically increasing | Count of events (orders, logins) |
| `Gauge` | Point-in-time value | Current state (active orders, stock level, sessions) |
| `Timer` | `_seconds_count`, `_sum`, `_bucket` | Measure duration + throughput |

#### Counter vs Gauge — Key Difference

```
Counter → only goes UP (e.g., orders_created_total: 0 → 1 → 2 → 3...)
Gauge   → goes UP and DOWN (e.g., orders_active: 0 → 1 → 0 → 2 → 1 → 0)
```

Always use `rate()` on counters in Prometheus:
```promql
# WRONG — shows raw counter value (not useful for rate)
orders_created_total

# RIGHT — shows how fast orders are being created
rate(orders_created_total[1m])
```

#### Common Tags Applied to All Metrics

Every single metric in this app has these extra labels (from `MetricsConfig.java`):
```
application="prom-grafana"
env="dev"
team="platform"
```

This lets you filter dashboards by environment in multi-env setups:
```promql
orders_created_total{env="prod"}  # production only
orders_created_total{env="dev"}   # dev only
```

---

## Load Testing

### Quick burst (manual)

```bash
# Fire 100 order requests in parallel
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"product\":\"widget\",\"quantity\":$((RANDOM % 5 + 1))}" > /dev/null &
done
wait
echo "Done — check Grafana!"
```

**What you'll see in Grafana during this burst:**
- 🔴 `orders_active` gauge jumps to 5–20 (concurrent processing)
- 🟡 `order_processing_duration_seconds P95` rises (thread contention)
- 🟠 `process_cpu_usage` spikes from ~5% to ~30–60%
- 🔵 `jvm_threads_live_threads` increases as Tomcat spawns more workers
- 🟤 `jvm_memory_used_bytes{area="heap"}` climbs rapidly
- 🟢 After burst: heap drops (GC fires), threads reduce, CPU returns to baseline

### Drain inventory to trigger low-stock alerts

```bash
# Consume stock repeatedly until low-stock alert fires
for i in {1..10}; do
  curl -s -X PUT http://localhost:8080/api/inventory/consume \
    -H "Content-Type: application/json" \
    -d '{"quantity": 10}' | jq '.alert'
done
```

**Expected output sequence:**
```
"OK"
"OK"
"OK"
"OK"
"OK"
"OK"
"LOW STOCK!"
"LOW STOCK!"
"LOW STOCK!"
"LOW STOCK!"
```

> Check Prometheus: `inventory_low_stock_alerts_total` should have incremented

### Force payment gateway errors

```bash
# Run 20 payment calls — expect ~4 failures (20% error rate)
ERRORS=0
for i in {1..20}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/external/payment \
    -H "Content-Type: application/json" \
    -d '{"amount": 50.00}')
  [ "$STATUS" = "502" ] && ERRORS=$((ERRORS + 1))
done
echo "Got $ERRORS errors out of 20 calls (~20% expected)"
```

---

## What Behaviour to Expect

### Normal Steady State (no manual load)

The load simulator fires automatically. Here's what steady state looks like:

| Metric | Expected Value | Notes |
|---|---|---|
| HTTP req/s | ~0.2–0.5 | 3 simulators firing every 3–7s |
| CPU usage | 2–8% | Mostly idle, JIT-compiled hot paths |
| Heap used | 30–60 MB | Depends on JVM max heap |
| Heap GC frequency | Every 10–30s | Young GC only |
| GC pause time | < 20ms | G1GC minor collection |
| Live threads | 25–40 | Tomcat pool + schedulers + JVM internals |
| Active orders | 0–1 | One simulator fires every 3s |
| Active sessions | 0–3 | Simulator logs in but doesn't log out |
| Inventory | 0–150 | Sawtooth: consume → restock cycle |
| External API errors | ~20% rate | By design (simulated 502) |

### During Load Burst

| Metric | Expected Change | Why |
|---|---|---|
| CPU usage | 30–70% | Order processing + GC + HTTP handling |
| Heap used | Climbs rapidly | Many concurrent `Order` objects created |
| GC pause rate | Increases | More allocation = more frequent GC |
| Active orders gauge | Spike to 10–50 | All orders in-flight simultaneously |
| HTTP P99 latency | Rises to 500ms–2s | Thread pool contention, longer queue |
| Tomcat threads | Grows to 20–50 | Tomcat spawns workers for concurrent req |
| Order P95 latency | Rises | Threads compete for CPU |

### After Burst Subsides

| Metric | Recovery |
|---|---|
| Active orders | Returns to 0 |
| CPU usage | Returns to 2–8% |
| Heap | GC fires, drops sharply |
| Threads | Return to base count |
| P99 latency | Returns to < 300ms |

---

## Troubleshooting

| Problem | Check | Fix |
|---|---|---|
| No metrics in Grafana | Prometheus → Status → Targets | Check if `prom-grafana-app` target is `UP` |
| Prometheus can't scrape | http://localhost:9090/targets | Ensure app health check passes |
| Dashboard is empty | Data source → Test | Re-save Prometheus datasource |
| App won't start | `docker compose logs app` | Check port 8080 not in use |
| `jvm_memory_max_bytes=-1` | JVM has no max set | Add `-Xmx256m` to JAVA_OPTS |

### Verify Prometheus is scraping

```bash
# Check target status via Prometheus API
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, state: .health, lastScrape: .lastScrape}'
```

**Expected Output:**
```json
{
  "job": "prom-grafana-app",
  "state": "up",
  "lastScrape": "2024-07-13T16:48:03.456Z"
}
```

### Verify a specific metric exists in Prometheus

```bash
curl -s "http://localhost:9090/api/v1/query?query=orders_created_total" | jq '.data.result'
```

**Expected Output:**
```json
[
  {
    "metric": {
      "__name__": "orders_created_total",
      "application": "prom-grafana",
      "env": "dev",
      "team": "platform",
      "type": "order"
    },
    "value": [1720884901.234, "14"]
  }
]
```

---

## Stopping the Stack

```bash
# Stop all containers
docker compose down

# Stop and remove all data (volumes)
docker compose down -v

# Rebuild from scratch
docker compose up --build --force-recreate
```

---

## Key Concepts Recap

| Concept | What it is |
|---|---|
| **Micrometer** | Vendor-neutral metrics API in Java — write once, export to Prometheus/Datadog/CloudWatch |
| **Counter** | Monotonically increasing number — always use `rate()` in Prometheus queries |
| **Gauge** | Current snapshot value — use directly, no `rate()` needed |
| **Timer/Histogram** | Records duration distribution — use `histogram_quantile()` for P95/P99 |
| **Scrape** | Prometheus *pulls* metrics from the app (not push) every 5 seconds |
| **PromQL** | Prometheus Query Language — `rate()`, `histogram_quantile()`, `sum by()` are the most used |
| **Common tags** | Labels added to every metric — `application`, `env`, `team` — enable dashboard filtering |
| **Actuator** | Spring Boot module that exposes operational endpoints (`/health`, `/metrics`, `/prometheus`) |

---

*Built with ❤️ using Spring Boot 3.3.5 + Micrometer + Prometheus + Grafana*
