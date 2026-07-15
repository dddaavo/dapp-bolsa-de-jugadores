# Escalado horizontal — Bolsa de Jugadores

## Topología de N instancias

```
            ┌─────────────────────────────────────────┐
            │              Load Balancer               │
            │   (Render / nginx / AWS ALB / etc.)     │
            └──────────┬─────────────┬────────────────┘
                       │             │
              ┌────────▼──────┐  ┌───▼────────────┐
              │  Instancia 1  │  │  Instancia 2   │   ... N instancias
              │  Spring Boot  │  │  Spring Boot   │
              │  :8080        │  │  :8080         │
              └────────┬──────┘  └───┬────────────┘
                       │             │
          ┌────────────▼─────────────▼────────────────┐
          │              PostgreSQL                    │
          │  - Entidades JPA                           │
          │  - Tabla `shedlock` (lock distribuido)     │
          └────────────────────────────────────────────┘
                       │             │
          ┌────────────▼─────────────▼────────────────┐
          │              Redis                         │
          │  - Caché distribuida (ranking, cotizaciones)│
          └────────────────────────────────────────────┘
```

## Componentes del escalado

### 1. ShedLock — coordinar scheduled jobs

Con N instancias corriendo, `@Scheduled` se ejecutaría en **todos** los nodos al mismo tiempo. ShedLock resuelve esto mediante un lock en la base de datos compartida: solo la instancia que adquiere el lock primero ejecuta el job.

**Jobs protegidos:**

| Job | Cron | lockAtMostFor | lockAtLeastFor |
|---|---|---|---|
| `QuoteRecalculationJob` | Lunes 3 AM | 2 horas | 1 minuto |
| `ExternalDataSyncJob` | Diario 2 AM | 2 horas | 1 minuto |

- **`lockAtMostFor`**: tiempo máximo que el lock se mantiene aunque el proceso falle o se cuelgue. Evita deadlocks permanentes.
- **`lockAtLeastFor`**: tiempo mínimo antes de que otra instancia pueda tomar el lock. Evita doble ejecución si el job termina muy rápido.

**Tabla requerida en la base de datos** (creada automáticamente por `schema.sql`):
```sql
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
```

### 2. Caché distribuida con Redis

La caché de ranking (`@Cacheable`) usa Redis, no Caffeine in-process. Esto garantiza que todas las instancias comparten la misma caché y un evict en una instancia se propaga a todas (ver `CacheConfig`).

### 3. Graceful shutdown

`server.shutdown: graceful` en `application.yml` hace que cuando se envía `SIGTERM` (por ejemplo, al hacer rolling update con docker-compose o Kubernetes), Spring Boot:

1. Deja de aceptar nuevas requests.
2. Espera hasta `spring.lifecycle.timeout-per-shutdown-phase` (default 30s) a que las requests en curso terminen.
3. Solo entonces cierra el contexto de Spring.

Esto evita que una actualización de versión corte transacciones a mitad de ejecución.

### 4. Health probes para orquestadores

`management.endpoint.health.probes.enabled: true` habilita dos endpoints:

| Endpoint | Uso |
|---|---|
| `GET /actuator/health/liveness` | ¿El proceso está vivo? Si responde DOWN, el orquestador reinicia el contenedor. |
| `GET /actuator/health/readiness` | ¿La instancia puede recibir tráfico? Si responde DOWN (ej: durante warm-up), el load balancer no le envía requests. |

En Render, el health check apunta a `/actuator/health`. En Kubernetes se configurarían como `livenessProbe` y `readinessProbe`.

## Levantar el stack completo localmente

```bash
# Variables de entorno (opcional, hay defaults en docker-compose.yml)
export JWT_SECRET="$(openssl rand -base64 32)"

# Build + start (primera vez descarga imágenes)
docker-compose up --build

# Escalar a 2 instancias de la app
docker-compose up --scale app=2

# Ver logs de un servicio
docker-compose logs -f app
```

La app queda disponible en `http://localhost:8080`. Con `--scale app=2`, el load balancer de docker-compose (modo VIP) distribuye el tráfico entre instancias.

## Consideraciones para producción

- Usar PostgreSQL managed (Render Postgres, RDS, etc.) — no un contenedor sin volumen.
- Redis con autenticación: `REDIS_PASSWORD` + `REDIS_SSL=true` para conexiones TLS.
- JWT_SECRET generado con `openssl rand -base64 64` y guardado como secret del orquestador (nunca en el código).
- `ddl-auto: validate` en vez de `update` cuando el schema esté estable y gestionado con Flyway/Liquibase.
