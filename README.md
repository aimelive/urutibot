<div align="center">

<img src="src/main/resources/static/logo.svg" alt="UrutiHub" width="280" />

# UrutiBot

**Your AI-powered assistant from UrutiHub - chat, ask, book.**

[![Java 21](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-6DB33F?style=flat&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.0.0--beta1-1a1a1a?style=flat)](https://github.com/langchain4j/langchain4j)
[![Anthropic Claude](https://img.shields.io/badge/Claude-Sonnet%204-D97757?style=flat&logo=anthropic&logoColor=white)](https://www.anthropic.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=flat&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat&logo=docker&logoColor=white)](#-docker)

<br />

<img src="src/main/resources/static/og-image.jpg" alt="UrutiBot - Chat with UrutiHub" width="780" />

</div>

---

## ✨ Overview

**UrutiBot** is a single-page chat experience and REST API that lets visitors of UrutiHub Ltd talk to a Claude-powered assistant, ask about services, and book appointments. It uses PostgreSQL with JPA/Hibernate for persistence and Spring Security with JWT-based RBAC for authentication and authorization.

The chat is server-streamed (token-by-token) for a real-time feel. Anonymous visitors can ask general questions; booking, cancelling, or listing appointments requires the user to be logged in. The product is intentionally one conversation per user - unified across tabs and devices via the JWT-derived user id (think ChatGPT's account-scoped thread). Anonymous chats are ephemeral: they live in server memory only and are discarded on page refresh or sign-in.

## 🚀 Features

- **Streaming chat** - token-by-token Server-Sent Events with a typewriter UI on the client.
- **One persistent conversation per user** - signed-in users have a single, durable thread keyed by their user id; "New chat" wipes it. The thread is the same across tabs and devices.
- **Anonymous = ephemeral** - logged-out visitors can ask general questions; the chat window is held in server memory only, discarded on refresh, and never persisted. Booking and other tool actions require login.
- **Resilient turn semantics** - if the LLM fails mid-turn (network, billing, rate limit), the partial turn is rolled back so the user's history is never left in a half-recorded state. Provider errors are surfaced as friendly user messages, not stack traces.
- **RBAC with roles + permissions** - `USER` and `ADMIN` roles, fine-grained permissions like `APPOINTMENT_READ_OWN`, `APPOINTMENT_READ_ALL`, `APPOINTMENT_UPDATE_STATUS`. Permissions are checked, not roles, so adding new roles requires no code changes.
- **JWT authentication** - long-lived bearer tokens, stateless filter chain, BCrypt password hashing.
- **Appointment management** - book, cancel, and update status via REST or the bot. Owners can cancel their own; admins can update any.
- **Email notifications** - branded HTML emails to the admin on every booked / cancelled / completed appointment.
- **Company knowledge grounding** - UrutiBot's answers are grounded in `urutihub.txt`.
- **Themed UI** - light / dark / system theme toggle, full-bleed responsive chat with mobile keyboard handling.
- **Flyway-managed schema** - clean, versioned migrations: V1 schema and V2 RBAC seed.

## 🛠 Tech stack

| Layer        | Choice                                               |
| ------------ | ---------------------------------------------------- |
| Runtime      | Java 21 (Eclipse Temurin)                            |
| Framework    | Spring Boot 3.5.4                                    |
| AI / LLM     | LangChain4j 1.0.0-beta1 + Anthropic Claude Sonnet 4  |
| Persistence  | PostgreSQL 16 + Spring Data JPA + Hibernate          |
| Migrations   | Flyway                                               |
| Security     | Spring Security + JWT (jjwt) + BCrypt                |
| Email        | Spring Mail + Thymeleaf templates                    |
| Frontend     | Vanilla HTML/CSS/JS, server-rendered Thymeleaf shell |
| Docs         | SpringDoc OpenAPI + Swagger UI                       |
| Build / Ship | Maven · Multi-stage Docker · Coolify-ready compose   |

## 📋 Prerequisites

- Java 21+
- Maven 3.6+ (or use the bundled `./mvnw`)
- A PostgreSQL 13+ instance (the bundled `docker compose` brings up Postgres 16 alongside the app)
- An Anthropic API key
- A Gmail account with an app password (for outbound notifications)

## ⚙️ Configuration

Create a `.env` file at the project root (or export the variables in your shell):

```ini
# PostgreSQL JDBC connection
DATABASE_URL=jdbc:postgresql://localhost:5432/urutibot
DATABASE_USERNAME=urutibot
DATABASE_PASSWORD=<strong-password>

# JWT - generate a 32+ char random secret. Default expiration is ~1 year (long-lived).
JWT_SECRET=<at-least-32-character-secret>
JWT_EXPIRATION_MS=31536000000

# Initial admin (seeded by AdminBootstrap on first boot if no admin exists)
# Generate the BCrypt hash beforehand, e.g.
#   htpasswd -bnBC 10 "" 'YourPassword!' | tr -d ':\n'
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD_HASH=$2a$10$.....
ADMIN_FULL_NAME=Administrator

# Anthropic
ANTHROPIC_API_KEY=sk-ant-...

# Gmail SMTP
APP_EMAIL_USERNAME=you@gmail.com
APP_EMAIL_PASSWORD=<gmail-app-password>

# CORS - comma-separated list of allowed origins
CORS_ALLOWED_ORIGINS=https://urutibot.aimelive.com,http://localhost:5173
```

> Schema is managed by Flyway. On first boot V1 creates all tables, V2 seeds
> the RBAC roles + permissions, and `AdminBootstrap` creates the admin user
> from the env vars above (idempotent - runs only when no admin exists).

## 🏃 Run locally

```bash
# clone
git clone https://github.com/aimelive/urutibot
cd urutibot

# run with Maven (loads .env if you use a shell hook or direnv)
./mvnw spring-boot:run
```

Then open <http://localhost:8080> for the chat UI and <http://localhost:8080/swagger-ui.html> for the API docs.

## 🐳 Docker

The repo ships a multi-stage Dockerfile (Maven build → Temurin JRE 21 runtime, ~250 MB image) and a Coolify-ready compose file.

### Build & run with `docker compose`

```bash
# .env at project root supplies the secrets
docker compose up -d --build
```

- The image is built **locally only** - no remote pull. Tagged as `urutibot:local`.
- Healthcheck uses bash's `/dev/tcp` redirect - no extra packages installed.
- A named `urutibot-tmp` volume holds JVM scratch space across restarts.

### One-shot `docker run` (no compose)

```bash
docker build -t urutibot:local .

docker run -d --name urutibot -p 8080:8080 \
  -e DATABASE_URL='jdbc:postgresql://host.docker.internal:5432/urutibot' \
  -e DATABASE_USERNAME='urutibot' \
  -e DATABASE_PASSWORD='<password>' \
  -e JWT_SECRET='<at-least-32-character-secret>' \
  -e ADMIN_EMAIL='admin@example.com' \
  -e ADMIN_PASSWORD_HASH='$2a$10$.....' \
  -e ANTHROPIC_API_KEY='sk-ant-...' \
  -e APP_EMAIL_USERNAME='you@gmail.com' \
  -e APP_EMAIL_PASSWORD='<app-password>' \
  -e CORS_ALLOWED_ORIGINS='https://urutibot.aimelive.com' \
  urutibot:local
```

### Override the company-knowledge file

The image bakes in `urutihub.txt`. To use your own without rebuilding:

```bash
docker run -d -p 8080:8080 \
  -v /absolute/path/urutihub.txt:/app/urutihub.txt:ro \
  -e APP_ABOUT_COMPANY_FILE='file:/app/urutihub.txt' \
  urutibot:local
```

## 🌐 API

All endpoints are documented at `/swagger-ui.html` once the app is running. Authenticated endpoints expect an `Authorization: Bearer <jwt>` header.

### Auth

| Method | Path                 | Auth   | Purpose                                         |
| ------ | -------------------- | ------ | ----------------------------------------------- |
| POST   | `/api/auth/register` | Public | Register a new user (assigned the `USER` role). |
| POST   | `/api/auth/login`    | Public | Exchange email + password for a JWT.            |
| GET    | `/api/auth/me`       | User   | Return the authenticated user.                  |

### Chatbot

| Method | Path                        | Auth     | Purpose                                                           |
| ------ | --------------------------- | -------- | ----------------------------------------------------------------- |
| POST   | `/api/chatbot/stream`       | Optional | Stream a chat response (SSE). Anonymous works without a token.    |
| GET    | `/api/chatbot/conversation` | User     | Paginated message history for the authenticated user (ascending). |
| DELETE | `/api/chatbot/conversation` | User     | Wipe the user's conversation. Used by "New chat" and on logout.   |

```http
POST /api/chatbot/stream
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer <jwt>          # optional - anonymous works too

{
  "memoryId": "8e1a4a55-3c4e-4f64-9a6c-7d3eb6c1d8ee",
  "message": "Can I book a free consultation next Tuesday afternoon?"
}
```

`memoryId` is **only** used for anonymous requests, where it scopes the in-memory chat window for that browser tab. The frontend generates it once on page load (in module memory, not `localStorage`) so refreshing starts a brand-new conversation. Authenticated requests omit `memoryId` entirely - the server keys persistence by the user id from the JWT, which means the same conversation is visible across all of a user's tabs and devices.

### Appointments

| Method | Path                            | Auth (permission)                                                       | Purpose                                          |
| ------ | ------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------ |
| POST   | `/api/appointments`             | `APPOINTMENT_CREATE`                                                    | Create a new appointment for the auth user.      |
| GET    | `/api/appointments/me`          | `APPOINTMENT_READ_OWN`                                                  | List the auth user's appointments.               |
| GET    | `/api/appointments`             | `APPOINTMENT_READ_ALL` (admin)                                          | Paginated list of all appointments.              |
| GET    | `/api/appointments/{id}`        | `APPOINTMENT_READ_ALL` or owner                                         | Look up by ID.                                   |
| PUT    | `/api/appointments/{id}/cancel` | `APPOINTMENT_UPDATE_STATUS` (admin) or `APPOINTMENT_CANCEL_OWN` + owner | Mark as cancelled.                               |
| PATCH  | `/api/appointments/{id}/status` | `APPOINTMENT_UPDATE_STATUS` (admin)                                     | Update status to BOOKED / COMPLETED / CANCELLED. |

Appointment ids are sequential `BIGSERIAL` numbers, surfaced in API responses
zero-padded to a minimum of three digits (`1` → `"001"`, `100` → `"100"`,
`1000` → `"1000"`). Path variables accept any valid base-10 form, so
`/api/appointments/001/cancel` and `/api/appointments/1/cancel` both resolve
to the same row.

### RBAC

Two roles are seeded by Flyway:

- **USER** - `APPOINTMENT_CREATE`, `APPOINTMENT_READ_OWN`, `APPOINTMENT_CANCEL_OWN`, `CHAT_HISTORY_READ_OWN`
- **ADMIN** - all of the above plus `APPOINTMENT_READ_ALL`, `APPOINTMENT_UPDATE_STATUS`, `USER_READ_ALL`, `USER_MANAGE`

Authorization checks use `@PreAuthorize("hasAuthority('PERMISSION_NAME')")`, so adding a new role is just an `INSERT` into `roles` + `role_permissions` - no code change required.

## 📁 Project layout

The codebase follows a **feature-based (vertical-slice) architecture**: each top-level package owns the full stack for one feature - controller, service, repository, model, DTOs, and feature-specific security/events. Cross-cutting concerns live in `shared/`.

```text
src/
├── main/
│   ├── java/com/aimelive/urutibot/
│   │   ├── auth/                       # Registration, login, JWT, RBAC
│   │   │   ├── AuthController.java
│   │   │   ├── AuthService.java
│   │   │   ├── CustomUserDetailsService.java
│   │   │   ├── config/                 # SecurityConfig, AdminBootstrap
│   │   │   ├── dto/                    # LoginRequest, RegisterRequest, AuthResponse, UserResponse
│   │   │   ├── model/                  # User, Role, Permission
│   │   │   ├── repository/             # UserRepository, RoleRepository, PermissionRepository
│   │   │   └── security/               # JwtService, JwtAuthenticationFilter, AppUserPrincipal, …
│   │   │
│   │   ├── chatbot/                    # Streaming chat + LangChain memory
│   │   │   ├── ChatbotController.java
│   │   │   ├── ChatbotService.java
│   │   │   ├── LlmErrorMapper.java     # Provider exceptions → friendly user messages
│   │   │   ├── dto/                    # ChatbotRequest/Response, ChatMessageResponse
│   │   │   ├── memory/                 # DurableChatMemory(Gateway), AnonChatMemoryRegistry, LangChainConfig
│   │   │   ├── model/                  # ChatMessage (one-per-user, keyed by user_id)
│   │   │   └── repository/             # ChatMessageRepository
│   │   │
│   │   ├── appointment/                # Booking, status transitions, lifecycle events
│   │   │   ├── AppointmentController.java
│   │   │   ├── AppointmentService.java
│   │   │   ├── AppointmentServiceTools.java   # @Tool methods exposed to the LLM
│   │   │   ├── dto/                    # AppointmentRequest/Response, AppointmentStatusUpdateRequest
│   │   │   ├── event/                  # AppointmentLifecycleEvent
│   │   │   ├── model/                  # Appointment
│   │   │   ├── repository/             # AppointmentRepository
│   │   │   └── security/               # AppointmentSecurity (ownership checks)
│   │   │
│   │   ├── notification/               # Outbound email side-effect (consumed by appointment)
│   │   │   ├── NotificationService.java
│   │   │   └── NotificationServiceImpl.java
│   │   │
│   │   ├── shared/                     # Cross-cutting infrastructure
│   │   │   ├── config/                 # AsyncConfig, CacheConfig, CorsConfig
│   │   │   ├── exception/              # HttpException, HttpExceptionAdvice
│   │   │   └── web/                    # PageController (Thymeleaf shell)
│   │   │
│   │   └── UrutiBotApplication.java
│   └── resources/
│       ├── application.properties
│       ├── db/migration/               # Flyway: V1 schema · V2 RBAC seed
│       ├── urutihub.txt                # Company knowledge fed to the model
│       ├── static/
│       │   ├── css/                    # Per-concern CSS partials (tokens, base, header, chat-*, modals, …)
│       │   │                           # Cascade order is owned by templates/fragments/head/stylesheets.html
│       │   ├── js/                     # api.js, auth.js, appointments.js, chat.js, main.js
│       │   └── (logo.svg, icon.svg, og-image.jpg, favicons, manifest)
│       └── templates/
│           ├── layout/
│           │   └── base.html           # Master layout: skeleton + named slots for pages
│           ├── pages/
│           │   └── home.html           # / route — composes base + chat fragments + modals
│           ├── fragments/
│           │   ├── head/               # meta, structured-data, theme-bootstrap, critical-css, stylesheets
│           │   ├── layout/             # header, footer, scripts
│           │   ├── chat/               # welcome, chat-card
│           │   └── modals/             # auth, my-appointments, create-appointment, admin-appointments
│           └── appointment-email-template.html   # Outbound email body
├── Dockerfile                          # Multi-stage build, non-root, libgomp1
├── docker-compose.yml                  # Local + Coolify deploy
└── pom.xml
```

### Frontend architecture notes

- **Thymeleaf layout pattern (no `thymeleaf-layout-dialect`):** pages call `<html th:replace="~{layout/base :: layout(...)}">` and pass content fragments as parameters. The base layout owns the skeleton (`<head>`, `<body>`, header, footer, scripts) and replaces named slots from the page. To add a new page: copy `pages/home.html`, change the slot bodies, point a controller method at `pages/<name>`.
- **CSS split into per-concern partials** under `static/css/`. Each partial is loaded via its own `<link>` tag from `templates/fragments/head/stylesheets.html` — that fragment is the **single source of truth for cascade order**. Edit any partial, refresh the browser, no build step. Critical above-the-fold tokens are still inlined via `fragments/head/critical-css.html` to prevent first-paint flash while the partials load.
- **JS load order is fixed** (`api.js` → `auth.js` → `appointments.js` → `chat.js` → `main.js`) and encapsulated in `fragments/layout/scripts.html`. All `defer` so document order = execution order.

## 📞 Support

- **Email** - info@urutihub.com
- **Web** - [urutihub.com](https://www.urutihub.com)
- **Live UrutiBot** - [urutibot.aimelive.com](https://urutibot.aimelive.com)

---

<div align="center">

Built with ☕ by **UrutiHub Ltd** - empowering businesses through innovative technology.

</div>
