Now that you have:

**Phase 1:** Spring Boot + PostgreSQL + Docker  
**Phase 2:** Micrometer + Prometheus  
**Phase 3:** Grafana dashboards  

Phase 4 adds the second major observability signal: **logs**.

# Phase 4 — Centralized Logging with Loki + Grafana Alloy

Our objective is:

```text
Spring Boot
    │
    │ Application Logs
    ▼
Docker Logs
    │
    ▼
Grafana Alloy
    │
    ▼
   Loki
    │
    ▼
 Grafana
```

By the end, you'll be able to generate an HTTP 500 error and investigate its logs directly from Grafana.

---

## 1. What are we building?

Currently you can detect this:

```text
Grafana Metrics Dashboard

HTTP 5xx Error Rate
        ↑
        │
      SPIKE
```

But metrics only tell us:

> Something is failing.

They don't necessarily tell us:

> Why is it failing?

Phase 4 adds logs.

```text
HTTP 500 spike
      │
      ▼
Check Grafana
      │
      ▼
Open Loki logs
      │
      ▼
Find ERROR
      │
      ▼
RuntimeException:
Intentional error generated...
```

This is the beginning of **metrics → logs investigation**.

---

# 2. Components

We're adding two components.

### Grafana Loki

Loki is our log aggregation/storage system.

Think:

```text
Prometheus → Metrics

Loki       → Logs
```

### Grafana Alloy

Alloy collects and forwards logs.

```text
Application
     │
     ▼
Docker logs
     │
     ▼
Grafana Alloy
     │
     ▼
Loki
```

Grafana then queries Loki.

---

# 3. Updated Architecture

Your environment becomes:

```text
                         USER
                          │
                          ▼
                    Spring Boot
                          │
                    PostgreSQL
                          │
            ┌─────────────┴─────────────┐
            │                           │
            │                           │
         METRICS                       LOGS
            │                           │
            ▼                           ▼
       Micrometer                  Docker Logs
            │                           │
            ▼                           ▼
       Prometheus                  Grafana Alloy
            │                           │
            │                           ▼
            │                          Loki
            │                           │
            └────────────┬──────────────┘
                         │
                         ▼
                      Grafana
                         │
              ┌──────────┴──────────┐
              │                     │
           Metrics                 Logs
```

---

# 4. Repository Structure

Create:

```text
grafana-observability-lab/
│
├── demo/
│
├── prometheus/
│   └── prometheus.yml
│
├── grafana/
│   ├── provisioning/
│   │   └── datasources/
│   │       ├── prometheus.yml
│   │       └── loki.yml
│   │
│   └── dashboards/
│
├── loki/
│   └── loki-config.yml
│
├── alloy/
│   └── config.alloy
│
├── screenshots/
│   ├── phase-02/
│   ├── phase-03/
│   └── phase-04/
│
├── docker-compose.yml
└── README.md
```

Create the directories:

```powershell
mkdir loki
mkdir alloy
mkdir grafana\provisioning\datasources
mkdir screenshots\phase-04
```

---

# 5. Configure Loki

Create:

```text
loki/loki-config.yml
```

Use:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  replication_factor: 1

  ring:
    instance_addr: 127.0.0.1

    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13

      index:
        prefix: index_
        period: 24h

storage_config:
  filesystem:
    directory: /loki/chunks
```

For our local lab, Loki stores its data using the local filesystem backed by a Docker volume.

---

# 6. Add Loki to Docker Compose

Add:

```yaml
  loki:
    image: grafana/loki:latest
    container_name: observability-loki

    ports:
      - "3100:3100"

    command:
      - "-config.file=/etc/loki/loki-config.yml"

    volumes:
      - ./loki/loki-config.yml:/etc/loki/loki-config.yml:ro
      - loki-data:/loki
```

Then add:

```yaml
volumes:
  postgres-data:
  prometheus-data:
  grafana-data:
  loki-data:
```

---

# 7. Start Loki First

Before adding Alloy, verify Loki independently.

Run:

```bash
docker compose config
```

Then:

```bash
docker compose up -d loki
```

Check:

```bash
docker compose ps
```

You should see:

```text
observability-loki       Up
```

Test:

```text
http://localhost:3100/ready
```

Or:

```bash
curl http://localhost:3100/ready
```

Expected:

```text
ready
```

If it fails:

```bash
docker logs observability-loki
```

Don't proceed until Loki is healthy.

---

# 8. Add Loki Datasource to Grafana

Create:

```text
grafana/provisioning/datasources/loki.yml
```

Add:

```yaml
apiVersion: 1

datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: true
```

Notice:

```text
http://loki:3100
```

not:

```text
http://localhost:3100
```

because Grafana is communicating with Loki through Docker networking.

---

# 9. Restart Grafana

Run:

```bash
docker compose restart grafana
```

Then go to:

```text
Grafana
   ↓
Connections
   ↓
Data sources
```

You should now have:

```text
Prometheus
Loki
```

Your Grafana architecture is now:

```text
                    Grafana
                      │
              ┌───────┴───────┐
              │               │
              ▼               ▼
         Prometheus          Loki
              │               │
           Metrics           Logs
```

---

# 10. Now Add Grafana Alloy

Alloy needs to discover Docker containers and read their logs.

Create:

```text
alloy/config.alloy
```

For the lab, configure Docker discovery:

```alloy
discovery.docker "containers" {
  host = "unix:///var/run/docker.sock"
}

discovery.relabel "docker_logs" {
  targets = discovery.docker.containers.targets

  rule {
    source_labels = ["__meta_docker_container_name"]
    regex         = "/(.*)"
    target_label  = "container"
  }

  rule {
    source_labels = ["__meta_docker_container_log_stream"]
    target_label  = "stream"
  }
}

loki.source.docker "containers" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.docker_logs.output
  forward_to = [loki.write.default.receiver]
}

loki.write "default" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}
```

Let's understand it before running it.

---

# 11. What Alloy Configuration Does

First:

```alloy
discovery.docker "containers"
```

means:

> Find Docker containers.

Then:

```alloy
discovery.relabel "docker_logs"
```

creates useful labels such as:

```text
container="observability-demo"
```

Then:

```alloy
loki.source.docker
```

reads Docker logs.

Finally:

```alloy
loki.write
```

sends those logs to:

```text
http://loki:3100/loki/api/v1/push
```

Therefore:

```text
Docker containers
       │
       ▼
discovery.docker
       │
       ▼
loki.source.docker
       │
       ▼
Grafana Alloy
       │
       ▼
loki.write
       │
       ▼
Loki
```

---

# 12. Add Alloy to Docker Compose

Add:

```yaml
  alloy:
    image: grafana/alloy:latest
    container_name: observability-alloy

    ports:
      - "12345:12345"

    volumes:
      - ./alloy/config.alloy:/etc/alloy/config.alloy:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro

    command:
      - "run"
      - "--server.http.listen-addr=0.0.0.0:12345"
      - "/etc/alloy/config.alloy"

    depends_on:
      - loki
```

The important part is:

```yaml
- /var/run/docker.sock:/var/run/docker.sock:ro
```

This allows Alloy to discover Docker containers and access their log streams through the Docker API.

For this local lab, we mount it read-only.

---

# 13. Your Services Now

You should have:

```text
postgres
demo
prometheus
grafana
loki
alloy
```

Run:

```bash
docker compose config
```

Then:

```bash
docker compose up -d
```

Check:

```bash
docker compose ps
```

Expected:

```text
observability-postgres
observability-demo
observability-prometheus
observability-grafana
observability-loki
observability-alloy
```

---

# 14. Check Alloy

Open:

```text
http://localhost:12345
```

You should see the Alloy interface.

Also check:

```bash
docker logs observability-alloy
```

You don't want repeated connection/configuration errors.

---

# 15. Generate Spring Boot Logs

First:

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

The error endpoint should create a stack trace in the Spring Boot logs.

Verify directly:

```bash
docker logs observability-demo
```

At this stage:

```text
Spring Boot
     │
     ▼
Docker logs
     │
     ▼
Alloy
     │
     ▼
Loki
```

should be operating.

---

# 16. Query Logs from Grafana

Open Grafana:

```text
http://localhost:3000
```

Go to:

```text
Explore
   ↓
Select datasource
   ↓
Loki
```

Start with:

```logql
{container="observability-demo"}
```

You should see Spring Boot logs.

Congratulations — these logs are no longer being inspected using only:

```bash
docker logs
```

They are now centralized in Loki.

---

# 17. Your First LogQL Query

Prometheus uses:

```text
PromQL
```

Loki uses:

```text
LogQL
```

Example:

```logql
{container="observability-demo"}
```

means:

> Give me logs where the container label is `observability-demo`.

---

# 18. Search for Errors

Generate errors:

```powershell
1..10 | ForEach-Object {

    try {
        Invoke-WebRequest http://localhost:8080/api/error
    }
    catch {}

}
```

Then query:

```logql
{container="observability-demo"} |= "ERROR"
```

`|=` means:

> Contains this text.

Conceptually:

```text
All application logs

       ↓

container="observability-demo"

       ↓

contains "ERROR"

       ↓

Error logs
```

---

# 19. Search for Your Exception

Your application generates something similar to:

```text
Intentional error generated for observability testing
```

Search:

```logql
{container="observability-demo"}
  |= "Intentional error"
```

Now you can directly identify the exception responsible for your intentionally generated HTTP 500.

---

# 20. Exclude Logs

You can also exclude text:

```logql
{container="observability-demo"} != "INFO"
```

This means:

> Show logs that don't contain `INFO`.

---

# 21. Count Errors

Logs can also be converted into metrics.

Try:

```logql
count_over_time(
  {container="observability-demo"} |= "ERROR" [5m]
)
```

This asks:

> How many matching ERROR log entries occurred during the last five minutes?

For a single total across streams, you can use:

```logql
sum(
  count_over_time(
    {container="observability-demo"} |= "ERROR" [5m]
  )
)
```

Now Loki can power a graph such as:

```text
ERROR LOG COUNT

10 ┤              █
 8 ┤              █
 6 ┤          █   █
 4 ┤      █   █   █
 2 ┤  █   █   █   █
 0 └────────────────
```

---

# 22. Understand Loki Labels

One of the most important Loki concepts is **labels**.

We currently have:

```text
container="observability-demo"
```

Later we'll improve this to labels such as:

```text
service="demo"
environment="local"
level="ERROR"
```

Then queries become much more useful:

```logql
{service="demo", environment="local"}
```

Important rule:

> Do not turn every log field into a Loki label.

High-cardinality labels can create performance and storage problems.

Good labels usually have relatively bounded values, such as:

```text
service
environment
namespace
container
cluster
```

Fields such as these generally should not become labels:

```text
user_id
request_id
trace_id
timestamp
random UUID
```

We'll still use values like `trace_id`, but usually as structured log fields rather than high-cardinality index labels.

---

# 23. Build a Logs Dashboard Row

Open your existing:

```text
Spring Boot Application Observability
```

dashboard.

Add another row:

```text
APPLICATION LOGS
```

Add a visualization using Loki.

Query:

```logql
{container="observability-demo"}
```

Visualization:

```text
Logs
```

Panel title:

```text
Spring Boot Application Logs
```

---

# 24. Add Error Log Count Panel

Datasource:

```text
Loki
```

Query:

```logql
sum(
  count_over_time(
    {container="observability-demo"} |= "ERROR" [5m]
  )
)
```

Visualization:

```text
Stat
```

Title:

```text
Application Error Logs — Last 5 Minutes
```

Now your dashboard combines:

```text
                 Grafana

        ┌──────────┴──────────┐
        │                     │
        ▼                     ▼

     METRICS                 LOGS

   Prometheus                Loki

 Request Rate              App Logs
 Error Rate                Error Logs
 Latency                   Exceptions
 JVM
```

---

# 25. Most Important Phase 4 Scenario

Now perform the scenario that makes the project valuable.

Generate normal traffic:

```powershell
1..50 | ForEach-Object {
    Invoke-WebRequest http://localhost:8080/api/health
}
```

Dashboard:

```text
Request Rate ↑
5xx Error Rate = 0
```

Now generate failures:

```powershell
1..20 | ForEach-Object {

    try {
        Invoke-WebRequest http://localhost:8080/api/error
    }
    catch {}

}
```

Your metrics should show:

```text
HTTP 5xx Error Rate
        ↑
```

Now query Loki:

```logql
{container="observability-demo"} |= "ERROR"
```

Then:

```logql
{container="observability-demo"}
  |= "Intentional error"
```

You have now demonstrated:

```text
             INCIDENT

HTTP 5xx spike detected
          │
          ▼
    Grafana Metrics
          │
          ▼
     Prometheus
          │
          ▼
 "Errors increased"
          │
          ▼
      Check Logs
          │
          ▼
        Loki
          │
          ▼
RuntimeException found
          │
          ▼
     Root-cause clue
```

This is a much stronger observability demonstration than simply saying:

> I installed Loki.

---

# 26. Metrics vs Logs

Understand this distinction clearly.

### Metrics

Tell you:

```text
WHAT is happening?
```

Examples:

```text
Error rate increased
Latency increased
CPU increased
Memory increased
Application down
```

### Logs

Help explain:

```text
WHY might it be happening?
```

Examples:

```text
RuntimeException
Database connection refused
TimeoutException
Authentication failure
NullPointerException
```

That's why we use both.

---

# 27. Prometheus vs Loki

| Prometheus | Loki |
|---|---|
| Metrics | Logs |
| PromQL | LogQL |
| Time-series metrics | Log streams |
| Request rate | Application messages |
| Error rate | Exceptions |
| CPU | Stack traces |
| Memory | Diagnostic messages |
| Latency | Error details |

Grafana sits above both:

```text
             Grafana
                │
        ┌───────┴───────┐
        ▼               ▼
   Prometheus          Loki
        │               │
     Metrics           Logs
```

---

# 28. Troubleshooting Loki

Check:

```bash
docker compose ps
```

Then:

```bash
docker logs observability-loki
```

Verify:

```bash
curl http://localhost:3100/ready
```

Expected:

```text
ready
```

---

# 29. Troubleshooting Alloy

Check:

```bash
docker logs observability-alloy
```

Verify Alloy UI:

```text
http://localhost:12345
```

If Loki is working but there are no logs, your troubleshooting path should be:

```text
No logs in Grafana
       │
       ▼
Does Loki datasource work?
       │
       ▼
Is Loki healthy?
       │
       ▼
Is Alloy running?
       │
       ▼
Can Alloy discover Docker containers?
       │
       ▼
Does Spring Boot actually produce logs?
       │
       ▼
docker logs observability-demo
```

This layer-by-layer approach is exactly how you should troubleshoot telemetry pipelines.

---

# 30. Full Docker Compose Structure

At the end of Phase 4, conceptually your Compose file contains:

```yaml
services:

  postgres:
    # PostgreSQL configuration

  demo:
    # Spring Boot configuration

  prometheus:
    # Prometheus configuration

  grafana:
    # Grafana configuration

  loki:
    # Loki configuration

  alloy:
    # Grafana Alloy configuration


volumes:
  postgres-data:
  prometheus-data:
  grafana-data:
  loki-data:
```

Keep the working Phase 3 configurations unchanged and add Loki/Alloy incrementally. This makes troubleshooting much easier than replacing the entire file at once.

---

# 31. Phase 4 Completion Checklist

Before moving forward:

```text
[ ] Loki container running

[ ] Loki /ready returns ready

[ ] Loki datasource available in Grafana

[ ] Alloy container running

[ ] Alloy UI available

[ ] Alloy discovers Docker containers

[ ] Spring Boot logs reach Loki

[ ] Loki logs visible in Grafana Explore

[ ] LogQL queries working

[ ] ERROR logs searchable

[ ] Intentional exception searchable

[ ] Error log count panel created

[ ] Application Logs panel created

[ ] HTTP 500 metric spike visible

[ ] Related ERROR logs visible

[ ] Metrics → Logs troubleshooting tested
```

---

# 32. Screenshots to Save

Create:

```text
screenshots/
└── phase-04/
```

Save:

```text
01-loki-datasource.png

02-alloy-running.png

03-spring-boot-logs.png

04-error-log-query.png

05-error-log-count.png

06-metrics-and-logs-dashboard.png

07-http500-investigation.png
```

For LinkedIn, screenshots **#6 and #7** will be particularly useful because they show actual observability rather than just running containers.

---

# 33. Git Commit

Once everything works:

```bash
git status
```

Then:

```bash
git add .
```

Commit:

```bash
git commit -m "feat: add centralized logging with Loki and Grafana Alloy"
```

Tag:

```bash
git tag phase-4
```

Push:

```bash
git push
```

Then:

```bash
git push origin phase-4
```

---

# What You Should Be Able to Explain After Phase 4

If an interviewer asks:

> How are logs collected in your observability project?

You should be able to explain:

> My Spring Boot application runs as a Docker container and writes logs to stdout/stderr. Grafana Alloy discovers the Docker container and collects its log stream through the Docker API. Alloy forwards those logs to Loki, which stores and indexes the log metadata. Grafana uses Loki as a datasource, and I use LogQL to search and analyze application logs.

And if they ask:

> Why do you need Loki when you already have Prometheus?

You can explain:

> Prometheus provides metrics such as request rate, error rate and latency, which help identify what is happening. Loki provides the application logs that give additional diagnostic context about why an error occurred. I use Grafana to investigate both.

---

# Phase 4 Final Architecture

```text
                         Spring Boot
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
           METRICS                           LOGS
              │                               │
         Micrometer                       stdout/stderr
              │                               │
         Actuator                             │
              │                               ▼
              │                         Docker Logging
              │                               │
              ▼                               ▼
         Prometheus                     Grafana Alloy
              │                               │
              │                               ▼
              │                              Loki
              │                               │
              └───────────────┬───────────────┘
                              │
                              ▼
                           Grafana
                              │
                  ┌───────────┴───────────┐
                  ▼                       ▼
             Metrics Panels           Log Panels
```

Your project has now progressed from **monitoring** toward actual **observability investigation**:

```text
Phase 1 → Application
Phase 2 → Metrics collection
Phase 3 → Metrics visualization
Phase 4 → Centralized logs
```

**Phase 5 will add OpenTelemetry + Tempo for distributed tracing.** Then we'll be able to follow the path:

**Grafana alert/metric → Loki logs → Tempo trace → request-level root-cause investigation.**