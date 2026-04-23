# Plan de Acción — Bolsa de Jugadores

> Proyecto UNQ — Desarrollo de Aplicaciones | 3 integrantes | 3 entregas
> Enfoque: de a poco, probando y descubriendo a medida que avanzamos.

---

## Leyenda

- [x] Completado
- [~] En progreso
- [ ] Pendiente

---

## ENTREGA 1

### Lo que ya tenemos

- [x] Proyecto Maven + Spring Boot 3.3 + Java 21
- [x] Estructura de paquetes (`auth`, `catalog`, `config`, `shared`)
- [x] H2 en memoria configurado (`application-local.yml`)
- [x] Entidades: `User`, `Player`, `Role`, `League`, `Position`
- [x] `DataInitializer`: superusuario admin + 25 jugadores (5 por liga)
- [x] JWT: `JwtService`, `JwtAuthFilter`, `SecurityConfig`
- [x] `POST /auth/register` y `POST /auth/login` → devuelven JWT
- [x] `GET /api/v1/players` → listado paginado con filtros opcionales
- [x] Swagger v3 en `/swagger-ui.html` con candado JWT
- [x] Tests unitarios: `JwtServiceTest` (4 tests), `PlayerServiceTest` (1 test)
- [x] `.github/workflows/ci.yml` → build + Sonar en push a `main`
- [x] `Dockerfile` multi-stage
- [x] `sonar-project.properties` (faltan los 2 TODOs)
- [x] `.gitignore`
- [x] Rama `entrega-1` creada

### Lo que falta

- [ ] Crear el repo en GitHub y hacer el primer push de la rama `entrega-1`
- [ ] Registrar el proyecto en SonarCloud y completar los `<TODO>` en `sonar-project.properties`
- [ ] Agregar secrets en GitHub: `SONAR_TOKEN`, `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION`
- [ ] Verificar que el CI pase en verde en GitHub
- [ ] `CODEOWNERS` con los usuarios GitHub de los 3 integrantes
- [ ] `README.md` con badge de CI, badge de Sonar y sección "How to run"
- [ ] Proteger rama `main` (requerir PR + CI verde para mergear)
- [ ] Abrir PR de `entrega-1` → `main` y mergearlo con CI verde
- [ ] Tag `v1.0.0` sobre `main` una vez mergeado

---

## ENTREGA 2

> Completar cuando la cátedra confirme los requisitos de funcionalidad.

**Core confirmado por la cátedra:**
- [ ] Build en verde
- [ ] H2 como base de datos (ya tenemos, verificar con entidades nuevas)
- [ ] Datos de prueba al arrancar la aplicación (expandir `DataInitializer`)
- [ ] Documentación completa de endpoints con Swagger v3
- [ ] Separar perfiles de test: unitarios vs integración
- [ ] Job de coverage con JaCoCo

**Funcionalidad:** a confirmar con la cátedra antes de arrancar.

---

## ENTREGA 3

> Completar cuando la cátedra confirme los requisitos de funcionalidad.

**Core confirmado por la cátedra:**
- [ ] Test de arquitectura (ArchUnit)
- [ ] Auditoría de web services: loguear `timestamp | user | operación | parámetros | tiempoDeEjecución`
- [ ] Tag en GitHub + Release Notes
- [ ] Configurar Prometheus + Spring Boot Actuator

**Funcionalidad:** a confirmar con la cátedra antes de arrancar.

---

## Registro de avance

| Fecha | Qué se hizo |
|---|---|
| 2026-04-22 | Scaffold E1: proyecto, auth, catálogo, tests, CI, rama `entrega-1` |
