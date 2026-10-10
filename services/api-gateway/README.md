# api-gateway

> Single entry point of TicketFlow: clients talk to the gateway, never to the services directly.

## Concept illustrated
Spring Cloud Gateway (WebFlux flavour) as a reverse proxy. A **route** =
an id + a target `uri` + **predicates** (when does it match?) + **filters**
(what do we change on the way?).

Since `step-03-discovery` the routing is **dynamic**: the gateway does not know
any host or port. With the **discovery locator**, it asks Eureka which services are
registered and creates one route per service, targeting `lb://<service-name>` where
`lb` means *load-balanced*. For each request it picks one of the available instances
(round-robin). In `step-02-gateway-static` the address of the service was written by
hand in the configuration (`http://localhost:8081`).

The URL contains the service name: `/<service-name>/<path>` is forwarded to
`<service-name>` as `/<path>`. Adding a new microservice requires **no change** in
the gateway.

## Overview
- **Port:** 8080
- **Depends on:** `discovery-server` (8761) to find `customer-service`
- **Runtime:** WebFlux on Netty (not Tomcat)

| Route (created by the locator) | Predicate | Target |
|---|---|---|
| `customer-service` | `Path=/customer-service/**` | `lb://customer-service` |

Filters: the locator's default `RewritePath` removes the `/customer-service` prefix,
and `default-filters` adds `AddResponseHeader=X-Served-By, api-gateway` to every route.

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

    curl -i -X POST localhost:8080/customer-service/api/customers \
      -H 'Content-Type: application/json' \
      -d '{"firstName":"Sara","lastName":"Idrissi","email":"sara@example.com"}'

    curl -i localhost:8080/customer-service/api/customers

Look for the `X-Served-By: api-gateway` response header. List the routes the
gateway knows about:

    curl localhost:8080/actuator/gateway/routes

## Load balancing demo
Because the routes target `lb://customer-service`, the gateway spreads requests over
**every** registered instance of the service, with no configuration change.

Start the registry, two instances of customer-service (on different ports) and the gateway:

    ./mvnw -pl services/discovery-server spring-boot:run
    ./mvnw -pl services/customer-service spring-boot:run
    ./mvnw -pl services/customer-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8083
    ./mvnw -pl services/api-gateway spring-boot:run

On http://localhost:8761, `CUSTOMER-SERVICE` now lists **2 instances**. Wait about
30 s so the gateway refreshes its copy of the registry, then call the `whoami`
endpoint through the gateway several times:

    for i in 1 2 3 4 5 6; do curl -s localhost:8080/customer-service/api/customers/whoami; echo; done

Expected output: the port alternates between the two instances (round-robin).

    {"service":"customer-service","port":"8081"}
    {"service":"customer-service","port":"8083"}
    {"service":"customer-service","port":"8081"}
    ...

Now stop the instance on 8083 (Ctrl+C) and repeat the loop. For a short while some
calls fail, then everything is served by 8081 once Eureka removes the dead instance.
That delay is the price of a registry that is only eventually consistent.

## Run with Docker
    docker compose -f infra/docker-compose.yml up --build

## Run the tests
    ./mvnw -pl services/api-gateway test

The test starts the gateway and a stub backend, so neither `customer-service`
nor Eureka needs to run. Eureka is disabled in the test
(`eureka.client.enabled=false`) and the stub is declared as the only instance of
`customer-service` through `spring.cloud.discovery.client.simple.instances`: the
locator builds its route from that in-memory registry.

## Exercise
1. Call `localhost:8080/api/customers` (without the service name). Why does the
   gateway answer 404 now, while it worked in `step-02-gateway-static`?
   Then write an explicit route that restores `/api/customers/**` (see the commented
   example in `application.yml`). When is an explicit route better than the locator?
2. Start a **second** instance of customer-service
   (`./mvnw -pl services/customer-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8083`).
   Both appear on the Eureka dashboard. Send several requests through the
   gateway: which instance handles each one? What did you have to change in the
   gateway configuration?
3. Turn the locator off (`enabled: false`) and restart: what does
   `/actuator/gateway/routes` show? The locator exposes **every** service registered in
   Eureka, including internal ones. Why can that be a security problem?

## Common pitfalls
- Spring Cloud 2025.1 renamed the properties: they live under
  `spring.cloud.gateway.server.webflux` (routes, default-filters, discovery.locator).
  Old tutorials using `spring.cloud.gateway.routes` silently do nothing.
- Setting custom `discovery.locator.filters` **replaces** the default `RewritePath`:
  without it the service name is not removed and the backend answers 404.
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
