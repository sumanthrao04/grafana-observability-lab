# Phase 7 — Grafana Alerting & Incident Detection

You now have a strong observability foundation:

```text
Phase 1 → Spring Boot + PostgreSQL + Docker       ✅
Phase 2 → Prometheus Metrics                     ✅
Phase 3 → Grafana Dashboards                     ✅
Phase 4 → Loki + Grafana Alloy Logs              ✅
Phase 5 → OpenTelemetry + Tempo Traces           ✅
Phase 6 → Logs ↔ Traces Correlation              ✅
Phase 7 → Grafana Alerting                       ← NOW
```

Phase 7 changes the project from:

```text
Engineer continuously checks dashboard
              ↓
         Finds problem
```

to:

```text
Problem occurs
      ↓
Prometheus detects metric change
      ↓
Grafana evaluates alert
      ↓
Alert FIRING
      ↓
Engineer investigates
      ↓
Metrics → Logs → Traces
```

For this phase, we'll create **four practical alerts**:

| Alert | Purpose |
|---|---|
| Application Down | Detect application unavailability |
| High 5xx Error Rate | Detect application failures |
| High Response Time | Detect application slowness |
| High JVM Heap | Detect memory pressure |

For the lab, we'll use deliberately sensitive thresholds so you can easily trigger and test each alert.

---

# 1. Understand the Alerting Architecture

Your application metrics already flow through:

```text
Spring Boot
     ↓
Micrometer
     ↓
/actuator/prometheus
     ↓
Prometheus
     ↓
Grafana
```

Now Grafana periodically evaluates PromQL queries.

```text
              Prometheus
                   │
                   │ PromQL
                   ▼
            Grafana Alert Rule
                   │
             condition met?
              /          \
            NO            YES
            │              │
          Normal         Firing
                           │
                           ▼
                    Contact Point
```

An important distinction:

**Prometheus stores the metrics. Grafana evaluates the alert rules we're creating in this phase.**

---

# 2. Grafana Alert States

You should understand these states before creating alerts.

### Normal

```text
Condition is false
```

Example:

```text
Application is UP
```

### Pending

```text
Threshold exceeded
        ↓
Waiting for configured duration
```

For example:

```text
5xx errors high

for 30 seconds
```

Grafana doesn't immediately fire.

### Firing

```text
Condition remained true
        ↓
Alert FIRING
```

### No Data

Grafana executed the query but no matching series/data was returned.

### Error

Grafana couldn't successfully evaluate the alert.

These states are useful interview concepts.

---

# 3. Check Grafana Alerting

Open:

```text
http://localhost:3000
```

Navigate to:

```text
Alerting
   ↓
Alert rules
```

You should see an option similar to:

**New alert rule**

---

# 4. Create an Alert Folder

Create a folder/group for this project.

Use:

```text
Observability Lab
```

This keeps the alert rules organized:

```text
Observability Lab

├── Application Down
├── High HTTP 5xx Error Rate
├── High Response Time
└── High JVM Heap Utilization
```

---

# 5. Alert 1 — Application Down

This is the easiest and most important alert to understand.

Our Prometheus query is:

```promql
up{job="spring-boot-demo"}
```

Normally:

```text
1 = Prometheus can scrape application
0 = Prometheus cannot scrape application
```

Therefore we want:

```text
up == 0
        ↓
Application Down Alert
```

---

# 6. Create the Application Down Alert

Navigate:

```text
Alerting
   ↓
Alert rules
   ↓
New alert rule
```

Name:

```text
Spring Boot Application Down
```

Datasource:

```text
Prometheus
```

Query A:

```promql
up{job="spring-boot-demo"}
```

Grafana alerting generally needs to reduce the time series to a single value before comparing it.

Add a **Reduce** expression.

```text
B = Reduce A
```

Function:

```text
Last
```

Then add a threshold:

```text
C = Threshold
```

Condition:

```text
B IS BELOW 1
```

Conceptually:

```text
A
up{job="spring-boot-demo"}

       ↓

B
Last value

       ↓

C
Is below 1?

       ↓

YES → Alert
```

---

# 7. Evaluation Behaviour

For the lab, use a short evaluation interval such as:

```text
1 minute
```

You can use a short pending period while testing.

Conceptually:

```text
Application DOWN
      ↓
Grafana evaluates
      ↓
Condition true
      ↓
Pending
      ↓
Condition still true
      ↓
Firing
```

In a real production environment, the threshold and pending period should be chosen based on service requirements and expected transient failures.

---

# 8. Add Alert Information

Add useful annotations.

Summary:

```text
Spring Boot application is unavailable
```

Description:

```text
Prometheus is unable to scrape the Spring Boot application.
Investigate application availability and container health.
```

Add labels such as:

```text
severity = critical
service = observability-demo
environment = local
```

Labels become especially useful later for routing notifications.

---

# 9. Test Application Down Alert

First verify:

```promql
up{job="spring-boot-demo"}
```

returns:

```text
1
```

Your alert should be:

```text
Normal
```

Now intentionally stop Spring Boot:

```powershell
docker stop observability-demo
```

Wait for Prometheus to perform another scrape.

Check Prometheus:

```promql
up{job="spring-boot-demo"}
```

It should become:

```text
0
```

Grafana should progress through:

```text
Normal
   ↓
Pending
   ↓
Firing
```

This is your first real alert test.

---

# 10. Investigate the Outage

When the alert fires, don't immediately restart the application.

Practice troubleshooting.

Start with:

```powershell
docker compose ps
```

You'll see the application isn't running.

Check:

```powershell
docker logs observability-demo
```

Then restore it:

```powershell
docker start observability-demo
```

After Prometheus successfully scrapes the application again:

```text
up = 1
```

Grafana should return the alert to:

```text
Normal
```

You've now tested the complete lifecycle:

```text
Normal
 ↓
Application stopped
 ↓
Prometheus up = 0
 ↓
Pending
 ↓
Firing
 ↓
Application restored
 ↓
Prometheus up = 1
 ↓
Normal
```

Take screenshots of both **Firing** and **Normal** states.

---

# 11. Alert 2 — High HTTP 5xx Error Rate

Now create an application-level alert.

Use:

```promql
sum(
  rate(
    http_server_requests_seconds_count{
      job="spring-boot-demo",
      status=~"5..",
      uri!~"/actuator.*"
    }[1m]
  )
)
```

We're using `[1m]` for the lab so the test responds relatively quickly.

Create:

```text
High HTTP 5xx Error Rate
```

Query:

```text
A = Prometheus query
```

Reduce:

```text
B = Last(A)
```

Threshold:

For the lab, choose a small value that your `/api/error` test can exceed.

For example:

```text
B > 0.05
```

This is roughly:

```text
more than 0.05 failed requests/second
```

The exact production threshold would depend on traffic volume and service objectives.

---

# 12. Generate Errors

Run:

```powershell
1..30 | ForEach-Object {

    try {
        Invoke-WebRequest http://localhost:8080/api/error
    }
    catch {}

}
```

Prometheus should detect:

```text
HTTP 5xx Rate ↑
```

Grafana:

```text
Normal
  ↓
Pending
  ↓
Firing
```

---

# 13. Investigate Using Phase 6

This is where your previous work becomes useful.

Alert:

```text
High HTTP 5xx Error Rate
```

Then:

```text
Alert
  ↓
Grafana Dashboard
  ↓
5xx spike
  ↓
Loki
```

Query:

```logql
{container="observability-demo"} |= "ERROR"
```

Find:

```text
ERROR
trace_id=ABC123
...
```

Then:

```text
Trace ID
   ↓
View Trace
   ↓
Tempo
```

Now you have:

```text
Detection
    ↓
Alert
    ↓
Metrics
    ↓
Logs
    ↓
Trace
    ↓
Investigation
```

This is one of the strongest demonstrations in the project.

---

# 14. Alert 3 — High Response Time

Use the metrics you've already used for your dashboard.

Average response time:

```promql
sum(
  rate(
    http_server_requests_seconds_sum{
      job="spring-boot-demo",
      uri!~"/actuator.*"
    }[1m]
  )
)
/
clamp_min(
  sum(
    rate(
      http_server_requests_seconds_count{
        job="spring-boot-demo",
        uri!~"/actuator.*"
      }[1m]
    )
  ),
  0.000001
)
```

Create:

```text
High Application Response Time
```

Reduce:

```text
Last
```

For our lab, use a threshold that is easy to trigger with `/api/slow`, for example:

```text
> 1 second
```

Your `/api/slow` endpoint takes approximately:

```text
3 seconds
```

so it should help trigger the condition.

---

# 15. Generate Latency

Run several slow requests.

If you run them sequentially:

```powershell
1..10 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/slow
}
```

this will take roughly 30 seconds because each request waits about three seconds.

Observe your Grafana latency panel.

You should see:

```text
Response Time
      ↑
```

Then the alert should move toward:

```text
Pending
   ↓
Firing
```

depending on your evaluation timing.

---

# 16. Investigate High Latency

Your investigation:

```text
High Response Time Alert
          ↓
Grafana Metrics
          ↓
Latency increased
          ↓
Tempo
          ↓
Find slow trace
          ↓
GET /api/slow
          ↓
~3 second duration
          ↓
Related Logs
          ↓
Loki
```

This demonstrates how alerts lead into observability investigation.

---

# 17. Important Improvement — P95 Latency

For production systems, average latency isn't always the best alert signal.

Imagine:

```text
99 requests = 100 ms

1 request = 10 seconds
```

Average latency can hide poor user experiences.

Percentiles are commonly more useful:

```text
P50
P95
P99
```

For example:

```text
P95 = 1.2 seconds
```

means approximately:

> 95% of observations are at or below 1.2 seconds.

To implement this properly, your application needs suitable histogram bucket metrics.

Check:

```promql
http_server_requests_seconds_bucket
```

If those buckets aren't available, keep the average-response-time alert for this phase.

We'll treat P95 alerting as an advanced improvement rather than using an incorrect query.

---

# 18. Alert 4 — JVM Heap Utilization

Your dashboard already has JVM heap metrics.

Used heap:

```promql
sum(
  jvm_memory_used_bytes{
    job="spring-boot-demo",
    area="heap"
  }
)
```

Max heap:

```promql
sum(
  jvm_memory_max_bytes{
    job="spring-boot-demo",
    area="heap"
  } > 0
)
```

Heap percentage:

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

Create:

```text
High JVM Heap Utilization
```

For a learning lab, you could use:

```text
> 70%
```

For production, don't copy that threshold blindly. Establish a normal baseline and account for JVM GC behavior and heap sizing.

---

# 19. Optional Alert — High Java Process CPU

PromQL:

```promql
process_cpu_usage{
  job="spring-boot-demo"
} * 100
```

Alert name:

```text
High Java Process CPU
```

Example lab threshold:

```text
> 80%
```

This is optional because generating sustained Java CPU load may require adding a CPU-load endpoint to the demo application.

---

# 20. Your Alert Rule Structure

At the end, your Grafana Alerting page should look roughly like:

```text
Observability Lab

├── Spring Boot Application Down
│      severity = critical
│
├── High HTTP 5xx Error Rate
│      severity = critical
│
├── High Application Response Time
│      severity = warning
│
└── High JVM Heap Utilization
       severity = warning
```

This introduces another useful concept:

### Critical

Problems requiring immediate attention.

Examples:

```text
Application unavailable
Major error-rate increase
```

### Warning

Conditions requiring investigation but not necessarily immediate outage response.

Examples:

```text
Latency increasing
Heap utilization increasing
```

Severity should ultimately reflect your organization's operational policy.

---

# 21. Contact Points

Detecting an alert is useful, but in production an engineer shouldn't need to keep Grafana open.

Grafana supports **Contact Points**.

Conceptually:

```text
Alert Rule
     ↓
Alert Fires
     ↓
Notification Policy
     ↓
Contact Point
     ↓
Engineer / Team
```

Depending on your environment, contact points can include integrations such as email, Slack, Teams, webhooks, and incident-management systems supported by your Grafana deployment.

For the local lab, the main requirement is understanding the architecture and getting at least one notification path working if you have a suitable destination.

---

# 22. Contact Point Lab

Navigate:

```text
Alerting
   ↓
Contact points
```

You can create a contact point for whichever notification method you have available.

Give it a meaningful name, for example:

```text
observability-team
```

Then test the contact point from Grafana.

If you don't currently have SMTP or another notification integration configured, **don't block Phase 7 on it**. A firing alert in Grafana is sufficient for the core lab; notification delivery can be added as an enhancement.

---

# 23. Notification Policies

Notification policies decide:

> Where should this alert go?

For example:

```text
severity=critical
       ↓
Observability Critical Contact Point
```

and:

```text
severity=warning
       ↓
Observability Warning Contact Point
```

Conceptually:

```text
                    Grafana Alert

                         |
               Check alert labels
                         |
            +------------+------------+
            |                         |
     severity=critical         severity=warning
            |                         |
            v                         v
      Critical Route              Warning Route
```

This becomes more important when managing many applications.

---

# 24. Labels

Add labels to your alerts.

For example:

```text
service=observability-demo
environment=local
team=observability
severity=critical
```

For latency:

```text
service=observability-demo
environment=local
team=observability
severity=warning
```

Labels allow alerts to be:

```text
Grouped
Filtered
Routed
Silenced
Managed
```

---

# 25. Annotations

Labels are machine-oriented metadata.

Annotations provide human-readable information.

For example:

```text
Summary:
High HTTP 5xx error rate detected
```

Description:

```text
The Spring Boot demo application is experiencing
an increased HTTP 5xx error rate.

Check application metrics, Loki logs and Tempo traces.
```

This makes alerts much more useful to whoever receives them.

---

# 26. Alert Design Principle

Avoid creating an alert for every metric.

For example, you could create:

```text
CPU > 50
CPU > 55
CPU > 60
Thread count > 30
GC occurred
Memory changed
Request count changed
```

but that creates noise.

A better goal is:

```text
Alert on conditions requiring human action.
```

For this project:

```text
Application Down
       ↓
ACTION REQUIRED

High Error Rate
       ↓
ACTION REQUIRED

Sustained High Latency
       ↓
INVESTIGATE

Sustained High Heap
       ↓
INVESTIGATE
```

This introduces the concept of **alert fatigue**.

---

# 27. Phase 7 Incident Scenario

Now combine everything you've built.

Generate normal traffic:

```powershell
1..50 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/health
}
```

Everything should be:

```text
NORMAL
```

Then generate errors:

```powershell
1..50 | ForEach-Object {

    try {
        Invoke-WebRequest http://localhost:8080/api/error
    }
    catch {}

}
```

The workflow becomes:

```text
/api/error
     ↓
Spring Boot
     ↓
HTTP 500
     ↓
Micrometer
     ↓
Prometheus
     ↓
5xx metric increases
     ↓
Grafana Alert
     ↓
FIRING
```

Then investigate:

```text
Alert
  ↓
Dashboard
  ↓
Prometheus metric
  ↓
Loki ERROR
  ↓
Trace ID
  ↓
Tempo Trace
  ↓
Related Logs
```

This is now a complete observability workflow.

---

# 28. Simulate Application Outage

Stop:

```powershell
docker stop observability-demo
```

Then observe:

```text
Prometheus

up = 0
```

Grafana:

```text
Spring Boot Application Down

Pending
   ↓
Firing
```

Now restore:

```powershell
docker start observability-demo
```

Observe:

```text
up = 1

Alert = Normal
```

This is an excellent screenshot sequence for your portfolio.

---

# 29. Alert Troubleshooting

If the alert doesn't fire:

First test the PromQL directly in Prometheus/Grafana Explore.

For example:

```promql
up{job="spring-boot-demo"}
```

Then:

```text
Does query return data?
       |
       +--- NO
       |
       v
Fix Prometheus/query first

       |
      YES
       |
       v
Check Reduce expression
       |
       v
Check Threshold
       |
       v
Check evaluation interval
       |
       v
Check pending period
```

Do not start by blaming Grafana Alerting if the underlying query isn't producing the expected value.

---

# 30. No Data vs Application Down

This distinction is important.

Your application being unavailable may produce:

```text
up = 0
```

because Prometheus still knows the configured target but cannot scrape it.

However, some queries can return:

```text
No Data
```

if the time series disappears or doesn't exist.

Therefore Grafana alerts have settings for:

```text
No Data
Error
```

You should deliberately decide how those states are handled for important alerts.

For the lab, observe what happens rather than assuming:

```text
No Data = Healthy
```

because it may actually indicate a telemetry problem.

---

# 31. Alerting vs Observability

Alerting is not observability by itself.

Alerting tells you:

```text
Something requires attention.
```

Observability helps you investigate:

```text
What changed?

Where did it happen?

What telemetry supports the diagnosis?
```

Our architecture now becomes:

```text
                  APPLICATION
                       |
       +---------------+---------------+
       |               |               |
       v               v               v
    Metrics           Logs           Traces
       |               |               |
       v               v               v
 Prometheus           Loki           Tempo
       |               |               |
       +---------------+---------------+
                       |
                       v
                    Grafana
                       |
              +--------+--------+
              |                 |
              v                 v
          Dashboard           Alerts
                                  |
                                  v
                           Engineer notified
                                  |
                                  v
                             Investigation
```

---

# 32. Screenshots to Capture

Create:

```text
screenshots/
└── phase-07/
```

Capture:

```text
01-alert-rules.png
02-application-down-normal.png
03-application-down-firing.png
04-high-5xx-alert.png
05-high-latency-alert.png
06-alert-details.png
07-alert-to-loki-investigation.png
08-loki-to-tempo-trace.png
```

For LinkedIn, a strong set would be:

```text
Grafana Alert FIRING

        +

5xx Metric Spike

        +

Loki Error + Trace ID

        +

Tempo Trace
```

It shows the complete story rather than simply showing an alert configuration screen.

---

# 33. Phase 7 Completion Checklist

Before declaring Phase 7 complete:

```text
[ ] Grafana Alerting accessible

[ ] Alert folder/group created

[ ] Application Down alert created

[ ] Application Down alert tested

[ ] Application Down alert entered FIRING state

[ ] Application recovery returned alert to NORMAL

[ ] HTTP 5xx alert created

[ ] HTTP 5xx alert tested

[ ] High Response Time alert created

[ ] Slow API generated

[ ] Latency alert tested

[ ] JVM Heap alert created

[ ] Labels configured

[ ] Annotations configured

[ ] Alert states understood

[ ] Normal understood

[ ] Pending understood

[ ] Firing understood

[ ] No Data understood

[ ] Error state understood

[ ] Alert → Metrics investigation tested

[ ] Metrics → Loki investigation tested

[ ] Loki → Tempo correlation tested

[ ] Screenshots captured
```

Contact-point delivery is a useful bonus, but I wouldn't block completion of the local lab if you don't have a notification service configured.

---

# 34. What You Should Be Able to Explain in an Interview

If asked:

> **How does alerting work in your project?**

You can say:

> My Spring Boot application exposes metrics through Micrometer and Actuator, and Prometheus scrapes those metrics. Grafana uses Prometheus as the datasource and evaluates alert rules based on PromQL queries. I configured alerts for application availability, HTTP 5xx errors, response latency, and JVM heap utilization. When an alert fires, I use the Grafana dashboard to investigate the metric, Loki for application logs, and the OpenTelemetry Trace ID to navigate to the corresponding Tempo trace.

If asked:

> **What's the difference between Pending and Firing?**

You can explain:

> Pending means the alert condition is currently true but hasn't remained true for the configured duration. Firing means the condition continued to be true for the required duration.

---

# 35. Your Complete Troubleshooting Story

This is the story you should eventually be able to explain confidently:

```text
User requests /api/error
          ↓
Spring Boot returns HTTP 500
          ↓
Micrometer records HTTP failure
          ↓
Prometheus scrapes metric
          ↓
Grafana evaluates alert
          ↓
5xx Alert FIRING
          ↓
Engineer opens dashboard
          ↓
Confirms error spike
          ↓
Opens Loki
          ↓
Finds ERROR
          ↓
Finds Trace ID
          ↓
Opens Tempo
          ↓
Inspects failed request
          ↓
Uses related logs
          ↓
Root-cause investigation
```

That demonstrates considerably more than simply knowing how to create a Grafana panel.

---

# 36. Git Commit

Once Phase 7 is complete:

```bash
git status
```

Then:

```bash
git add .
```

Commit:

```bash
git commit -m "feat: add Grafana alerting and incident detection"
```

Tag:

```bash
git tag phase-7
```

Push:

```bash
git push
git push origin phase-7
```

---

# Phase 7 Final Architecture

```text
                         Spring Boot
                              |
            +-----------------+-----------------+
            |                 |                 |
            v                 v                 v
         Metrics             Logs             Traces
            |                 |                 |
            v                 v                 v
       Prometheus           Alloy        OpenTelemetry
            |                 |                 |
            |                 v                 v
            |                Loki         OTel Collector
            |                                   |
            |                                   v
            |                                 Tempo
            |                 |                 |
            +-----------------+-----------------+
                              |
                              v
                           Grafana
                              |
                 +------------+------------+
                 |                         |
                 v                         v
             Dashboards                  Alerts
                                             |
                                             v
                                       Alert FIRING
                                             |
                                             v
                                       Investigation
                                             |
                              +--------------+--------------+
                              |              |              |
                              v              v              v
                           Metrics          Logs          Traces
```

At the end of Phase 7, your stack is no longer just **collecting and visualizing telemetry**. It can also **detect conditions that require investigation**.
