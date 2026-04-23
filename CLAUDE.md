# Bolsa de Jugadores — Backend

Trabajo Práctico de la asignatura **Desarrollo de Aplicaciones (UNQ)**. Backend REST en Java/Spring Boot que modela un mercado de tokens de jugadores de las 5 grandes ligas europeas (Premier League, Bundesliga, La Liga, Serie A, Ligue 1), con cotización periódica calculada por estrategias configurables, operaciones de compra/venta contra un superusuario emisor y portfolio por usuario.

Fines didácticos: prioridad al **diseño**, **arquitectura** y **buenas prácticas** sobre la complejidad de features. Sólo backend; no hay frontend.

---

## 1. Stack y versiones

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
| Scraping | **Jsoup** (+ **Playwright/Selenium** sólo si Jsoup no alcanza) | WhoScored tiene anti-bot: adapter con fallback a fixtures |
| Caché | **Caffeine** (Spring Cache abstraction) | In-process, TTL configurable, sin infraestructura extra |
| Scheduler | **Spring `@Scheduled`** (+ **ShedLock** si escalamos a N>1 instancias) | Cotización semanal, sync externo |
| Mapeo | **MapStruct** | DTO ↔ Entity en compile-time, sin reflection |
| Validación | **Jakarta Bean Validation** (`spring-boot-starter-validation`) | `@Valid` en controllers |
| Observabilidad | **Actuator + Micrometer + Prometheus + Logback (JSON) + AOP audit** | Health, métricas Prometheus, logs estructurados, auditoría de WS (Entrega 3) |
| Testing | **JUnit 5, Mockito, AssertJ, Spring Test, Rest Assured, ArchUnit** | Unit (Surefire) + e2e/integration (Failsafe con H2); test de arquitectura (Entrega 3) |
| CI | **GitHub Actions** | Build, test, Jacoco, SonarCloud, deploy |
| Deploy | **Render** (Docker runtime + Postgres managed) | Free tier con GitHub integration |
| Calidad | **SonarCloud** + **JaCoCo** + **Spotless** (Google Java Format) | < 10 issues en entrega 1 |

### Dependencias Maven clave

```xml
<!-- Web & Core -->
<dependency>spring-boot-starter-web</dependency>
<dependency>spring-boot-starter-validation</dependency>
<dependency>spring-boot-starter-actuator</dependency>

<!-- Persistencia -->
<dependency>spring-boot-starter-data-jpa</dependency>
<dependency>com.h2database:h2</dependency>                         <!-- runtime; dev/test -->
<dependency>org.postgresql:postgresql</dependency>                 <!-- runtime; solo perfil prod -->

<!-- Métricas Prometheus (Entrega 3) -->
<dependency>io.micrometer:micrometer-registry-prometheus</dependency>

<!-- Seguridad + JWT -->
<dependency>spring-boot-starter-security</dependency>
<dependency>io.jsonwebtoken:jjwt-api:0.12.6</dependency>
<dependency>io.jsonwebtoken:jjwt-impl:0.12.6</dependency>          <!-- runtime -->
<dependency>io.jsonwebtoken:jjwt-jackson:0.12.6</dependency>       <!-- runtime -->

<!-- OpenAPI / Swagger v3 -->
<dependency>org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0</dependency>

<!-- Caché -->
<dependency>spring-boot-starter-cache</dependency>
<dependency>com.github.ben-manes.caffeine:caffeine</dependency>

<!-- Integraciones externas -->
<dependency>org.jsoup:jsoup:1.17.2</dependency>
<dependency>io.github.resilience4j:resilience4j-spring-boot3</dependency>

<!-- Mapeo -->
<dependency>org.mapstruct:mapstruct:1.5.5.Final</dependency>

<!-- AOP para auditoría de WS (Entrega 3) -->
<dependency>spring-boot-starter-aop</dependency>

<!-- Testing -->
<dependency>spring-boot-starter-test</dependency>                  <!-- test -->
<dependency>org.springframework.security:spring-security-test</dependency>  <!-- test -->
<dependency>io.rest-assured:rest-assured</dependency>              <!-- test -->
<dependency>com.tngtech.archunit:archunit-junit5:1.3.0</dependency> <!-- test; Entrega 3 -->
```

Plugins Maven: `spring-boot-maven-plugin`, `jacoco-maven-plugin` (coverage ≥ 70% objetivo), `spotless-maven-plugin` (formato), `maven-surefire-plugin` (tests unitarios `*Test.java`), `maven-failsafe-plugin` (tests e2e `*IT.java`).

---

## 2. Arquitectura

### 2.1 Enfoque

Arquitectura **por features (vertical slicing)** con respeto a la separación en capas pedida por el enunciado (Controller → Service → Repository → Adapter). Dentro de cada feature aplica un mini-hexagonal: el dominio no conoce infraestructura; los `integration.*` exponen **Ports** (interfaces) e implementan **Adapters**.

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
│   ├── api/
│   ├── application/     # OrderService (Buy/Sell), IdempotencyService
│   ├── domain/          # Order, OrderType, TokenHolding, OrderStatus
│   └── infrastructure/  # OrderRepository, IdempotencyKeyRepository
├── portfolio/           # Vista consolidada del usuario
├── integration/
│   ├── port/            # FootballDataPort, PlayerStatsPort (interfaces del dominio)
│   ├── footballdata/    # FootballDataAdapter (RestClient, circuit breaker)
│   └── whoscored/       # WhoScoredJsoupAdapter + WhoScoredFixturesFallback
├── scheduling/          # QuoteRecalculationJob, ExternalDataSyncJob
└── shared/
    ├── error/           # ApiExceptionHandler (RFC 7807), DomainException, ErrorCode
    ├── audit/           # AuditListener, auditable base entity
    └── util/
```

**Regla:** los paquetes `domain` no importan Spring (excepto anotaciones JPA si decidimos mezclar entidades y modelo; alternativa: entidades separadas y dominio puro). Los `application.*Service` orquestan transacciones (`@Transactional`). Los `api.*Controller` son delgados: validan, delegan, mapean.

### 2.2 Patrones clave

- **Strategy** para cotización: `PricingStrategy.calculate(PlayerMetricsSnapshot) → QuoteValue`. `StrategyRegistry` resuelve por nombre (`@Qualifier` + `Map<String, PricingStrategy>`). Cada `Quote` persiste `strategyName` y `strategyVersion` para auditoría.
- **Port/Adapter** para cada integración externa. El service nunca importa `footballdata` directamente.
- **Fallback chain** en scraping: `WhoScoredJsoupAdapter → WhoScoredFixturesFallback`. Orquestado con Resilience4j `@Retry` + `@CircuitBreaker` + `@Fallback`.
- **Idempotency-Key**: órdenes POST requieren header `Idempotency-Key: <uuid>`. Se persiste `(userId, idempotencyKey) UNIQUE`; en duplicado, devolver resultado previo.
- **Optimistic locking** con `@Version` en `TokenHolding`/`PlayerTokenInventory`. Si hay conflicto, `OrderService` reintenta hasta N veces con backoff. Para el inventory del superusuario, usar `SELECT ... FOR UPDATE` (pessimistic) dentro de la transacción — es el hotspot de contención.
- **RFC 7807 Problem Details** (`application/problem+json`) en `@RestControllerAdvice`.

### 2.3 Modelo de dominio (borrador)

- `User { id, email (unique), passwordHash, role (USER|ADMIN), createdAt }`. Superusuario = seed con email `system@bolsa.local` y rol `ADMIN`.
- `Player { id, externalId, name, position, team, league, nationality, ... }`
- `PlayerTokenInventory { playerId, totalEmitted=100, heldBySystem, @Version }`
- `Quote { id, playerId, value, calculatedAt, strategyName, strategyVersion }` — índice `(playerId, calculatedAt DESC)`
- `PlayerMetricsSnapshot { id, playerId, periodStart, periodEnd, goals, assists, shots, keyPasses, dribbles, tackles, minutes, yellowCards, redCards, rating, sourceAdapter }`
- `Order { id, userId, playerId, type (BUY|SELL), quantity, unitPrice, totalAmount, status, idempotencyKey, createdAt }`
- `TokenHolding { userId, playerId, quantity, avgBuyPrice, @Version }` — clave compuesta
- `StrategyConfig { name, version, active, weightsJson }` — configuración serializada como JSON en columna `TEXT` (no JSONB: H2 no lo soporta; se deserializa en el service con Jackson)

Todas las entidades extienden `AuditableEntity { createdAt, updatedAt }` vía `@EntityListeners(AuditingEntityListener.class)`.

### 2.4 Decisiones de concurrencia y consistencia

- **Transacciones**: `@Transactional` en services de escritura, con `isolation = READ_COMMITTED` (default Postgres) y `propagation = REQUIRED`.
- **Compra**:
  1. Validar usuario, player, quantity > 0, idempotency key.
  2. `SELECT FOR UPDATE` sobre `PlayerTokenInventory` del player.
  3. Tomar quote vigente (la última `Quote` de ese player).
  4. Verificar `heldBySystem >= quantity`.
  5. Decrementar inventory, upsert `TokenHolding` recalculando `avgBuyPrice`, persistir `Order` con status `EXECUTED`.
  6. Commit. Si falla cualquier paso: rollback completo.
- **Venta**: `SELECT FOR UPDATE` sobre `TokenHolding` del usuario; se devuelven los tokens al inventory del sistema.
- **Reintentos** ante `OptimisticLockException`: decorador en service con máximo 3 intentos y jitter.
- **Scheduler**: si alguna vez corre multi-instancia, activar **ShedLock** sobre la tabla `shedlock`; mientras tanto con 1 instancia en Render alcanza el lock lógico por `@SchedulerLock` opcional.

### 2.5 Caché (Caffeine)

| Cache | Clave | TTL | Uso |
|---|---|---|---|
| `players` | playerId | 1 h | `GET /players/{id}` |
| `players.search` | filtros | 10 min | `GET /players` con query params |
| `quotes.current` | playerId | 5 min | cotización vigente (se invalida al recalcular) |
| `ranking` | strategyName | 10 min | `GET /players/ranking` |
| `footballdata.*` | endpoint+params | según recurso | respuestas externas |

Invalidación explícita (`@CacheEvict`) al recalcular cotizaciones.

---

## 3. API (endpoints)

Todos los endpoints bajo `/api/v1`. Documentados vía OpenAPI 3 en `/v3/api-docs`; UI en `/swagger-ui.html`.

### 3.1 Auth
| Método | Path | Rol | Descripción | Entrega |
|---|---|---|---|---|
| POST | `/auth/register` | público | Crea usuario rol USER, devuelve `{ accessToken, expiresIn }` | E1 |
| POST | `/auth/login` | público | Devuelve JWT (el "API KEY" para el resto de endpoints) | E1 |
| POST | `/auth/refresh` | público (con refresh token) | Renueva access token | opcional |

### 3.2 Catálogo
| Método | Path | Rol | Descripción |
|---|---|---|---|
| GET | `/players?league=&team=&position=&page=&size=` | USER | Listado paginado con filtros |
| GET | `/players/{id}` | USER | Detalle |

### 3.3 Cotización
| Método | Path | Rol | Descripción |
|---|---|---|---|
| GET | `/players/{id}/quotes?from=&to=` | USER | Historial de cotizaciones |
| GET | `/players/{id}/quotes/current` | USER | Cotización vigente |
| GET | `/players/ranking?strategy=` | USER | Top N según estrategia activa |
| POST | `/quotes/recalculate` | ADMIN | Dispara job manual |

### 3.4 Mercado
| Método | Path | Rol | Descripción | Headers |
|---|---|---|---|---|
| POST | `/orders/buy` | USER | `{ playerId, quantity }` | `Idempotency-Key` |
| POST | `/orders/sell` | USER | `{ playerId, quantity }` | `Idempotency-Key` |

### 3.5 Usuario
| Método | Path | Rol | Descripción |
|---|---|---|---|
| GET | `/users/{id}/portfolio` | dueño o ADMIN | Portfolio con ganancia/pérdida |
| GET | `/users/{id}/transactions` | dueño o ADMIN | Historial de órdenes |

### 3.6 Reglas de autorización

- `/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health` → públicos.
- `/actuator/**` (resto) → ADMIN.
- Autorización fine-grained en `/users/{id}/**`: método-level `@PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")`.
- Errores de auth → 401; de autorización → 403; validación → 400; idempotencia duplicada con mismatch de body → 409; dominio → 422.

---

## 4. Config local

### 4.1 Variables de entorno

```
# App
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local

# Security
JWT_SECRET=<256-bit base64>           # generar: openssl rand -base64 64
JWT_ACCESS_TTL_MINUTES=15

# External APIs
FOOTBALL_DATA_API_KEY=<TODO: key free de football-data.org>
FOOTBALL_DATA_BASE_URL=https://api.football-data.org/v4
WHOSCORED_BASE_URL=https://www.whoscored.com

# Scheduling
QUOTE_RECALC_CRON=0 0 3 * * MON       # lunes 03:00
EXTERNAL_SYNC_CRON=0 0 2 * * *        # diario 02:00
```

Perfiles:
- `local` — H2 in-memory, `ddl-auto=create-drop`, H2 console en `/h2-console`, datos de prueba cargados por `DataInitializer`
- `test` — H2 in-memory, misma config que local, usado por Surefire (unit) y Failsafe (e2e/IT)
- `prod` — PostgreSQL en Render, secrets inyectados como env vars, H2 deshabilitado

### 4.2 Config H2 (perfiles local y test)

```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:h2:mem:bolsadb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console.enabled: true   # accesible en /h2-console solo en local
  jpa:
    hibernate.ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
    show-sql: false
```

### 4.3 Bootstrap local

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

No requiere Docker. La app arranca, crea el schema en H2 y carga los datos de prueba vía `DataInitializer`.

Swagger: http://localhost:8080/swagger-ui.html | H2 Console: http://localhost:8080/h2-console

---

## 5. CI/CD

### 5.1 GitHub Actions

**`.github/workflows/ci.yml`** (push/PR a cualquier branch)
1. `actions/checkout@v4`
2. `actions/setup-java@v4` (temurin 21)
3. Cache `~/.m2`
4. `./mvnw -B test` — tests unitarios con Surefire (`*Test.java`, perfil unit, H2)
5. `./mvnw -B verify` — tests e2e con Failsafe (`*IT.java`, H2), incluye compilación completa
6. `./mvnw jacoco:report` — genera reporte JaCoCo (Entrega 2+)
7. Análisis SonarCloud: `./mvnw sonar:sonar -Dsonar.projectKey=<TODO> -Dsonar.organization=<TODO> -Dsonar.host.url=https://sonarcloud.io -Dsonar.token=$SONAR_TOKEN`

No se necesita Docker en CI porque la DB es H2 in-memory.

**`.github/workflows/deploy.yml`** (push a `main`, `needs: ci`)
1. Build Docker image → GHCR
2. `curl` al Render Deploy Hook (o usar action oficial de Render)

Secrets necesarios: `SONAR_TOKEN`, `RENDER_DEPLOY_HOOK_URL`, `FOOTBALL_DATA_API_KEY`, `JWT_SECRET`.

### 5.2 SonarCloud

- Organización: `<TODO>` — vincular GitHub org.
- Project key: `<TODO>`
- Quality gate: **Sonar way** (default) — objetivo **< 10 issues** en entrega 1.
- `sonar-project.properties` en raíz con `sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml`.
- Excluir de análisis: `**/dto/**`, `**/config/**`, clases generadas por MapStruct (`**/generated-sources/**`).

### 5.3 Dockerfile (multi-stage)

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Health check en Render: `GET /actuator/health`.

---

## 6. Flujo de trabajo (proyecto grupal 2-3 personas)

- `main` protegida: requerir PR + 1 review + CI verde.
- Branches: `feature/<área>-<tema>`, `fix/<bug>`, `chore/<tarea>`.
- Commits: **Conventional Commits** (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- `CODEOWNERS` en `.github/` cubriendo al menos `pom.xml`, `src/main/`, `.github/`.
- PRs deben incluir: descripción, checklist (tests, cobertura, Sonar), captura de Swagger si cambia API.

---

## 7. Testing

### Pirámide

- **Unit** (rápidos, sin Spring context — Surefire corre `*Test.java`): estrategias de pricing, mappers, validators, services con mocks. `src/test/java/.../domain`, `.../application`.
- **Slice**: `@WebMvcTest` (controllers + security), `@DataJpaTest` (repos con H2), `@JsonTest`.
- **Integration / e2e** (Failsafe corre `*IT.java`): `@SpringBootTest` + H2 in-memory + Rest Assured. Escenarios completos: login → buy → sell → portfolio.

### Separación de perfiles (requerida en Entrega 2)

```xml
<!-- pom.xml -->
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <!-- corre *Test.java — unit tests, sin Spring context pesado -->
  <configuration>
    <includes><include>**/*Test.java</include></includes>
  </configuration>
</plugin>
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <!-- corre *IT.java — integration/e2e con Spring context completo -->
  <configuration>
    <includes><include>**/*IT.java</include></includes>
  </configuration>
</plugin>
```

### Test de arquitectura — ArchUnit (Entrega 3)

```java
@AnalyzeClasses(packages = "com.unq.dapp.bolsa")
class ArchitectureTest {

    @ArchTest
    static ArchRule controllers_no_acceden_a_repositories =
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static ArchRule services_no_importan_controllers =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static ArchRule adapters_solo_en_integration =
        classes().that().haveNameMatching(".*Adapter")
            .should().resideInAPackage("..integration..");
}
```

### Objetivos

- Cobertura JaCoCo ≥ 70% líneas (verificar con `./mvnw jacoco:report`)
- 0 `@Ignore`/`@Disabled` sin comentario justificando
- Tests idempotentes: cada test crea su propia data, no depende de orden de ejecución

---

## 8. Estrategias de cotización (diseño)

Interface mínima:

```java
public interface PricingStrategy {
    String name();
    String version();
    Money calculate(PlayerMetricsSnapshot snapshot, PricingContext ctx);
}
```

Implementaciones **mínimo 2**:

1. **MatchMetricsStrategy v1.0**: normaliza cada métrica a `[0,1]` sobre máximos móviles (ventana 10 jugadores top). Score = Σ peso_i · métrica_norm_i. Valor = `valorBase + score * factorEscala`.
2. **PositionWeightedStrategy v1.0**: pesos distintos por posición (FW→goals/shots, MF→keyPasses/assists, DF→tackles/intercepts, GK→cleanSheets).

Persistencia de la configuración en `StrategyConfig` (columna `TEXT` con JSON serializado por Jackson, no JSONB — incompatible con H2) → swappable sin redeploy. Cada `Quote` registra `(strategyName, strategyVersion)` para reproducibilidad.

---

## 9. Integración externa — estrategia

| Fuente | Uso | Librería | Tolerancia |
|---|---|---|---|
| Football-Data.org (API oficial) | resultados, fixtures, alineaciones | `RestClient` | Resilience4j retry 3x con backoff + circuit breaker |
| WhoScored (scraping) | stats detalladas por jugador/partido | Jsoup (primero) → Playwright (si JS-rendered) | Circuit breaker + fallback a fixtures JSON en `resources/fixtures/whoscored/` |

El `PlayerStatsPort` tiene un orquestador (`CompositePlayerStatsAdapter`) que decide fuente por `Player.source` o por disponibilidad. Todo dato externo se persiste en `PlayerMetricsSnapshot` — la cotización **nunca** pega a la API en el request path (requisito 4.2).

---

## 10. Observabilidad

### Auditoría de Web Services — AOP (Entrega 3, requerida por la cátedra)

Interceptar **todos** los endpoints publicados con un `@Around` que loguee:

```
timestamp | user | operación/método | parámetros | tiempoDeEjecución(ms)
```

Implementación con Spring AOP + Logback:

```java
@Aspect @Component
public class WebServiceAuditAspect {
    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        // ejecutar método
        Object result = pjp.proceed();
        // loguear: timestamp, user (SecurityContext), método, args, duración
        log.info("{\"ts\":\"{}\",\"user\":\"{}\",\"op\":\"{}\",\"params\":\"{}\",\"durationMs\":{}}",
            Instant.now(), currentUser(), pjp.getSignature(), sanitize(pjp.getArgs()),
            System.currentTimeMillis() - start);
        return result;
    }
}
```

**No loguear:** passwords, tokens JWT, datos sensibles.

### Métricas Prometheus (Entrega 3)

- Dependencia: `micrometer-registry-prometheus`
- Exponer `/actuator/prometheus` (formato Prometheus scrape)
- Métricas custom: contador de órdenes ejecutadas, duración del job de recalc, errores de adapters externos
- Config en `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### Logs estructurados

- **Logback JSON** encoder. Nivel INFO por default; WARN en integraciones externas; DEBUG solo en perfil `local`.
- Registrar siempre: orden ejecutada, falla en adapter externo, recalc completo (con #jugadores, duración, estrategia).

---

## 11. Checklist por entrega

### Entrega 1

**Core:**
- [ ] Repo GitHub creado: `<TODO: org/repo>`
- [ ] `.github/workflows/ci.yml` — **build SUCCESS** en `main`
- [ ] SonarCloud registrado, análisis corriendo, **issues < 10**
- [ ] JWT implementado: `POST /auth/register` + `POST /auth/login` → devuelve access token (API KEY)
- [ ] Swagger v3 en `/swagger-ui.html` con `SecurityScheme` Bearer JWT

**Modelo:**
- [ ] Scaffold Maven + Spring Boot 3.3 + Java 21, estructura de paquetes de §2.1
- [ ] Entidades base: `User`, `Player`, `AuditableEntity`
- [ ] H2 configurado en perfil `local` (sin Docker)
- [ ] Tests unitarios según pautas de la materia (al menos una clase de test por service)

**Funcionalidad:**
- [ ] `POST /auth/register` — crea usuario rol USER
- [ ] `POST /auth/login` — devuelve JWT (el "API KEY" para el resto)
- [ ] `GET /api/v1/players` — listado paginado (datos hardcoded o fixture JSON está bien)
- [ ] `GET /api/v1/players/{id}` — detalle de jugador
- [ ] `CODEOWNERS` y branch protection en `main`
- [ ] README con badge de CI y sección "How to run"

---

### Entrega 2

**Core:**
- [ ] Build en "Verde" — CI sin regresiones
- [ ] H2 como DB principal (`ddl-auto=create-drop`, sin Docker)
- [ ] `DataInitializer` — carga datos de prueba al arrancar (superusuario, 25+ jugadores, 4 usuarios, cotizaciones iniciales)
- [ ] Swagger v3 documentando **todos** los endpoints con `@Operation`, `@ApiResponse`, `@Schema`
- [ ] Perfiles de testing separados: Surefire (`*Test.java`) para unit, Failsafe (`*IT.java`) para e2e
- [ ] Job de Coverage JaCoCo corriendo en CI, reporte subido a SonarCloud

**Funcionalidad:** ❓ confirmar con la cátedra — candidatos según enunciado del proyecto:
- [ ] Sistema de cotización: al menos una `PricingStrategy` implementada
- [ ] `GET /api/v1/players/{id}/quotes/current`
- [ ] `GET /api/v1/players/{id}/quotes?from=&to=`
- [ ] `POST /api/v1/quotes/recalculate` (ADMIN)
- [ ] `GET /api/v1/players/ranking?strategy=`

---

### Entrega 3

**Core:**
- [ ] Test de arquitectura con **ArchUnit** corriendo en CI (ver §7)
- [ ] Auditoría de WS con AOP + Logback: loguear `timestamp, user, operación, parámetros, tiempoDeEjecución` (ver §10)
- [ ] TAG en GitHub (`v3.0.0`) + Release Notes según formato de la cátedra
- [ ] Prometheus configurado: `/actuator/prometheus` con métricas custom
- [ ] Spring Boot Actuator: endpoints `health`, `info`, `metrics`, `prometheus` expuestos

**Funcionalidad:** ❓ confirmar con la cátedra — candidatos según enunciado del proyecto:
- [ ] Mercado: `POST /api/v1/orders/buy`, `POST /api/v1/orders/sell` (atómicos, con idempotencia)
- [ ] Portfolio: `GET /api/v1/users/{id}/portfolio`, `GET /api/v1/users/{id}/transactions`
- [ ] Segunda estrategia de cotización
- [ ] Integración completa con Football-Data.org y/o WhoScored

---

## 12. Distribución funcional por entrega

| Feature | E1 | E2 | E3 |
|---|---|---|---|
| Repo + CI + SonarCloud | ✅ | — | — |
| JWT + register/login | ✅ | — | — |
| Swagger v3 (básico) | ✅ | completo | — |
| H2 + DataInitializer básico | ✅ | expandido | — |
| Tests unitarios | ✅ | + cobertura | + ArchUnit |
| Perfiles Surefire/Failsafe | — | ✅ | — |
| JaCoCo job | — | ✅ | — |
| Catálogo players | ✅ | — | — |
| Sistema de cotización | — | ✅ | — |
| Scheduler semanal | — | ✅ | — |
| Mercado buy/sell + idempotencia | — | — | ✅ |
| Portfolio + historial | — | — | ✅ |
| Integración Football-Data.org | — | (si alcanza) | ✅ |
| AOP audit logging | — | — | ✅ |
| Prometheus + Actuators | — | — | ✅ |
| ArchUnit test | — | — | ✅ |
| GitHub TAG + Release Notes | v1.0.0 | v2.0.0 | v3.0.0 |

> Las celdas E2/E3 de funcionalidad son estimaciones — confirmar con la cátedra cuando publiquen el detalle de cada entrega.

---

## 13. Placeholders a completar

Reemplazar antes de cerrar Entrega 1:

- `<TODO: GitHub org/user y nombre del repo>`
- `<TODO: SonarCloud organization>` y `<TODO: SonarCloud project key>`
- `<TODO: Football-Data.org API key>` (secret en GH; registrar gratis en football-data.org)
- `<TODO: Render service ID / deploy hook URL>`
- `<TODO: JWT_SECRET generado>` — NO commitear; generar con `openssl rand -base64 64`
- `<TODO: integrantes del equipo>` para CODEOWNERS

---

## 14. Reglas para el asistente (Claude)

- **Preguntar antes de asumir** decisiones arquitectónicas que afecten más de un módulo.
- Respetar las capas: controllers delgados, lógica en services, acceso a datos solo en repositories, APIs externas solo en adapters.
- Cualquier nueva dependencia: justificar en PR description.
- Schema: con H2 + `ddl-auto=create-drop` no hay migraciones. Si en el futuro se migra a Flyway (prod), crear nueva migración `V{n}__descripcion.sql` sin editar las anteriores.
- Cambios de API: actualizar anotaciones OpenAPI y agregar test de slice.
- No introducir lógica de negocio en entidades JPA sin discutirlo (preferir servicios y value objects en `domain`).
- Commits con Conventional Commits; no mergear sin CI verde + Sonar pasando.
- El test de arquitectura (ArchUnit) debe correr en CI desde Entrega 3 y no puede estar deshabilitado.
