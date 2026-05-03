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
| Scraping | **Jsoup** (+ **Playwright/Selenium** solo si Jsoup no alcanza) | WhoScored tiene anti-bot: adapter con fallback a fixtures |
| Caché | **Caffeine** (Spring Cache abstraction) | In-process, TTL configurable, sin infraestructura extra |
| Scheduler | **Spring `@Scheduled`** (+ **ShedLock** si escalamos a N>1 instancias) | Cotización semanal, sync externo |
| Mapeo | **MapStruct** | DTO ↔ Entity en compile-time, sin reflection |
| Validación | **Jakarta Bean Validation** (`spring-boot-starter-validation`) | `@Valid` en controllers |
| Observabilidad | **Actuator + Micrometer + Prometheus + Logback (JSON) + AOP audit** | Health, métricas Prometheus, logs estructurados, auditoría de WS (Entrega 3) |
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

**Regla:** los paquetes `domain` no importan Spring (excepto anotaciones JPA). Los `application.*Service` orquestan transacciones (`@Transactional`). Los `api.*Controller` son delgados: validan, delegan, mapean.

### 3.2 Patrones clave

- **Strategy** para cotización: `PricingStrategy.calculate(PlayerMetricsSnapshot) → QuoteValue`. `StrategyRegistry` resuelve por nombre. Cada `Quote` persiste `strategyName` y `strategyVersion` para auditoría.
- **Port/Adapter** para cada integración externa. El service nunca importa `footballdata` directamente.
- **Fallback chain** en scraping: `WhoScoredJsoupAdapter → WhoScoredFixturesFallback`. Orquestado con Resilience4j.
- **Idempotency-Key**: órdenes POST requieren header `Idempotency-Key: <uuid>`.
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
| Football-Data.org | resultados, fixtures | `RestClient` | Resilience4j retry 3x + circuit breaker |
| WhoScored | stats detalladas | Jsoup → Playwright | Circuit breaker + fallback a fixtures JSON |

`CompositePlayerStatsAdapter` decide la fuente. Todo dato externo se persiste en `PlayerMetricsSnapshot` — la cotización nunca pega a la API en el request path.

---

## 12. Observabilidad

### AOP audit (Entrega 3)

```java
@Around("within(@org.springframework.web.bind.annotation.RestController *)")
public Object audit(ProceedingJoinPoint pjp) throws Throwable { ... }
```

Loguear: `timestamp | user | operación | parámetros | tiempoDeEjecución(ms)`. No loguear passwords ni tokens JWT.

### Prometheus (Entrega 3)

Exponer `/actuator/prometheus`. Métricas custom: contador de órdenes, duración de recalc, errores de adapters.

---

## 13. Checklist por entrega

### Entrega 1
- [ ] Repo GitHub + CI verde en `main`
- [ ] SonarCloud registrado, issues < 10
- [ ] JWT: `POST /auth/register` + `POST /auth/login`
- [ ] Swagger v3 en `/swagger-ui.html` con `SecurityScheme` Bearer JWT
- [ ] Scaffold Maven + Spring Boot 3.3 + Java 21
- [ ] Entidades base: `User`, `Player`, `AuditableEntity`
- [ ] H2 configurado en perfil `local`
- [ ] Tests unitarios (al menos una clase de test por service)
- [ ] `GET /api/v1/players` y `GET /api/v1/players/{id}`
- [ ] `CODEOWNERS` y branch protection en `main`
- [ ] README con badge de CI y sección "How to run"
- [ ] Tag `v1.0.0` + Release Notes

### Entrega 2
- [ ] CI sin regresiones, H2 como DB principal
- [ ] `DataInitializer`: superusuario, 25+ jugadores (al menos uno por liga), 4 usuarios, cotizaciones iniciales — debe cubrir los escenarios de evaluación del enunciado §8: 4 usuarios + compra de 5 jugadores + evolución de cotizaciones por liga
- [ ] Swagger v3 completo con `@Operation`, `@ApiResponse`, `@Schema`
- [ ] Surefire + Failsafe separados; JaCoCo en CI
- [ ] Sistema de cotización: al menos una `PricingStrategy`
- [ ] `GET /players/{id}/quotes/current`, historial, ranking
- [ ] `POST /quotes/recalculate` (ADMIN)
- [ ] Tag `v2.0.0`

### Entrega 3
- [ ] ArchUnit en CI
- [ ] AOP audit logging
- [ ] Prometheus + Actuator
- [ ] Mercado: buy/sell con idempotencia
- [ ] Portfolio + historial de órdenes
- [ ] Segunda estrategia de cotización
- [ ] Integración Football-Data.org
- [ ] Tag `v3.0.0`

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

- `sonar.projectKey` y `sonar.organization` en `sonar-project.properties`
- Secrets en GitHub Actions: `SONAR_TOKEN`, `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION`
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

El `SecurityConfig` actual (desde issue #1 — scaffold) es **mínimo**: permite Swagger, H2 Console y Actuator sin autenticación; protege el resto con `anyRequest().authenticated()`.

**El issue #2 (JWT + auth) lo expande** con: JWT filter, stateless session, rutas públicas de auth, y autorización fine-grained. No reemplazar el SecurityConfig del scaffold — extenderlo.

Rutas públicas actuales:
- `/swagger-ui/**`, `/swagger-ui.html`
- `/v3/api-docs/**`
- `/actuator/**`
- `/h2-console/**`

---

## 17. Estado actual del proyecto

**Última actualización:** 2026-05-03

| Issue | Título | Estado |
|---|---|---|
| #1 | Scaffold del proyecto | ✅ Mergeado a `develop` |
| #2 | JWT + endpoints de autenticación | Pendiente |
| #3 | Configuración Swagger v3 (OpenAPI 3) | Pendiente |
| #4 | Catálogo de jugadores + DataInitializer | Pendiente |
| #5 | Tests unitarios (Entrega 1) | Pendiente |
| #6 | SonarCloud — registro y quality gate | Pendiente |

**Ramas activas:**
- `develop` — integración; base de las features
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

4. **Crear PR en draft:** 
   ```
   gh pr create --draft --title "feat: <descripción>" --base develop --body "..."
   ```
   El body debe incluir referencia al issue (`Closes #N`) y secciones Summary / Test plan.

5. **Plan de implementación (BARRERA):** Presentar al dev los archivos a crear/modificar y la lógica a implementar. **Esperar confirmación explícita** antes de escribir código.

6. **Implementar:** Una vez aprobado el plan, desarrollar según las reglas de §15. Correr `./mvnw verify -Dspring.profiles.active=test` antes de dar el trabajo por terminado.

7. **Marcar PR como ready:** `gh pr ready <número>` cuando el desarrollo esté completo y el CI en verde.

---

## 19. Rutina: "Cerrá el issue #N"

Cuando el dev escriba **"Cerrá el issue #N"**, el asistente ejecuta antes del merge:

1. Actualizar la tabla de estado en §17 (marcar como mergeado).
2. Registrar en §17 las decisiones relevantes del issue: gotchas, patrones nuevos, convenciones.
3. Si el issue introdujo arquitectura o patrones no documentados, agregar la sección correspondiente.
4. Commitear en la feature branch con mensaje: `chore: actualiza estado del proyecto tras cierre de issue #N`.

Recién después de ese commit el dev mergea el PR a `develop` y cierra el issue manualmente en GitHub.

---

## 20. Workflow completo por issue (resumen para el dev)

```
"Iniciá el issue #N"   → el asistente crea rama, PR draft, propone plan
[confirmás el plan]    → el asistente implementa y corre los tests
"Cerrá el issue #N"    → el asistente actualiza AGENTS.md y commitea
[mergeás el PR]        → cerrás el issue en GitHub
```
