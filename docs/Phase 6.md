
```

# Phase 6 — Logs & Traces Correlation

## Grafana Observability Lab

Phase 6 focuses on connecting the telemetry signals implemented in the previous phases.

Before Phase 6, the project had:

```text
Metrics → Prometheus
Logs    → Loki
Traces  → Tempo
```

Each signal could be investigated independently.

In this phase, **logs and traces were correlated using OpenTelemetry Trace IDs**, allowing navigation between application logs in Loki and request traces in Tempo.

---

# 1. Phase 6 Objective

The main objective is to create an investigation workflow like:

```text
Application Error
       │
       ▼
     Loki
       │
       │ trace_id
       ▼
     Tempo
       │
       ▼
Request Trace
```

and the reverse direction:

```text
Tempo Trace
     │
     │ Related Logs
     ▼
    Loki
     │
     ▼
Application Logs
```

This provides:

```text
Logs → Traces
Traces → Logs
```

correlation.

---

# 2. Previous Architecture

Before correlation:

```text
                     Spring Boot
                          │
           ┌──────────────┼──────────────┐
           │              │              │
           ▼              ▼              ▼
        Metrics          Logs          Traces
           │              │              │
           ▼              ▼              ▼
      Prometheus        Alloy      OpenTelemetry
                          │              │
                          ▼              ▼
                         Loki      OTel Collector
                                         │
                                         ▼
                                       Tempo
           │              │              │
           └──────────────┼──────────────┘
                          ▼
                       Grafana
```

Grafana could display all three signals, but logs and traces were still largely separate investigation paths.

---

# 3. Phase 6 Architecture

After Phase 6:

```text
                       Spring Boot
                            │
           ┌────────────────┼────────────────┐
           │                │                │
           ▼                ▼                ▼
        Metrics            Logs            Traces
           │                │                │
           ▼                ▼                ▼
      Prometheus          Alloy       OpenTelemetry
                            │                │
                            ▼                ▼
                           Loki        OTel Collector
                            │                │
                            │                ▼
                            │              Tempo
                            │                │
                            │    trace_id    │
                            ├───────────────►│
                            │                │
                            │ Related Logs   │
                            │◄───────────────┤
                            │                │
                            └───────┬────────┘
                                    │
                                    ▼
                                 Grafana
```

The shared identifier is:

```text
trace_id
```

---

# 4. What is a Trace ID?

When OpenTelemetry instruments an incoming request, it creates a trace context.

Example:

```text
GET /api/error
      │
      ▼
OpenTelemetry
      │
      ├── Trace ID
      │
      └── Span ID
```

Example Trace ID:

```text
4bf92f3577b34da6a3ce929d0e0e4736
```

A trace represents the overall request.

A span represents an individual operation within that trace.

Example:

```text
Trace ID: ABC123

GET /api/orders
│
├── HTTP Request
│
├── Controller
│
├── Service
│
└── Database operation
```

---

# 5. Why Trace ID is Important

Consider an application error log:

```text
2026-10-02 10:20:31 ERROR

trace_id=4bf92f3577b34da6a3ce929d0e0e4736

Intentional error generated for observability testing
```

Tempo also stores a trace with:

```text
Trace ID:

4bf92f3577b34da6a3ce929d0e0e4736
```

Therefore:

```text
              SAME REQUEST

          Trace ID = ABC123
                  │
          ┌───────┴───────┐
          │               │
          ▼               ▼
        Loki            Tempo
        Logs            Trace
```

The Trace ID acts as the correlation key.

---

# 6. Application Logging Configuration

The Spring Boot console log pattern was configured to include OpenTelemetry trace information.

Example:

```properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] trace_id=%X{trace_id:-} span_id=%X{span_id:-} %logger{36} - %msg%n
```

The important fields are:

```text
trace_id
span_id
```

Example output:

```text
2026-10-02 10:30:10.123 ERROR
trace_id=4bf92f3577b34da6a3ce929d0e0e4736
span_id=00f067aa0ba902b7
...
```

---

# 7. Log Collection Flow

Application logs follow:

```text
Spring Boot
     │
     │ stdout / stderr
     ▼
Docker Logs
     │
     ▼
Grafana Alloy
     │
     ▼
Loki
```

Alloy collects Docker container logs and forwards them to Loki.

The Trace ID contained in the application log remains available when the log reaches Loki.

---

# 8. Trace Collection Flow

Traces follow:

```text
Spring Boot
     │
     ▼
OpenTelemetry Java Agent
     │
     │ OTLP
     ▼
OpenTelemetry Collector
     │
     │ OTLP
     ▼
Grafana Tempo
```

Tempo stores the trace information.

Grafana uses Tempo as its tracing datasource.

---

# 9. Complete Telemetry Flow

The project now has:

```text
                    Spring Boot
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
     Metrics            Logs            Traces
        │                │                │
        ▼                ▼                ▼
   Prometheus          Alloy        OTel Collector
                         │                │
                         ▼                ▼
                        Loki            Tempo
                         │                │
                         └───────┬────────┘
                                 │
                            Trace ID
                                 │
                                 ▼
                              Grafana
```

---

# 10. Logs → Traces Correlation

The first correlation direction implemented was:

```text
Loki
 ↓
Application Log
 ↓
Trace ID
 ↓
Tempo
 ↓
Corresponding Trace
```

For example, an error appears in Loki:

```text
ERROR

trace_id=ABC123

RuntimeException...
```

The Trace ID can then be used to locate:

```text
Tempo

Trace ID = ABC123
```

This confirms that both telemetry signals belong to the same request.

---

# 11. Loki Derived Field

Grafana Loki supports **Derived Fields**.

A derived field can extract a value from a log line and turn it into a link.

The following derived field was configured:

```text
Name:
TraceID

Type:
Regex in log line

Regex:
trace_id=([a-fA-F0-9]+)

Internal Link:
Enabled

Target Datasource:
Tempo
```

The regex:

```regex
trace_id=([a-fA-F0-9]+)
```

extracts the hexadecimal Trace ID.

For example:

```text
trace_id=4bf92f3577b34da6a3ce929d0e0e4736
```

becomes:

```text
TraceID

4bf92f3577b34da6a3ce929d0e0e4736
```

Grafana can then use this value to open the corresponding Tempo trace.

---

# 12. Loki → Tempo Workflow

The resulting workflow is:

```text
Application produces ERROR
          │
          ▼
         Loki
          │
          ▼
ERROR trace_id=ABC123
          │
          ▼
     Derived Field
          │
          ▼
      View Trace
          │
          ▼
         Tempo
          │
          ▼
   Trace ID ABC123
```

This removes the need to manually copy and paste Trace IDs during normal investigation.

---

# 13. Traces → Logs Correlation

The reverse correlation was also tested.

From a Tempo trace, Grafana provides access to the related Loki logs.

The workflow becomes:

```text
Tempo
  │
  ▼
Trace
  │
  ▼
Related Logs
  │
  ▼
Loki
  │
  ▼
Application Logs
```

This is called:

```text
Traces → Logs correlation
```

---

# 14. Bidirectional Correlation

At the end of Phase 6:

```text
                 Trace ID
                    │
            ┌───────┴───────┐
            │               │
            ▼               ▼
          Loki            Tempo
          Logs            Traces
            │               │
            ├──────────────►│
            │ Logs → Trace  │
            │               │
            │◄──────────────┤
            │ Trace → Logs  │
```

Therefore the implementation provides:

```text
Logs ↔ Traces
```

bidirectional correlation.

---

# 15. Testing with `/api/error`

The application contains an endpoint designed to generate HTTP 500 errors:

```text
/api/error
```

Generate an error:

```powershell
try {
    Invoke-WebRequest http://localhost:8080/api/error
}
catch {}
```

Or generate multiple errors:

```powershell
1..10 | ForEach-Object {

    try {
        Invoke-WebRequest http://localhost:8080/api/error
    }
    catch {}

}
```

---

# 16. Find Error Logs in Loki

Open:

```text
Grafana
   ↓
Explore
   ↓
Loki
```

Query:

```logql
{container="observability-demo"} |= "ERROR"
```

The result should contain application errors.

Example:

```text
ERROR
trace_id=ABC123
span_id=XYZ456

Intentional error generated for observability testing
```

---

# 17. Open the Corresponding Trace

Expand the Loki log.

The derived field extracts:

```text
TraceID = ABC123
```

Use:

```text
View Trace
```

to open Tempo.

Tempo should display:

```text
Trace ID

ABC123
```

representing the same request.

This verifies:

```text
Loki → Tempo
```

correlation.

---

# 18. Navigate Back to Related Logs

From the Tempo trace, use the related logs functionality.

The flow becomes:

```text
Tempo Trace
      │
      ▼
Related Logs
      │
      ▼
Loki
      │
      ▼
Logs associated with request
```

This verifies:

```text
Tempo → Loki
```

correlation.

---

# 19. Testing with `/api/slow`

The application also contains:

```text
/api/slow
```

which intentionally delays the response.

Generate slow requests:

```powershell
1..5 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/slow
}
```

The investigation can follow:

```text
Slow request
     │
     ▼
Tempo
     │
     ▼
Long-duration trace
     │
     ▼
Related Logs
     │
     ▼
Loki
```

This demonstrates that correlation is useful for both failures and latency investigations.

---

# 20. Role of Metrics

Prometheus remains part of the overall investigation workflow.

For example:

```text
Prometheus

HTTP 5xx Error Rate
        ↑
```

tells us that application failures are increasing.

Similarly:

```text
Average Response Time
        ↑
```

indicates increased latency.

Metrics answer:

```text
WHAT is happening?
```

Logs provide diagnostic context:

```text
WHAT did the application report?
```

Traces answer:

```text
WHERE did the request spend time
or fail during execution?
```

---

# 21. Metrics vs Logs vs Traces

| Signal | Backend | Main Purpose |
|---|---|---|
| Metrics | Prometheus | Detect trends and abnormal behavior |
| Logs | Loki | Investigate application events/errors |
| Traces | Tempo | Follow individual request execution |
| Visualization | Grafana | Explore and correlate telemetry |

A useful way to remember this:

```text
Metrics
   ↓
What is happening?

Logs
   ↓
What diagnostic information was produced?

Traces
   ↓
Where did the request spend time/fail?
```

---

# 22. Incident Investigation Workflow

A production-style investigation can now look like:

```text
User reports problem
        │
        ▼
Grafana Dashboard
        │
        ▼
HTTP 5xx ↑
or
Latency ↑
        │
        ▼
Prometheus Metrics
        │
        ▼
Investigate Logs
        │
        ▼
Loki
        │
        ▼
ERROR
trace_id=ABC123
        │
        ▼
View Trace
        │
        ▼
Tempo
        │
        ▼
Inspect request
        │
        ▼
Related Logs
        │
        ▼
Loki
        │
        ▼
Root-cause investigation
```

---

# 23. Important Concept — Metrics Are Aggregated

Logs and traces can be connected relatively naturally using a Trace ID.

Metrics are different.

For example:

```text
HTTP Request Rate = 25 requests/sec
```

represents multiple requests.

There is not necessarily one Trace ID associated with that metric.

Therefore:

```text
Metric
   ↓
Aggregated information
```

while:

```text
Trace
   ↓
Individual request
```

This is an important distinction when designing observability systems.

---

# 24. Prometheus Exemplars

An advanced enhancement is to use **Prometheus exemplars**.

Conceptually:

```text
Latency Metric
      │
      ▼
   Exemplar
      │
      ▼
   Trace ID
      │
      ▼
    Tempo
```

This can provide a stronger:

```text
Metrics → Traces
```

workflow.

Exemplars are considered an advanced enhancement and are **not required for completion of the current Phase 6 scope**.

---

# 25. Grafana Datasource Issue Encountered

During Phase 6, datasource provisioning was changed to introduce correlation.

Grafana subsequently failed during startup with:

```text
Datasource provisioning error:
data source not found
```

The logs showed that the provisioning module itself was failing during datasource provisioning. Pasted text

Other Grafana services subsequently failed because they depended on the failed provisioning module. Pasted text

The datasource plugins themselves were available, including Prometheus, Tempo and Loki. Pasted text

---

# 26. Root Cause of the Provisioning Conflict

The existing Grafana persistent data already contained the datasources created during previous phases.

Existing datasource UIDs were generated by Grafana.

New provisioning configuration attempted to introduce fixed datasource UIDs.

Rather than deleting the existing Grafana data and dashboards, datasource provisioning was temporarily removed and the existing datasources were retained.

This preserved previous project work.

---

# 27. Working Solution

The Grafana provisioning volume was temporarily removed from the Grafana container while keeping:

```text
grafana-data:/var/lib/grafana
```

This allowed Grafana to start using its existing persistent database.

The existing datasources were retained:

```text
Prometheus
Loki
Tempo
```

Correlation was then configured using the existing Grafana datasources rather than recreating them.

This avoided deleting:

```text
Existing dashboards
Datasource configuration
Grafana state
```

---

# 28. Important Lesson — Persistent Grafana State

Grafana uses:

```text
/var/lib/grafana
```

for persistent state.

The project maps this to:

```text
grafana-data
```

Therefore:

```text
Grafana container
      │
      ▼
/var/lib/grafana
      │
      ▼
grafana-data
```

can retain:

```text
Dashboards
Datasources
Users/settings
Grafana database
```

even when the Grafana container itself is recreated.

---

# 29. Do Not Delete Volumes During Troubleshooting

During troubleshooting, avoid:

```bash
docker compose down -v
```

unless intentionally resetting the entire lab.

The `-v` option can remove persistent volumes containing:

```text
PostgreSQL data
Prometheus data
Grafana dashboards/configuration
Loki data
Tempo data
```

Instead, individual containers can be recreated:

```bash
docker compose stop grafana

docker compose rm -f grafana

docker compose up -d grafana
```

without deleting the persistent volume.

---

# 30. Troubleshooting — Trace ID Missing from Logs

If Loki shows:

```text
trace_id=
```

instead of:

```text
trace_id=ABC123
```

check:

```text
Is OpenTelemetry Java Agent running?
            │
            ▼
Is the request generating a trace?
            │
            ▼
Does Tempo receive the trace?
            │
            ▼
Is trace context available to logging?
            │
            ▼
Is the logging pattern configured correctly?
```

Check application logs:

```bash
docker logs observability-demo
```

---

# 31. Troubleshooting — Loki Cannot Link to Tempo

Check whether the log contains:

```text
trace_id=<hexadecimal-trace-id>
```

Then verify the derived field regex:

```regex
trace_id=([a-fA-F0-9]+)
```

The regex must match the actual application log format.

Also verify:

```text
Internal Link = Enabled

Datasource = Tempo
```

---

# 32. Troubleshooting — Trace Exists but Logs Are Missing

Search Loki manually using the Trace ID:

```logql
{container="observability-demo"} |= "TRACE_ID"
```

If no result is returned, investigate:

```text
Did application write the log?
        │
        ▼
Does log contain Trace ID?
        │
        ▼
Did Alloy collect the log?
        │
        ▼
Did Loki receive it?
        │
        ▼
Is Grafana time range correct?
```

---

# 33. Troubleshooting Philosophy

One of the major lessons from this project is to troubleshoot observability pipelines layer-by-layer.

For logs:

```text
Application
    ↓
Docker
    ↓
Alloy
    ↓
Loki
    ↓
Grafana
```

For traces:

```text
Application
    ↓
OpenTelemetry Agent
    ↓
OTel Collector
    ↓
Tempo
    ↓
Grafana
```

For metrics:

```text
Application
    ↓
Micrometer
    ↓
Actuator
    ↓
Prometheus
    ↓
Grafana
```

This makes it easier to determine exactly where telemetry is being lost.

---

# 34. Phase 6 Skills Demonstrated

This phase demonstrates practical understanding of:

```text
OpenTelemetry Trace Context

Trace IDs

Span IDs

Grafana Loki

Grafana Tempo

LogQL

Distributed Tracing

Log-to-Trace Correlation

Trace-to-Log Correlation

Grafana Derived Fields

Telemetry Correlation

Incident Investigation

Docker Troubleshooting

Grafana Datasource Troubleshooting

Persistent Grafana Storage
```

---

# 35. Interview Explanation

A concise explanation:

> In my observability lab, application logs are collected by Grafana Alloy and stored in Loki, while OpenTelemetry traces are exported through the OTel Collector to Tempo. I include the OpenTelemetry Trace ID in application logs so the same request can be identified in both Loki and Tempo. In Grafana, I configured log-to-trace and trace-to-log correlation, allowing me to move from an application error log directly to its corresponding request trace and from a Tempo trace back to the related Loki logs.

---

# 36. How to Explain Trace ID Correlation

If asked:

> How are logs and traces correlated?

Answer:

> OpenTelemetry assigns a Trace ID to a request. That Trace ID is stored with the trace in Tempo and is also included in the application's log context. Since both telemetry signals contain the same Trace ID, Grafana can use it as the correlation key between Loki logs and Tempo traces.

Conceptually:

```text
Request
   │
   ▼
Trace ID = ABC123
   │
   ├───────────────┐
   │               │
   ▼               ▼
Log ABC123      Trace ABC123
   │               │
   ▼               ▼
 Loki            Tempo
```

---

# 37. Phase 6 Completion Checklist

Phase 6 is complete when:

- [x] OpenTelemetry traces are generated
- [x] Trace IDs are available
- [x] Trace IDs appear in application logs
- [x] Logs reach Loki through Grafana Alloy
- [x] Traces reach Tempo through OpenTelemetry
- [x] Trace ID can be identified in Loki
- [x] Same Trace ID can be found in Tempo
- [x] Loki Derived Field configured
- [x] Loki → Tempo navigation works
- [x] Tempo → Loki related logs works
- [x] `/api/error` correlation tested
- [x] Logs ↔ Traces relationship understood
- [x] Datasource provisioning issue troubleshot without deleting existing Grafana data

---

# 38. Screenshots to Save

Recommended repository structure:

```text
screenshots/
└── phase-06/
    ├── 01-loki-trace-id.png
    ├── 02-loki-view-trace.png
    ├── 03-tempo-trace.png
    ├── 04-tempo-related-logs.png
    └── 05-logs-traces-correlation.png
```

The strongest screenshots for the portfolio are:

```text
Loki ERROR
trace_id=ABC123
       ↓
View Trace

        +

Tempo
Trace ID ABC123

        +

Related Logs
       ↓
Loki
```

---

# 39. Git Commit

After saving the documentation and screenshots:

```bash
git status
```

Then:

```bash
git add .
```

Commit:

```bash
git commit -m "feat: add logs and traces correlation"
```

Create the Phase 6 tag:

```bash
git tag phase-6
```

Push:

```bash
git push
```

Push the tag:

```bash
git push origin phase-6
```

---

# 40. Project Progress

```text
Phase 1
Spring Boot + PostgreSQL + Docker
              │
              │ COMPLETE
              ▼
Phase 2
Micrometer + Prometheus
              │
              │ COMPLETE
              ▼
Phase 3
Grafana Dashboards
              │
              │ COMPLETE
              ▼
Phase 4
Loki + Grafana Alloy
              │
              │ COMPLETE
              ▼
Phase 5
OpenTelemetry + Tempo
              │
              │ COMPLETE
              ▼
Phase 6
Logs ↔ Traces Correlation
              │
              │ COMPLETE
              ▼
Phase 7
Grafana Alerting
              │
              ▼
Phase 8
Incident Simulation & Troubleshooting
```

---

# 41. Phase 6 Final Result

Before Phase 6:

```text
Prometheus       Loki       Tempo
    │              │           │
 Metrics          Logs       Traces

      Separate telemetry signals
```

After Phase 6:

```text
                  Grafana
                     │
          ┌──────────┴──────────┐
          │                     │
          ▼                     ▼
        Loki                  Tempo
        Logs                  Traces
          │                     │
          │      Trace ID       │
          ├────────────────────►│
          │                     │
          │    Related Logs     │
          │◄────────────────────┤
          │                     │
          └──────────┬──────────┘
                     │
                     ▼
             Correlated Investigation
```

## Phase 6 Status

**COMPLETE ✅**

The project now supports **bidirectional Logs ↔ Traces correlation** using OpenTelemetry Trace IDs.

The next phase is:

# Phase 7 — Grafana Alerting

The goal will be to move from manually watching dashboards to proactive detection:

```text
Problem occurs
      ↓
Prometheus metric changes
      ↓
Grafana Alert Rule
      ↓
Alert FIRING
      ↓
Engineer investigates
      ↓
Metrics
      ↓
Loki Logs
      ↓
Tempo Trace
      ↓
Root-cause investigation
```
