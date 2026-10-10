# api-gateway

> Single entry point of TicketFlow: clients talk to the gateway, never to the services directly.

## Concept illustrated
Spring Cloud Gateway (WebFlux flavour) as a reverse proxy. A **route** =
an id + a target `uri` + **predicates** (when does it match?) + **filters**
(what do we change on the way?).

Since `step-03-discovery` the routes are **dynamic**: the gateway does not know
any host or port. A route targets `lb://customer-service`, where `lb` means
*load-balanced* and `customer-service` is the name the service registered under
in Eureka. For each request the gateway asks the registry for the available
instances and picks one (round-robin). In `step-02-gateway-static` the address
was written in the configuration (`http://localhost:8081`).

## Overview
- **Port:** 8080
- **Depends on:** `discovery-server` (8761) to find `customer-service`
- **Runtime:** WebFlux on Netty (not Tomcat)

| Route | Predicate | Target |
|---|---|---|
| `customer-service` | `Path=/api/customers/**` | `lb://customer-service` |

Filter used: `AddResponseHeader=X-Served-By, api-gateway`.

The Eureka address can be overridden with the `EUREKA_URL` environment variable
(default `http://localhost:8761/eureka/`). This is what Docker Compose does.

## Run locally
Start the registry first, then the service, then the gateway.

Terminal 1:

    ./mvnw -pl services/discovery-server spring-boot:run

Terminal 2:

    ./mvnw -pl services/customer-service spring-boot:run

Terminal 3:

    ./mvnw -pl services/api-gateway spring-boot:run

Open http://localhost:8761 and wait until `CUSTOMER-SERVICE` and `API-GATEWAY`
are listed (up to ~30 s). Requests sent before that may answer 503.

Call customer-service **through the gateway** (port 8080, not 8081):

    curl -i -X POST localhost:8080/api/customers \
      -H 'Content-Type: application/json' \
      -d '{"firstName":"Sara","lastName":"Idrissi","email":"sara@example.com"}'

    curl -i localhost:8080/api/customers

Look for the `X-Served-By: api-gateway` response header. List the routes the
gateway knows about:

    curl localhost:8080/actuator/gateway/routes

## Run with Docker
    docker compose -f infra/docker-compose.yml up --build

## Run the tests
    ./mvnw -pl services/api-gateway test

The test starts the gateway and a stub backend, so neither `customer-service`
nor Eureka needs to run. Eureka is disabled in the test
(`eureka.client.enabled=false`) and the stub is declared as the only instance of
`customer-service` through `spring.cloud.discovery.client.simple.instances`,
which resolves `lb://customer-service` in memory.

## Exercise
1. Add a second route for `/api/events/**` pointing to `http://localhost:8082`.
   What does the gateway answer while nothing listens on 8082?
2. Start a **second** instance of customer-service
   (`./mvnw -pl services/customer-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8083`).
   Both appear on the Eureka dashboard. Send several requests through the
   gateway: which instance handles each one? What did you have to change in the
   gateway configuration?
3. Add a `StripPrefix=1` filter and see how the path received by the backend changes.

## Common pitfalls
- Spring Cloud 2025.1 renamed the properties: routes live under
  `spring.cloud.gateway.server.webflux.routes`. Old tutorials using
  `spring.cloud.gateway.routes` silently do nothing.
- Never add `spring-boot-starter-web` (Tomcat/MVC) next to the WebFlux gateway:
  the two models conflict and the application fails to start.
- A 404 from the gateway means no route matched; a 5xx (502/503) means a route
  matched but the backend is unreachable. With `lb://`, a 503 usually means the
  service is not (yet) registered in Eureka.
- Registration and registry refresh are not instant: after starting a service,
  wait up to ~30 s before calling it through the gateway.
- `/actuator/gateway/routes` answers 404 unless the endpoint is enabled with
  `management.endpoint.gateway.access=read-only` **and** exposed with
  `management.endpoints.web.exposure.include=gateway`.
