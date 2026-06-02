# Czar Backend

**App:** Czar (Notes & Planner) | **Stack:** Java 21 · Spring Boot 3.3 · Spring Cloud GCP · PostgreSQL · Docker

## Architecture

| Service | Port | Description |
|---|---|---|
| `czar-gateway` | 8080 | Spring Cloud Gateway — single public entry point |
| `czar-auth` | 8081 | Email OTP, Phone OTP, Google/GitHub OAuth2, JWT issue/refresh |
| `czar-user` | 8082 | User profile, preferences, folders, device tokens |
| `czar-planner` | 8083 | Plan CRUD, conflict detection, Pub/Sub events |
| `czar-notes` | 8084 | Note CRUD, JSONB storage, full-text search |
| `czar-voice-ai` | 8085 | Voice transcript → Groq AI → structured plan/note items |
| `czar-conflict` | 8086 | Async conflict detection via Pub/Sub |
| `czar-notification` | 8087 | FCM push delivery via Pub/Sub |

**Shared library:** `czar-common` — JWT filter, Pub/Sub envelope, RFC 7807 error handler

## Local Development Setup

### Prerequisites
- Java 21 (Temurin)
- Maven 3.9+
- Docker + Docker Compose

### 1. Configure environment
```bash
cp .env.example .env
# Edit .env with your values (all Phase 1 defaults work out of the box)
```

### 2. Start infrastructure (PostgreSQL + Pub/Sub emulator)
```bash
docker compose up postgres pubsub-emulator
```
- PostgreSQL available at `localhost:5432` (db: `czardb`)
- Pub/Sub emulator available at `localhost:9085`

### 3. Build all modules
```bash
mvn clean install -DskipTests
```

### 4. Run a service locally (example: czar-auth)
```bash
cd czar-auth
mvn spring-boot:run
```

### 5. Run all services in Docker
```bash
docker compose --profile apps up --build
```

### Health checks
All services expose `GET /actuator/health` on their respective port.

## Build Phases

| Phase | Status | Description |
|---|---|---|
| 1 | ✅ Done | Maven scaffold, Docker Compose infra |
| 2 | ⏳ Next | Neon PostgreSQL schemas, Flyway migrations |
| 3 | — | Auth service (OTP, OAuth2, JWT) |
| 4 | — | User service (profile, folders, device tokens) |
| 5 | — | Planner service (CRUD, conflict detection) |
| 6 | — | Notes service (JSONB, full-text search) |
| 7 | — | Voice AI service (Groq integration) |
| 8 | — | Conflict + Notification services |
| 9 | — | Gateway routing, integration tests, security hardening |
| 10 | — | GCP Cloud Run deploy, CI/CD |

## Module Structure
```
czar-backend/
├── pom.xml                  ← parent/aggregator POM
├── docker-compose.yml
├── .env.example
├── docker/postgres/init.sql ← creates schemas + roles for local dev
├── czar-common/             ← shared library (no Spring Boot app)
├── czar-gateway/            ← Spring Cloud Gateway (reactive/WebFlux)
├── czar-auth/
├── czar-user/
├── czar-planner/
├── czar-notes/
├── czar-voice-ai/           ← WebFlux (reactive HTTP client for Groq)
├── czar-conflict/
└── czar-notification/
```
