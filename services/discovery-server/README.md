# discovery-server

> Service registry: every microservice registers here and finds the others by name.

## Concept illustrated
Service discovery with Netflix Eureka. Instead of hard-coding `http://localhost:8081`
(step-02), a service asks the registry where `customer-service` currently runs.
If you start a second instance, it registers itself and the callers see it
automatically.

## Overview
- **Port:** 8761
- **Depends on:** nothing
- **Key URLs:** `/` (dashboard), `/eureka/apps` (registry content), `/actuator/health`

How it works:
1. A client starts and **registers** (name, host, port) with the server.
2. It sends a **heartbeat** every 30 s to say "I am alive".
3. Clients **fetch the registry** and cache it locally (refresh every 30 s).
4. If heartbeats stop, the server removes the instance after a delay.

## Run locally
    ./mvnw -pl services/discovery-server spring-boot:run

Open http://localhost:8761: the list of instances is empty until a client registers.

## Run with Docker
    docker compose -f infra/docker-compose.yml up --build discovery-server

## Run the tests
    ./mvnw -pl services/discovery-server test

## Exercise
1. Start the server, then `customer-service` and `api-gateway`. Refresh the
   dashboard: which instances appear, and under which names?
2. Stop `customer-service` with Ctrl+C. How long before it disappears from the
   dashboard? Why is it not instant?
3. Set `register-with-eureka: true` on the server and restart. What changes, and
   why is it unnecessary for a standalone registry?

## Common pitfalls
- Forgetting `register-with-eureka: false` and `fetch-registry: false` on the
  server produces noisy "connection refused" errors at startup.
- The red "EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP"
  banner is Eureka's self-preservation mode: normal in development.
- The registry is eventually consistent: a new instance can take up to ~30 s
  to be visible to the gateway.