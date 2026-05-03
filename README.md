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
