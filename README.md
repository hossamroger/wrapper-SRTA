# SRTA Gateway

A standalone Spring Boot gateway that mirrors the Oracle Service Bus (OSB)
`ShjRoadsTransportSocialDeptServicesPipeline` flow — the APIs that route to the
SRTA backends. It is a transparent facade over two native APIs:
**`https://eformstaging.srta.gov.ae/DeG/V1`** (complaints/lookup) and
**`https://ebooking.srta.gov.ae:9101/taxidispatch`** (ebooking/taxidispatch).

- **Java:** 8
- **Spring Boot:** 2.2.8.RELEASE
- **Packaging:** WAR (runs via `java -jar`; also deploys to WebLogic/Tomcat)

Built to match the sibling `wrapper-social` / `wrapper-economic` projects
(WebLogic class-isolation, reactive `WebClient` exclusion, actuator health,
environment profiles).

## Flow (per request)

```
Client ──► POST /<NativeOp>                   (SrtaProxyController — native paths, 1:1)
   │
   ├─ 1. VALIDATE USER  (UserValidationService)   [OSB: only on lookup]
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

The gateway is a transparent facade: the inbound paths are the **native** SRTA
operation names, forwarded 1:1 (no renaming).

| Inbound (POST) | Native backend (SRTA DeG/V1) |
|---|---|
| `/Lockup` | `/Lockup` |
| `/TaxiComplaintDetails` | `/TaxiComplaintDetails` |
| `/LostComplaintDetails` | `/LostComplaintDetails` |
| `/RoadComplaintDetails` | `/RoadComplaintDetails` |

### taxidispatch / ebooking (auth: static `accessToken` header)

| Inbound | Native backend (`…:9101/taxidispatch`) | Method |
|---|---|---|
| `/vehicleType` | `/vehicleType` | POST |
| `/vehiclelocation` | `/vehiclelocation` | POST |
| `/cancel` | `/cancel` | POST |
| `/getFare` | `/getFare` | POST |
| `/nearbyvehicles` | `/nearbyvehicles` | POST |
| `/jobstatus` | `/jobstatus` | POST |
| `/jobdetails` | `/jobdetails` | POST |
| `/book` | `/book` | POST |
| `/driverPhoto` | `/driverPhoto` | GET |
| `/searchAddrByLatLon` | `/searchAddrByLatLon` | GET |
| `/searchAddrByKeyword` | `/searchAddrByKeyword` | GET |

## Configuration (`application.properties` + profiles)

| Property | Meaning |
|---|---|
| `srta.backend-base-url` | SRTA base URL (`https://eformstaging.srta.gov.ae/DeG/V1`) |
| `srta.backend.bearer-token` | Static Bearer JWT for DeG/V1. **Supply via `SRTA_BACKEND_BEARER` — do not commit.** |
| `srta.ebooking.base-url` | taxidispatch base URL (`https://ebooking.srta.gov.ae:9101/taxidispatch`) |
| `srta.ebooking.access-token` | Static `accessToken` for taxidispatch. **Supply via `SRTA_EBOOKING_TOKEN` — do not commit.** |
| `srta.validation.enabled` / `.url` / `.dscode` | User validation (dscode `RT-001`) |
| `srta.validation.paths` | Which inbound paths to validate. Empty = all; set `/Lockup` for exact OSB parity |
Profiles: `local`, `stg`, `prod`. Default is `stg`; override with
`SPRING_PROFILES_ACTIVE` or `--spring.profiles.active=<profile>`.

> **Security:** the OSB export hard-coded the Bearer JWT in the pipeline. Here it
> is externalised — supply it via env var and rotate it. Validation in OSB was
> applied only to the lookup op; this gateway validates all native paths by default
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
