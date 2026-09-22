Since you're testing Prometheus + Grafana observability, it's good to generate different types of traffic:

✅ Normal traffic (/api/health)
✅ Slow requests (/api/slow)
✅ Errors (/api/error)
✅ Mixed traffic (realistic)

1. Generate Normal Traffic
for i in {1..100}
do
  curl -s http://localhost:8080/api/health > /dev/null
done

2. Generate Slow Requests

This will create latency spikes.

for i in {1..50}
do
  curl -s http://localhost:8080/api/slow > /dev/null
done


Concurrent version:

for i in {1..20}
do
  curl -s http://localhost:8080/api/slow > /dev/null &
done

wait

3. Generate Error Traffic
for i in {1..30}
do
  curl -s http://localhost:8080/api/error > /dev/null 2>&1
done


Concurrent version:

for i in {1..20}
do
  curl -s http://localhost:8080/api/error > /dev/null 2>&1 &
done

wait

4. Mixed Traffic Simulation

This is closer to real-world usage.

for i in {1..100}
do
  curl -s http://localhost:8080/api/health > /dev/null

  if (( i % 10 == 0 )); then
    curl -s http://localhost:8080/api/error > /dev/null 2>&1
  fi

  if (( i % 5 == 0 )); then
    curl -s http://localhost:8080/api/slow > /dev/null
  fi
done


Results:

100 health requests
20 slow requests
10 error requests
5. Continuous Traffic Generator

Run until you stop with Ctrl + C.

while true
do
  curl -s http://localhost:8080/api/health > /dev/null

  RAND=$((RANDOM % 100))

  if [ $RAND -lt 10 ]; then
      curl -s http://localhost:8080/api/error > /dev/null 2>&1
  fi

  if [ $RAND -lt 20 ]; then
      curl -s http://localhost:8080/api/slow > /dev/null
  fi

  sleep 1
done


This generates:

~80% normal traffic
~10% errors
~10% slow requests
6. High Load Test
for i in {1..500}
do
  curl -s http://localhost:8080/api/health > /dev/null &
done

wait

Prometheus Queries to Watch

Request Rate:

sum(rate(http_server_requests_seconds_count[1m]))


Requests by Endpoint:

sum by (uri)(
  rate(http_server_requests_seconds_count[1m])
)


Average Response Time:

rate(http_server_requests_seconds_sum[1m])
/
rate(http_server_requests_seconds_count[1m])


Error Count:

sum by (uri,status)(
  rate(http_server_requests_seconds_count{status=~"5.."}[1m])
)


For your observability demo, the best sequence is:

# Normal traffic
for i in {1..100}; do curl -s http://localhost:8080/api/health > /dev/null; done

# Slow traffic
for i in {1..20}; do curl -s http://localhost:8080/api/slow > /dev/null & done
wait

# Error traffic
for i in {1..20}; do curl -s http://localhost:8080/api/error > /dev/null 2>&1; done


This will create visible spikes in request rate, latency, and error rate on your Grafana dashboards.

https://www.youtube.com/watch?v=FLT0d8fyhK4