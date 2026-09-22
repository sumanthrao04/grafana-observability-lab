Below is a detailed Phase 3 README you can save as:

`docs/phase-03-grafana.md`

# Phase 3 — Grafana Observability Dashboard

## Grafana Observability Lab

Phase 3 introduces **Grafana** as the visualization layer for the application metrics collected by Prometheus.

In the previous phases:

```text
Phase 1
Spring Boot + PostgreSQL + Docker

              ↓

Phase 2
Spring Boot + Micrometer
        ↓
Prometheus

              ↓

Phase 3
Spring Boot + Micrometer
        ↓
Prometheus
        ↓
Grafana
```

The main goal of this phase is to transform raw Prometheus metrics into a useful **production-style application observability dashboard**.

---

# 1. Phase 3 Objectives

By completing this phase, we will:

* Run Grafana using Docker
* Configure Prometheus as a Grafana datasource
* Provision the datasource automatically
* Query Prometheus from Grafana
* Create an application observability dashboard
* Implement RED monitoring
* Monitor application availability
* Monitor HTTP request rate
* Monitor HTTP 5xx errors
* Monitor HTTP error percentage
* Monitor HTTP response time
* Monitor JVM heap memory
* Monitor JVM CPU utilization
* Monitor JVM threads
* Monitor garbage collection
* Generate test traffic
* Simulate application errors
* Simulate application latency
* Simulate an application outage
* Export the dashboard as JSON
* Store dashboard configuration in Git

---

# 2. Architecture

At the end of Phase 3, the application architecture is:

```text
                         USER
                          |
                          |
                  http://localhost:8080
                          |
                          v
                +-------------------+
                |                   |
                |    Spring Boot    |
                |       Demo        |
                |                   |
                +---------+---------+
                          |
                          |
                          | JDBC
                          |
                          v
                +-------------------+
                |                   |
                |    PostgreSQL     |
                |                   |
                +-------------------+


                  OBSERVABILITY FLOW


                +-------------------+
                |    Spring Boot    |
                |                   |
                |    Micrometer     |
                +---------+---------+
                          |
                          |
                /actuator/prometheus
                          ^
                          |
                          | scrape every 15s
                          |
                +---------+---------+
                |                   |
                |    Prometheus     |
                |       :9090       |
                |                   |
                +---------+---------+
                          ^
                          |
                          | PromQL
                          |
                +---------+---------+
                |                   |
                |      Grafana      |
                |       :3000       |
                |                   |
                +-------------------+
                          |
                          v
                 Grafana Dashboard
```

---

# 3. Component Responsibilities

| Component      | Responsibility                            |
| -------------- | ----------------------------------------- |
| Spring Boot    | Runs the demo application                 |
| PostgreSQL     | Application database                      |
| Actuator       | Exposes operational endpoints             |
| Micrometer     | Instruments application/JVM metrics       |
| Prometheus     | Scrapes and stores time-series metrics    |
| PromQL         | Queries and aggregates Prometheus metrics |
| Grafana        | Visualizes and explores metrics           |
| Docker         | Runs individual containers                |
| Docker Compose | Manages the complete environment          |

An important concept is that **Grafana does not collect Spring Boot metrics directly**.

The flow is:

```text
Spring Boot
     |
     | metrics
     v
Prometheus
     |
     | PromQL
     v
Grafana
```

Prometheus is responsible for collecting and storing metrics.

Grafana is responsible for querying and visualizing them.

---

# 4. Repository Structure

After Phase 3, the repository should look similar to:

```text
grafana-observability-lab/
│
├── demo/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── prometheus/
│   └── prometheus.yml
│
├── grafana/
│   ├── provisioning/
│   │   └── datasources/
│   │       └── prometheus.yml
│   │
│   └── dashboards/
│       └── spring-boot-observability.json
│
├── docs/
│   ├── phase-01.md
│   ├── phase-02-prometheus.md
│   └── phase-03-grafana.md
│
├── screenshots/
│   ├── phase-02/
│   └── phase-03/
│
├── docker-compose.yml
├── .gitignore
└── README.md
```

---

# 5. Create Grafana Directories

Create:

```text
grafana/
└── provisioning/
    └── datasources/
```

Windows PowerShell:

```powershell
mkdir grafana
mkdir grafana\provisioning
mkdir grafana\provisioning\datasources
mkdir grafana\dashboards
```

---

# 6. Configure Prometheus Datasource

Create:

```text
grafana/provisioning/datasources/prometheus.yml
```

Add:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

This automatically configures Prometheus as a Grafana datasource when Grafana starts.

---

# 7. Why `prometheus:9090` Is Used

Grafana and Prometheus run inside separate Docker containers.

Therefore Grafana should connect using:

```text
http://prometheus:9090
```

and not:

```text
http://localhost:9090
```

Inside the Grafana container:

```text
localhost
   |
   v
Grafana container itself
```

Docker Compose provides internal DNS.

Therefore:

```text
prometheus
    |
    v
Prometheus container
```

The complete communication path is:

```text
Laptop
  |
  | localhost:3000
  v
Grafana container
  |
  | prometheus:9090
  v
Prometheus container
  |
  | demo:8080/actuator/prometheus
  v
Spring Boot container
```

---

# 8. Docker Compose Configuration

The complete environment now contains four services:

```text
postgres
demo
prometheus
grafana
```

Example `docker-compose.yml`:

```yaml
services:

  postgres:
    image: postgres:16
    container_name: observability-postgres

    environment:
      POSTGRES_DB: observability
      POSTGRES_USER: observability
      POSTGRES_PASSWORD: observability123

    ports:
      - "5432:5432"

    volumes:
      - postgres-data:/var/lib/postgresql/data

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U observability -d observability"]
      interval: 10s
      timeout: 5s
      retries: 5


  demo:
    build:
      context: ./demo

    container_name: observability-demo

    ports:
      - "8080:8080"

    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: observability
      DB_USER: observability
      DB_PASSWORD: observability123

    depends_on:
      postgres:
        condition: service_healthy


  prometheus:
    image: prom/prometheus:v3.5.0
    container_name: observability-prometheus

    ports:
      - "9090:9090"

    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus

    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.path=/prometheus"

    depends_on:
      - demo


  grafana:
    image: grafana/grafana:latest
    container_name: observability-grafana

    ports:
      - "3000:3000"

    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin

    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro

    depends_on:
      - prometheus


volumes:
  postgres-data:
  prometheus-data:
  grafana-data:
```

> The `admin/admin` credentials are intended only for this local learning environment. Production credentials should be managed securely.

---

# 9. Validate Docker Compose

Before starting:

```bash
docker compose config
```

If the configuration is valid:

```bash
docker compose up -d
```

Verify:

```bash
docker compose ps
```

Expected services:

```text
observability-postgres       Up (healthy)

observability-demo           Up

observability-prometheus     Up

observability-grafana        Up
```

---

# 10. Verify Existing Components

Before troubleshooting Grafana, confirm the application is healthy.

Spring Boot:

```bash
curl http://localhost:8080/api/health
```

Actuator:

```bash
curl http://localhost:8080/actuator/health
```

Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Prometheus UI:

```text
http://localhost:9090
```

Prometheus targets:

```text
http://localhost:9090/targets
```

Expected:

```text
spring-boot-demo

Endpoint:
http://demo:8080/actuator/prometheus

State:
UP
```

---

# 11. Access Grafana

Open:

```text
http://localhost:3000
```

Login using:

```text
Username: admin

Password: admin
```

Grafana should now be accessible.

---

# 12. Verify Prometheus Datasource

Navigate to:

```text
Connections
    ↓
Data sources
    ↓
Prometheus
```

The datasource should contain:

```text
Name:

Prometheus


URL:

http://prometheus:9090
```

The datasource was created automatically through:

```text
grafana/provisioning/datasources/prometheus.yml
```

This is preferable to manually creating the datasource every time the environment is recreated.

---

# 13. Test Prometheus from Grafana

Navigate to:

```text
Explore
   ↓
Prometheus
```

Run:

```promql
up{job="spring-boot-demo"}
```

Expected:

```text
1
```

This proves:

```text
Grafana
   |
   v
Prometheus
   |
   v
Spring Boot
```

is working.

Also test:

```promql
jvm_memory_used_bytes
```

and:

```promql
process_cpu_usage
```

---

# 14. Create Application Dashboard

Navigate to:

```text
Dashboards
     ↓
New
     ↓
New Dashboard
     ↓
Add Visualization
```

Select:

```text
Prometheus
```

Dashboard name:

```text
Spring Boot Application Observability
```

---

# 15. Dashboard Monitoring Strategy

The dashboard is divided into two major areas:

```text
APPLICATION OBSERVABILITY

RED Metrics
    |
    +--- Rate
    |
    +--- Errors
    |
    +--- Duration


JVM OBSERVABILITY

Resources
    |
    +--- Heap Memory
    |
    +--- CPU
    |
    +--- Threads
    |
    +--- Garbage Collection
```

This prevents the dashboard from becoming a collection of unrelated graphs.

---

# 16. RED Monitoring Method

RED stands for:

```text
R = Rate

E = Errors

D = Duration
```

RED is useful for monitoring request-driven applications and services.

For our Spring Boot application:

```text
RATE

How much traffic is the application receiving?


ERRORS

How many requests are failing?


DURATION

How long are requests taking?
```

---

# 17. Panel 1 — Application Availability

## Purpose

Determine whether Prometheus can currently scrape the Spring Boot application.

PromQL:

```promql
up{job="spring-boot-demo"}
```

Visualization:

```text
Stat
```

Panel title:

```text
Application Availability
```

Configuration:

```text
Unit: none

Min: 0

Max: 1
```

Configure value mappings:

```text
1 → UP

0 → DOWN
```

Concept:

```text
Prometheus successfully scrapes application
                  |
                  v
                up = 1


Prometheus cannot scrape application
                  |
                  v
                up = 0
```

Important:

`up=1` means the Prometheus scrape target is reachable.

It does not guarantee that every application business function is working correctly.

---

# 18. Panel 2 — HTTP Request Rate

## Purpose

Determine how much application traffic is being processed.

PromQL:

```promql
sum(
  rate(
    http_server_requests_seconds_count{
      job="spring-boot-demo",
      uri!~"/actuator.*"
    }[5m]
  )
)
```

Visualization:

```text
Time series
```

Panel title:

```text
HTTP Request Rate
```

Unit:

```text
requests/sec
```

The query calculates:

```text
HTTP counter
     |
     v
rate over 5 minutes
     |
     v
Requests per second
```

---

# 19. Why Actuator Requests Are Excluded

Prometheus periodically calls:

```text
/actuator/prometheus
```

These are monitoring requests rather than normal application/business requests.

Therefore:

```promql
uri!~"/actuator.*"
```

excludes Actuator traffic.

Without this filter, the dashboard could count Prometheus's own scraping activity as application traffic.

---

# 20. Generate Normal Traffic

Windows PowerShell:

```powershell
1..100 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/health
}
```

After generating traffic, the request-rate panel should increase.

The flow is:

```text
100 HTTP requests
       |
       v
Spring Boot
       |
       v
Micrometer counter
       |
       v
Prometheus
       |
       v
rate()
       |
       v
Grafana
```

---

# 21. Panel 3 — HTTP 5xx Error Rate

## Purpose

Monitor server-side HTTP failures.

PromQL:

```promql
sum(
  rate(
    http_server_requests_seconds_count{
      job="spring-boot-demo",
      status=~"5..",
      uri!~"/actuator.*"
    }[5m]
  )
)
```

Visualization:

```text
Time series
```

Panel title:

```text
HTTP 5xx Error Rate
```

Unit:

```text
requests/sec
```

The filter:

```promql
status=~"5.."
```

matches:

```text
500
501
502
503
504
...
```

---

# 22. Generate HTTP 500 Errors

Our application intentionally provides:

```text
/api/error
```

Generate errors using PowerShell:

```powershell
1..20 | ForEach-Object {

    try {

        Invoke-WebRequest http://localhost:8080/api/error

    }
    catch {

    }

}
```

Observe the:

```text
HTTP 5xx Error Rate
```

panel.

It should increase.

---

# 23. Panel 4 — HTTP 5xx Error Percentage

Raw error rate is useful, but error percentage gives additional context.

For example:

```text
10 failed requests out of 20

is very different from

10 failed requests out of 100,000
```

PromQL:

```promql
100 *
sum(
  rate(
    http_server_requests_seconds_count{
      job="spring-boot-demo",
      status=~"5..",
      uri!~"/actuator.*"
    }[5m]
  )
)
/
clamp_min(
  sum(
    rate(
      http_server_requests_seconds_count{
        job="spring-boot-demo",
        uri!~"/actuator.*"
      }[5m]
    )
  ),
  0.000001
)
```

Visualization:

```text
Stat
```

Panel title:

```text
HTTP 5xx Error %
```

Unit:

```text
Percent (0-100)
```

Concept:

```text
             Failed request rate
Error % = ------------------------- × 100
              Total request rate
```

---

# 24. Panel 5 — Average HTTP Response Time

## Purpose

Determine the average amount of time requests spend being processed.

PromQL:

```promql
sum(
  rate(
    http_server_requests_seconds_sum{
      job="spring-boot-demo",
      uri!~"/actuator.*"
    }[5m]
  )
)
/
clamp_min(
  sum(
    rate(
      http_server_requests_seconds_count{
        job="spring-boot-demo",
        uri!~"/actuator.*"
      }[5m]
    )
  ),
  0.000001
)
```

Visualization:

```text
Time series
```

Panel title:

```text
Average HTTP Response Time
```

Unit:

```text
seconds
```

Concept:

```text
Total time spent processing requests
------------------------------------
        Number of requests

                 =
       Average response time
```

---

# 25. Generate Slow Requests

The application provides:

```text
/api/slow
```

This endpoint intentionally waits approximately three seconds.

Generate slow traffic:

```powershell
1..10 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/slow
}
```

Observe:

```text
Average HTTP Response Time
```

The value should increase.

---

# 26. Average vs P95/P99 Latency

Average response time alone can hide slow requests.

Consider:

```text
Request 1 = 100 ms
Request 2 = 120 ms
Request 3 = 110 ms
Request 4 = 130 ms
Request 5 = 5000 ms
```

The average does not fully describe the user experience.

Production monitoring commonly uses percentiles such as:

```text
P50
P95
P99
```

Before creating percentile panels, verify whether histogram bucket metrics are available:

```promql
http_server_requests_seconds_bucket
```

If no series is returned, histogram publishing needs to be enabled.

Do not create P95/P99 panels until suitable histogram data exists.

This will be improved later in the project.

---

# 27. Panel 6 — JVM Heap Memory Used

## Purpose

Monitor the amount of JVM heap memory currently being used.

PromQL:

```promql
sum(
  jvm_memory_used_bytes{
    job="spring-boot-demo",
    area="heap"
  }
)
```

Visualization:

```text
Time series
```

Panel title:

```text
JVM Heap Memory Used
```

Unit:

```text
bytes
```

Grafana can automatically display the value as:

```text
KiB
MiB
GiB
```

depending on the size.

---

# 28. JVM Heap Concept

Java memory can broadly include:

```text
JVM Memory
    |
    +---- Heap
    |
    +---- Non-Heap
```

Heap is used for Java objects.

Unusual heap growth can indicate:

```text
Heavy object allocation
Memory pressure
Insufficient heap sizing
Possible memory leaks
GC pressure
```

Memory metrics alone do not prove a memory leak; they provide evidence for further investigation.

---

# 29. Panel 7 — JVM Heap Utilization

Absolute memory usage is useful, but utilization percentage makes the metric easier to interpret.

PromQL:

```promql
100 *
sum(
  jvm_memory_used_bytes{
    job="spring-boot-demo",
    area="heap"
  }
)
/
sum(
  jvm_memory_max_bytes{
    job="spring-boot-demo",
    area="heap"
  } > 0
)
```

Visualization:

```text
Gauge
```

Panel title:

```text
JVM Heap Utilization
```

Unit:

```text
Percent (0-100)
```

Configure:

```text
Min = 0

Max = 100
```

Concept:

```text
          Used Heap
Heap % = ----------- × 100
           Max Heap
```

---

# 30. Panel 8 — Java Process CPU Usage

## Purpose

Monitor CPU utilization associated with the Java application process.

PromQL:

```promql
process_cpu_usage{job="spring-boot-demo"} * 100
```

Visualization:

```text
Time series
```

Panel title:

```text
Java Process CPU Usage
```

Unit:

```text
Percent (0-100)
```

Micrometer exposes `process_cpu_usage` as a ratio.

For example:

```text
0.25
```

approximately represents:

```text
25%
```

Therefore:

```promql
process_cpu_usage * 100
```

converts the value into percentage form.

---

# 31. Process CPU vs System CPU

Two useful metrics are:

```promql
process_cpu_usage
```

and:

```promql
system_cpu_usage
```

Conceptually:

```text
process_cpu_usage

        ↓

CPU utilization associated with
the Java application process
```

while:

```text
system_cpu_usage

        ↓

Overall system/environment CPU
utilization visible to the JVM
```

This distinction is useful during troubleshooting.

For example:

```text
System CPU high
Process CPU low

        ↓

Something other than the Java
process may be consuming CPU.
```

---

# 32. Panel 9 — JVM Live Threads

PromQL:

```promql
jvm_threads_live_threads{
  job="spring-boot-demo"
}
```

Visualization:

```text
Time series
```

Panel title:

```text
JVM Live Threads
```

Unit:

```text
none
```

Thread monitoring can help identify:

```text
Unexpected thread growth
Thread exhaustion
Application saturation
Concurrency problems
```

A high thread count alone does not necessarily indicate a problem. It should be interpreted relative to the application's normal baseline and workload.

---

# 33. Panel 10 — JVM Garbage Collection Rate

First verify the metric:

```promql
jvm_gc_pause_seconds_count
```

If available:

```promql
sum(
  rate(
    jvm_gc_pause_seconds_count{
      job="spring-boot-demo"
    }[5m]
  )
)
```

Visualization:

```text
Time series
```

Panel title:

```text
JVM GC Pause Rate
```

This indicates how frequently recorded GC pauses are occurring.

---

# 34. Panel 11 — JVM GC Pause Time

PromQL:

```promql
sum(
  rate(
    jvm_gc_pause_seconds_sum{
      job="spring-boot-demo"
    }[5m]
  )
)
```

Visualization:

```text
Time series
```

Panel title:

```text
JVM GC Pause Time
```

GC metrics become useful when investigating relationships such as:

```text
Heap usage increases
        |
        v
GC activity increases
        |
        v
Application latency increases
```

Correlation does not automatically prove causation, but it gives us useful evidence for investigation.

---

# 35. Recommended Dashboard Layout

A useful dashboard structure is:

```text
+------------------------------------------------------------------+
|                SPRING BOOT APPLICATION OBSERVABILITY             |
+---------------------+---------------------+----------------------+
|                     |                     |                      |
| Application         | HTTP Request        | HTTP 5xx             |
| Availability        | Rate                | Error %              |
|                     |                     |                      |
|        UP            |     req/sec         |        %             |
|                     |                     |                      |
+---------------------+---------------------+----------------------+

+--------------------------------+---------------------------------+
|                                |                                 |
| HTTP Request Rate              | HTTP 5xx Error Rate             |
|                                |                                 |
|         /\                     |                  /\             |
|    ____/  \_______             |             ____/  \____        |
|                                |                                 |
+--------------------------------+---------------------------------+

+------------------------------------------------------------------+
|                                                                  |
|                 Average HTTP Response Time                       |
|                                                                  |
|        normal                        slow requests                |
| _________                         ______/\________                |
|                                                                  |
+------------------------------------------------------------------+

+--------------------------------+---------------------------------+
|                                |                                 |
| JVM Heap Utilization           | Java Process CPU                |
|                                |                                 |
|            %                   |               %                 |
|                                |                                 |
+--------------------------------+---------------------------------+

+--------------------------------+---------------------------------+
|                                |                                 |
| JVM Live Threads               | JVM GC Pause Rate               |
|                                |                                 |
+--------------------------------+---------------------------------+

+------------------------------------------------------------------+
|                       JVM GC Pause Time                          |
+------------------------------------------------------------------+
```

---

# 36. Dashboard Variables

Production dashboards should ideally be reusable across services.

Create a variable:

```text
Dashboard settings
        ↓
Variables
        ↓
Add variable
```

Name:

```text
job
```

Type:

```text
Query
```

Datasource:

```text
Prometheus
```

Query:

```promql
label_values(up, job)
```

The dashboard will now have a job selector.

Instead of hardcoding:

```promql
up{job="spring-boot-demo"}
```

we can use:

```promql
up{job="$job"}
```

Similarly:

```promql
process_cpu_usage{job="$job"} * 100
```

This makes the dashboard reusable.

---

# 37. Why Variables Matter

Today we only have:

```text
spring-boot-demo
```

Later we could have:

```text
api-gateway

user-service

order-service

payment-service
```

Instead of creating four separate dashboards:

```text
Dashboard
    |
    v
Select service
    |
    +---- api-gateway
    |
    +---- user-service
    |
    +---- order-service
```

This is a common Grafana dashboard design pattern.

---

# 38. Scenario 1 — Normal Application Traffic

Generate:

```powershell
1..100 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/health
}
```

Observe:

```text
Application Availability = UP

Request Rate = increases

5xx Error Rate = 0

5xx Error % = 0

Response Time = relatively low
```

This represents normal application behavior.

---

# 39. Scenario 2 — Application Errors

Generate:

```powershell
1..30 | ForEach-Object {

    try {

        Invoke-WebRequest http://localhost:8080/api/error

    }
    catch {

    }

}
```

Observe:

```text
Request Rate
     ↑

HTTP 5xx Error Rate
     ↑

HTTP 5xx Error %
     ↑
```

This demonstrates how an application failure appears in metrics.

---

# 40. Scenario 3 — Application Latency

Generate:

```powershell
1..10 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/slow
}
```

The endpoint intentionally takes approximately three seconds.

Observe:

```text
Average HTTP Response Time
             ↑
```

The troubleshooting question becomes:

```text
Application is UP

        but

Users are experiencing slowness.

Why?
```

Availability alone is therefore not sufficient.

We also need latency and error monitoring.

---

# 41. Scenario 4 — Application Outage

Stop the Spring Boot container:

```bash
docker stop observability-demo
```

Wait longer than the configured Prometheus scrape interval.

Query:

```promql
up{job="spring-boot-demo"}
```

The metric should change:

```text
1
|
v
0
```

Grafana should display:

```text
Application Availability

DOWN
```

Prometheus Targets should also show the target as unavailable.

Restart:

```bash
docker start observability-demo
```

Wait for Prometheus to scrape again.

The value should return to:

```text
1
```

and Grafana should display:

```text
UP
```

---

# 42. Observability Troubleshooting Workflow

The dashboard allows us to start thinking in terms of incidents.

Example:

```text
User reports application problem
              |
              v
        Check availability
              |
              v
        Is application UP?
              |
       +------+------+
       |             |
      NO            YES
       |             |
       v             v
Investigate      Check errors
availability         |
                     v
                Check latency
                     |
                     v
                  Check JVM
                     |
             +-------+-------+
             |       |       |
             v       v       v
            CPU    Heap     GC
```

This is more useful than checking CPU first for every incident.

---

# 43. Understanding the Metrics Pipeline

Our metrics pipeline is now:

```text
                 APPLICATION

                Spring Boot
                     |
                     v
                 Micrometer
                     |
                     v
                  Actuator
                     |
                     |
              /actuator/prometheus
                     |
                     v
                Prometheus
                     |
                     |
                  PromQL
                     |
                     v
                  Grafana
                     |
                     v
                 Dashboard
```

Each component has a specific role.

---

# 44. Grafana Does Not Replace Prometheus

Grafana and Prometheus serve different purposes.

Prometheus:

```text
Collect
   +
Store
   +
Query metrics
```

Grafana:

```text
Query datasource
       +
Visualize
       +
Explore
       +
Dashboard
```

Therefore:

```text
Grafana ≠ Prometheus
```

They complement each other.

---

# 45. Grafana Data Persistence

Docker Compose configures:

```yaml
grafana-data:/var/lib/grafana
```

Grafana stores persistent state in:

```text
/var/lib/grafana
```

The named Docker volume allows that state to survive normal container recreation.

For example:

```bash
docker compose down
```

followed by:

```bash
docker compose up -d
```

should preserve data stored in the volume.

---

# 46. Important Warning About Volumes

Running:

```bash
docker compose down -v
```

removes Compose-managed volumes.

That can remove:

```text
PostgreSQL data

Prometheus historical metrics

Grafana persistent data
```

Do not use `-v` unless intentionally resetting the lab.

---

# 47. Export Grafana Dashboard

Dashboards created only through the Grafana UI should not be the only copy of the configuration.

Export the dashboard JSON from Grafana and save it under:

```text
grafana/
└── dashboards/
    └── spring-boot-observability.json
```

This provides:

```text
Dashboard
    |
    v
JSON
    |
    v
Git
    |
    v
Version Control
```

Benefits include:

```text
Reproducibility

Version history

Code review

Backup

Portfolio evidence

Future automated provisioning
```

---

# 48. Screenshots

Create:

```text
screenshots/
└── phase-03/
```

Recommended screenshots:

```text
grafana-overview.png

normal-traffic.png

error-spike.png

latency-spike.png

application-down.png
```

For example:

```text
screenshots/
│
├── phase-02/
│   └── prometheus-target-up.png
│
└── phase-03/
    ├── grafana-overview.png
    ├── normal-traffic.png
    ├── error-spike.png
    ├── latency-spike.png
    └── application-down.png
```

These screenshots can later be included in the main GitHub README and LinkedIn post.

---

# 49. Grafana Troubleshooting

If Grafana is not running:

```bash
docker compose ps
```

Check:

```bash
docker logs observability-grafana
```

Follow logs:

```bash
docker logs -f observability-grafana
```

---

# 50. Prometheus Datasource Troubleshooting

If Grafana cannot connect to Prometheus, first verify Prometheus from the host:

```text
http://localhost:9090
```

Then check:

```bash
docker compose ps
```

Verify both containers are running:

```text
observability-prometheus

observability-grafana
```

Check Grafana's datasource URL.

Correct:

```text
http://prometheus:9090
```

Incorrect:

```text
http://localhost:9090
```

---

# 51. No Data in Grafana

If a panel displays:

```text
No data
```

do not immediately assume Grafana is broken.

Troubleshoot layer-by-layer:

```text
No Data
   |
   v
Does query work in Grafana Explore?
   |
   +---- NO
   |
   v
Does query work in Prometheus?
   |
   +---- NO
   |
   v
Does metric exist?
   |
   +---- NO
   |
   v
Check /actuator/prometheus
```

Also verify the selected dashboard time range.

For example:

```text
Last 5 minutes
Last 15 minutes
Last 1 hour
```

If traffic was generated outside the selected range, it may not appear.

---

# 52. Useful Troubleshooting Commands

Check containers:

```bash
docker compose ps
```

Application logs:

```bash
docker logs observability-demo
```

Prometheus logs:

```bash
docker logs observability-prometheus
```

Grafana logs:

```bash
docker logs observability-grafana
```

Restart Grafana:

```bash
docker compose restart grafana
```

Restart Prometheus:

```bash
docker compose restart prometheus
```

Restart application:

```bash
docker compose restart demo
```

---

# 53. Core PromQL Reference

## Availability

```promql
up{job="spring-boot-demo"}
```

## Request Rate

```promql
sum(
  rate(
    http_server_requests_seconds_count{
      job="spring-boot-demo",
      uri!~"/actuator.*"
    }[5m]
  )
)
```

## HTTP 5xx Rate

```promql
sum(
  rate(
    http_server_requests_seconds_count{
      job="spring-boot-demo",
      status=~"5..",
      uri!~"/actuator.*"
    }[5m]
  )
)
```

## Error Percentage

```promql
100 *
sum(
  rate(
    http_server_requests_seconds_count{
      job="spring-boot-demo",
      status=~"5..",
      uri!~"/actuator.*"
    }[5m]
  )
)
/
clamp_min(
  sum(
    rate(
      http_server_requests_seconds_count{
        job="spring-boot-demo",
        uri!~"/actuator.*"
      }[5m]
    )
  ),
  0.000001
)
```

## Average Response Time

```promql
sum(
  rate(
    http_server_requests_seconds_sum{
      job="spring-boot-demo",
      uri!~"/actuator.*"
    }[5m]
  )
)
/
clamp_min(
  sum(
    rate(
      http_server_requests_seconds_count{
        job="spring-boot-demo",
        uri!~"/actuator.*"
      }[5m]
    )
  ),
  0.000001
)
```

## Heap Memory

```promql
sum(
  jvm_memory_used_bytes{
    job="spring-boot-demo",
    area="heap"
  }
)
```

## Process CPU

```promql
process_cpu_usage{
  job="spring-boot-demo"
} * 100
```

## Threads

```promql
jvm_threads_live_threads{
  job="spring-boot-demo"
}
```

## GC Pause Rate

```promql
sum(
  rate(
    jvm_gc_pause_seconds_count{
      job="spring-boot-demo"
    }[5m]
  )
)
```

---

# 54. Skills Demonstrated in Phase 3

This phase demonstrates practical experience with:

```text
Grafana

Prometheus

PromQL

Spring Boot Observability

Micrometer

Grafana Datasources

Datasource Provisioning

Grafana Dashboards

RED Metrics

Application Availability

HTTP Traffic Monitoring

HTTP Error Monitoring

Latency Monitoring

JVM Monitoring

CPU Monitoring

Memory Monitoring

Thread Monitoring

Garbage Collection Monitoring

Docker Networking

Docker Compose

Incident Simulation

Observability Troubleshooting
```

---

# 55. Interview Explanation

A concise explanation of this phase:

> I deployed Grafana as part of my Docker Compose observability environment and provisioned Prometheus automatically as its datasource. I built a Spring Boot application observability dashboard using PromQL, focusing on RED metrics—request rate, errors, and duration—along with JVM metrics such as heap utilization, CPU, live threads, and garbage collection. I also simulated HTTP 500 errors, slow requests, and application outages to validate how incidents appear in Grafana.

---

# 56. Example Troubleshooting Explanation

If asked:

> How would you investigate application slowness using your dashboard?

An example approach is:

```text
User reports slowness
        |
        v
Check Application Availability
        |
        v
Application is UP
        |
        v
Check HTTP Response Time
        |
        v
Latency increased
        |
        v
Check Request Rate
        |
        +---- Traffic spike?
        |
        v
Check Error Rate
        |
        v
Check JVM resources
        |
        +---- CPU
        +---- Heap
        +---- Threads
        +---- GC
        |
        v
Identify correlations
```

In later phases, this investigation will continue into:

```text
Metrics
   ↓
Logs
   ↓
Traces
```

---

# 57. Phase 3 Completion Checklist

Before marking Phase 3 complete:

* [ ] Grafana container is running
* [ ] Grafana is accessible on port 3000
* [ ] Prometheus datasource is provisioned
* [ ] Grafana can query Prometheus
* [ ] `up` metric is visible
* [ ] Application Availability panel created
* [ ] HTTP Request Rate panel created
* [ ] HTTP 5xx Error Rate panel created
* [ ] HTTP 5xx Error Percentage panel created
* [ ] Average Response Time panel created
* [ ] JVM Heap Used panel created
* [ ] JVM Heap Utilization panel created
* [ ] Java Process CPU panel created
* [ ] JVM Threads panel created
* [ ] JVM GC panel created
* [ ] Normal traffic tested
* [ ] HTTP 500 traffic tested
* [ ] Slow traffic tested
* [ ] Application outage tested
* [ ] Dashboard JSON exported
* [ ] Dashboard JSON committed to Git
* [ ] Phase 3 screenshots saved

---

# 58. Git Commit

Check:

```bash
git status
```

Add changes:

```bash
git add .
```

Commit:

```bash
git commit -m "feat: add Grafana application observability dashboard"
```

Create Phase 3 tag:

```bash
git tag phase-3
```

Push:

```bash
git push
```

Push tag:

```bash
git push origin phase-3
```

---

# 59. Phase 3 Final Architecture

```text
                    USER
                     |
                     v
               Spring Boot
                     |
            +--------+--------+
            |                 |
            v                 v
       PostgreSQL         Micrometer
                              |
                              v
                     Spring Boot Actuator
                              |
                              v
                    /actuator/prometheus
                              ^
                              |
                         Scrape 15s
                              |
                              |
                         Prometheus
                              |
                              | PromQL
                              |
                              v
                           Grafana
                              |
             +----------------+----------------+
             |                |                |
             v                v                v
          Traffic           Errors          Latency
             |                |                |
             +----------------+----------------+
                              |
                              v
                       JVM Resources
                              |
                +-------------+-------------+
                |             |             |
               CPU           Heap          Threads
                                             |
                                             v
                                             GC
```

At this stage we have implemented the **metrics pillar of observability**.

---

# 60. Project Progress

```text
Phase 1
Application Foundation
Spring Boot + PostgreSQL + Docker
                 |
                 | COMPLETE
                 v

Phase 2
Metrics Collection
Micrometer + Prometheus
                 |
                 | COMPLETE
                 v

Phase 3
Metrics Visualization
Grafana + PromQL + RED Dashboard
                 |
                 | COMPLETE
                 v

Phase 4
Centralized Logging
Grafana Loki + Grafana Alloy
                 |
                 v

Phase 5
Distributed Tracing
OpenTelemetry + Tempo
                 |
                 v

Phase 6
Metrics + Logs + Traces Correlation
                 |
                 v

Phase 7
Grafana Alerting
                 |
                 v

Phase 8
Production Incident Simulation
```

---

# Next Phase — Loki + Grafana Alloy

Phase 4 will add the **logs pillar** of observability.

The architecture will evolve into:

```text
                         Spring Boot
                              |
                 +------------+------------+
                 |                         |
                 v                         v
              Metrics                    Logs
                 |                         |
                 v                         v
            Prometheus              Grafana Alloy
                                           |
                                           v
                                         Loki
                 \                         /
                  \                       /
                   +---------+-----------+
                             |
                             v
                          Grafana
```

This will allow us to investigate an incident like:

```text
Grafana detects
HTTP 500 spike
      |
      v
Check metrics
      |
      v
Identify affected endpoint
      |
      v
Open application logs
      |
      v
Find exception
      |
      v
Determine root cause
```

This is the beginning of **metrics + logs correlation** and moves the project from basic monitoring toward full observability.


