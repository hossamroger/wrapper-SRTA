# SRTA Gateway

A standalone Spring Boot gateway that mirrors the Oracle Service Bus (OSB)
`ShjRoadsTransportSocialDeptServicesPipeline` flow — the APIs that route to the
SRTA backend **`https://eformstaging.srta.gov.ae/DeG/V1`**. A single dynamic
controller replaces the 4 OSB route-nodes for the `/deg/*` endpoints.

- **Java:** 8
- **Spring Boot:** 2.2.8.RELEASE
- **Packaging:** WAR (runs via `java -jar`; also deploys to WebLogic/Tomcat)

Built to match the sibling `wrapper-social` / `wrapper-economic` projects
(WebLogic class-isolation, reactive `WebClient` exclusion, actuator health,
environment profiles).

## Flow (per request)

```
Client ──► POST /deg/**                       (SrtaProxyController — one handler)
   │
   ├─ 1. VALIDATE USER  (UserValidationService)   [OSB: only on /deg/lookup]
   │      POST {srta.validation.url}  headers: dstoken, dscode(RT-001)
   │      if response statusCode == 401  ⇒  return 401 UNAUTHORIZED
   │
   ├─ 2. INJECT STATIC BEARER
   │      header: Authorization: Bearer <srta.backend.bearer-token>
   │
   └─ 3. CALL SRTA BACKEND
          POST {srta.backend-base-url}{renamed-path}   (body + query forwarded)
          return backend status + body; add Access-Control-Allow-Origin: *
```

There is **no token service and no DB lookup** — the OSB business service
authenticated to SRTA with a hard-coded Bearer JWT, injected here from config.

## Endpoints and path mapping

The SRTA backend uses PascalCase operation names that differ from the inbound
REST paths, so every operation is mapped in `srta.path-overrides`:

| Inbound (POST) | Backend (SRTA DeG/V1) |
|---|---|
| `/deg/lookup` | `/Lockup` |
| `/deg/taxi-complaint-details` | `/TaxiComplaintDetails` |
| `/deg/lost-complaint-details` | `/LostComplaintDetails` |
| `/deg/road-complaint-details` | `/RoadComplaintDetails` |

## Configuration (`application.properties` + profiles)

| Property | Meaning |
|---|---|
| `srta.backend-base-url` | SRTA base URL (`https://eformstaging.srta.gov.ae/DeG/V1`) |
| `srta.backend.bearer-token` | Static Bearer JWT. **Supply via `SRTA_BACKEND_BEARER` — do not commit.** |
| `srta.validation.enabled` / `.url` / `.dscode` | User validation (dscode `RT-001`) |
| `srta.validation.paths` | Which inbound paths to validate. Empty = all; set `/deg/lookup` for exact OSB parity |
| `srta.path-overrides[...]` | Inbound → backend path map |

Profiles: `local`, `stg`, `prod`. Default is `stg`; override with
`SPRING_PROFILES_ACTIVE` or `--spring.profiles.active=<profile>`.

> **Security:** the OSB export hard-coded the Bearer JWT in the pipeline. Here it
> is externalised — supply it via env var and rotate it. Validation in OSB was
> applied only to `/deg/lookup`; this gateway validates all `/deg/*` by default
> (tighten or match OSB via `srta.validation.paths`).

## Build / run / test

```bash
mvn clean package                 # -> target/srta-gateway.war
java -jar target/srta-gateway.war \
  --spring.profiles.active=local \
  --srta.validation.url=<validateUser-url> \
  --srta.backend.bearer-token=$SRTA_BACKEND_BEARER
# or deploy target/srta-gateway.war to WebLogic/Tomcat
mvn test                          # 8 tests (MockRestServiceServer + standalone MockMvc)
```

Health: `GET {context}/actuator/health` → `{"status":"UP"}`.

## WebLogic notes

`webapp/WEB-INF/weblogic.xml` sets `context-root=/online/shjroadsransportdeptservices`
and uses `prefer-application-packages` to isolate the app's Spring classes from
the container's. The main class also excludes the reactive `WebClient`
auto-configurations, preventing the `NoClassDefFoundError:
DefaultExchangeStrategiesBuilder` seen when WebLogic leaks its own reactive jars.
