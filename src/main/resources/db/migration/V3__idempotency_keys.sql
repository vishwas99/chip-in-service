-- V3: idempotency cache for retried money-moving POSTs.
-- See API_CONTRACT.md §1.1 for the contract.

SET search_path TO chip_in_core;

CREATE TABLE IF NOT EXISTS idempotency_keys (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    endpoint          VARCHAR(256) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    response_status   INT          NOT NULL,
    response_body     TEXT,
    response_type     VARCHAR(256),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);

-- Used by the scheduled cleanup job to drop expired rows.
CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at
    ON idempotency_keys (expires_at);
