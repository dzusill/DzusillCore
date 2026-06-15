-- DzusillCore example schema (PostgreSQL dialect).
-- Replace with your plugin's tables. Statements are separated by semicolons.

CREATE TABLE IF NOT EXISTS core_players (
    uuid       VARCHAR(36) NOT NULL,
    name       VARCHAR(16) NOT NULL,
    coins      BIGINT      NOT NULL DEFAULT 0,
    last_seen  BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid)
);
