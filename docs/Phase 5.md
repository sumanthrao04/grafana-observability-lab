# Phase 5 — Distributed Tracing with OpenTelemetry + Grafana Tempo

Now your observability project has:

```text
Phase 1 → Spring Boot + PostgreSQL + Docker
Phase 2 → Prometheus Metrics
Phase 3 → Grafana Dashboards
Phase 4 → Loki + Grafana Alloy Logs
Phase 5 → OpenTelemetry + Tempo Traces  ← NOW
```

The main objective of Phase 5 is to understand **what happens to an individual request inside the application**.

---

## 1. Why do we need tracing?

Your current stack can answer:

```text
Prometheus
   ↓
WHAT happened?

"Latency increased"
"5xx errors increased"
"CPU increased"


Loki
   ↓
WHAT was logged?

"RuntimeException"
"Database error"
"Timeout"
```

Tracing adds:

```text
Tempo
   ↓
WHERE did this request spend its time?

Request
   |
   +-- Controller      10 ms
   |
   +-- Service         25 ms
   |
   +-- Database       450 ms
   |
   └-- Total          485 ms
```

This becomes especially valuable when we later introduce multiple microservices.

---

# 2. Phase 5 Architecture

We will add:

**OpenTelemetry Java Agent → OpenTelemetry Collector → Tempo**

The architecture becomes:

```text
                         USER
                           |
                           v
                     Spring Boot
                           |
                      PostgreSQL
                           |
          +----------------+----------------+
          |                |                |
          v                v                v
       METRICS            LOGS            TRACES
          |                |                |
      Micrometer       Docker Logs    OpenTelemetry
          |                |            Java Agent
          v                v                |
     Prometheus          Alloy              v
          |                |          OTel Collector
          |                v                |
          |               Loki              v
          |                                 Tempo
          |                |                |
          +----------------+----------------+
                           |
                           v
                        Grafana
```

---

# 3. Why OpenTelemetry?

OpenTelemetry provides a vendor-neutral standard for generating and transporting telemetry.

In this phase we're primarily using it for:

```text
Application
     |
     v
OpenTelemetry
     |
     v
Traces
```

Later the same ecosystem can participate in handling:

```text
Metrics
Logs
Traces
```

---

# 4. Why Tempo?

Think of the stack like this:

| Signal | Backend |
|---|---|
| Metrics | Prometheus |
| Logs | Loki |
| Traces | Tempo |
| Visualization | Grafana |

So your stack becomes easy to remember:

```text
Prometheus → Metrics
Loki       → Logs
Tempo      → Traces
Grafana    → Visualization
```

---

# 5. Repository Structure

Extend your repository:

```text
grafana-observability-lab/
│
├── demo/
│
├── prometheus/
│   └── prometheus.yml
│
├── loki/
│   └── loki-config.yml
│
├── alloy/
│   └── config.alloy
│
├── tempo/
│   └── tempo.yml
│
├── otel-collector/
│   └── otel-collector.yml
│
├── grafana/
│   ├── provisioning/
│   │   └── datasources/
│   │       ├── prometheus.yml
│   │       ├── loki.yml
│   │       └── tempo.yml
│   └── dashboards/
│
├── screenshots/
│   ├── phase-03/
│   ├── phase-04/
│   └── phase-05/
│
└── docker-compose.yml
```

Create:

```powershell
mkdir tempo
mkdir otel-collector
mkdir screenshots\phase-05
```

---

# 6. Configure Tempo

Create:

```text
tempo/tempo.yml
```

Add:

```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
```

For this local project, Tempo stores traces on local persistent storage.

---

# 7. Add Tempo to Docker Compose

Add:

```yaml
  tempo:
    image: grafana/tempo:latest
    container_name: observability-tempo

    command:
      - "-config.file=/etc/tempo/tempo.yml"

    volumes:
      - ./tempo/tempo.yml:/etc/tempo/tempo.yml:ro
      - tempo-data:/var/tempo

    ports:
      - "3200:3200"
```

Add the volume:

```yaml
volumes:
  postgres-data:
  prometheus-data:
  grafana-data:
  loki-data:
  tempo-data:
```

---

# 8. Add OpenTelemetry Collector

Instead of making every application know about Tempo directly, we'll introduce an OTel Collector.

```text
Spring Boot
     |
     | OTLP
     v
OTel Collector
     |
     | OTLP
     v
Tempo
```

This is useful because the collector becomes the telemetry processing layer.

Later it could perform:

```text
Receive
   ↓
Process
   ↓
Batch
   ↓
Filter
   ↓
Enrich
   ↓
Export
```

---

# 9. Configure OTel Collector

Create:

```text
otel-collector/otel-collector.yml
```

Add:

```yaml
receivers:

  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317

      http:
        endpoint: 0.0.0.0:4318


processors:

  batch:


exporters:

  otlp/tempo:
    endpoint: tempo:4317

    tls:
      insecure: true


service:

  pipelines:

    traces:

      receivers:
        - otlp

      processors:
        - batch

      exporters:
        - otlp/tempo
```

The flow is now:

```text
Application
     |
     | OTLP
     v
Collector :4317
     |
     v
Batch Processor
     |
     v
Tempo :4317
```

---

# 10. Add Collector to Docker Compose

Add:

```yaml
  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    container_name: observability-otel-collector

    command:
      - "--config=/etc/otelcol-contrib/config.yml"

    volumes:
      - ./otel-collector/otel-collector.yml:/etc/otelcol-contrib/config.yml:ro

    ports:
      - "4317:4317"
      - "4318:4318"

    depends_on:
      - tempo
```

---

# 11. Instrument Spring Boot

For this lab, use the **OpenTelemetry Java Agent**.

This is useful because we don't have to manually modify every controller to create spans.

The idea is:

```text
Spring Boot Application
          +
OpenTelemetry Java Agent
          |
          v
Automatic instrumentation
```

It can instrument common Java frameworks and libraries and generate traces automatically.

---

# 12. Add Java Agent to the Docker Image

Download the OpenTelemetry Java agent and place it inside:

```text
demo/
├── src/
├── pom.xml
├── Dockerfile
└── opentelemetry-javaagent.jar
```

Then modify the runtime portion of your Dockerfile.

For example:

```dockerfile
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar

EXPOSE 8080

ENTRYPOINT [
  "java",
  "-javaagent:/app/opentelemetry-javaagent.jar",
  "-jar",
  "app.jar"
]
```

Keep your existing build stage.

---

# 13. Configure Spring Boot Trace Export

Add these environment variables under your `demo` service:

```yaml
environment:
  DB_HOST: postgres
  DB_PORT: 5432
  DB_NAME: observability
  DB_USER: observability
  DB_PASSWORD: observability123

  OTEL_SERVICE_NAME: observability-demo

  OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318

  OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf

  OTEL_TRACES_EXPORTER: otlp

  OTEL_METRICS_EXPORTER: none

  OTEL_LOGS_EXPORTER: none
```

We're intentionally using OpenTelemetry for **traces only** at this stage.

Metrics continue through:

```text
Micrometer
    ↓
Prometheus
```

Logs continue through:

```text
Docker
   ↓
Alloy
   ↓
Loki
```

Traces use:

```text
OpenTelemetry
      ↓
OTel Collector
      ↓
Tempo
```

This separation helps you understand each telemetry pipeline.

---

# 14. Add Tempo Datasource to Grafana

Create:

```text
grafana/provisioning/datasources/tempo.yml
```

Add:

```yaml
apiVersion: 1

datasources:
  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    editable: true
```

Grafana will now have:

```text
Datasources

Prometheus → Metrics
Loki       → Logs
Tempo      → Traces
```

Restart Grafana after adding the datasource:

```bash
docker compose restart grafana
```

---

# 15. Build Everything

Because the Spring Boot Docker image changed:

```bash
docker compose down
```

Then:

```bash
docker compose build demo
```

Start:

```bash
docker compose up -d
```

Verify:

```bash
docker compose ps
```

You should now have roughly:

```text
observability-postgres
observability-demo
observability-prometheus
observability-grafana
observability-loki
observability-alloy
observability-tempo
observability-otel-collector
```

---

# 16. Check OpenTelemetry Agent

Check Spring Boot logs:

```bash
docker logs observability-demo
```

Look for OpenTelemetry Java agent startup information.

Then check Collector:

```bash
docker logs observability-otel-collector
```

And Tempo:

```bash
docker logs observability-tempo
```

You don't want continuous connection or export errors.

---

# 17. Generate Your First Trace

Call:

```bash
curl http://localhost:8080/api/health
```

Then:

```bash
curl http://localhost:8080/api/slow
```

And:

```bash
curl http://localhost:8080/api/error
```

Each HTTP request should produce tracing information.

---

# 18. Find Traces in Grafana

Open:

```text
http://localhost:3000
```

Go to:

```text
Explore
   ↓
Tempo
```

Use the trace search interface.

Look for service:

```text
observability-demo
```

You should begin seeing traces.

---

# 19. What Is a Trace?

Suppose a user calls:

```text
GET /api/slow
```

That entire request can be represented as a:

```text
TRACE
```

Inside a trace are:

```text
SPANS
```

For example:

```text
Trace
│
├── HTTP GET /api/slow
│
├── Controller
│
├── Service
│
└── Database operation
```

A **trace** represents the overall request path.

A **span** represents one operation within that request.

---

# 20. Trace ID

Every trace has a unique:

```text
Trace ID
```

For example:

```text
4bf92f3577b34da6a3ce929d0e0e4736
```

Conceptually:

```text
Request
   |
   v
Trace ID = ABC123
   |
   +---- Span 1
   |
   +---- Span 2
   |
   +---- Span 3
```

Later, this Trace ID becomes very important for correlating logs and traces.

---

# 21. Compare `/health` and `/slow`

Generate:

```bash
curl http://localhost:8080/api/health
```

Then:

```bash
curl http://localhost:8080/api/slow
```

Compare the traces in Tempo.

You should see a clear duration difference.

Conceptually:

```text
/api/health

HTTP request
████

very short
```

versus:

```text
/api/slow

HTTP request
████████████████████████████████

~3 seconds
```

This demonstrates why tracing is useful for latency investigation.

---

# 22. Generate an Error Trace

Call:

```bash
curl http://localhost:8080/api/error
```

Now inspect the corresponding trace.

Depending on the instrumentation and exception handling, you may see attributes/events indicating the request failed.

Your investigation becomes:

```text
HTTP 500
   |
   v
Trace
   |
   v
Failed request
   |
   v
Inspect span details
```

---

# 23. The Three Observability Signals

At this point, your lab has all three primary telemetry signals:

```text
                  APPLICATION
                       |
        +--------------+--------------+
        |              |              |
        v              v              v

     METRICS           LOGS          TRACES

        |              |              |
        v              v              v

   Prometheus         Loki           Tempo

        \              |              /
         \             |             /
          +------------+------------+
                       |
                       v
                    Grafana
```

---

# 24. How Each Signal Helps

Suppose `/api/slow` becomes slow.

### Metrics

Prometheus tells you:

```text
Average Response Time
        ↑
```

Question answered:

> What is happening?

### Logs

Loki might show:

```text
Database timeout
Slow query
Connection error
```

Question answered:

> What diagnostic information did the application produce?

### Traces

Tempo shows:

```text
GET /api/orders          4.2 sec
       |
       +-- Controller     10 ms
       |
       +-- Service        30 ms
       |
       +-- DB query       4.1 sec
```

Question answered:

> Where did the request spend its time?

---

# 25. Important Limitation of the Current Demo

Your application currently has only **one Spring Boot service**.

Therefore, you're learning tracing correctly, but you aren't yet demonstrating a true distributed request across multiple services.

Current:

```text
User
 |
 v
Spring Boot
 |
 v
PostgreSQL
```

Later we can evolve this into:

```text
User
 |
 v
API Gateway
 |
 +----------------+
 |                |
 v                v
User Service   Order Service
                   |
                   v
               PostgreSQL
```

Then one Trace ID can follow a request across several services.

That will be the stronger distributed-tracing demonstration.

---

# 26. Phase 5 Incident Scenario

Generate several normal requests:

```powershell
1..20 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/health
}
```

Then generate slow requests:

```powershell
1..5 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/slow
}
```

Your investigation:

```text
User reports:
"Application is slow"

        ↓

Grafana Metrics

        ↓

Response time increased

        ↓

Open Tempo

        ↓

Find slow trace

        ↓

Inspect span duration

        ↓

Identify where time was spent
```

---

# 27. Phase 5 Error Scenario

Generate:

```powershell
1..10 | ForEach-Object {

    try {
        Invoke-WebRequest http://localhost:8080/api/error
    }
    catch {}

}
```

Then investigate:

```text
Grafana
   |
   v
5xx Error Rate ↑
   |
   v
Prometheus
   |
   v
Something is failing
   |
   v
Loki
   |
   v
Application exception
   |
   v
Tempo
   |
   v
Inspect failed request trace
```

This is the foundation for the next phase.

---

# 28. What Phase 6 Will Improve

Right now you can manually move between:

```text
Metrics
   ↓
Logs
   ↓
Traces
```

Phase 6 will focus on **correlation**.

The goal will be closer to:

```text
Grafana detects latency spike
           |
           v
     Prometheus metric
           |
           v
      Related trace
           |
           v
         Tempo
           |
           v
       Trace ID
           |
           v
     Related logs
           |
           v
          Loki
```

That is where the project starts demonstrating deeper observability skills.

---

# 29. Screenshots to Save

Create:

```text
screenshots/
└── phase-05/
```

Capture:

```text
01-tempo-datasource.png
02-otel-collector-running.png
03-tempo-trace-search.png
04-normal-request-trace.png
05-slow-request-trace.png
06-error-request-trace.png
07-trace-span-details.png
08-complete-observability-stack.png
```

For LinkedIn, the most useful screenshots will be:

```text
Grafana dashboard showing latency
        +
Tempo slow trace
        +
Trace span details
```

That tells an actual troubleshooting story.

---

# 30. Phase 5 Completion Checklist

Before moving to Phase 6:

- [ ] Tempo container running
- [ ] OTel Collector running
- [ ] OpenTelemetry Java Agent attached to Spring Boot
- [ ] Spring Boot starts successfully
- [ ] Collector receives traces
- [ ] Collector exports traces to Tempo
- [ ] Tempo datasource available in Grafana
- [ ] `observability-demo` visible in trace search
- [ ] `/api/health` trace visible
- [ ] `/api/slow` trace visible
- [ ] `/api/error` trace visible
- [ ] Trace ID understood
- [ ] Span concept understood
- [ ] Slow request duration visible
- [ ] Error request investigated
- [ ] Screenshots saved

---

# 31. What You Should Understand for Interviews

You should be able to explain:

**What is distributed tracing?**

> Distributed tracing tracks a request as it moves through application components or services. A trace represents the complete request, while spans represent individual operations performed as part of that request.

**What is OpenTelemetry?**

> OpenTelemetry provides standardized APIs, SDKs, instrumentation and protocols for generating and transporting telemetry such as traces, metrics and logs.

**What does Tempo do?**

> Tempo is the trace backend in my observability stack. OpenTelemetry generates and exports trace telemetry, the OTel Collector processes and forwards it, Tempo stores the traces, and Grafana is used to search and visualize them.

**What does the Collector do?**

```text
Receive telemetry
       ↓
Process telemetry
       ↓
Export telemetry
```

This separation is important in larger environments because applications don't have to be tightly coupled to a particular backend.

---

# 32. Git Commit

Once Phase 5 works:

```bash
git status
```

Then:

```bash
git add .
```

Commit:

```bash
git commit -m "feat: add distributed tracing with OpenTelemetry and Tempo"
```

Tag:

```bash
git tag phase-5
```

Push:

```bash
git push
git push origin phase-5
```

---

## Phase 5 Final Architecture

```text
                         Spring Boot
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
     METRICS                 LOGS                 TRACES
        |                     |                     |
   Micrometer             stdout/stderr        OTel Java Agent
        |                     |                     |
        v                     v                     v
   Prometheus               Alloy             OTel Collector
        |                     |                     |
        |                     v                     v
        |                    Loki                  Tempo
        |                     |                     |
        +---------------------+---------------------+
                              |
                              v
                           Grafana
```

After completing this, **don't jump directly to alerting**. Phase 6 should focus on making these three signals work together: **Metrics → Trace → Logs**, including trace IDs in application logs and configuring Grafana links between Loki and Tempo. That correlation is what will turn the individual tools you've installed into a much stronger end-to-end observability project.