-- ============================================================================
-- V1: baseline schema for ChipIn
--
-- Designed to be IDEMPOTENT (every CREATE uses IF NOT EXISTS / ON CONFLICT)
-- so the migration is safe to apply against the existing populated Neon DB
-- where the schema was historically produced by Hibernate ddl-auto.
--
-- Future schema changes must NOT be added here. Add a forward-only V{n+1}__*.sql.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS chip_in_core;

SET search_path TO chip_in_core;

-- ---- currencies -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS currencies (
    currency_id  UUID PRIMARY KEY,
    code         VARCHAR(3)   NOT NULL,
    name         VARCHAR(100) NOT NULL,
    symbol       VARCHAR(10)  NOT NULL,
    groupid      UUID,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_currencies_code   ON currencies (code);
CREATE INDEX IF NOT EXISTS idx_currencies_groupid ON currencies (groupid);

-- ---- users ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    userid                          UUID PRIMARY KEY,
    name                            TEXT      NOT NULL,
    email                           TEXT      UNIQUE,
    phone                           TEXT      UNIQUE,
    profile_pic_url                 TEXT,
    password_hash                   TEXT,
    auth_provider                   TEXT,
    oauth_provider_id               TEXT,
    token_version                   INT       DEFAULT 1,
    is_registered                   BOOLEAN   NOT NULL DEFAULT FALSE,
    status                          TEXT      NOT NULL DEFAULT 'PENDING_INVITE',
    is_deleted                      BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMP NOT NULL DEFAULT now(),
    last_login_at                   TIMESTAMP,
    default_currency_id             UUID,
    invitation_token                TEXT,
    invitation_token_expiry_date    TIMESTAMP,
    version                         BIGINT    NOT NULL DEFAULT 0
);

-- ---- groups -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS groups (
    groupid              UUID PRIMARY KEY,
    name                 TEXT      NOT NULL,
    description          TEXT,
    image_url            TEXT,
    type                 TEXT,
    simplify_debt        BOOLEAN   NOT NULL DEFAULT TRUE,
    default_currency_id  UUID      NOT NULL,
    created_by           UUID      NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now(),
    is_deleted           BOOLEAN   NOT NULL DEFAULT FALSE,
    version              BIGINT    NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_groups_default_currency_id ON groups (default_currency_id);
CREATE INDEX IF NOT EXISTS idx_groups_created_by          ON groups (created_by);

-- ---- group_members ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS group_members (
    groupid    UUID      NOT NULL,
    userid     UUID      NOT NULL,
    is_admin   BOOLEAN   NOT NULL DEFAULT FALSE,
    status     TEXT      NOT NULL DEFAULT 'ACTIVE',
    joined_at  TIMESTAMP NOT NULL DEFAULT now(),
    version    BIGINT    NOT NULL DEFAULT 0,
    PRIMARY KEY (groupid, userid)
);

CREATE INDEX IF NOT EXISTS idx_group_members_userid ON group_members (userid);

-- ---- group_currencies -------------------------------------------------------
-- NOTE: origin_currency_id and is_active are added in V2. Do not add here.
CREATE TABLE IF NOT EXISTS group_currencies (
    currency_id          UUID PRIMARY KEY,
    groupid              UUID          NOT NULL,
    master_currency_id   UUID          NOT NULL,
    name                 TEXT          NOT NULL,
    exchange_rate        NUMERIC(19,6) NOT NULL DEFAULT 1,
    created_by           UUID          NOT NULL,
    created_at           TIMESTAMP     NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_group_currencies_groupid ON group_currencies (groupid);
CREATE INDEX IF NOT EXISTS idx_group_currencies_master  ON group_currencies (master_currency_id);

-- ---- expenses ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS expenses (
    expenseid         UUID PRIMARY KEY,
    groupid           UUID          NOT NULL,
    description       TEXT          NOT NULL,
    amount            NUMERIC(19,4) NOT NULL,
    currency_id       UUID          NOT NULL,
    split_type        TEXT          NOT NULL,
    receipt_img_url   TEXT,
    created_by        UUID          NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT now(),
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    type              TEXT          NOT NULL,
    category          TEXT,
    version           BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_expenses_groupid      ON expenses (groupid);
CREATE INDEX IF NOT EXISTS idx_expenses_currency_id  ON expenses (currency_id);
CREATE INDEX IF NOT EXISTS idx_expenses_created_by   ON expenses (created_by);
CREATE INDEX IF NOT EXISTS idx_expenses_type         ON expenses (type);

-- ---- expense_payers ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS expense_payers (
    payer_id     UUID PRIMARY KEY,
    expenseid    UUID          NOT NULL,
    userid       UUID          NOT NULL,
    paid_amount  NUMERIC(19,4) NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_expense_payers_expenseid ON expense_payers (expenseid);
CREATE INDEX IF NOT EXISTS idx_expense_payers_userid    ON expense_payers (userid);

-- ---- expense_splits ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS expense_splits (
    splitid       UUID PRIMARY KEY,
    expenseid     UUID          NOT NULL,
    userid        UUID          NOT NULL,
    amount_owed   NUMERIC(19,4) NOT NULL,
    raw_value     NUMERIC(19,4),
    version       BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_expense_splits_expenseid ON expense_splits (expenseid);
CREATE INDEX IF NOT EXISTS idx_expense_splits_userid    ON expense_splits (userid);
