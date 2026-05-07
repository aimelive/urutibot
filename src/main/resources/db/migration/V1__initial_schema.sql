CREATE TABLE roles (
    id          SMALLSERIAL  PRIMARY KEY,
    name        VARCHAR(32)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE permissions (
    id          SMALLSERIAL  PRIMARY KEY,
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
    id         BIGSERIAL     PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date_time  TIMESTAMP     NOT NULL,
    purpose    VARCHAR(1000) NOT NULL,
    status     VARCHAR(32)   NOT NULL,
    created_at TIMESTAMP     NOT NULL,
    updated_at TIMESTAMP     NOT NULL
);

CREATE INDEX idx_appointments_user_datetime   ON appointments(user_id, date_time);
CREATE INDEX idx_appointments_status_datetime ON appointments(status,  date_time);


CREATE INDEX idx_appointments_user_booked
    ON appointments(user_id, date_time DESC)
    WHERE status = 'BOOKED';

ALTER SEQUENCE appointments_id_seq INCREMENT BY 50;


CREATE TABLE chat_messages (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role         VARCHAR(16)  NOT NULL,
    content      TEXT,
    payload_json JSONB,
    created_at   TIMESTAMP    NOT NULL
);


CREATE INDEX idx_chat_messages_user_id
    ON chat_messages(user_id, id);


CREATE INDEX idx_chat_messages_user_created
    ON chat_messages(user_id, created_at);

ALTER SEQUENCE chat_messages_id_seq INCREMENT BY 50;
