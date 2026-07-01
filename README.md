# Bolsa de Jugadores

[![CI](https://github.com/dddaavo/dapp-bolsa-de-jugadores/actions/workflows/ci.yml/badge.svg)](https://github.com/dddaavo/dapp-bolsa-de-jugadores/actions/workflows/ci.yml)

Backend REST en Java/Spring Boot que modela un mercado de tokens de jugadores de las 5 grandes ligas europeas (Premier League, Bundesliga, La Liga, Serie A, Ligue 1).

Trabajo Práctico — Desarrollo de Aplicaciones, UNQ.

## Stack

- Java 21 · Spring Boot 3.3 · Maven
- H2 file-based (local) · H2 in-memory (test/e2e) · PostgreSQL 16 (prod) · Spring Data JPA
- Spring Security 6 + JWT (jjwt 0.12)
- Redis 7 — caché distribuida del ranking de jugadores
- OpenAPI 3 / Swagger UI · springdoc 2.5
- Prometheus + Grafana — métricas y dashboard de observabilidad
- AOP audit — log de cada request HTTP con usuario, operación y duración
- Playwright Java — scraping WhoScored (datos de jugadores)

## Requisitos previos

| Herramienta | Versión mínima | Notas |
|---|---|---|
| JDK | 21 | Temurin recomendado |
| Docker Desktop | cualquiera reciente | Para Redis, Prometheus y Grafana |
| Maven | — | Usar el wrapper `./mvnw`, no instalar Maven global |

> **Playwright y scraping:** el scraping de WhoScored requiere JDK 25+ en la JVM que corre la app (limitación del driver de Node.js embebido en macOS arm64). En IntelliJ, configurar la run configuration con JDK 25 o superior. Los tests CI usan el fallback estático (sin scraping real).

## Cómo correr la app localmente

### 1. Levantar Redis

```bash
docker-compose up -d redis
```

Verifica que levantó: `docker-compose ps` debe mostrar `redis` como `healthy`.

### 2. Correr la app

**Opción A — IntelliJ (recomendado, necesario para scraping real):**

Run configuration `BolsaApplication` con:
- JDK 25 (para Playwright)
- Variables de entorno:
  ```
  SPRING_PROFILES_ACTIVE=local
  WHOSCORED_SCRAPING_ENABLED=true
  NODE_TLS_REJECT_UNAUTHORIZED=0
  PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
  ```

**Opción B — terminal (sin scraping, carga datos estáticos):**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Verificar que levantó

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **H2 Console:** http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:file:./data/bolsadb`
  - User: `sa` · Password: *(vacío)*
- **Health:** http://localhost:8080/actuator/health

### Usuarios de prueba (cargados por DataInitializer)

| Email | Password | Rol |
|---|---|---|
| `system@bolsa.local` | `admin1234` | ADMIN |
| `alice@example.com` | `Alice1234` | USER |
| `bob@example.com` | `Bob1234` | USER |
| `charlie@example.com` | `Charlie1234` | USER |
| `diana@example.com` | `Diana1234` | USER |

## Tests

```bash
# Unit tests (Surefire — perfil test)
./mvnw test

# Unit + e2e/integration + build completo (Failsafe — perfil e2e)
./mvnw verify
```

Los tests e2e (`*IT.java`) usan H2 in-memory con perfil `e2e` — no requieren Redis ni Docker.

## Smoke test manual

Importar `postman/bolsa-de-jugadores.postman_collection.json` en Postman. La variable `{{token}}` se rellena automáticamente al ejecutar **POST /auth/login**.

## Stack de monitoreo (opcional)

Prometheus + Grafana para métricas de la app en tiempo real.

```bash
# Levantar (requiere la app corriendo en localhost:8080)
docker-compose -f docker-compose.monitoring.yml up -d

# Bajar
docker-compose -f docker-compose.monitoring.yml down
```

| Servicio | URL | Credenciales |
|---|---|---|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | — |
| Métricas raw | http://localhost:8080/actuator/prometheus | público |

El dashboard **"Bolsa de Jugadores"** se provisiona automáticamente en Grafana con paneles de:
- Recalculaciones de cotización (total, por estrategia, duración p95)
- Órdenes de compra/venta (total, rate por tipo)
- Latencia HTTP p99
- Heap JVM

## Audit logs

Cada request HTTP queda registrado por el aspecto `WebServiceAuditAspect`:

```
HH:mm:ss.SSS INFO audit - user=alice@example.com operation=PlayerController.list(..) params=[...] durationMs=12
```

Los logs de auditoría se escriben en:
- **Consola de IntelliJ** (perfil `local`)
- **`logs/audit.log`** (perfil `local`, rotación diaria, 7 días de retención)

Passwords y tokens JWT se enmascaran automáticamente como `[PROTECTED:LoginRequest]` / `[PROTECTED:token]`.

## Desarrollo con IA

Este proyecto se desarrolla principalmente con asistentes de IA (Claude, Gemini, etc.). El archivo [`AGENTS.md`](AGENTS.md) es la fuente de verdad del proyecto para cualquier asistente: stack, arquitectura, convenciones y estado actual.

Rutinas disponibles — escribirlas tal cual en el chat del asistente:

- **`Iniciá el issue #N`** — crea la rama, propone el plan de implementación y desarrolla
- **`Cerrá el issue #N`** — actualiza el estado en `AGENTS.md` antes del merge

**Regla obligatoria:** cada cambio debe verificarse corriendo `./mvnw verify` y, si hay comportamiento de API nuevo, probarse con la colección Postman antes de mergear.

## Seguimiento

- **Project board:** https://github.com/users/dddaavo/projects/1
- **Issues:** https://github.com/dddaavo/dapp-bolsa-de-jugadores/issues
