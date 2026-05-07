-- V3 — performance & batching tuning
--
-- 1. Composite indexes that match the sort key of session-list endpoints, so
--    PostgreSQL serves them as a single index scan (no in-memory ORDER BY).
-- 2. Bump the chat_messages id sequence increment to match the JPA pooled
--    optimiser's allocationSize=50 — Hibernate fetches one nextval() per 50
--    inserts instead of one per row, then assigns ids in memory.

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_activity
    ON chat_sessions(user_id, last_activity_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_anon_activity
    ON chat_sessions(anonymous_visitor_id, last_activity_at DESC);

-- BIGSERIAL creates this sequence with INCREMENT 1; switch to 50 to match
-- the JPA SequenceGenerator allocationSize. Safe to run repeatedly.
ALTER SEQUENCE chat_messages_id_seq INCREMENT BY 50;

-- Partial index hot-path: the chatbot tool's "list my upcoming appointments"
-- query filters by user + booked status. A partial index on BOOKED rows is
-- markedly smaller than the full status composite and often outperforms it.
CREATE INDEX IF NOT EXISTS idx_appointments_user_booked
    ON appointments(user_id, date_time DESC)
    WHERE status = 'BOOKED';
