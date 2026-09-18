# Phase 2 — Spring Boot Metrics with Prometheus

## Grafana Observability Lab

Phase 2 introduces **application metrics monitoring using Micrometer and Prometheus**.

In Phase 1, we created the basic application environment using:

* Spring Boot
* PostgreSQL
* Docker
* Docker Compose
* Spring Boot Actuator

In Phase 2, we instrument the Spring Boot application with **Micrometer**, expose metrics through Spring Boot Actuator, and configure **Prometheus** to collect and store those metrics.

---

# 1. Phase 2 Objective

The objective of this phase is to understand the complete metrics flow:

```text
User Request
     |
     v
Spring Boot Application
     |
     v
Micrometer
     |
     v
Spring Boot Actuator
     |
     | /actuator/prometheus
     |
     v
Prometheus
     |
     v
Prometheus TSDB
```

By completing this phase, we will be able to monitor:

* HTTP request count
* Request rate
* HTTP 5xx errors
* Request duration
* JVM memory
* JVM threads
* CPU utilization
* Application uptime
* Garbage collection metrics

Grafana will be introduced in Phase 3.

---

# 2. Architecture

```text
                       User
                        |
                        |
                  localhost:8080
                        |
                        v
              +--------------------+
              |                    |
              |    Spring Boot     |
              |       Demo         |
              |                    |
              +---------+----------+
                        |
                  JDBC  |
                        v
              +--------------------+
              |                    |
              |    PostgreSQL      |
              |                    |
              +--------------------+


                  METRICS FLOW

              Spring Boot Application
                        |
                        |
                    Micrometer
                        |
                        v
              /actuator/prometheus
                        ^
                        |
                 Scrape every 15s
                        |
              +---------+----------+
              |                    |
              |    Prometheus      |
              |      :9090         |
              |                    |
              +--------------------+
                        |
                        v
                 Prometheus TSDB
```

---

# 3. Why PostgreSQL Is Included

PostgreSQL is **not required by Prometheus**.

It is the database dependency of our demo Spring Boot application.

We keep PostgreSQL because the goal of this project is eventually to demonstrate observability for a realistic application architecture.

Later phases can use the database to demonstrate scenarios such as:

```text
Slow API
   |
   v
Spring Boot
   |
   v
Slow database operation
   |
   v
High latency detected
   |
   v
Grafana investigation
   |
   v
Trace identifies database latency
```

This will become particularly useful when distributed tracing is introduced.

---

# 4. Technology Stack

| Technology           | Purpose                                      |
| -------------------- | -------------------------------------------- |
| Java 17              | Application language                         |
| Spring Boot          | Demo REST API                                |
| Spring Boot Actuator | Application management and metrics endpoints |
| Micrometer           | Metrics instrumentation                      |
| PostgreSQL           | Application database                         |
| Prometheus           | Metrics collection and storage               |
| PromQL               | Prometheus query language                    |
| Docker               | Containerization                             |
| Docker Compose       | Multi-container environment                  |

---

# 5. Repository Structure

After Phase 2:

```text
grafana-observability-lab/
│
├── demo/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       └── resources/
│   │
│   ├── pom.xml
│   └── Dockerfile
│
├── prometheus/
│   └── prometheus.yml
│
├── docs/
│   ├── phase-01.md
│   └── phase-02-prometheus.md
│
├── docker-compose.yml
├── .gitignore
└── README.md
```

---

# 6. Adding Micrometer Prometheus Registry

Add the following dependency to:

```text
demo/pom.xml
```

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Spring Boot Actuator should also be present:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

The metrics architecture becomes:

```text
Spring Boot
     |
     v
Spring Boot Actuator
     |
     v
Micrometer
     |
     v
Prometheus-formatted metrics
```

---

# 7. Configure Spring Boot Actuator

Open:

```text
demo/src/main/resources/application.properties
```

Configure:

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
```

This exposes:

```text
/actuator/health

/actuator/info

/actuator/metrics

/actuator/prometheus
```

The most important endpoint for Prometheus is:

```text
/actuator/prometheus
```

---

# 8. Verify Metrics Locally

After rebuilding the application, verify:

```bash
curl http://localhost:8080/actuator/metrics
```

This returns the metrics available through Micrometer.

Examples include:

```text
application.started.time
application.ready.time

http.server.requests

jvm.memory.used
jvm.memory.max

jvm.threads.live

process.cpu.usage
process.uptime

system.cpu.usage
```

---

# 9. Prometheus Metrics Endpoint

Verify:

```bash
curl http://localhost:8080/actuator/prometheus
```

Unlike `/actuator/metrics`, this endpoint exposes metrics in a format Prometheus understands.

Example metrics:

```text
jvm_memory_used_bytes

jvm_memory_max_bytes

jvm_threads_live_threads

process_cpu_usage

system_cpu_usage

http_server_requests_seconds_count

http_server_requests_seconds_sum
```

---

# 10. Prometheus Configuration

Create:

```text
prometheus/prometheus.yml
```

Configuration:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:

  - job_name: "spring-boot-demo"

    metrics_path: "/actuator/prometheus"

    static_configs:
      - targets:
          - "demo:8080"
```

---

# 11. Why We Use `demo:8080`

Our Docker Compose service is named:

```yaml
demo:
```

Docker Compose provides DNS resolution between containers.

Therefore:

```text
Prometheus
     |
     |
     | http://demo:8080/actuator/prometheus
     |
     v
Spring Boot
```

Prometheus should **not** use:

```text
localhost:8080
```

because inside the Prometheus container:

```text
localhost
    =
Prometheus container itself
```

Instead:

```text
demo
    =
Spring Boot container
```

---

# 12. Complete Docker Compose Configuration

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


volumes:
  postgres-data:
  prometheus-data:
```

---

# 13. Docker Communication

The containers communicate through the Docker Compose network.

```text
                Docker Compose Network

        +--------------------------+
        |                          |
        | PostgreSQL               |
        | Service: postgres        |
        | Port: 5432               |
        |       ^                  |
        |       |                  |
        |       | JDBC             |
        |       |                  |
        | Spring Boot              |
        | Service: demo            |
        | Port: 8080               |
        |       ^                  |
        |       |                  |
        |       | HTTP Scrape      |
        |       |                  |
        | Prometheus               |
        | Service: prometheus      |
        | Port: 9090               |
        |                          |
        +--------------------------+
```

Communication:

```text
Spring Boot → PostgreSQL

postgres:5432
```

and:

```text
Prometheus → Spring Boot

demo:8080/actuator/prometheus
```

---

# 14. Validate Docker Compose

Before starting the environment:

```bash
docker compose config
```

If the configuration is valid, rebuild:

```bash
docker compose down
```

```bash
docker compose build
```

Start:

```bash
docker compose up -d
```

Check:

```bash
docker compose ps
```

Expected containers:

```text
observability-postgres
observability-demo
observability-prometheus
```

---

# 15. Verify Spring Boot

Test application health:

```bash
curl http://localhost:8080/api/health
```

Test Actuator:

```bash
curl http://localhost:8080/actuator/health
```

Test Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

---

# 16. Access Prometheus

Open:

```text
http://localhost:9090
```

Prometheus should display its web interface.

Check target health using:

```text
http://localhost:9090/targets
```

Expected target:

```text
Job:

spring-boot-demo


Endpoint:

http://demo:8080/actuator/prometheus


State:

UP
```

`UP` confirms that Prometheus is successfully collecting metrics from the Spring Boot application.

---

# 17. Understanding Prometheus Scraping

Prometheus primarily uses a **pull model**.

Spring Boot does not continuously send metrics to Prometheus.

Instead:

```text
Every 15 seconds

Prometheus
     |
     | HTTP GET
     |
     | /actuator/prometheus
     |
     v
Spring Boot
     |
     |
     | metrics response
     |
     v
Prometheus
```

The interval is controlled by:

```yaml
scrape_interval: 15s
```

---

# 18. Prometheus `up` Metric

Run:

```promql
up
```

Expected result:

```text
up{job="spring-boot-demo"} 1
```

Meaning:

```text
1 = Prometheus successfully scraped target

0 = Prometheus could not scrape target
```

This is one of the simplest and most useful Prometheus health metrics.

---

# 19. JVM Memory Monitoring

Query:

```promql
jvm_memory_used_bytes
```

This shows JVM memory consumption.

For heap memory:

```promql
sum(jvm_memory_used_bytes{area="heap"})
```

This allows us to monitor how much JVM heap the application is currently consuming.

---

# 20. CPU Monitoring

Java process CPU:

```promql
process_cpu_usage
```

System CPU:

```promql
system_cpu_usage
```

Conceptually:

```text
process_cpu_usage

CPU consumed by the Java process
```

while:

```text
system_cpu_usage

CPU utilization of the system/environment
visible to the JVM
```

---

# 21. JVM Thread Monitoring

Query:

```promql
jvm_threads_live_threads
```

This shows the number of currently active JVM threads.

Other JVM metrics can later help investigate:

* Thread growth
* Memory pressure
* Garbage collection
* Application saturation
* Resource exhaustion

---

# 22. Generate Normal Traffic

Generate successful requests:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health
```

Windows PowerShell can generate more traffic:

```powershell
1..50 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/health
}
```

---

# 23. HTTP Request Rate

Micrometer exposes an HTTP request counter such as:

```text
http_server_requests_seconds_count
```

Query:

```promql
rate(http_server_requests_seconds_count[1m])
```

For the total application request rate:

```promql
sum(rate(http_server_requests_seconds_count[5m]))
```

Conceptually:

```text
Requests
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
Requests / second
```

---

# 24. Generate HTTP Errors

The demo application contains an endpoint that intentionally generates an HTTP 500 error:

```text
/api/error
```

Generate errors:

```bash
curl http://localhost:8080/api/error
```

PowerShell:

```powershell
1..10 | ForEach-Object {
    try {
        Invoke-WebRequest http://localhost:8080/api/error
    } catch {}
}
```

---

# 25. Monitor HTTP 5xx Errors

Query:

```promql
sum(
  rate(
    http_server_requests_seconds_count{status=~"5.."}[5m]
  )
)
```

This calculates the HTTP 5xx request rate.

Later this will become a Grafana dashboard panel.

```text
HTTP 5xx Error Rate
```

---

# 26. Generate Slow Requests

The demo application also provides:

```text
/api/slow
```

The endpoint intentionally waits approximately three seconds before returning.

Test:

```bash
curl http://localhost:8080/api/slow
```

PowerShell:

```powershell
1..10 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/slow
}
```

This gives us a controlled way to generate application latency.

---

# 27. Average Request Duration

Query:

```promql
sum(rate(http_server_requests_seconds_sum[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
```

Conceptually:

```text
Total request duration
----------------------
Total number of requests

          =
Average response duration
```

Later we will configure and use histogram data for percentile-based latency such as:

```text
P95 latency

P99 latency
```

---

# 28. RED Monitoring Method

A major concept introduced in this phase is the **RED method**.

RED stands for:

```text
R = Rate

E = Errors

D = Duration
```

It is particularly useful for service/application monitoring.

---

## Rate

Question:

> How much traffic is the application receiving?

PromQL:

```promql
sum(rate(http_server_requests_seconds_count[5m]))
```

---

## Errors

Question:

> How many requests are failing?

PromQL:

```promql
sum(
  rate(
    http_server_requests_seconds_count{status=~"5.."}[5m]
  )
)
```

---

## Duration

Question:

> How long are requests taking?

PromQL:

```promql
sum(rate(http_server_requests_seconds_sum[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
```

---

# 29. RED Architecture

```text
                   Spring Boot
                        |
                        v
                    Micrometer
                        |
          +-------------+-------------+
          |             |             |
          v             v             v
        RATE          ERRORS       DURATION
          |             |             |
          +-------------+-------------+
                        |
                        v
                    Prometheus
```

These metrics will become the foundation of our application Grafana dashboard.

---

# 30. Infrastructure vs Application Metrics

At this stage we primarily collect **application and JVM metrics**.

Examples:

```text
Application Metrics

HTTP requests
HTTP errors
Request duration
Application uptime
```

JVM metrics:

```text
JVM Metrics

Heap memory
Non-heap memory
Threads
Garbage collection
Java process CPU
```

Later we will add infrastructure/container metrics such as:

```text
Container CPU
Container memory
Container network
Container restart information
Host resources
```

---

# 31. Prometheus Time-Series Database

Prometheus stores collected metrics in its own time-series database.

Inside the container:

```text
/prometheus
```

Docker Compose maps this location to:

```text
prometheus-data
```

Architecture:

```text
Prometheus
     |
     v
Prometheus TSDB
     |
     v
prometheus-data
     |
     v
Docker Volume
```

---

# 32. Verify Docker Volumes

Run:

```bash
docker volume ls
```

You should see volumes associated with:

```text
postgres-data

prometheus-data
```

Stopping the environment:

```bash
docker compose down
```

does not normally remove these named volumes.

Starting again:

```bash
docker compose up -d
```

allows Prometheus and PostgreSQL to continue using their persisted data.

---

# 33. Removing All Lab Data

To remove containers and Compose-managed volumes:

```bash
docker compose down -v
```

Be careful with this command.

It removes the persistent data associated with the lab, including PostgreSQL and Prometheus data.

---

# 34. Prometheus Troubleshooting

If the Prometheus target shows:

```text
DOWN
```

start troubleshooting with:

```bash
docker compose ps
```

Check Prometheus logs:

```bash
docker logs observability-prometheus
```

Check application logs:

```bash
docker logs observability-demo
```

Verify the application metrics endpoint from the host:

```bash
curl http://localhost:8080/actuator/prometheus
```

Check Docker networks:

```bash
docker network ls
```

Inspect the Compose network:

```bash
docker network inspect grafana-observability-lab_default
```

Both `demo` and `prometheus` should be connected to the same Compose network.

---

# 35. Common Problem — Using localhost

Incorrect Prometheus target:

```yaml
targets:
  - "localhost:8080"
```

Inside the Prometheus container:

```text
localhost
     |
     v
Prometheus itself
```

Prometheus therefore cannot reach the Spring Boot application through that address.

Correct:

```yaml
targets:
  - "demo:8080"
```

Docker DNS resolves:

```text
demo
 |
 v
Spring Boot container
```

---

# 36. Useful Docker Commands

Check services:

```bash
docker compose ps
```

Check running containers:

```bash
docker ps
```

Application logs:

```bash
docker logs observability-demo
```

Follow application logs:

```bash
docker logs -f observability-demo
```

Prometheus logs:

```bash
docker logs observability-prometheus
```

Restart Prometheus:

```bash
docker compose restart prometheus
```

Restart Spring Boot:

```bash
docker compose restart demo
```

Stop everything:

```bash
docker compose down
```

Start everything:

```bash
docker compose up -d
```

---

# 37. Phase 2 Troubleshooting Scenario

Suppose Prometheus shows:

```text
spring-boot-demo

State: DOWN
```

Troubleshooting flow:

```text
Prometheus target DOWN
        |
        v
Is demo container running?
        |
        +---- NO ---> Investigate Spring Boot
        |
       YES
        |
        v
Does /actuator/prometheus work?
        |
        +---- NO ---> Check Actuator/Micrometer
        |
       YES
        |
        v
Check prometheus.yml
        |
        v
Is target demo:8080?
        |
        +---- NO ---> Correct target
        |
       YES
        |
        v
Check Docker network
        |
        v
Check Prometheus logs
```

This troubleshooting process is important because observability engineers must understand the telemetry pipeline itself.

---

# 38. Important PromQL Queries

### Target availability

```promql
up
```

### JVM heap memory

```promql
sum(jvm_memory_used_bytes{area="heap"})
```

### JVM threads

```promql
jvm_threads_live_threads
```

### Java process CPU

```promql
process_cpu_usage
```

### System CPU

```promql
system_cpu_usage
```

### Request rate

```promql
sum(rate(http_server_requests_seconds_count[5m]))
```

### HTTP 5xx rate

```promql
sum(
  rate(
    http_server_requests_seconds_count{status=~"5.."}[5m]
  )
)
```

### Average request duration

```promql
sum(rate(http_server_requests_seconds_sum[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
```

---

# 39. Important Concepts Learned

After completing Phase 2, we should understand:

### Micrometer

Micrometer provides application metrics instrumentation for Spring Boot.

### Actuator

Spring Boot Actuator exposes operational endpoints such as:

```text
/actuator/health

/actuator/metrics

/actuator/prometheus
```

### Prometheus

Prometheus periodically collects metrics from configured targets and stores them as time-series data.

### Scraping

Prometheus performs HTTP requests against metrics endpoints.

### Scrape Interval

Our current configuration:

```text
15 seconds
```

means Prometheus attempts to collect metrics every 15 seconds.

### PromQL

PromQL is the query language used to query and aggregate Prometheus metrics.

### RED

```text
Rate
Errors
Duration
```

provides a useful framework for monitoring service health.

---

# 40. Interview Explanation

A concise explanation of this phase:

> I instrumented a Spring Boot application using Micrometer and Spring Boot Actuator and exposed Prometheus-compatible metrics through `/actuator/prometheus`. Prometheus runs as a Docker container and periodically scrapes the application over the Docker Compose network. I used PromQL to analyze request rate, HTTP 5xx errors, request duration, JVM memory, threads, and CPU metrics. I also generated intentional slow and failed requests to understand how application behavior appears in telemetry.

---

# 41. Phase 2 Completion Checklist

Before moving to Phase 3, verify:

* [ ] Spring Boot container is running
* [ ] PostgreSQL container is healthy
* [ ] Prometheus container is running
* [ ] Micrometer Prometheus registry is configured
* [ ] `/actuator/metrics` works
* [ ] `/actuator/prometheus` works
* [ ] Prometheus UI is accessible on port `9090`
* [ ] `spring-boot-demo` target shows `UP`
* [ ] `up` query returns `1`
* [ ] JVM memory metrics are available
* [ ] CPU metrics are available
* [ ] JVM thread metrics are available
* [ ] Normal traffic generates HTTP metrics
* [ ] `/api/error` generates 5xx metrics
* [ ] `/api/slow` affects request-duration metrics
* [ ] Basic PromQL queries work
* [ ] Prometheus data is persisted in a Docker volume

---

# 42. Git Commit

After successfully completing Phase 2:

```bash
git status
```

Add changes:

```bash
git add .
```

Commit:

```bash
git commit -m "feat: add Prometheus monitoring and Spring Boot metrics"
```

Create Phase 2 tag:

```bash
git tag phase-2
```

Push:

```bash
git push
```

Push the tag:

```bash
git push origin phase-2
```

---

# 43. Phase 2 Complete

The observability architecture now looks like:

```text
                  USER
                   |
                   v
             Spring Boot
                   |
          +--------+--------+
          |                 |
          v                 v
      PostgreSQL        Micrometer
                            |
                            v
                  /actuator/prometheus
                            ^
                            |
                            | Scrape
                            |
                       Prometheus
                            |
                            v
                     Prometheus TSDB
```

At this point we have **metrics collection and storage**, but we do not yet have a production-style visualization layer.

---

# Next — Phase 3

## Grafana Dashboards

Phase 3 will introduce **Grafana** on top of Prometheus.

The architecture will become:

```text
Spring Boot
     |
     v
Micrometer
     |
     v
Prometheus
     |
     v
Grafana
```

We will build our own dashboard containing:

```text
+------------------------------------------------+
|            APPLICATION OVERVIEW                |
+------------+------------+----------------------+
| Request    | 5xx Error  | Application          |
| Rate       | Rate       | Availability         |
+------------+------------+----------------------+

+------------------------------------------------+
|              REQUEST LATENCY                   |
|                                                |
| P95                         P99                 |
+------------------------------------------------+

+----------------------+-------------------------+
| JVM Heap             | CPU Usage               |
+----------------------+-------------------------+
| JVM Threads          | Garbage Collection      |
+----------------------+-------------------------+
```

Phase 3 will focus on turning the raw Prometheus metrics created in this phase into useful **Grafana observability dashboards**.
