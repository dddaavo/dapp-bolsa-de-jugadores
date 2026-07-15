# Bolsa de Jugadores — Contexto del Proyecto para Asistentes IA

Este archivo es la fuente de verdad del proyecto para cualquier asistente de IA (Claude, Gemini, Copilot, Cursor, etc.). Documenta el stack, la arquitectura, las convenciones de desarrollo, el estado actual y las reglas que el asistente debe respetar.

Cuando este archivo y un archivo específico de agente (`CLAUDE.md`, `GEMINI.md`) digan cosas distintas, el archivo del agente prevalece sobre su propio comportamiento. Este archivo gobierna todo lo que es transversal.

---

## 1. Proyecto

Backend REST en Java/Spring Boot que modela un mercado de tokens de jugadores de las 5 grandes ligas europeas (Premier League, Bundesliga, La Liga, Serie A, Ligue 1), con cotización periódica calculada por estrategias configurables, operaciones de compra/venta contra un superusuario emisor y portfolio por usuario.

**Contexto académico:** Trabajo Práctico de la asignatura Desarrollo de Aplicaciones (UNQ). Fines didácticos: prioridad al diseño, arquitectura y buenas prácticas sobre la complejidad de features. Solo backend; no hay frontend.

---

## 2. Stack y versiones

| Capa | Elección | Razón |
|---|---|---|
| Lenguaje | **Java 21 (LTS)** | Records, pattern matching, virtual threads disponibles |
| Framework | **Spring Boot 3.3.x** | Madurez, Jakarta EE 10, soporte nativo OpenAPI 3 |
| Build | **Maven** | Default de la cátedra, integración directa con SonarCloud |
| Persistencia dev/test | **H2** in-memory + **Spring Data JPA (Hibernate 6)** | Requerido por la cátedra en Entrega 2; sin infraestructura extra, `ddl-auto=create-drop` |
| Persistencia prod | **PostgreSQL 16** (Render managed) | Solo en perfil `prod`; H2 no persiste entre reinicios |
| Seguridad | **Spring Security 6 + JWT (jjwt 0.12.x)** | Stateless, estándar de la cátedra |
| API Docs | **springdoc-openapi 2.5.x (OpenAPI 3)** | Swagger UI y `/v3/api-docs` automáticos |
| HTTP client | **Spring `RestClient`** (Spring 6.1+) | Sync, fluent, reemplazo moderno de RestTemplate |
| Resiliencia | **Resilience4j** (circuit breaker + retry + bulkhead) | Tolerancia a fallas del proveedor externo |
| Scraping | **Playwright Java** (Chromium headless) | WhoScored usa Cloudflare + JS rendering; Jsoup no funciona (declarado en pom.xml pero sin uso real) |
| Caché | **Redis** (Spring Cache abstraction) | **Distribuida** — coherente bajo escalado horizontal (N instancias). Caffeine (in-process) daría una copia por nodo e inconsistencia; descartado. Implementada para ranking (issue #54 parcial) |
| Scheduler | **Spring `@Scheduled`** (+ **ShedLock** si escalamos a N>1 instancias) | Cotización semanal, sync externo |
| Mapeo | **MapStruct** | DTO ↔ Entity en compile-time, sin reflection |
| Validación | **Jakarta Bean Validation** (`spring-boot-starter-validation`) | `@Valid` en controllers |
| Observabilidad | **Actuator + Micrometer + Prometheus + Loki + Grafana + loki-logback-appender + AOP audit** | Health, métricas Prometheus, logs en Loki, dashboard Grafana, auditoría de WS (Entrega 3) |
| Testing | **JUnit 5, Mockito, AssertJ, Spring Test, Rest Assured, ArchUnit** | Unit (Surefire) + e2e/integration (Failsafe con H2); test de arquitectura (Entrega 3) |
| CI | **GitHub Actions** | Build, test, Jacoco, SonarCloud, deploy |
| Deploy | **Render** (Docker runtime + Postgres managed) | Free tier con GitHub integration |
| Calidad | **SonarCloud** + **JaCoCo** + **Spotless** (Google Java Format) | < 10 issues en entrega 1 |

---

## 3. Arquitectura

### 3.1 Enfoque

Arquitectura **por features (vertical slicing)** con separación en capas (Controller → Service → Repository → Adapter). Dentro de cada feature aplica un mini-hexagonal: el dominio no conoce infraestructura; los `integration.*` exponen **Ports** (interfaces) e implementan **Adapters**.

```
com.unq.dapp.bolsa
├── BolsaApplication.java
├── config/              # SecurityConfig, OpenApiConfig, CacheConfig, AsyncConfig, RestClientConfig
├── auth/                # Registro, login, JWT, User, Role
│   ├── api/             # AuthController, DTOs
│   ├── application/     # AuthService, JwtService
│   ├── domain/          # User, Role, RefreshToken
│   ├── infrastructure/  # UserRepository, JwtAuthFilter, CustomUserDetailsService
├── catalog/             # Jugadores (lectura)
│   ├── api/ application/ domain/ infrastructure/
├── pricing/             # Cotización e historial
│   ├── api/
│   ├── application/     # QuoteService, QuoteRecalculationOrchestrator
│   ├── domain/
│   │   ├── Quote, QuoteHistory, PlayerMetricsSnapshot
│   │   └── strategy/    # PricingStrategy (interface), MatchMetricsStrategy, PositionWeightedStrategy, StrategyRegistry
│   └── infrastructure/
├── trading/             # Órdenes de compra/venta
│   ├── api/             # OrderController, UserController (transactions)
│   ├── application/     # OrderService (buy/sell + idempotencia interna, sin IdempotencyService aparte)
│   ├── domain/          # Order, OrderType, TokenHolding
│   └── infrastructure/  # OrderRepository, TokenHoldingRepository
├── portfolio/           # (PLANIFICADO E3) Vista consolidada del usuario — aún no implementado
├── integration/
│   ├── port/            # PlayerStatsPort (FootballDataPort planificado E3)
│   ├── footballdata/    # (PLANIFICADO E3) FootballDataAdapter — aún no implementado
│   └── whoscored/       # WhoScoredAdapter (@Primary) + WhoScoredPlaywrightScraper + WhoScoredScraper (fallback)
├── scheduling/          # QuoteRecalculationJob (ExternalDataSyncJob planificado E3)
└── shared/
    ├── error/           # ApiExceptionHandler (RFC 7807), DomainException, ErrorCode
    ├── audit/           # AuditListener, auditable base entity
    └── util/
```

**Regla:** los paquetes `domain` no importan Spring (excepto anotaciones JPA). Los `application.*Service` orquestan transacciones (`@Transactional`). Los `api.*Controller` son delgados: validan, delegan, mapean.

> El diagrama describe el **diseño objetivo** (estado final del proyecto). A 2026-06-28, los nodos marcados `(PLANIFICADO E3)` todavía no existen en el código: `portfolio/`, `integration/footballdata/`, `FootballDataPort`, `ExternalDataSyncJob`. Ver §17 para el estado real por módulo.

### 3.2 Patrones clave

- **Strategy** para cotización: `PricingStrategy.calculate(PlayerMetricsSnapshot) → QuoteValue`. `StrategyRegistry` resuelve por nombre. Cada `Quote` persiste `strategyName` y `strategyVersion` para auditoría.
- **Port/Adapter** para cada integración externa. El service nunca importa `footballdata` directamente.
- **Fallback chain** en scraping: `WhoScoredJsoupAdapter → WhoScoredFixturesFallback`. Orquestado con Resilience4j.
- **Idempotency-Key**: órdenes POST requieren header `Idempotency-Key: <uuid>`. La idempotencia se resuelve **dentro de `OrderService`** (lookup por `idempotencyKey` único en `Order`); no hay un `IdempotencyService` separado.
- **Optimistic locking** con `@Version` en `TokenHolding`/`PlayerTokenInventory`.

### 3.3 Modelo de dominio

- `User { id, email (unique), passwordHash, role (USER|ADMIN), createdAt }`
- `Player { id, externalId, name, position, team, league, nationality }`
- `PlayerTokenInventory { playerId, totalEmitted=100, heldBySystem, initialTokenValue=1, @Version }` — valor inicial 1 crédito por token (definido en enunciado §3.3)
- `Quote { id, playerId, value, calculatedAt, strategyName, strategyVersion }`
- `PlayerMetricsSnapshot { id, playerId, periodStart, periodEnd, goals, assists, ... }`
- `Order { id, userId, playerId, type (BUY|SELL), quantity, unitPrice, totalAmount, status, idempotencyKey, createdAt }`
- `TokenHolding { userId, playerId, quantity, avgBuyPrice, @Version }`
- `StrategyConfig { name, version, active, weightsJson }` — JSON en columna TEXT (no JSONB: H2 no lo soporta)

Todas las entidades extienden `AuditableEntity { createdAt, updatedAt }`.

### 3.4 Decisiones de concurrencia

- `@Transactional` en services de escritura, `isolation = READ_COMMITTED`, `propagation = REQUIRED`
- Compra: `SELECT FOR UPDATE` sobre `PlayerTokenInventory`; venta: `SELECT FOR UPDATE` sobre `TokenHolding`
- Reintentos ante `OptimisticLockException`: máximo 3 intentos con jitter

---

## 4. API (endpoints)

El enunciado define los endpoints sin prefijo (`/players`, `/orders`, `/users`). Usamos `/api/v1` como prefijo por convención de buenas prácticas (versionado), no por requerimiento. Swagger UI en `/swagger-ui.html`, contrato en `/v3/api-docs`.

### Auth
> Los endpoints de auth no están en la tabla del enunciado — son un requerimiento de la materia (API key para acceder al resto de endpoints).

| Método | Path | Rol | Descripción |
|---|---|---|---|
| POST | `/auth/register` | público | Crea usuario USER, devuelve `{ accessToken, expiresIn }` |
| POST | `/auth/login` | público | Devuelve JWT |

### Catálogo
| Método | Path | Rol | Descripción |
|---|---|---|---|
| GET | `/players?league=&team=&position=&page=&size=` | USER | Listado paginado |
| GET | `/players/{id}` | USER | Detalle |

### Cotización
| Método | Path | Rol | Descripción |
|---|---|---|---|
| GET | `/players/{id}/quotes?from=&to=` | USER | Historial |
| GET | `/players/{id}/quotes/current` | USER | Cotización vigente |
| GET | `/players/ranking?strategy=` | USER | Top N |
| POST | `/quotes/recalculate` | ADMIN | Job manual |

### Mercado
| Método | Path | Headers |
|---|---|---|
| POST | `/orders/buy` | `Idempotency-Key` |
| POST | `/orders/sell` | `Idempotency-Key` |

### Usuario
| Método | Path | Rol |
|---|---|---|
| GET | `/users/{id}/portfolio` | dueño o ADMIN |
| GET | `/users/{id}/transactions` | dueño o ADMIN |

### Reglas de autorización

- Públicos: `/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/h2-console/**`
- `/actuator/**` (resto): ADMIN
- `/users/{id}/**`: `@PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")`
- 401 → no autenticado; 403 → sin permiso; 400 → validación; 409 → idempotencia duplicada con body distinto; 422 → error de dominio

---

## 5. Config local

### Variables de entorno

```
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
JWT_SECRET=<256-bit base64>
JWT_ACCESS_TTL_MINUTES=15
FOOTBALL_DATA_API_KEY=<key de football-data.org>
FOOTBALL_DATA_BASE_URL=https://api.football-data.org/v4
QUOTE_RECALC_CRON=0 0 3 * * MON
EXTERNAL_SYNC_CRON=0 0 2 * * *
```

### Perfiles

| Perfil | DB | ddl-auto | H2 Console |
|---|---|---|---|
| `local` | H2 in-memory `bolsadb` | create-drop | habilitada en `/h2-console` |
| `test` | H2 in-memory `testdb` | create-drop | deshabilitada |
| `prod` | PostgreSQL en Render | validate | deshabilitada |

### Bootstrap local

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:bolsadb`, User: `sa`, Password: vacía)
- Health: http://localhost:8080/actuator/health

---

## 6. Tooling del equipo

### Requisitos para contribuir

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 21 | Temurin recomendado |
| Maven | — | Usar el wrapper `./mvnw`, no instalar Maven global |
| gh CLI | latest | Requerido para gestión de issues/PRs desde línea de comando |
| Git | — | Ver configuración abajo |

### gh CLI — setup inicial

```bash
# Instalación (Ubuntu/Debian)
sudo apt install gh

# Autenticación
gh auth login
# → GitHub.com → SSH → pegar token
```

**Scopes requeridos para el token:** `repo`, `workflow`

Verificar: `gh auth status` debe mostrar el usuario autenticado.

### Configuración de Git

El email de git local debe coincidir con alguno de los emails registrados en la cuenta de GitHub para que los commits linkeen al perfil:

```bash
# Verificar config actual
git config user.name
git config user.email

# Ajustar si es necesario (solo para este repo)
git config user.email "tu-email@registrado-en-github.com"

# O globalmente
git config --global user.email "tu-email@registrado-en-github.com"
```

Para agregar un email a GitHub: https://github.com/settings/emails

---

## 7. CI/CD

### GitHub Actions

**`.github/workflows/ci.yml`** (push/PR a cualquier branch):
1. `actions/checkout@v4`
2. `actions/setup-java@v4` (temurin 21)
3. Cache `~/.m2`
4. `./mvnw -B verify -Dspring.profiles.active=test` — compila + Surefire (`*Test.java`) + Failsafe (`*IT.java`) con H2
5. SonarCloud: solo en push a `main`

Secrets necesarios: `SONAR_TOKEN`, `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION`.

### Dockerfile (multi-stage)

```dockerfile
FROM eclipse-temurin:21-jdk AS build
# ... build con Maven
FROM eclipse-temurin:21-jre
# ... copia jar, EXPOSE 8080
```

Health check en Render: `GET /actuator/health`.

---

## 8. Flujo de trabajo

### Gitflow

```
main          ← entrega final por versión (tag v1.0.0, v2.0.0, v3.0.0)
  └── develop ← integración continua; base de todas las features
        └── feature/<área>-<tema>  ← una por issue
```

- `feature/*`, `fix/*`, `chore/*` salen de `develop` y mergean a `develop`
- `develop` → `main` al cerrar cada entrega (tag + release notes)
- `main` protegida: requiere PR + 1 review + CI verde

### Convenciones de commits

Formato: `<tipo>: <descripción corta>`

Prefijos: `feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`

- Descripción en tercera persona del presente (agrega, corrige, actualiza)
- Título corto (no truncado en GitHub)
- Detalles en el cuerpo separado por línea en blanco
- Sin lista de archivos modificados en el cuerpo (el diff los muestra)

### PRs

- Título: `<tipo>: <descripción corta>`
- Descripción: summary, test plan, evidencia visual si hay cambio de comportamiento
- Usar `Closes #N` para vincular al issue — GitHub lo cierra automáticamente al mergear a `main`
- Al mergear a `develop`, cerrar el issue manualmente (GitHub solo auto-cierra al mergear a la rama por defecto)

### Issues

- Un issue por bloque funcional (ver checklist §11)
- Board de seguimiento en GitHub Projects: un proyecto **Board** por entrega
- Las entregas E2 y E3 tienen **milestone** propio en GitHub (`Entrega 2`, `Entrega 3`) y label (`entrega-2`, `entrega-3`)

### Release Notes y TAG (requerimiento de la cátedra)

Convención obligatoria (fuente: documento de la cátedra en Drive):

- **`RELEASE-NOTES.txt` en el root del repo**, **acumulativo** (un bloque por TAG, no se sobreescriben los anteriores). **Arranca en E2** — E1 no cuenta.
- Cada bloque declara el **estado de cada punto de entrega** y **detalla lo no implementado**, con el formato exacto:
  ```
  ---------------------------------------------------------------------
  TAG XXXXXX
  ---------------------------------------------------------------------
  NEW FEATURES (lo que están entregando y está funcionando):
  * ...
  NOTES (ej: funcionalidad que falta, alguna consideración especial):
  * ...
  KNOWN ISSUES (ej: errores conocidos en funcionalidad terminada):
  * ...
  ```
- **Naming del TAG (doble esquema):** el **git tag** usa **semver** (`v2.0.0`, `v3.0.0`); el **header del bloque** en `RELEASE-NOTES.txt` usa el formato cátedra (`ENTREGA 2 - 1.0`). Re-tag de la misma entrega = **+1 al último dígito** (`v2.0.1` / `ENTREGA 2 - 1.1`).
- El contador del header **arranca en 1.0 por cada entrega**.

---

## 9. Testing

### Pirámide

- **Unit** (Surefire — `*Test.java`): sin Spring context. Servicios con mocks, estrategias, mappers.
- **Slice**: `@WebMvcTest`, `@DataJpaTest`, `@JsonTest`
- **Integration/e2e** (Failsafe — `*IT.java`): `@SpringBootTest` + H2 + Rest Assured

### Separación Surefire/Failsafe

```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <includes><include>**/*Test.java</include></includes>
  </configuration>
</plugin>
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <includes><include>**/*IT.java</include></includes>
  </configuration>
  <executions>
    <execution>
      <goals><goal>integration-test</goal><goal>verify</goal></goals>
    </execution>
  </executions>
</plugin>
```

Ambos plugins ya están configurados en `pom.xml` desde el scaffold (issue #1).

### Profiles de testing (issue #48 — E2)

**Decisión adoptada (2026-06-28):** separación literal de Spring profiles por fase de test.

| Fase | Plugin Maven | Spring profile | Config file |
|---|---|---|---|
| Unit / Slice (`*Test.java`) | Surefire | `test` | `application-test.yml` |
| e2e / Integration (`*IT.java`) | Failsafe | `e2e` | `application-e2e.yml` |

Cada plugin declara su perfil vía `systemPropertyVariables` en `pom.xml`. Los `*IT.java` usan `@ActiveProfiles("e2e")`. El CI corre `./mvnw -B verify` sin pasar `-Dspring.profiles.active` globalmente.

**Rationale:** la consigna pide "separar profiles para unitarios y e2e". Con Surefire/Failsafe ya se separa la ejecución, pero usar el mismo profile `test` para ambas fases no cumple la separación literal. El profile `e2e` usa su propia DB (`e2edb`) y permite configurar ITs de forma independiente en el futuro.

### Pautas de la materia

- JUnit 5 + Mockito + AssertJ
- Nombres descriptivos: `deberiaLanzarExcepcionCuandoEmailYaExiste()`
- Sin comentarios dentro de métodos de test
- Tests idempotentes: cada test crea su propia data

### ArchUnit (Entrega 3)

```java
@ArchTest
static ArchRule controllers_no_acceden_a_repositories =
    noClasses().that().resideInAPackage("..api..")
        .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
```

---

## 10. Estrategias de cotización

```java
public interface PricingStrategy {
    String name();
    String version();
    Money calculate(PlayerMetricsSnapshot snapshot, PricingContext ctx);
}
```

**Implementaciones mínimas (Entrega 2):**
1. `MatchMetricsStrategy v1.0`: normaliza métricas a [0,1], score = Σ peso_i · métrica_norm_i
2. `PositionWeightedStrategy v1.0`: pesos distintos por posición (FW→goals, MF→passes, DF→tackles, GK→cleanSheets)

Configuración en `StrategyConfig` (JSON en columna TEXT — no JSONB, incompatible con H2).

---

## 11. Integración externa

| Fuente | Uso | Librería | Tolerancia |
|---|---|---|---|
| Football-Data.org | resultados, fixtures (E3) | `RestClient` | Resilience4j retry 3x + circuit breaker |
| WhoScored | catálogo de jugadores (E1) + stats de rendimiento (E2+) | Playwright Java | Fallback a `WhoScoredFixturesFallback` (datos estáticos) |

**Flujo de inicialización del catálogo (implementado en E1):**
`DataInitializer` → `PlayerStatsPort` → `WhoScoredAdapter` (@Primary) → `WhoScoredPlaywrightScraper` (si `whoscored.scraping.enabled=true`) o `WhoScoredFixturesFallback`. Los datos se persisten en `Player` al startup si la tabla está vacía (idempotente).

**Scraper WhoScored:** extrae hasta 10 jugadores por liga (primera página de la tabla de estadísticas). Para más jugadores se necesita implementar paginación via botón "Next" (pendiente E2). La nationality no se extrae desde esta vista — queda vacía.

---

## 12. Observabilidad

### AOP audit (implementado en feature/audit-ws-e3)

`WebServiceAuditAspect` en `shared/audit/` intercepta todos los `@RestController`:

```java
@Around("within(@org.springframework.web.bind.annotation.RestController *)")
public Object audit(ProceedingJoinPoint pjp) throws Throwable { ... }
```

Loguea: `user=`, `operation=`, `params=`, `durationMs=` (y `error=` si lanza excepción). Passwords y tokens JWT se enmascaran (`[PROTECTED:LoginRequest]`, `[PROTECTED:token]`).

Logger nombrado `"audit"` (SLF4J string, no paquete). Configurado en `logback-spring.xml`:
- Perfil `local`: consola + `logs/audit.log` (rotación diaria, 7 días)
- Resto de perfiles: solo consola

**Gotcha:** `logging.level.com.unq.dapp.bolsa: DEBUG` no cubre el logger `"audit"` porque es un nombre string, no un paquete. Necesita `logging.level.audit: INFO` explícito en el yml del perfil.

### Prometheus + Grafana (implementado en feature/audit-ws-e3)

`/actuator/prometheus` expuesto como ruta pública (sin auth) para que Prometheus pueda scrapearlo.

Métricas custom implementadas:
- `orders_total{type=buy|sell}` — contador de órdenes (Micrometer Counter)
- `quotes_recalculation_duration` — duración de recalculación (Micrometer Timer)

Histogramas percentil habilitados en `application.yml`:
```yaml
management.metrics.distribution.percentiles-histogram:
  http.server.requests: true
  quotes.recalculation.duration: true
```

Stack de monitoreo en `docker-compose.monitoring.yml` (Prometheus + Grafana + Loki). Dashboard provisionado en `monitoring/grafana/provisioning/dashboards/bolsa-dashboard.json` con 11 paneles. Prometheus scrapeado desde `host.docker.internal:8080` (en Linux requiere `extra_hosts: host-gateway` — ya configurado).

### Loki — logs centralizados

Loki corre en el stack de monitoring (`grafana/loki:2.9.8`, puerto 3100). La app pushea logs directamente con `loki-logback-appender` (`com.github.loki4j:loki-logback-appender:1.5.2`), configurado en `logback-spring.xml` solo en perfil `local`.

Labels enviados: `app=bolsa-de-jugadores`, `level=%level`, `logger=%logger{20}`.

Dashboard incluye 3 panels de Loki: logs generales, logs de auditoría (`logger=~"audit.*"`), rate de errores por minuto.

Explorar en Grafana → **Explore** → datasource **Loki**:
```logql
{app="bolsa-de-jugadores"}
{app="bolsa-de-jugadores", level="ERROR"}
{app="bolsa-de-jugadores", logger=~"audit.*"}
{app="bolsa-de-jugadores"} |= "OrderService"
```

Config de Loki en `monitoring/loki/local-config.yaml`. Datasources provisionados con UIDs explícitos (`uid: prometheus`, `uid: loki`) para que los panels del dashboard los referencien sin ambigüedad.

---

## 13. Checklist por entrega

### Entrega 1
- [x] Repo GitHub + CI verde en `main` — CI configurado y verde en `develop` ✅
- [x] SonarCloud registrado, issues < 10 — issue #6 ✅ (projectKey=`dddaavo_dapp-bolsa-de-jugadores`, org=`dappgrupom`; Quality Gate personalizado)
- [x] JWT: `POST /auth/register` + `POST /auth/login` — issue #2 ✅
- [x] Swagger v3 en `/swagger-ui.html` con `SecurityScheme` Bearer JWT — issue #3 ✅
- [x] Scaffold Maven + Spring Boot 3.3 + Java 21 — issue #1 ✅
- [x] Entidades base: `User`, `Player`, `AuditableEntity` ✅
- [x] H2 configurado en perfil `local` ✅
- [x] Tests unitarios (al menos una clase de test por service) — issue #5 ✅ (AuthServiceTest, JwtServiceTest, PlayerServiceTest, WhoScoredAdapterTest)
- [x] `GET /api/v1/players` y `GET /api/v1/players/{id}` — issue #4 ✅ (endpoints funcionales con anotaciones OpenAPI completas)
- [x] `DataInitializer`: superusuario ADMIN + 50 jugadores reales via scraping WhoScored + 4 usuarios de prueba (alice, bob, charlie, diana) ✅
- [x] `CODEOWNERS` — ✅ (branch protection en `main` pendiente de configuración manual en GitHub Settings)
- [x] README con badge de CI y sección "How to run" ✅
- [x] Tag `v1.0.0` + Release Notes — ✅ completado el 2026-06-01

### Entrega 2
- [x] CI sin regresiones, H2 como DB principal ✅
- [x] `DataInitializer`: 4 usuarios de prueba + cotizaciones iniciales — issues #22, #39 ✅ (50 jugadores + métricas + historial de cotizaciones + escenario de trading)
- [x] Swagger v3 completo con `@Operation`, `@ApiResponse`, `@Schema` — chore #38 ✅
- [x] Surefire + Failsafe separados; JaCoCo en CI — chore #37 ✅ (separación desde el scaffold; reporte JaCoCo publicado como artifact)
- [x] Sistema de cotización: al menos una `PricingStrategy` — issue #19 ✅ (`MatchMetricsStrategy v1.0` + `PositionWeightedStrategy v1.0`, domain + application + controllers)
- [x] `GET /players/{id}/quotes/current`, historial, ranking — issue #40 ✅
- [x] `POST /quotes/recalculate` (ADMIN) — issue #41 ✅
- [x] Mercado: compra/venta de tokens (la cátedra ubica buy/sell e historial en E2) — issues #33, #34 ✅
- [x] **Cotización a una fecha dada** (point-in-time, `GET /players/{id}/quotes/at?date=`) — issue #47 ✅ (PR #63)
- [x] Separar profiles de testing unit/e2e — issue #48 ✅ (PR #64)
- [x] Unit tests del módulo trading — issue #49 ✅ (PR #65)
- [x] Portfolio del usuario (`GET /users/{id}/portfolio`, enunciado §3.4 + escenario §8.3) — issue #58 ✅ (PR #66, #67)
- [x] Estrategias configurables: `StrategyConfig` persistida + pesos — issue #62 ✅ (PR #67)
- [x] `RELEASE-NOTES.txt` + Tag `v2.0.0` (ver convención §8) — issue #50 ✅ (2026-06-29)

### Entrega 3

> La consigna oficial de E3 (Core + Funcionalidad) NO incluye `portfolio` ni `mercado buy/sell` (eso es E2 según cátedra) ni la 2da estrategia (ya hecha en #31). E3 = observabilidad + ArchUnit + optimización de ranking + métricas avanzadas, más los requisitos del enunciado de integración externa.

**Core (consigna):**
- [x] Test de arquitectura con ArchUnit — issue #52 ✅ (9 reglas de capas + naming, corre en el build)
- [x] Auditoría de WS (AOP + logback): timestamp/user/método/params/tiempo — issue #51 ✅ (enmascara passwords y JWT)
- [x] Prometheus + Actuator (endpoints de monitoreo y métricas) — issue #53 ✅ (+ stack Grafana/Loki #82)
- [ ] TAG + `RELEASE-NOTES.txt` (ver convención §8) — issue #57 (RELEASE-NOTES.txt agregado; tag `v3.0.0` pendiente de merge a `main`)

**Funcionalidad (consigna):**
- [x] Optimizar ranking para alta frecuencia → **caché distribuida Redis** — issue #54 ✅ (cache-aside + pre-warming; fix de serialización + test Testcontainers #83)
- [x] Endpoint de métricas avanzadas — issue #55 ✅ (`GET /api/v1/metrics/market`)

**Requisitos del enunciado + arquitectura (E3):**
- [x] Integración con API externa (§7) — **cubierto por WhoScored** (scraping con tolerancia a fallas). Football-Data #59 **cerrado** (sin consumidor en el dominio)
- [x] Job de sincronización de datos externos (§4.5) — issue #61 ✅ (**sincroniza WhoScored** con métricas reales)
- [x] Escalado horizontal N>1 (ShedLock, readiness, graceful shutdown) — issue #56 ✅ (**opcional/stretch**, diseño preparado)
- [x] Deploy: perfil prod + Postgres + docker-compose — issue #60 ✅ (**opcional**; docker-compose local, sin cloud)
- [ ] Tag `v3.0.0` (cierre) — issue #57

### Distribución por entrega

| Feature | E1 | E2 | E3 |
|---|---|---|---|
| Repo + CI + SonarCloud | ✅ | — | — |
| JWT + register/login | ✅ | — | — |
| Swagger v3 | básico | completo | — |
| H2 + DataInitializer | básico | expandido | — |
| Tests unitarios | ✅ | + cobertura | + ArchUnit |
| Catálogo players | ✅ | — | — |
| Sistema de cotización | — | ✅ | — |
| Mercado buy/sell | — | — | ✅ |
| Portfolio | — | — | ✅ |
| AOP audit + Prometheus | — | — | ✅ |

---

## 14. Placeholders pendientes

Reemplazar antes de cerrar Entrega 1:

- ~~`sonar.projectKey` y `sonar.organization` en `sonar-project.properties`~~ ✅ completado en issue #6
- ~~Secrets en GitHub Actions: `SONAR_TOKEN`, `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION`~~ ✅ completado en issue #6
- `FOOTBALL_DATA_API_KEY` (registrar gratis en football-data.org)
- `JWT_SECRET` para prod — generar con `openssl rand -base64 64`, NO commitear
- GitHub org/user en `CODEOWNERS` — completar con todos los integrantes del equipo

---

## 15. Reglas para el asistente

- **Preguntar antes de asumir** decisiones arquitectónicas que afecten más de un módulo.
- Respetar las capas: controllers delgados, lógica en services, acceso a datos solo en repositories, APIs externas solo en adapters.
- Toda nueva dependencia debe justificarse en la descripción del PR.
- Con H2 + `ddl-auto=create-drop` no hay migraciones. Si en el futuro se migra a Flyway, crear `V{n}__descripcion.sql` sin editar las anteriores.
- Cambios de API: actualizar anotaciones OpenAPI y agregar test de slice.
- No introducir lógica de negocio en entidades JPA sin discutirlo.
- Commits con Conventional Commits; no mergear sin CI verde.
- ArchUnit no puede estar deshabilitado desde Entrega 3.
- Al cerrar cada issue, ejecutar la **Rutina de cierre** (§17).

---

## 16. Seguridad — SecurityConfig

El `SecurityConfig` actual (implementado en issue #2) incluye: JWT filter, stateless session, rutas públicas de auth, y `AuthenticationEntryPoint` explícito para retornar 401.

Rutas públicas:
- `/auth/**`
- `/swagger-ui/**`, `/swagger-ui.html`
- `/v3/api-docs/**`
- `/actuator/health`, `/actuator/health/**`
- `/actuator/prometheus` ← agregado en E3 para que Prometheus scrapeé sin auth
- `/h2-console/**`

**Gotcha crítico:** Spring Security 6 stateless sin `AuthenticationEntryPoint` explícito retorna **403** (no 401) para requests sin token. Siempre agregar:
```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((req, res, e) -> res.sendError(401, "Unauthorized"))
)
```

---

## 17. Estado actual del proyecto

**Última actualización:** 2026-07-01

| Issue | Título | Estado |
|---|---|---|
| #1 | Scaffold del proyecto | ✅ Mergeado a `develop` (PR #7) |
| #2 | JWT + endpoints de autenticación | ✅ Mergeado a `develop` (PR #9, #10) |
| #3 | Configuración Swagger v3 (OpenAPI 3) | ✅ Mergeado a `develop` (PR #11) |
| #4 | Catálogo de jugadores + DataInitializer | ✅ Mergeado a `develop` (PR #12) |
| #5 | Tests unitarios (Entrega 1) | ✅ Mergeado a `develop` (PR #13) |
| #6 | SonarCloud — registro y quality gate | ✅ Mergeado a `develop` (PR #14) |
| #19 | Sistema de cotización base (domain + application) | ✅ Mergeado a `develop` (PR #23, #24) |
| #22 | DataInitializer con métricas y cotizaciones iniciales | ✅ Mergeado a `develop` (PR #30) |
| #31 | PositionWeightedStrategy v1.0 (segunda estrategia) | ✅ Mergeado a `develop` (PR #32) |
| #33 | Mercado: compra/venta de tokens (buy/sell + idempotencia) | ✅ Mergeado a `develop` (PR #35) |
| #34 | Historial de operaciones (GET /users/{id}/transactions) | ✅ Mergeado a `develop` (PR #36) |
| #40 | endpoints REST de cotización (historial, actual y ranking) | ✅ Mergeado a `develop` (PR #42) |
| #41 | POST /quotes/recalculate (ADMIN) | ✅ Mergeado a `develop` (PR #43) |
| #47 | Cotización a fecha dada (GET /players/{id}/quotes/at) | ✅ Mergeado a `develop` (PR #63) |
| #48 | Separación profiles unit/e2e | ✅ Mergeado a `develop` (PR #64) |
| #49 | Unit tests módulo trading | ✅ Mergeado a `develop` (PR #65) |
| #58 | Portfolio del usuario (GET /users/{id}/portfolio) | ✅ Mergeado a `develop` (PR #66, #67) |
| #62 | Estrategias configurables (StrategyConfig persistida) | ✅ Mergeado a `develop` (PR #67) |
| #50 | Cierre E2: RELEASE-NOTES.txt + tag v2.0.0 | ✅ RELEASE-NOTES.txt en feature/release-e2; tag v2.0.0 pendiente |
| #51 | Auditoría AOP de Web Services | 🔄 En progreso — `feature/audit-ws-e3` (pendiente merge a `develop`) |
| #53 | Prometheus + Actuator | 🔄 En progreso — `feature/audit-ws-e3` (pendiente merge a `develop`) |
| #54 | Caché Redis para ranking | 🔄 En progreso — `feature/audit-ws-e3` (ranking cacheado + pre-warming; pendiente merge) |
| —  | Stack Loki + fix host.docker.internal | ✅ Configurado (2026-07-01) — ver §12 |

**Entrega 1 completada:** Todos los issues de E1 están mergeados en `develop` y en `main`. Tag v1.0.0 creado el 2026-06-01. Release publicado en GitHub: https://github.com/dddaavo/dapp-bolsa-de-jugadores/releases/tag/v1.0.0

**Entrega 2 completada (2026-06-29):** Todos los issues de E2 mergeados en `develop`. PR de RELEASE-NOTES.txt en process, pendiente merge a `main` y tag `v2.0.0`. Sistema de cotización con 2 estrategias configurables, endpoints de quotes (current/historial/ranking/at), recalculate ADMIN, job semanal, mercado buy/sell, portfolio, unit tests trading, separación profiles, DataInitializer expandido, Swagger completo, JaCoCo en CI.

**Entrega 3 — pendiente:** ver checklist §13.

**PRs de Entrega 1:**
- PR #7, #9, #10, #11, #12, #13, #14, #16, #17 → mergeados a `develop`
- PR #18 → mergeado a `main` (Release v1.0.0)

**PRs de Entrega 2:**
- PR #23, #24 → pricing system base (issue #19)
- PR #25, #26, #27, #42 → endpoints de cotización y recalculate (issues #40, #41)
- PR #30 → DataInitializer con métricas (issue #22)
- PR #32 → PositionWeightedStrategy (issue #31)
- PR #37, #38, #39 → JaCoCo en CI, Swagger completo, DataInitializer con escenario de trading
- PR #44, #45 → fix de issues SonarCloud y reorganización de tests en `e2e/` vs unit
- PR #63 → cotización a fecha dada (issue #47)
- PR #64 → separación profiles unit/e2e (issue #48)
- PR #65 → unit tests trading (issue #49)
- PR #66, #67 → portfolio (issue #58) + estrategias configurables (issue #62)
- PR #68 → refactor dominio anémico trading (en revisión)
- PR de cierre → RELEASE-NOTES.txt + AGENTS.md (issue #50)

**Backlog abierto:**
- **Entrega 2** (milestone `Entrega 2`): ✅ CERRADA
- **Entrega 3** (milestone `Entrega 3`): ✅ CERRADA salvo #57 (cierre: `RELEASE-NOTES.txt` agregado, pendiente merge a `main` + tag `v3.0.0`). Resto de issues (#51-#56, #61) mergeados.
- **En progreso en `feature/audit-ws-e3`:** #51 (AOP audit ✅), #53 (Prometheus ✅), #54 (Redis cache ✅ parcial)
- **#59 (Football-Data) cerrado** — WhoScored cumple §7; reabrir solo si la cátedra exige una API REST.
- Decisiones (sesión 2026-06-28): caché = Redis (#54); escalado #56 y deploy #60 = opcionales; #47 = endpoint point-in-time dedicado.

**Ramas activas:**
- `develop` — integración; base de las features
- `feature/audit-ws-e3` — E3: AOP audit, Prometheus, Redis cache, Grafana dashboard (pendiente merge)
- `entrega-1` — referencia del diseño original; **NO mergear**

**`entrega-1` como código de referencia:**
La rama `entrega-1` contiene una implementación completa del proyecto en un único commit. Puede usarse como referencia al trabajar en cualquier issue — hacer `git show entrega-1:ruta/al/archivo.java` para leer un archivo sin hacer checkout. El código no está garantizado como funcional al 100% ni sigue la estructura final acordada, pero es útil para no partir de cero en cada issue.

**Board de seguimiento:** https://github.com/users/dddaavo/projects/1

**Decisiones tomadas en sesión inicial (2026-05-03):**
- `SecurityConfig` mínimo incluido en el scaffold; el issue #2 lo expande con JWT
- Separación Surefire/Failsafe configurada desde el scaffold (`*Test.java` / `*IT.java`)
- `BolsaApplicationIT` es el único test hasta que llegue el issue #5
- `application-test.yml` vive en `src/test/resources/` (no en `src/main/`)
- CI activa perfil `test` con `-Dspring.profiles.active=test`
- GitHub cierra issues automáticamente solo al mergear a `main`; cerrar manualmente al mergear a `develop`

**Decisiones tomadas en issue #2 (2026-05-03):**
- `User` no tiene campo `nombre` en Entrega 1 (solo `email`, `passwordHash`, `role`)
- `JwtService` y `BCryptPasswordEncoder` se instancian directamente en tests unitarios (no `@Mock`) por incompatibilidad con Java 25 (ver §21)
- `application-test.yml` incluye `jwt.secret` base64 para tests de integración
- Colección Postman en `postman/bolsa-de-jugadores.postman_collection.json` — importar para smoke test manual

**Decisiones tomadas en issue #3 (2026-05-03):**
- `OpenApiConfig` en `config/` — bean `OpenAPI` con `SecurityScheme` Bearer JWT aplicado globalmente vía `SecurityRequirement`; no hace falta anotar cada endpoint con `@SecurityRequirement`
- `springdoc-openapi-starter-webmvc-ui:2.5.0` ya estaba en `pom.xml` desde el scaffold; no requirió agregar dependencia
- `@Schema` en records Java se anota en cada campo del record (no en la clase); la anotación a nivel de clase no es reconocida por springdoc
- `sonar.exclusions` ampliado con `**/auth/api/**` y `**/shared/error/**` para excluir DTOs y excepciones del an��lisis
- Anotaciones de `PlayerController` (`@Tag`, `@Operation`) pendientes para issue #4; Swagger funciona con los endpoints de auth ya anotados

**Decisiones tomadas en sesión de scraping WhoScored (2026-05-05/06) — parte de issue #4:**
- Scraping implementado con Playwright Java (no Jsoup — WhoScored usa Cloudflare + JS rendering)
- Arquitectura: `WhoScoredAdapter` (@Primary) orquesta entre `WhoScoredPlaywrightScraper` y `WhoScoredFixturesFallback`
- `WhoScoredPlaywrightScraper` no es @Component — se instancia manualmente desde el adapter cuando `whoscored.scraping.enabled=true`
- `DataInitializer` es idempotente: si `playerRepository.count() > 0` no hace nada
- Scraping desactivado por defecto en todos los perfiles (`whoscored.scraping.enabled=false`); activar vía env var `WHOSCORED_SCRAPING_ENABLED=true` en Run Config de IntelliJ
- Env vars recomendadas en Run Config local: `WHOSCORED_SCRAPING_ENABLED=true`, `NODE_TLS_REJECT_UNAUTHORIZED=0`, `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`
- Chrome del sistema en `/usr/bin/google-chrome` (Linux); se configura via `CHROME_EXECUTABLE_PATH` para override
- Resultado típico: ~10 jugadores por liga (50 total) — primera página de WhoScored; paginación pendiente para E2
- `nationality` no se extrae desde la vista de estadísticas de liga — queda vacía; se populará desde perfil individual en E2

**Decisiones tomadas en cierre de Entrega 1 (2026-06-01):**
- 4 usuarios de prueba agregados en `DataInitializer`: alice@example.com, bob@example.com, charlie@example.com, diana@example.com
- Password de cada usuario: `{Nombre}1234` (ej: Alice1234)
- Todos con rol USER para testing de endpoints protegidos
- `Position` y `League` tienen campo `label` descriptivo (Goalkeeper, Premier League, etc.) — BD persiste el `name()` del enum
- Decisión: NO usar Python para scraping (overhead de deployment, interfaz string-based, doble runtime); todo en Java con Playwright
- Decisión: Football-Data.org free tier no da acceso a `/competitions/{id}/teams` — WhoScored cubre el catálogo para E1

**Decisiones tomadas en issue #6 (2026-05-05):**
- `sonar.projectKey=dddaavo_dapp-bolsa-de-jugadores`, `sonar.organization=dappgrupom` — valores reales cargados en `sonar-project.properties` y en GitHub Secrets
- SonarCloud analiza automáticamente los PRs (Automatic Analysis activado en la plataforma), independiente de la condición del CI
- El step de SonarCloud en `ci.yml` solo corre en push a `main` — el análisis de PRs lo hace SonarCloud automáticamente
- Quality Gate "Sonar way" requiere ≥ 80% cobertura en nuevo código — incompatible con Entrega 1. Crear Quality Gate personalizado en SonarCloud con umbral menor (ver §23)
- `sonar.coverage.exclusions` ampliado con `**/domain/**`, `**/*Repository.java`, `**/shared/audit/**` para excluir entidades JPA, enums e interfaces Spring Data
- JaCoCo 0.8.12 configurado en `pom.xml` con exclusiones equivalentes
- `fetch-depth: 0` agregado en `ci.yml` (requerido por SonarCloud para análisis incremental correcto)
- `JwtAuthFilter`: corregido bug real — token JWT malformado/expirado causaba HTTP 500; ahora se captura y devuelve 401
- `ApiExceptionHandler`: parámetros `ex` usados con logging SLF4J (resuelve `java:S1172`)
- `DomainException` + `PlayerNotFoundException`: `serialVersionUID` agregado (`java:S2057`)

**Decisiones tomadas en issue #41 — POST /quotes/recalculate (2026-06-09):**
- `RecalculationController` en `pricing/api/` con `@RequestMapping("/api/v1/quotes")`, separado de `QuoteController` para evitar conflictos de mapping
- `@PreAuthorize("hasRole('ADMIN')")` a nivel de método — 403 si el token es USER, 401 si no hay token
- Body de request opcional (`@RequestBody(required = false)`) — si se omite usa la estrategia activa por defecto del `StrategyRegistry`
- Bug corregido: el controller calculaba `resolvedStrategy` (no-null) pero pasaba `strategyName` (puede ser null) al orquestador; corregido para pasar `resolvedStrategy` en ambos lugares
- 5 tests de integración: 200 con ADMIN, 403 con USER, 401 sin token, recalculate con MatchMetrics, recalculate con PositionWeighted
- El `@BeforeAll` del IT crea jugador + `PlayerMetricsSnapshot` + `PlayerTokenInventory` — los tres son necesarios para que el orquestador calcule sin errores

**Decisiones tomadas en issue #40 — endpoints REST de cotización (2026-06-09):**
- `QuoteController` en `pricing/api/` con `@RequestMapping("/api/v1/players")` — coexiste con `PlayerController` sin conflicto porque los paths son distintos (`/{id}/quotes/*` y `/ranking`)
- El endpoint `GET /players/ranking` acepta `?limit=` (no `?size=`) como parámetro con default 10 y cap 50 — evita abusos sin paginación pesada
- Ranking resuelve el nombre del jugador llamando a `PlayerService.findById()` por cada Quote — N+1 aceptable dado que el ranking tiene cap en 50
- Tests de integración (`QuoteIT`) usan `@TestInstance(PER_CLASS)` + `@BeforeAll` para seed de datos; `@BeforeEach` para obtener token — mismo patrón que el resto de ITs
- 5 tests de integración: cotización actual, 404 sin cotización, historial completo, historial filtrado por fechas, ranking
- `QuoteResponse.from(Quote)` accede a `quote.getValue().amount()` y `.currency()` — el value object `Money` es record, no entidad JPA

**Decisiones tomadas en issue #19 — Sistema de cotización (2026-06-08):**
- Domain y application layer implementados; controllers REST (pricing `api/`) pendientes para un issue separado
- `Money` como value object inmutable con `BigDecimal` — no entidad JPA, sin columna propia; se almacena como columna `value` en `Quote`
- `StrategyWeights` como value object con validación: pesos deben sumar exactamente 1.0 (delta ≤ 0.001)
- `PricingContext` como record — contiene el `PlayerMetricsSnapshot` y datos auxiliares del jugador
- `MatchMetricsStrategy v1.0`: normaliza métricas a [0,1], score = Σ peso_i · métrica_norm_i, resultado escalado a `Money`
- `StrategyRegistry`: Spring-managed map de `PricingStrategy` beans por nombre — permite agregar estrategias nuevas sin modificar código existente
- `QuoteRecalculationOrchestrator`: recorre todos los jugadores y calcula cotización usando la estrategia activa; no expuesto aún via HTTP
- `PlayerTokenInventory`: entidad con `@Version` para optimistic locking; totalEmitted=100, initialTokenValue=1 crédito
- Tests unitarios: 51 tests en total para el módulo pricing (MoneyTest 14, MatchMetricsStrategyTest 6, QuoteServiceTest 5, QuoteRecalculationOrchestratorTest 6, QuoteRepositoryTest 7, StrategyWeightsTest 6, PricingContextTest 3, StrategyRegistryTest 5)
- Cobertura del módulo pricing subió de 78.4% a ≥80% para satisfacer el Quality Gate personalizado

**Decisiones tomadas en feature/audit-ws-e3 (2026-06-30):**
- `WebServiceAuditAspect` ya existía implementado; el problema era que el logger nombrado `"audit"` no tenía nivel configurado. Fix: `logging.level.audit: INFO` en `application-local.yml` + `logback-spring.xml` explícito.
- `logback-spring.xml` creado en `src/main/resources/` — usa `<springProfile>` para variar el comportamiento por perfil. En `local` escribe a `logs/audit.log` (rotación diaria). En otros perfiles solo consola.
- `GenericJackson2JsonRedisSerializer` usa su propio `ObjectMapper` que no tiene `JavaTimeModule` registrado. Al cachear objetos con campos `Instant` (`AuditableEntity`) fallaba con `Java 8 date/time type not supported`. Fix: pasarle un `ObjectMapper` con `JavaTimeModule` + `NON_FINAL` typing en `CacheConfig`.
- `/actuator/prometheus` requería auth por el wildcard `/actuator/**`. Agregado explícitamente a `permitAll()` antes del wildcard.
- Tests `QuoteIT.deberiaRetornarRanking` era frágil — asumía que "Kylian Mbappé" estaba en el ranking, pero `DataInitializerIT` pre-calienta el cache sin ese jugador. Fix: verificar estructura de respuesta, no nombre específico.
- Tests `ActuatorIT` tenían 2 tests que esperaban 401/403 en `/actuator/prometheus`; actualizados a 200.
- Grafana provisiona dashboards desde JSON en `monitoring/grafana/provisioning/dashboards/`. Para forzar un reload sin reiniciar el contenedor, incrementar el campo `"version"` en el JSON.
- Playwright falla con JDK 21 en macOS arm64 (error `Failed to create driver`). Funciona con JDK 25. La run configuration de IntelliJ usa JDK 25; el wrapper de Maven usa el `JAVA_HOME` del sistema (JDK 21). Los tests CI no usan Playwright (scraping desactivado).
- `SPRING_PROFILES_ACTIVE=local` debe estar en las variables de entorno de la run configuration de IntelliJ, no solo como system property. Sin esto, la app levanta sin perfil y no levanta Redis ni scraping.

---

## 23. SonarCloud — Quality Gate personalizado

El Quality Gate "Sonar way" por defecto requiere **≥ 80% de cobertura en nuevo código**. Para Entrega 1 (y mientras no haya cobertura completa), crear un Quality Gate propio:

1. SonarCloud → **Quality Gates** → **Create**
2. Nombre: `bolsa-jugadores`
3. Condiciones sugeridas para E1/E2:
   - `Reliability Rating` ≤ A (sin bugs nuevos)
   - `Security Rating` ≤ A (sin vulnerabilidades)
   - `Maintainability Rating` ≤ A
   - `Coverage on New Code` ≥ 50% *(ajustar según avance de tests)*
4. Asignar al proyecto: **Administration → Quality Gate → bolsa-jugadores**

**Valores actuales del proyecto:** `sonar.projectKey=dddaavo_dapp-bolsa-de-jugadores`, `sonar.organization=dappgrupom`

---

## 18. Rutina: "Iniciá el issue #N"

Cuando el dev escriba **"Iniciá el issue #N"**, el asistente ejecuta de forma autónoma:

1. **Leer el issue:** `gh issue view N` — título, descripción, criterios de aceptación.

2. **Análisis (BARRERA):** Cruzar el issue contra el enunciado (`§3`, `§4`) y el estado actual (`§17`). Identificar:
   - Dependencias con issues anteriores no mergeados
   - Gaps o ambigüedades en los requerimientos
   - Decisiones de arquitectura que afecten más de un módulo
   Si hay dudas → **DETENER y preguntar** antes de continuar.

3. **Crear la rama:** `git checkout develop && git pull && git checkout -b feature/<área>` y pushear.

4. **Plan de implementación (BARRERA):** Presentar al dev los archivos a crear/modificar y la lógica a implementar. **Esperar confirmación explícita** antes de escribir código.

5. **Implementar:** Una vez aprobado el plan, desarrollar según las reglas de §15. El **primer commit** debe hacerse apenas hay algo compilable — aunque sea el esqueleto.

6. **Crear PR en draft** (después del primer commit — `gh pr create` falla si no hay commits entre la feature branch y `develop`):
   ```
   gh pr create --draft --title "feat: <descripción>" --base develop --body "..."
   ```
   El body debe incluir referencia al issue (`Closes #N`) y secciones Summary / Test plan.

7. **Continuar implementación** hasta completar todos los criterios de aceptación. Correr `./mvnw verify -Dspring.profiles.active=test` antes de dar el trabajo por terminado.

8. **Marcar PR como ready:** `gh pr ready <número>` cuando el desarrollo esté completo y el CI en verde.

---

## 19. Rutina: "Cerrá el issue #N"

Cuando el dev escriba **"Cerrá el issue #N"**, el asistente ejecuta antes del merge:

1. Actualizar la tabla de estado en §17 (marcar como mergeado).
2. Registrar en §17 las decisiones relevantes del issue: gotchas, patrones nuevos, convenciones.
3. Si el issue introdujo arquitectura o patrones no documentados, agregar la sección correspondiente.
4. Actualizar el checklist de §13: marcar `[x]` en todos los ítems que el issue haya completado, total o parcialmente. Si un ítem queda parcialmente cubierto, anotarlo con una nota inline.
5. Commitear en la feature branch con mensaje: `chore: actualiza estado del proyecto tras cierre de issue #N`.

Recién después de ese commit el dev mergea el PR a `develop` y cierra el issue manualmente en GitHub.

---

## 20. Workflow completo por issue (resumen para el dev)

```
"Iniciá el issue #N"   → el asistente crea rama, propone plan
[confirmás el plan]    → el asistente implementa, hace primer commit, crea PR draft
                       → continúa implementando hasta terminar y corre los tests
"Cerrá el issue #N"    → el asistente actualiza AGENTS.md y commitea
[mergeás el PR]        → cerrás el issue en GitHub
```

---

## 21. Gotchas descubiertos

### host.docker.internal no resuelve en Linux nativo

En Docker Desktop (Mac/Windows) `host.docker.internal` resuelve automáticamente al host. En Linux con Docker Engine nativo **no resuelve**, por lo que Prometheus no puede scrapear la app y Grafana muestra "No data".

**Fix:** agregar `extra_hosts` al servicio que necesita acceder al host:
```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```
Ya configurado en `docker-compose.monitoring.yml` para el servicio `prometheus`.

### Grafana no carga nuevos datasources tras actualizar provisioning

Si el volumen `grafana_data` tiene estado anterior (datasources viejos), reiniciar el contenedor de Grafana no siempre aplica los cambios del provisioning. Si tras `docker compose restart grafana` el datasource Loki no aparece, borrar el volumen:
```bash
docker compose -f docker-compose.monitoring.yml down
docker volume rm dapp-bolsa-de-jugadores_grafana_data
docker compose -f docker-compose.monitoring.yml up -d
```

### gh CLI — errores GraphQL por deprecation de Projects (classic)

`gh pr edit`, `gh issue view` y similares fallan con: `"Projects (classic) is being deprecated"` en repos con Projects v2.

**Fix:** usar `gh api` REST en lugar de los comandos de alto nivel:
```bash
# En vez de gh issue view 2 --json title,body
gh api repos/dddaavo/dapp-bolsa-de-jugadores/issues/2 --jq '{title:.title,body:.body}'

# En vez de gh pr edit 9 --body "..."
gh api repos/dddaavo/dapp-bolsa-de-jugadores/pulls/9 -X PATCH -f body='...'
```

### gh pr create — falla si no hay commits entre branches

`gh pr create` falla con `"No commits between develop and feature/xxx"` si se ejecuta antes del primer commit en la feature branch.

**Fix:** hacer al menos un commit antes de crear el PR draft. La rutina §18 ya refleja este orden corregido.

### Java 25 + Mockito inline — no puede mockear clases concretas con JVM flags nuevos

El sistema tiene JDK 25 instalado aunque el proyecto compile a Java 21 (`--release 21`). Mockito inline mock maker falla al intentar mockear `JwtService` o `BCryptPasswordEncoder`:
```
Could not modify all classes [class JwtService, class java.lang.Object]
```

**Fix:** en lugar de `@Mock JwtService`, instanciar directamente en el `@BeforeEach`:
```java
jwtService = new JwtService("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3Rpbmctb25seQ==", 15);
passwordEncoder = new BCryptPasswordEncoder();
```
Aplica a cualquier clase concreta sin interfaz que Mockito no pueda subclasear. Solo mockear lo que tenga interfaz o sea heredable.

### Spring Security 6 stateless — retorna 403 en vez de 401 sin AuthenticationEntryPoint

En configuración stateless (`SessionCreationPolicy.STATELESS`) sin `AuthenticationEntryPoint` explícito, Spring Security 6 devuelve **403 Forbidden** para requests sin token en lugar del esperado **401 Unauthorized**.

**Fix:** siempre agregar en el `SecurityFilterChain`:
```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((req, res, e) -> res.sendError(401, "Unauthorized"))
)
```
Ver implementación en `config/SecurityConfig.java`.

### SonarCloud — Automatic Analysis analiza PRs aunque el CI no lo haga

SonarCloud tiene una feature de "Automatic Analysis" que analiza PRs automáticamente desde GitHub, **independiente del step de CI**. Si el Quality Gate falla en un PR, aparece como check fallido aunque el step `Analyze with SonarCloud` en `ci.yml` tenga condición `refs/heads/main`.

**Consecuencia:** el análisis de PRs lo hace SonarCloud por su cuenta; el step del CI es solo para el análisis de la rama `main` (full analysis con cobertura).

**Fix para Quality Gate en PRs:** crear un Quality Gate personalizado en SonarCloud con umbral de cobertura realista para la etapa del proyecto (ver §23).

### WhoScored — selectores CSS cambian entre temporadas

El DOM de WhoScored cambia cada temporada. Los selectores originales (`tr.player-table-statistics`, `data-player-id`, `td.pn`, `td.tname`, `td.pos`) quedaron obsoletos. Selectores actuales (temporada 2024/25):

- Filas: `#player-table-statistics-body tr` (sin clase específica)
- ID del jugador: regex sobre `href` del `a.player-link` → `/players/(\d+)/`
- Nombre: `td.overflow-text a.player-link span.iconize` → innerText
- Equipo: `td.overflow-text span.team-name` → innerText sin la coma final
- Posición: último `span.player-meta-data` dentro de `td.overflow-text` → formato `",  D(L),M(CLR)  "`

**Cada inicio de temporada revisar el DOM con DevTools** — inspeccionar una fila de la tabla y verificar que los selectores sigan siendo válidos.

### WhoScored — formato de posición con paréntesis y múltiples valores

Las posiciones ya no son simples (`FW`, `MF`) sino con zona y lado: `D(L),M(CLR)`, `AM(CLR),FW`, `DMC`.

**Regla de mapeo:** tomar la primera posición (`split(",")[0]`), eliminar el sufijo entre paréntesis (`replaceAll("\\(.*?\\)", "")`), aplicar:
- `GK` → GK
- `DM*`, `M*`, `AM*` → MF (verificar `DM` antes de `D` para no mapear defensivos medios a DF)
- `D*`, `SW` → DF
- resto → FW

**Limitación conocida:** extremos como Bukayo Saka quedan mapeados como DF porque WhoScored les asigna `D(L)` por su posición real en el campo. El mapeo es una simplificación aceptable para E1.

### WhoScored — 10 jugadores por página, sin dropdown

WhoScored muestra exactamente 10 jugadores por página. **No existe un dropdown** para cambiar ese límite. Para obtener más jugadores hay que implementar paginación via el botón "Next" del DOM (pendiente E2).

### Playwright Java — descarga Firefox y WebKit al inicializar

`Playwright.create()` descarga todos los browsers a `~/.cache/ms-playwright/` la primera vez, aunque el proyecto use solo Chrome del sistema. Agregar `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` como env var evita las descargas innecesarias (~170MB).

### Playwright Java — nueva instancia de browser por cada liga

`WhoScoredPlaywrightScraper` se instancia por cada llamada a `fetchPlayersByLeague`. Esto crea un proceso Chrome nuevo por liga (5 en total al startup). Es ineficiente pero aceptable para E1. En E2, cuando haya sync periódico, conviene refactorizar para reutilizar una sola sesión.

### H2 in-memory — no accesible desde IntelliJ Database tool

Conectar IntelliJ al datasource `jdbc:h2:mem:bolsadb` abre una instancia H2 nueva y vacía, separada de la que usa Spring Boot. **Fix:** usar la H2 Console integrada en http://localhost:8080/h2-console mientras la app esté corriendo. Alternativa: cambiar a `jdbc:h2:file:./data/bolsadb` en `application-local.yml` para persistencia en archivo.

### WhoScored — LA_LIGA timeout intermitente

La URL de La Liga tiende a tardar más que las otras en cargar (Cloudflare throttling). Con timeout de 45s a veces falla y activa el fallback estático. Si es recurrente, aumentar el timeout de esa liga específicamente o agregar retry.

### JwtAuthFilter — token JWT inválido causaba HTTP 500

Sin try-catch en `doFilterInternal`, un token JWT malformado o expirado lanzaba `MalformedJwtException` que propagaba como 500 en lugar de 401.

**Fix:** envolver la lógica de extracción en try-catch:
```java
try {
    String username = jwtService.extractUsername(token);
    // ... set authentication
} catch (Exception ex) {
    logger.debug("JWT inválido o expirado: {}", ex.getMessage());
}
filterChain.doFilter(request, response); // continúa → resultado: 401 por falta de auth
```

---

## 22. Rutina: "Onboarding"

Cuando un dev escriba **"Onboarding"** (o "cómo arranco", "quiero sumarme al proyecto"), el asistente ejecuta esta secuencia interactiva:

1. **Verificar JDK 21:**
   ```bash
   java -version
   ```
   Si la versión no es 21, indicar que instale Temurin 21: https://adoptium.net

2. **Verificar o instalar `gh` CLI:**
   ```bash
   gh --version
   ```
   Si no está instalado:
   ```bash
   # Ubuntu/Debian
   sudo apt install gh
   ```
   Luego autenticar:
   ```bash
   gh auth login
   # → GitHub.com → SSH → pegar fine-grained PAT
   ```
   El PAT necesita scopes `repo` + `workflow` (Issues: Read & Write). Verificar con `gh auth status`.

3. **Clonar el repo y pararse en `develop`:**
   ```bash
   git clone git@github.com:dddaavo/dapp-bolsa-de-jugadores.git
   cd dapp-bolsa-de-jugadores
   git checkout develop
   ```

4. **Correr los tests para verificar que el entorno funciona:**
   ```bash
   ./mvnw verify -Dspring.profiles.active=test
   ```
   Debe terminar en `BUILD SUCCESS`. Si falla, revisar que el JDK activo sea 21 (`java -version`).

5. **Importar la colección Postman:**
   Abrir Postman → Import → seleccionar `postman/bolsa-de-jugadores.postman_collection.json`.

6. **Leer el estado actual del proyecto** (§17) para ver qué issues están pendientes.

7. **Listo.** Para arrancar con un issue: `Iniciá el issue #N`.
