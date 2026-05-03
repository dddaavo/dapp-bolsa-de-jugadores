# Bolsa de Jugadores

[![CI](https://github.com/dddaavo/dapp-bolsa-de-jugadores/actions/workflows/ci.yml/badge.svg)](https://github.com/dddaavo/dapp-bolsa-de-jugadores/actions/workflows/ci.yml)

Backend REST en Java/Spring Boot que modela un mercado de tokens de jugadores de las 5 grandes ligas europeas (Premier League, Bundesliga, La Liga, Serie A, Ligue 1).

Trabajo Práctico — Desarrollo de Aplicaciones, UNQ.

## Stack

- Java 21 · Spring Boot 3.3 · Maven
- H2 in-memory (dev/test) · Spring Data JPA
- Spring Security + JWT
- OpenAPI 3 / Swagger UI

## How to run

**Requisitos:** JDK 21

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:bolsadb`)
- Health: http://localhost:8080/actuator/health

## Tests

```bash
# Unit tests
./mvnw test

# Integration tests + build completo
./mvnw verify -Dspring.profiles.active=test
```

## Smoke test manual

Importar `postman/bolsa-de-jugadores.postman_collection.json` en Postman. La colección tiene una variable `{{token}}` que se rellena automáticamente al ejecutar **POST /auth/login**.

## Seguimiento

- **Project board (Entrega 1):** https://github.com/users/dddaavo/projects/1
- **Issues:** https://github.com/dddaavo/dapp-bolsa-de-jugadores/issues

## Desarrollo con IA

Este proyecto se desarrolla principalmente con asistentes de IA (Claude, Gemini, etc.). El archivo [`AGENTS.md`](AGENTS.md) es la fuente de verdad del proyecto para cualquier asistente: stack, arquitectura, convenciones y estado actual.

Rutinas disponibles — escribirlas tal cual en el chat del asistente:

- **`Iniciá el issue #N`** — crea la rama, propone el plan de implementación y desarrolla
- **`Cerrá el issue #N`** — actualiza el estado en `AGENTS.md` antes del merge

**Regla obligatoria:** cada cambio debe verificarse corriendo `./mvnw verify -Dspring.profiles.active=test` y, si hay comportamiento de API nuevo, probarse con la colección Postman antes de mergear.
