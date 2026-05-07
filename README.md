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

The chat is server-streamed (token-by-token) for a real-time feel. Anonymous visitors can ask general questions; booking, cancelling, or listing appointments requires the user to be logged in. Conversations are persisted per user (or per anonymous visitor ID) so returning users can resume their chat history.

## 🚀 Features

- **Streaming chat** - token-by-token Server-Sent Events with a typewriter UI on the client.
- **Resumable chat history** - each session is stored per user (or anonymous visitor ID) in PostgreSQL; users can come back to past conversations.
- **Anonymous + authenticated chat** - general questions about the company are open to everyone; appointment actions require login.
- **RBAC with roles + permissions** - `USER` and `ADMIN` roles, fine-grained permissions like `APPOINTMENT_READ_OWN`, `APPOINTMENT_READ_ALL`, `APPOINTMENT_UPDATE_STATUS`. Permissions are checked, not roles, so adding new roles requires no code changes.
- **JWT authentication** - long-lived bearer tokens, stateless filter chain, BCrypt password hashing.
- **Appointment management** - book, cancel, and update status via REST or the bot. Owners can cancel their own; admins can update any.
- **Email notifications** - branded HTML emails to the admin on every booked / cancelled / completed appointment.
- **Company knowledge grounding** - UrutiBot's answers are grounded in `urutihub.txt`.
- **Themed UI** - light / dark / system theme toggle, full-bleed responsive chat with mobile keyboard handling.
- **Flyway-managed schema** - clean, versioned migrations including RBAC seed and bootstrap admin.

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

# JWT — generate a 32+ char random secret. Default expiration is ~1 year (long-lived).
JWT_SECRET=<at-least-32-character-secret>
JWT_EXPIRATION_MS=31536000000

# Initial admin (seeded by Flyway V3 migration on first boot)
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

> Schema is managed by Flyway. On first boot the migrations create all tables, seed the
> RBAC roles + permissions, and create the admin user from the env vars above.

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

| Method | Path                 | Auth   | Purpose                                                |
| ------ | -------------------- | ------ | ------------------------------------------------------ |
| POST   | `/api/auth/register` | Public | Register a new user (assigned the `USER` role).        |
| POST   | `/api/auth/login`    | Public | Exchange email + password for a JWT.                   |
| GET    | `/api/auth/me`       | User   | Return the authenticated user.                         |

### Chatbot

| Method | Path                                              | Auth        | Purpose                                                 |
| ------ | ------------------------------------------------- | ----------- | ------------------------------------------------------- |
| POST   | `/api/chatbot/stream`                             | Optional    | Stream a chat response (SSE). Anonymous-friendly.       |
| GET    | `/api/chatbot/sessions`                           | User        | List the authenticated user's chat sessions.            |
| GET    | `/api/chatbot/sessions/anonymous?visitorId=...`   | Public      | List anonymous sessions for a localStorage visitor ID.  |
| GET    | `/api/chatbot/sessions/{memoryId}/messages`       | User/Public | Fetch the message history of a session you own.        |

```http
POST /api/chatbot/stream
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer <jwt>          # optional — anonymous works too

{
  "memoryId": "8e1a4a55-3c4e-4f64-9a6c-7d3eb6c1d8ee",
  "anonymousVisitorId": "8e1a4a55-3c4e-4f64-9a6c-7d3eb6c1d8ee",
  "message": "Can I book a free consultation next Tuesday afternoon?"
}
```

The frontend should generate a UUID once and persist it in `localStorage`, sending it as both `memoryId` and `anonymousVisitorId`. After login, the same `memoryId` is reused so the conversation is automatically linked to the user's account.

### Appointments

| Method | Path                                | Auth (permission)                                                       | Purpose                                       |
| ------ | ----------------------------------- | ----------------------------------------------------------------------- | --------------------------------------------- |
| POST   | `/api/appointments`                 | `APPOINTMENT_CREATE`                                                    | Create a new appointment for the auth user.   |
| GET    | `/api/appointments/me`              | `APPOINTMENT_READ_OWN`                                                  | List the auth user's appointments.            |
| GET    | `/api/appointments`                 | `APPOINTMENT_READ_ALL` (admin)                                          | Paginated list of all appointments.           |
| GET    | `/api/appointments/{id}`            | `APPOINTMENT_READ_ALL` or owner                                         | Look up by ID.                                |
| PUT    | `/api/appointments/{id}/cancel`     | `APPOINTMENT_UPDATE_STATUS` (admin) or `APPOINTMENT_CANCEL_OWN` + owner | Mark as cancelled.                            |
| PATCH  | `/api/appointments/{id}/status`     | `APPOINTMENT_UPDATE_STATUS` (admin)                                     | Update status to BOOKED / COMPLETED / CANCELLED. |

### RBAC

Two roles are seeded by Flyway:

- **USER** — `APPOINTMENT_CREATE`, `APPOINTMENT_READ_OWN`, `APPOINTMENT_CANCEL_OWN`, `CHAT_SESSION_READ_OWN`
- **ADMIN** — all of the above plus `APPOINTMENT_READ_ALL`, `APPOINTMENT_UPDATE_STATUS`, `USER_READ_ALL`, `USER_MANAGE`

Authorization checks use `@PreAuthorize("hasAuthority('PERMISSION_NAME')")`, so adding a new role is just an `INSERT` into `roles` + `role_permissions` — no code change required.

## 📁 Project layout

```text
src/
├── main/
│   ├── java/com/aimelive/urutibot/
│   │   ├── config/             # CORS, security, LangChain, Swagger config
│   │   ├── controller/         # REST controllers (auth, chat, appointment) + page
│   │   ├── dto/                # Request / response DTOs (incl. dto/auth/*)
│   │   ├── exception/          # Centralized error handling
│   │   ├── model/              # JPA entities (User, Role, Permission, Appointment, ChatSession, ChatMessage)
│   │   ├── repository/         # Spring Data JPA repositories
│   │   ├── security/           # JWT service + filter, UserDetails, ownership checks
│   │   ├── service/            # Auth, chat session, appointment, email, LangChain tools
│   │   └── UrutiBotApplication.java
│   └── resources/
│       ├── application.properties
│       ├── db/migration/       # Flyway: V1 schema · V2 RBAC seed · V3 admin bootstrap
│       ├── urutihub.txt        # Company knowledge fed to the model
│       ├── static/             # logo.svg, icon.svg, og-image.jpg, css/, js/
│       └── templates/          # index.html (chat UI) + email templates
├── Dockerfile                  # Multi-stage build, non-root, libgomp1
├── docker-compose.yml          # Local + Coolify deploy
└── pom.xml
```

## 📞 Support

- **Email** - info@urutihub.com
- **Web** - [urutihub.com](https://www.urutihub.com)
- **Live UrutiBot** - [urutibot.aimelive.com](https://urutibot.aimelive.com)

---

<div align="center">

Built with ☕ by **UrutiHub Ltd** - empowering businesses through innovative technology.

</div>
