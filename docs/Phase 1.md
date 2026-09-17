# Grafana Observability Lab

A hands-on observability project designed to demonstrate practical monitoring, troubleshooting, and DevOps skills using **Spring Boot, PostgreSQL, Docker, Prometheus, Grafana, Loki, Tempo, OpenTelemetry, and Grafana Alloy**.

The project is built incrementally. Each phase introduces a new part of the observability stack.

---

# Phase 1 — Spring Boot + PostgreSQL + Docker

## 1. Objective

The objective of Phase 1 is to build the application foundation that will later be monitored using the Grafana observability stack.

In this phase, we:

* Create a Spring Boot REST API
* Create endpoints for normal, slow, and failed requests
* Configure PostgreSQL
* Containerize the Spring Boot application
* Run the application and database using Docker Compose
* Configure Docker networking
* Configure PostgreSQL persistent storage
* Configure container health checks
* Enable Spring Boot Actuator

The `/slow` and `/error` endpoints intentionally generate abnormal application behavior. These endpoints will be used in later phases to demonstrate metrics, logging, tracing, alerting, and incident troubleshooting.

---

## 2. Phase 1 Architecture

```text
                  User / curl
                       |
                       |
                 localhost:8080
                       |
                       v
             +-------------------+
             |                   |
             |    Spring Boot    |
             |     Demo App      |
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
                       |
                       v
              Persistent Volume
```

Both the Spring Boot application and PostgreSQL run as Docker containers.

---

## 3. Technology Stack

| Technology           | Purpose                                     |
| -------------------- | ------------------------------------------- |
| Java 17              | Application programming language            |
| Spring Boot          | REST API                                    |
| Spring Web           | REST endpoints                              |
| Spring Data JPA      | Database integration                        |
| Spring Boot Actuator | Application health and management endpoints |
| PostgreSQL           | Relational database                         |
| Maven                | Build and dependency management             |
| Docker               | Application containerization                |
| Docker Compose       | Multi-container orchestration               |

---

## 4. Repository Structure

```text
grafana-observability-lab/
│
├── demo-app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       └── resources/
│   │
│   ├── pom.xml
│   └── Dockerfile
│
├── docker-compose.yml
├── .gitignore
└── README.md
```

As additional observability components are introduced, this repository will expand with Prometheus, Grafana, Loki, Tempo, OpenTelemetry, and Grafana Alloy configurations.

---

# 5. Application Endpoints

The application exposes endpoints specifically designed for observability testing.

### Health endpoint

```text
GET /api/health
```

Used to generate normal successful traffic.

Example:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "observability-demo-app"
}
```

---

### Slow endpoint

```text
GET /api/slow
```

Introduces an intentional delay of approximately three seconds.

```bash
curl http://localhost:8080/api/slow
```

This endpoint will later be used to investigate:

* API latency
* Request duration
* P95/P99 response times
* Distributed traces
* Latency alerts

---

### Error endpoint

```text
GET /api/error
```

Generates an intentional application exception and HTTP 500 response.

```bash
curl http://localhost:8080/api/error
```

This endpoint will later be used to investigate:

* HTTP 5xx errors
* Application error rate
* Error logs
* Grafana alerts
* Trace-to-log correlation

---

### Spring Boot health endpoint

```text
GET /actuator/health
```

Test using:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

---

# 6. Docker Architecture

Docker Compose runs two services during Phase 1.

```text
       Docker Compose Network
       
+---------------------------+
|                           |
|   demo-app                |
|   Spring Boot             |
|   Port: 8080              |
|       |                   |
|       |                   |
|       | JDBC              |
|       v                   |
|   postgres                |
|   PostgreSQL              |
|   Port: 5432              |
|                           |
+---------------------------+
```

Docker Compose provides internal DNS resolution.

Therefore, the Spring Boot application connects to PostgreSQL using:

```text
postgres:5432
```

rather than:

```text
localhost:5432
```

Inside a container, `localhost` refers to that same container.

---

# 7. Database Configuration

The application uses environment variables for database configuration.

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:observability}

spring.datasource.username=${DB_USER:observability}

spring.datasource.password=${DB_PASSWORD:observability123}
```

Docker Compose provides these variables to the application container:

```text
DB_HOST=postgres
DB_PORT=5432
DB_NAME=observability
DB_USER=observability
DB_PASSWORD=observability123
```

This keeps application configuration portable between local and container environments.

> The credentials used in this lab are development-only credentials and should not be used in production environments. Production secrets should be managed through an appropriate secrets-management solution.

---

# 8. Docker Multi-Stage Build

The Spring Boot application uses a multi-stage Docker build.

```text
Stage 1
Maven + JDK
      |
      v
Compile application
      |
      v
Create JAR
      |
      v
Stage 2
Java Runtime
      |
      v
Copy application JAR
      |
      v
Run application
```

This separates the build environment from the runtime environment.

The final application image does not require Maven.

---

# 9. Prerequisites

Before starting Phase 1, install:

* Git
* Docker Desktop
* Java 17 (optional for local development)
* Maven (optional because Maven Wrapper/Docker can be used)

Verify Docker:

```bash
docker version
```

Verify Docker Compose:

```bash
docker compose version
```

Verify Git:

```bash
git --version
```

---

# 10. Running the Project

Clone the repository:

```bash
git clone https://github.com/<your-username>/grafana-observability-lab.git
```

Navigate to the project:

```bash
cd grafana-observability-lab
```

Validate the Docker Compose configuration:

```bash
docker compose config
```

Build the containers:

```bash
docker compose build
```

Start the environment:

```bash
docker compose up -d
```

Check container status:

```bash
docker compose ps
```

Expected containers:

```text
observability-demo-app
observability-postgres
```

PostgreSQL should eventually report a healthy status.

---

# 11. Testing the Application

### Normal request

```bash
curl http://localhost:8080/api/health
```

### Slow request

```bash
curl http://localhost:8080/api/slow
```

### Error request

```bash
curl http://localhost:8080/api/error
```

### Spring Boot health

```bash
curl http://localhost:8080/actuator/health
```

---

# 12. Viewing Application Logs

View Spring Boot logs:

```bash
docker logs observability-demo-app
```

Follow logs continuously:

```bash
docker logs -f observability-demo-app
```

After requesting:

```bash
curl http://localhost:8080/api/error
```

the logs should contain the intentionally generated exception.

These logs will later be collected using Grafana Alloy and stored in Loki.

---

# 13. Docker Troubleshooting Commands

Check running containers:

```bash
docker ps
```

Check all containers:

```bash
docker ps -a
```

Check Compose services:

```bash
docker compose ps
```

View application logs:

```bash
docker compose logs demo-app
```

View PostgreSQL logs:

```bash
docker compose logs postgres
```

Follow all logs:

```bash
docker compose logs -f
```

Inspect Docker networks:

```bash
docker network ls
```

Inspect the project network:

```bash
docker network inspect grafana-observability-lab_default
```

Restart the application:

```bash
docker compose restart demo-app
```

Stop the environment:

```bash
docker compose down
```

Start it again:

```bash
docker compose up -d
```

---

# 14. Persistent Database Storage

PostgreSQL uses a Docker named volume:

```text
postgres-data
```

The architecture is:

```text
PostgreSQL Container
        |
        v
   postgres-data
        |
        v
 Persistent Storage
```

Therefore, removing and recreating the PostgreSQL container does not automatically remove the database volume.

Stop containers:

```bash
docker compose down
```

To intentionally remove containers **and the database volume**:

```bash
docker compose down -v
```

> `-v` deletes the Compose-managed volumes and therefore removes the PostgreSQL data stored by this lab.

---

# 15. Health Checks

PostgreSQL uses a Docker health check:

```text
pg_isready
```

The Spring Boot application depends on PostgreSQL becoming healthy before it starts.

```text
Docker Compose
      |
      v
Start PostgreSQL
      |
      v
PostgreSQL health check
      |
      v
Database HEALTHY
      |
      v
Start Spring Boot
```

This reduces application startup failures caused by attempting to connect before the database is ready.

---

# 16. Observability Scenarios Created in Phase 1

Phase 1 deliberately introduces different application behaviors.

| Scenario           | Endpoint           | Expected Behavior  |
| ------------------ | ------------------ | ------------------ |
| Healthy request    | `/api/health`      | HTTP 200           |
| Slow request       | `/api/slow`        | ~3-second response |
| Failed request     | `/api/error`       | HTTP 500           |
| Application health | `/actuator/health` | Application status |

These scenarios become telemetry sources in later phases.

For example:

```text
/api/slow
     |
     v
High request duration
     |
     v
Prometheus metric
     |
     v
Grafana dashboard
     |
     v
Latency alert
```

And:

```text
/api/error
     |
     v
HTTP 500
     |
     +------> Prometheus metric
     |
     +------> Loki log
     |
     +------> Tempo trace
                     |
                     v
                   Grafana
```

---

# 17. Skills Demonstrated in Phase 1

This phase demonstrates hands-on experience with:

* Spring Boot REST APIs
* Docker containerization
* Docker Compose
* Multi-stage Docker builds
* Container networking
* Docker DNS/service discovery
* Environment-based configuration
* PostgreSQL
* Docker volumes
* Container health checks
* Application logs
* Spring Boot Actuator
* Failure simulation
* Latency simulation
* Basic container troubleshooting

---

# 18. Project Roadmap

```text
Phase 1
Spring Boot + PostgreSQL + Docker
              |
              v
Phase 2
Micrometer + Prometheus
              |
              v
Phase 3
Grafana Dashboards
              |
              v
Phase 4
Loki + Grafana Alloy
              |
              v
Phase 5
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
Incident Simulation & Troubleshooting
```

---

# Phase 1 Status

**Spring Boot + PostgreSQL + Docker**

Goal:

```text
Application running
        +
PostgreSQL healthy
        +
Docker networking working
        +
Test traffic generated
        =
Phase 1 Complete
```

Once Phase 1 is complete, the next step is **Phase 2 — instrumenting the Spring Boot application with Micrometer and collecting application metrics using Prometheus**.
