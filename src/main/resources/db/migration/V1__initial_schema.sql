-- Initial schema for UrutiBot (PostgreSQL + JPA)

CREATE TABLE roles (
    id          SMALLSERIAL PRIMARY KEY,
    name        VARCHAR(32)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE permissions (
    id          SMALLSERIAL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id       SMALLINT NOT NULL REFERENCES roles(id)       ON DELETE CASCADE,
    permission_id SMALLINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(32),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_users_email ON users(email);

CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE appointments (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date_time  TIMESTAMP    NOT NULL,
    purpose    VARCHAR(1000) NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_appointments_user_datetime    ON appointments(user_id, date_time);
CREATE INDEX idx_appointments_status_datetime  ON appointments(status,  date_time);

CREATE TABLE chat_sessions (
    id                   UUID         PRIMARY KEY,
    memory_id            VARCHAR(128) NOT NULL UNIQUE,
    user_id              UUID         REFERENCES users(id) ON DELETE SET NULL,
    anonymous_visitor_id VARCHAR(128),
    title                VARCHAR(255),
    started_at           TIMESTAMP    NOT NULL,
    last_activity_at     TIMESTAMP    NOT NULL,
    ended_at             TIMESTAMP
);

CREATE INDEX idx_chat_sessions_memory_id            ON chat_sessions(memory_id);
CREATE INDEX idx_chat_sessions_user_id              ON chat_sessions(user_id);
CREATE INDEX idx_chat_sessions_anonymous_visitor_id ON chat_sessions(anonymous_visitor_id);

CREATE TABLE chat_messages (
    id              BIGSERIAL    PRIMARY KEY,
    session_id      UUID         NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    sequence        INTEGER      NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT,
    payload_json    JSONB,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uq_chat_messages_session_sequence UNIQUE (session_id, sequence)
);

CREATE INDEX idx_chat_messages_session_sequence ON chat_messages(session_id, sequence);
