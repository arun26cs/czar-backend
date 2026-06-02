# CZAR Backend — Build Phase Plan
**App:** Czar (Notes & Planner) | **Stack:** Java 21 · Spring Boot 3.3.5 · Maven multi-module · 8 microservices · local Docker dev | **Version:** 1.0

---

## Phase 1 — Project Scaffold & Monorepo Setup ✅ COMPLETED

**Goal:** Create the base repository structure for all 8 microservices.

### Steps
1. Create a Maven multi-module project root (`czar-backend/pom.xml`) with modules for each service.
2. Create 7 sub-modules:
   - `czar-gateway`
   - `czar-auth`
   - `czar-user`
   - `czar-planner`
   - `czar-notes`
   - `czar-voice-ai`
   - `czar-conflict`
   - `czar-notification`
3. Add shared parent POM with Spring Boot 3.x BOM, Java 21, and common dependencies:
   - `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`
   - `spring-boot-starter-actuator`, `spring-boot-starter-validation`
   - `nimbus-jose-jwt`, `spring-cloud-gcp-pubsub`
4. Add a shared `czar-common` library module for:
   - JWT validation filter (shared across all services)
   - Pub/Sub message envelope POJO
   - RFC 7807 Problem Detail error response handler
5. Configure Spring Boot Actuator `/actuator/health` endpoint on all services.
6. Add `.gitignore`, `README.md`, and initialize GitHub repository.
7. Set up Docker base image (`eclipse-temurin:21-jre-alpine`) — one `Dockerfile` per service.

### Output
- Working Maven multi-module build (`mvn clean install` passes for all modules)
- All services start on their designated ports (8080–8087)
- `czar-common` module provides JWT filter, Pub/Sub envelope, Problem Detail handler
- 9 modules total: czar-common + czar-gateway + 7 service modules
- All Docker images build and run via `docker compose --profile apps`

---

## Phase 2 — Database Setup (Local PostgreSQL + Flyway Migrations) ✅ COMPLETED

**Goal:** Local PostgreSQL via Docker with all 3 schemas and tables auto-migrated by Flyway on service startup.

### Steps
1. Run `docker compose up -d postgres` to start local PostgreSQL 16.
2. Create 3 schemas: `auth`, `users`, `planner` (handled by Flyway migrations).
3. Create 3 PostgreSQL roles with least-privilege access:
   - `czar_auth_role` → access to `auth` schema only
   - `czar_user_role` → access to `users` schema only
   - `czar_planner_role` → access to `planner` schema only
4. Add Flyway to each service's `pom.xml`. Set `spring.flyway.schemas` per service.
5. Write Flyway migration scripts (`V1__init.sql`) for each schema:

   **auth schema:**
   - `auth.users_auth` — id, email, phone, email_verified, phone_verified, created_at, last_login_at
   - `auth.otp_requests` — id, identifier, otp_hash, expires_at, used, created_at, ip_address
   - `auth.refresh_tokens` — id, user_id, token_hash, expires_at, revoked, created_at, device_hint
   - `auth.oauth_connections` — id, user_id, provider, provider_user_id, provider_email, access_token, connected_at

   **users schema:**
   - `users.users_profile` — id, display_name, avatar_url, created_at, updated_at
   - `users.preferences` — id, user_id, theme, dashboard_collapsed, default_view, reminder_minutes, updated_at
   - `users.tags` — id, user_id, name, color_hex, created_at *(replaces folders — no hierarchy, no is_default)*
   - `users.device_tokens` — id, user_id, fcm_token, platform, created_at, updated_at
   - `users.notes` — id, user_id, title, body (JSONB), pinned, search_vector (TSVECTOR), created_at, updated_at, deleted_at *(no folder_id)*
   - `users.note_tags` — note_id, tag_id *(junction table — one note can have many tags)*

   **planner schema:**
   - `planner.plans` — id, user_id, title, plan_type, scheduled_date, hour, minute, duration_minutes, status, confirmed, ai_generated, reminder_sent, created_at, updated_at, deleted_at *(no folder_id)*
   - `planner.plan_tags` — plan_id, tag_id *(junction table — one plan can have many tags)*
   - `planner.conflict_log` — id, user_id, plan_a_id, plan_b_id, detected_at, resolved_at

6. Add GIN index on `users.notes(search_vector)` and tsvector trigger for full-text search.
7. Add all indexes: `notes(user_id)`, `plans(user_id)`, `plans(scheduled_date)`, `otp_requests(identifier)`, `note_tags(note_id)`, `note_tags(tag_id)`, `plan_tags(plan_id)`, `plan_tags(tag_id)`, `tags(user_id)`.
8. Add UNIQUE constraint on `tags(user_id, name)` — no duplicate tag names per user.
9. Validate all tables created on local PostgreSQL startup.

### Output
- All 3 schemas fully migrated in local PostgreSQL on service startup
- Flyway migrations run automatically (`spring.flyway.enabled=true`)
- Schemas: `auth` (czar-auth), `users` (czar-user), `planner` (czar-planner)
- No `folders` table anywhere — tag-based from day one
- `note_tags` and `plan_tags` junction tables enable many-to-many tagging

---

## Phase 3 — Auth Service (`czar-auth`) ✅ COMPLETED

**Goal:** Implement all authentication flows — Email OTP, Google OAuth2, GitHub OAuth2, JWT issue/refresh/revoke.

### Implementation Details
- **RSA-2048 key pair** generated via `java scripts/GenKeys.java` — stored at `czar-auth/src/main/resources/keys/` (gitignored via `*.pem`)
- **JWT:** RS256 access tokens (15 min) + BCrypt-hashed refresh tokens (30 days) stored in `auth.refresh_tokens`
- **Email OTP:** SendGrid SDK — falls back to console `[DEV MODE]` log when `SENDGRID_API_KEY` is blank
- **Phone OTP:** Stubbed for local dev (always returns success; no Twilio account required)
- **Google OAuth2:** Real Google Cloud Console app — redirect URI: `http://localhost:8081/login/oauth2/code/google`
- **GitHub OAuth2:** Real GitHub OAuth App — callback URL: `http://localhost:8081/login/oauth2/code/github`
- **Spring Security OAuth2 Client:** handles PKCE + code exchange automatically via `oauth2Login()`
- **Pub/Sub:** `user.created` published to `czar-user-events` via local emulator; `Optional<PubSubTemplate>` injection so tests pass without GCP

### Steps
1. **JWT Setup:**
   - Generate RSA-2048 key pair. Store private key in GCP Secret Manager (`czar-auth-private-key`). Store public key accessible at `GET /auth/.well-known/jwks.json`.
   - Implement JWT issue (access: 15 min, RS256) and refresh token (30 days, stored hashed in `auth.refresh_tokens`).
   - Implement `POST /auth/token/refresh` and `POST /auth/logout`.

2. **Email OTP:**
   - `POST /auth/email/request-otp` — generate 6-digit OTP, BCrypt hash it, store in `auth.otp_requests` with 10-min expiry, send via SendGrid SDK.
   - `POST /auth/email/verify-otp` — verify hash, create user in `auth.users_auth` if first login, return JWT pair.
   - Rate limit: 5 requests per 15 min per IP.

3. **Phone OTP (Twilio Verify):**
   - `POST /auth/phone/request-otp` — call Twilio Verify `start` with E.164 phone number.
   - `POST /auth/phone/verify-otp` — call Twilio Verify `check`. On success, create/fetch user, return JWT pair.
   - Rate limit: 3 per phone per hour.

4. **Google OAuth2:**
   - Configure Spring Security OAuth2 Client with Google credentials (stored in Secret Manager).
   - `GET /auth/oauth/google` → redirect to consent screen.
   - `GET /auth/oauth/google/callback` → exchange code, verify ID token, create/fetch user, return JWT.

5. **GitHub OAuth2:**
   - Configure Spring Security OAuth2 Client with GitHub credentials.
   - `GET /auth/oauth/github` → redirect to GitHub authorize.
   - `GET /auth/oauth/github/callback` → exchange code, call `/user` + `/user/emails` APIs, resolve verified email, create/fetch user, return JWT.

6. **Pub/Sub publish:** On first user registration, publish `user.created` event to `czar-user-events` topic.

7. **Security hardening:**
   - All OTPs hashed with BCrypt strength 10. Never stored in plaintext.
   - OAuth client secrets loaded from Secret Manager — never from env vars or code.
   - Input validated with Jakarta Bean Validation on all endpoints.

### Output
- All auth endpoints functional and verified live:
  - `GET  /auth/.well-known/jwks.json` → RS256 public key (KID: czar-auth-v1)
  - `POST /auth/email/request-otp` → OTP sent (or logged to console in dev)
  - `POST /auth/email/verify-otp` → JWT pair on success
  - `POST /auth/token/refresh` → new access token
  - `POST /auth/token/logout` → revokes refresh token
  - `GET  /oauth2/authorization/google` → Google consent redirect
  - `GET  /oauth2/authorization/github` → GitHub consent redirect
- JWT issued and verifiable by JWKS endpoint
- Service runs on port 8081 via Docker

### Prerequisites before testing OAuth2 flows
1. Copy `.env.example` → `.env` and fill in `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
2. Google Cloud Console → OAuth 2.0 Client → add redirect URI: `http://localhost:8081/login/oauth2/code/google`
3. GitHub Developer Settings → OAuth App → callback URL: `http://localhost:8081/login/oauth2/code/github`

---

## Phase 4 — User Service (`czar-user`)

**Goal:** Implement user profile, preferences, tag management, and device token registration. No folders — tags only.

### Steps
1. **Pub/Sub subscribe:** Listen to `czar-user-events` for `user.created` → create `users_profile` row + seed default tags (`Personal`, `Work`, `Health`, `Travel`) in `users.tags`.
2. **Profile endpoints:**
   - `GET /api/v1/users/me` — return user profile from `users.users_profile`
   - `PATCH /api/v1/users/me` — update display_name, avatar_url
3. **Preferences endpoints:**
   - `GET /api/v1/users/me/preferences` — return preferences row
   - `PATCH /api/v1/users/me/preferences` — update theme, reminder_minutes, default_view, etc.
4. **Tag endpoints:**
   - `GET /api/v1/users/me/tags` — list all tags for the user (id, name, color_hex, note_count, plan_count)
   - `POST /api/v1/users/me/tags` — create tag (name, colorHex); enforce UNIQUE per user
   - `PATCH /api/v1/users/me/tags/{id}` — rename or recolor a tag
   - `DELETE /api/v1/users/me/tags/{id}` — delete tag; removes all `note_tags` and `plan_tags` rows referencing it
5. **Device token endpoints:**
   - `POST /api/v1/users/me/device-token` — upsert FCM token (platform: android/ios)
   - `DELETE /api/v1/users/me/device-token` — remove token on logout
6. Add JWT validation filter (from `czar-common`) to all endpoints.
7. Return `409 Conflict` if tag name already exists for that user on create.

### Output
- User profile and tag management fully functional
- Default tags (`Personal`, `Work`, `Health`, `Travel`) seeded automatically on new user registration via Pub/Sub
- No folder logic anywhere in this service

---

## Phase 5 — Planner Service (`czar-planner`)

**Goal:** Implement full plan CRUD, tag assignment, conflict detection, status management, and Pub/Sub publishing. No folder references.

### Steps
1. **Plan CRUD endpoints:**
   - `GET /api/v1/plans` — list with filters: `date`, `tags[]` (AND match), `status`, pagination
   - `POST /api/v1/plans` — create plan with optional `tagIds[]`; run conflict check before saving
   - `GET /api/v1/plans/{id}` — get single plan including its tags
   - `PATCH /api/v1/plans/{id}` — partial update; re-run conflict check
   - `DELETE /api/v1/plans/{id}` — soft delete (set `deleted_at`); also removes `plan_tags` rows
   - `PATCH /api/v1/plans/{id}/status` — update to `done | missed | upcoming`
   - `PATCH /api/v1/plans/{id}/confirm` — confirm AI-suggested plan
2. **Tag assignment endpoints:**
   - `PUT /api/v1/plans/{id}/tags` — replace full tag set for a plan (`{ tagIds[] }`)
   - `POST /api/v1/plans/bulk-tag` — bulk assign tags: `{ planIds[], tagIds[] }` — adds tags without removing existing ones
3. **Conflict detection logic** (run on every POST/PATCH):
   - Query all non-deleted plans for same `userId` + `scheduledDate`
   - Check overlap: `A.start < B.end AND B.start < A.end` where `end = start + durationMinutes`
   - Conflicts do NOT block save — flag with warning in response
   - Insert conflict pairs into `planner.conflict_log`
4. **Conflict query endpoints:**
   - `GET /api/v1/plans/conflicts?date=YYYY-MM-DD` — return conflicting plan pairs
   - `GET /api/v1/plans/stats?date=YYYY-MM-DD` — done, missed, upcoming, conflict counts
5. **Pub/Sub publish:** After every create/update/delete, publish to `czar-plan-events` using the standard message envelope.
6. Add JWT validation filter and userId extraction from JWT claims on all endpoints.

### Output
- Full plan lifecycle with tag-based filtering and bulk tagging
- Automatic conflict detection unaffected by tag model
- Events publishing to Pub/Sub on every mutation
- No folder_id on any plan — zero folder dependency

---

## Phase 6 — Notes Service (`czar-notes`)

**Goal:** Implement note CRUD with JSONB body storage, tag assignment, pin/unpin, full-text search, bulk tagging, and Pub/Sub events. No folder references.

### Steps
1. **Note CRUD endpoints:**
   - `GET /api/v1/notes` — list notes with filters: `tags[]` (AND match), `pinned`, `untagged` (bool), pagination, sort (`recent|modified`)
   - `POST /api/v1/notes` — create note with optional `tagIds[]`; body stored as JSONB `{ text, sections, wordCount, voiceTranscript }`
   - `GET /api/v1/notes/{id}` — get single note including its tags
   - `PATCH /api/v1/notes/{id}` — partial update (title, body, pinned) — no folderId field
   - `DELETE /api/v1/notes/{id}` — soft delete (set `deleted_at`); also removes `note_tags` rows
   - `PATCH /api/v1/notes/{id}/pin` — toggle pinned flag
2. **Tag assignment endpoints:**
   - `PUT /api/v1/notes/{id}/tags` — replace full tag set for a note (`{ tagIds[] }`)
   - `POST /api/v1/notes/bulk-tag` — bulk assign tags: `{ noteIds[], tagIds[] }` — adds tags without removing existing ones; enables inline `+` tag pill and checkbox multi-select bulk tagging from list view
3. **Full-text search:**
   - `GET /api/v1/notes/search?q=keyword&tags[]=czar&tags[]=backend` — combines tsvector keyword search with tag AND filter
   - `search_vector` column updated automatically by DB trigger on title/body.text change
4. **Stats endpoint:**
   - `GET /api/v1/notes/stats` — count by tag, pinned count, untagged count, last edited timestamp
5. **Pub/Sub publish:** Publish `note.created`, `note.updated`, `note.deleted` to `czar-note-events` on mutations.
6. Map `users` schema JPA entities including `note_tags` junction; notes-service connects with `czar_user_role`.
7. Add JWT validation filter; always scope queries to `userId` from JWT claims.

### Output
- Full note lifecycle with JSONB storage, tsvector search, and tag-based filtering
- Bulk tag endpoint supports inline `+` pill tagging and checkbox multi-select from list view
- `untagged` filter enables cleanup of notes with no tags
- All note events published to Pub/Sub
- No folder_id on any note — zero folder dependency

---

## Phase 7 — Voice AI Service (`czar-voice-ai`)

**Goal:** Receive mobile transcript, call Groq API, parse intent into structured plan/note items, publish result.

### Steps
1. **Groq API client:**
   - Use Spring WebClient (reactive) to call Groq REST API.
   - Model: `llama-3.1-8b-instant`. Load API key from GCP Secret Manager (`czar-groq-api-key`).
   - Build structured system prompt instructing Groq to return a JSON array of items with fields: `type`, `title`, `scheduledDate`, `hour`, `minute`, `durationMinutes`, `folderName`, `planType`.
2. **Parse endpoint:**
   - `POST /api/v1/voice/parse` — accepts `{ transcript, context: { date, existingFolders[] } }`
   - Call Groq API, parse response JSON array
   - Map `folderName` (AI-assigned) to actual folder IDs from `context.existingFolders`
   - Return `{ jobId, items[] }` immediately to the app
   - Publish result to `czar-ai-results` Pub/Sub topic with `userId` and `jobId`
3. **Async result endpoint:**
   - `GET /api/v1/voice/result/{jobId}` — poll for async result (for slow parses)
4. **Multi-item and note detection:**
   - Handle multiple items in one utterance (Groq returns array)
   - Detect note type: `"note: ..."` → `{ type: note, title, body }`
   - AI folder auto-assignment: gym/run/workout → Health, meeting/standup/call → Work
5. **Rate limit protection:**
   - Respect Groq free tier: 14,400 req/day. Gateway enforces 60 req/min per userId.
   - Return HTTP 503 with `Retry-After` header on Groq 429 responses.
6. Service is stateless — no database connection required.

### Output
- Voice transcript parsed to structured items end-to-end
- Results published to Pub/Sub and returned synchronously to the app

---

## Phase 8 — Conflict Service & Notification Service

**Goal:** Complete async event-driven pipeline — conflict detection via Pub/Sub and FCM push delivery.

### Steps

#### Conflict Service (`czar-conflict`)
1. Subscribe to `czar-plan-events` topic (GCP Pub/Sub pull subscription).
2. On `plan.created` or `plan.updated` event:
   - Call `GET /api/v1/plans?userId={id}&date={date}` on `czar-planner` (service-to-service HTTPS using internal service account JWT).
   - Run O(n²) overlap check in Java memory.
   - If conflicts found, publish to `czar-notifications` topic: `{ userId, type: conflict_alert, planIds: [], message: "..." }`.
3. Service is fully stateless — no database.

#### Notification Service (`czar-notification`)
1. Subscribe to `czar-notifications` topic.
2. On any notification event:
   - Call `GET /api/v1/users/me/device-token` on `czar-user` to fetch FCM token for the userId.
   - Send push via Firebase Admin SDK based on notification type:
     - `plan_reminder` — X minutes before scheduled plan (default 10 min per user preference)
     - `conflict_alert` — immediate on conflict detection
     - `missed_plan` — triggered when plan time passes with status = upcoming
     - `ai_result_ready` — when Groq parse completes; carries jobId
     - `note_saved` — optional confirmation for voice-dictated notes
3. **Reminder scheduling:**
   - Set up GCP Cloud Scheduler job to call `POST /internal/reminders/check` every minute.
   - Endpoint queries `czar-planner` for plans within next 10 min with `status=upcoming` and `reminder_sent=false`.
   - Dispatch FCM push and call `PATCH /api/v1/plans/{id}` to set `reminder_sent=true`.
4. Handle stale FCM tokens: on FCM `UNREGISTERED` error, delete token from `users.device_tokens` via call to `czar-user`.
5. Both services are stateless — no database connections.

### Output
- Full async pipeline: plan saved → Pub/Sub → conflict checked → push sent to device
- Reminder scheduler operational via Cloud Scheduler

---

## Phase 9 — API Gateway, Integration Testing & Security Hardening

**Goal:** Wire all services behind Spring Cloud Gateway, validate end-to-end flows, and harden security.

### Steps

#### API Gateway (`czar-gateway`)
1. Set up Spring Cloud Gateway 4.x with routing table:
   - `/auth/**` → `czar-auth` (public, no JWT)
   - `/api/v1/users/**` → `czar-user` (JWT required)
   - `/api/v1/plans/**` → `czar-planner` (JWT required)
   - `/api/v1/notes/**` → `czar-notes` (JWT required)
   - `/api/v1/voice/**` → `czar-voice-ai` (JWT required)
   - `/api/v1/notifications/**` → `czar-notification` (JWT required)
2. Add JWT validation filter (from `czar-common`): fetch JWKS from `czar-auth` on startup, cache public key, validate RS256 signature on every protected route.
3. Add rate limiting filters (in-memory bucket):
   - Global: 200 req/min per IP
   - Auth endpoints: 10 req/min per IP
   - Voice endpoint: 60 req/min per userId
4. Configure CORS: allow only `prodczar.com` origins.
5. Add request logging filter.

#### Integration Testing
6. Write integration tests using JUnit 5 + Testcontainers:
   - Spin up PostgreSQL container; run Flyway migrations
   - Test full auth flow: email OTP request → verify → JWT → refresh → logout
   - Test plan creation → conflict detection → Pub/Sub event
   - Test note create → full-text search
   - Test voice parse → Groq mock → result published
7. Write service-to-service tests: gateway routing, JWT propagation, internal service account calls.
8. Validate RFC 7807 Problem Detail error responses on all 4xx/5xx scenarios.

#### Security Hardening
9. Confirm all secrets (private key, API keys, DB credentials) loaded from GCP Secret Manager — never from env vars or code.
10. Verify BCrypt on OTPs; confirm refresh tokens stored as SHA-256 hashes.
11. Validate all request bodies with Jakarta Bean Validation annotations.
12. Confirm all JPA queries use parameterised Spring Data methods (no raw SQL string concatenation).

### Output
- All 7 services routable through the gateway
- Full integration test suite passing
- All security requirements verified

---

## Phase 10 — GCP Deployment, CI/CD & Smoke Testing

**Goal:** Deploy all services to GCP Cloud Run, configure CI/CD pipeline, and run production smoke tests.

### Steps

#### GCP Infrastructure Setup
1. Create 3 GCP projects: `czar-dev`, `czar-staging`, `czar-prod`.
2. Enable APIs: Cloud Run, Cloud Build, Artifact Registry, Pub/Sub, Secret Manager, Cloud Scheduler, GCP IAM.
3. Create GCP Pub/Sub topics: `czar-user-events`, `czar-plan-events`, `czar-note-events`, `czar-ai-results`, `czar-notifications`. Create pull subscriptions for each consuming service.
4. Store all secrets in GCP Secret Manager per project:
   - `czar-auth-private-key` (RSA private key)
   - `czar-db-auth-url`, `czar-db-user-url`, `czar-db-planner-url`
   - `czar-sendgrid-api-key`, `czar-twilio-account-sid`, `czar-twilio-auth-token`
   - `czar-google-client-id`, `czar-google-client-secret`
   - `czar-github-client-id`, `czar-github-client-secret`
   - `czar-groq-api-key`
   - Firebase Admin SDK JSON
5. Create one GCP Service Account per service with least-privilege IAM roles (Pub/Sub publisher/subscriber, Secret Manager accessor, Cloud Run invoker).

#### Cloud Run Deployment (per service)
6. Build Docker images and push to GCP Artifact Registry.
7. Deploy all 8 services to Cloud Run with configuration:

   | Service | Port | Memory | Min Instances | Max Instances |
   |---|---|---|---|---|
   | czar-gateway | 8080 | 512MB | 0 | 10 |
   | czar-auth | 8081 | 512MB | 0 | 5 |
   | czar-user | 8082 | 512MB | 0 | 5 |
   | czar-planner | 8083 | 512MB | 0 | 10 |
   | czar-notes | 8084 | 512MB | 0 | 5 |
   | czar-voice-ai | 8085 | 512MB | 0 | 10 |
   | czar-conflict | 8086 | 256MB | 0 | 5 |
   | czar-notification | 8087 | 256MB | 0 | 5 |

8. Set `czar-gateway` as the **only public** Cloud Run service. All others set to private (Cloud Run IAM authentication).
9. Set timeout: 60s for all services; 120s for `czar-voice-ai`.
10. Configure startup probe: `GET /actuator/health`.
11. Point custom domain `api.prodczar.com` → `czar-gateway` Cloud Run URL.

#### CI/CD Pipeline (GCP Cloud Build)
12. Create `cloudbuild.yaml` at repo root with steps:
    - **Step 1:** `mvn test` — run all unit and integration tests
    - **Step 2:** `docker build` — build image for changed service
    - **Step 3:** Push image to Artifact Registry with commit SHA tag
    - **Step 4:** `gcloud run deploy` — deploy to Cloud Run
    - **Step 5:** Smoke test — `curl GET /actuator/health` returns 200
13. Trigger: push to `main` branch or release tag on GitHub.
14. Add **manual approval gate** for production deploys in Cloud Build.

#### Smoke & End-to-End Testing on GCP
15. Run post-deploy smoke tests against staging:
    - `GET https://api.prodczar.com/health` → 200
    - `POST /auth/email/request-otp` → OTP sent
    - `POST /auth/email/verify-otp` → JWT returned
    - `POST /api/v1/plans` (with JWT) → plan created
    - `POST /api/v1/notes` (with JWT) → note created
    - `POST /api/v1/voice/parse` (with JWT) → items returned
    - Verify Pub/Sub messages flowing (check Cloud Console)
    - Verify FCM push received on test device
16. Verify all services scale to 0 when idle (min instances = 0).
17. Promote from `czar-staging` to `czar-prod` after manual approval.

### Output
- All 8 services live on GCP Cloud Run
- CI/CD pipeline auto-deploys on every push to main
- Full smoke test suite passes on staging and production

---

## Summary

| Phase | Focus | Services Involved |
|---|---|---|
| 1 | Project scaffold & Maven multi-module setup | All |
| 2 | Database schema (tag-based), Flyway migrations, indexes | local PostgreSQL |
| 3 | Auth flows — OTP, OAuth2, JWT | czar-auth |
| 4 | User profile, preferences, tag management, device tokens | czar-user |
| 5 | Plan CRUD, tag assignment, bulk-tag, conflict detection | czar-planner |
| 6 | Note CRUD, JSONB, tag assignment, bulk-tag, full-text search | czar-notes |
| 7 | Voice transcript → Groq AI → structured items | czar-voice-ai |
| 8 | Async event pipeline, conflict alerts, FCM push | czar-conflict, czar-notification |
| 9 | API Gateway routing, integration tests, security | czar-gateway + all |
| 10 | GCP Cloud Run deploy, CI/CD, smoke tests | All + GCP infrastructure |
