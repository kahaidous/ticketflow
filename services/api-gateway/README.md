# api-gateway

> Single entry point of TicketFlow: clients talk to the gateway, never to the services directly.

## Concept illustrated
Spring Cloud Gateway (WebFlux flavour) as a reverse proxy. A **route** =
an id + a target `uri` + **predicates** (when does it match?) + **filters**
(what do we change on the way?).

In this step the routes are **static**: the address of every service is
written in the configuration. That is on purpose; see the exercise below
to feel why it does not scale.

## Overview
- **Port:** 8080
- **Depends on:** `customer-service` (8081)
- **Runtime:** WebFlux on Netty (not Tomcat)

| Route | Predicate | Target |
|---|---|---|
| `customer-service` | `Path=/api/customers/**` | `http://localhost:8081` |

Filter used: `AddResponseHeader=X-Served-By, api-gateway`.

The target can be overridden with the `customer-service.url` property or the
`CUSTOMER_SERVICE_URL` environment variable (this is what Docker Compose does).

## Run locally
Terminal 1:

    ./mvnw -pl services/customer-service spring-boot:run

Terminal 2:

    ./mvnw -pl services/api-gateway spring-boot:run

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

The test starts the gateway and a stub backend, so `customer-service` does not need to run.

## Exercise
1. Add a second route for `/api/events/**` pointing to `http://localhost:8082`.
   What does the gateway answer while nothing listens on 8082?
2. Start **two** instances of customer-service (the second one with
   `--server.port=8083`). Can the gateway spread requests over both? Why not?
   What would you need to change every time an instance moves or scales?
   (This question is answered in `step-03-discovery`.)
3. Add a `StripPrefix=1` filter and see how the path received by the backend changes.

## Common pitfalls
- Spring Cloud 2025.1 renamed the properties: routes live under
  `spring.cloud.gateway.server.webflux.routes`. Old tutorials using
  `spring.cloud.gateway.routes` silently do nothing.
- Never add `spring-boot-starter-web` (Tomcat/MVC) next to the WebFlux gateway:
  the two models conflict and the application fails to start.
- A 404 from the gateway means no route matched; a 5xx (502/503) means a route
  matched but the backend is unreachable.
