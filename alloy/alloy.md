This is a good simple **Grafana Alloy → Docker → Loki** configuration. The important thing is that this configuration is **not reading log files using a normal filesystem path**. Alloy connects to the **Docker daemon through the Docker socket**, discovers containers, reads their logs, adds labels, and sends the logs to Loki.

The complete flow is:

```text
Docker Containers
   │
   │ Docker socket
   │ /var/run/docker.sock
   ▼
discovery.docker "containers"
   │
   │ discovered container metadata
   ▼
discovery.relabel "docker_logs"
   │
   │ adds useful labels
   │ container="demo"
   │ stream="stdout"
   ▼
loki.source.docker "containers"
   │
   │ reads container logs
   ▼
loki.write "default"
   │
   │ HTTP POST
   ▼
http://loki:3100/loki/api/v1/push
   │
   ▼
Loki
   │
   ▼
Grafana
```

### 1. Docker discovery

```alloy
discovery.docker "containers" {
  host = "unix:///var/run/docker.sock"
}
```

This tells Alloy:

> Connect to the Docker daemon and discover the containers running there.

The important path is:

```text
unix:///var/run/docker.sock
```

This is **not a log-file path**. It is a Unix socket used to communicate with Docker.

Normally on Linux:

```text
/var/run/docker.sock
```

is the Docker daemon socket.

You can think of it like an API endpoint:

```text
Alloy
  │
  │ /var/run/docker.sock
  ▼
Docker Engine
  │
  ├── postgres
  ├── demo
  ├── prometheus
  ├── grafana
  └── loki
```

Because you are running this stack with Docker Compose, your Alloy service will usually need something similar to:

```yaml
alloy:
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock
```

This makes the host's Docker socket available inside the Alloy container.

So:

```text
HOST
/var/run/docker.sock
        │
        │ volume mount
        ▼
ALLOY CONTAINER
/var/run/docker.sock
```

Without this mount, Alloy running inside a container normally can't access that socket.

---

# 2. `discovery.docker.containers.targets`

Next:

```alloy
discovery.relabel "docker_logs" {
  targets = discovery.docker.containers.targets
```

This line connects the first component to the second.

Break the reference into pieces:

```text
discovery.docker.containers.targets
│         │       │
│         │       └── output containing discovered targets
│         │
│         └────────── component label/name
│
└──────────────────── component type
```

You created:

```alloy
discovery.docker "containers"
```

Therefore its reference starts with:

```text
discovery.docker.containers
```

and `.targets` means:

> Give me the targets discovered by this component.

For example, suppose Docker contains:

```text
observability-demo
observability-postgres
prometheus
grafana
loki
```

Alloy discovers those containers along with Docker metadata.

Conceptually:

```text
discovery.docker "containers"

        ↓

Container 1
Container 2
Container 3
Container 4
Container 5
```

---

# 3. Why `__meta_docker_container_name`?

Now you have:

```alloy
rule {
  source_labels = ["__meta_docker_container_name"]
  regex         = "/(.*)"
  target_label  = "container"
}
```

Docker discovery provides Alloy with **internal metadata labels**.

One is:

```text
__meta_docker_container_name
```

Suppose your container is:

```text
observability-demo
```

Docker metadata may contain:

```text
__meta_docker_container_name="/observability-demo"
```

Notice the `/` at the beginning.

Your regex:

```text
/(.*)
```

means:

```text
/observability-demo
││
│└──────── capture this
└───────── match /
```

The captured value becomes:

```text
observability-demo
```

Then:

```alloy
target_label = "container"
```

creates a normal Loki label:

```text
container="observability-demo"
```

So you're effectively converting:

```text
Internal Docker metadata

__meta_docker_container_name="/observability-demo"

                  ↓

              Relabel

                  ↓

Useful Loki label

container="observability-demo"
```

That makes querying logs in Grafana much easier.

For example, you could query:

```logql
{container="observability-demo"}
```

---

# 4. Docker log stream

Your second rule:

```alloy
rule {
  source_labels = ["__meta_docker_container_log_stream"]
  target_label  = "stream"
}
```

Docker container logs normally come from:

```text
stdout
stderr
```

For example, your Spring Boot/Python/demo application might print:

```text
Application started
Request received
Database connected
```

to:

```text
stdout
```

Errors may be written to:

```text
stderr
```

Alloy receives Docker metadata indicating the stream.

For example:

```text
__meta_docker_container_log_stream="stdout"
```

Your relabel rule converts that into:

```text
stream="stdout"
```

Therefore Loki can receive something conceptually like:

```text
container="observability-demo"
stream="stdout"
```

You could then query:

```logql
{container="observability-demo", stream="stdout"}
```

---

# 5. `discovery.relabel.docker_logs.output`

Now look at this:

```alloy
loki.source.docker "containers" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.docker_logs.output
  forward_to = [loki.write.default.receiver]
}
```

The important connection is:

```text
discovery.relabel.docker_logs.output
```

Again:

```text
discovery.relabel.docker_logs.output
│               │           │
│               │           └── output
│               │
│               └──────────── component name
│
└──────────────────────────── component type
```

You defined:

```alloy
discovery.relabel "docker_logs"
```

Therefore:

```text
discovery.relabel.docker_logs
```

refers to that component.

`.output` means:

> Give me the targets after relabeling.

So the pipeline is:

```text
discovery.docker.containers.targets
                │
                ▼
discovery.relabel "docker_logs"
                │
                │ Add:
                │ container="..."
                │ stream="..."
                ▼
discovery.relabel.docker_logs.output
                │
                ▼
loki.source.docker
```

---

# 6. `loki.source.docker`

This is where Alloy actually starts collecting the Docker logs.

```alloy
loki.source.docker "containers" {
```

There is an important difference between:

```text
discovery.docker
```

and:

```text
loki.source.docker
```

Think of them as:

| Component | Responsibility |
|---|---|
| `discovery.docker` | Find containers |
| `discovery.relabel` | Modify/add labels |
| `loki.source.docker` | Collect logs |
| `loki.write` | Send logs to Loki |

So discovery doesn't mean the logs are already being collected.

It basically says:

```text
"I found these containers."
```

Then:

```text
loki.source.docker
```

says:

```text
"Now collect logs from those containers."
```

---

# 7. Why Docker socket appears again

Inside:

```alloy
loki.source.docker "containers" {
  host = "unix:///var/run/docker.sock"
```

You may wonder:

> We already gave Docker socket to `discovery.docker`. Why again?

Because they are separate Alloy components.

The first component uses Docker to **discover containers**:

```text
discovery.docker
       │
       ▼
Which containers exist?
```

The second uses Docker to **collect container logs**:

```text
loki.source.docker
       │
       ▼
Give me logs from those containers.
```

Both therefore need access to Docker.

---

# 8. `forward_to`

Next:

```alloy
forward_to = [loki.write.default.receiver]
```

This is another pipeline connection.

You created:

```alloy
loki.write "default"
```

Therefore:

```text
loki.write.default
```

refers to that component.

`.receiver` means the input where another component can send log entries.

So:

```text
loki.source.docker
       │
       │ forward_to
       ▼
loki.write.default.receiver
```

In simple terms:

> After collecting Docker logs, send them to the `default` Loki writer.

---

# 9. Loki endpoint

Finally:

```alloy
loki.write "default" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}
```

This is where Alloy sends the logs.

Break it down:

```text
http://loki:3100/loki/api/v1/push
│      │     │            │
│      │     │            └── Loki log ingestion API
│      │     │
│      │     └────────────── Loki HTTP port
│      │
│      └──────────────────── Docker service/container hostname
│
└─────────────────────────── HTTP
```

### Why `loki` instead of `localhost`?

This is very important when learning Docker observability.

If your Compose file contains:

```yaml
services:

  loki:
    image: grafana/loki

  alloy:
    image: grafana/alloy
```

Docker provides internal DNS.

Therefore Alloy can reach Loki using:

```text
http://loki:3100
```

Here:

```text
loki
```

is the Compose service name.

The network looks like:

```text
Docker Compose Network

┌──────────────────────────────────────────────┐

    Alloy
      │
      │ http://loki:3100
      ▼
    Loki
      │
      │
      ▼
   :3100

└──────────────────────────────────────────────┘
```

You normally should **not** use:

```text
http://localhost:3100
```

from the Alloy container, because `localhost` would mean:

```text
Alloy container itself
```

not the Loki container.

---

# 10. `/loki/api/v1/push`

This part:

```text
/loki/api/v1/push
```

is Loki's HTTP endpoint for receiving log data.

So Alloy essentially does:

```text
Collected Docker logs
        ↓
Add labels
        ↓
Batch logs
        ↓
HTTP request
        ↓
POST
http://loki:3100/loki/api/v1/push
        ↓
Loki stores them
```

---

# Your entire configuration in plain English

Your configuration essentially says:

> **Discover Docker containers → create useful labels from Docker metadata → collect logs from those containers → send the logs to Loki.**

The exact pipeline is:

```text
                 Docker Engine
                      │
             /var/run/docker.sock
                      │
                      ▼
        discovery.docker "containers"
                      │
                      │
          containers.targets
                      │
                      ▼
      discovery.relabel "docker_logs"
                      │
              ┌───────┴────────┐
              │                │
              ▼                ▼
         container          stream
       "demo-app"          "stdout"
              │                │
              └───────┬────────┘
                      │
                      ▼
         loki.source.docker
             "containers"
                      │
             Collect logs
                      │
                      ▼
       loki.write.default.receiver
                      │
                      ▼
            loki.write "default"
                      │
                      ▼
     http://loki:3100/loki/api/v1/push
                      │
                      ▼
                    Loki
                      │
                      ▼
                   Grafana
```

### The 4 paths/references you should remember

| Configuration | Meaning |
|---|---|
| `unix:///var/run/docker.sock` | Connection to Docker daemon |
| `discovery.docker.containers.targets` | Containers discovered by Docker discovery |
| `discovery.relabel.docker_logs.output` | Discovered targets after your relabel rules |
| `loki.write.default.receiver` | Input of your Loki writer |
| `http://loki:3100/loki/api/v1/push` | Loki API where Alloy sends logs |

One key concept: **`discovery.docker.containers.targets`, `discovery.relabel.docker_logs.output`, and `loki.write.default.receiver` are not filesystem paths.** They are references connecting Alloy components together. `/var/run/docker.sock` is the Unix socket path, while `/loki/api/v1/push` is an HTTP API path.