-- V2: enable the three-hop currency resolution chain.
-- A GroupCurrency row's role (bucket vs FX rate) is now decided by whether
-- origin_currency_id == master_currency_id.

SET search_path TO chip_in_core;

ALTER TABLE group_currencies
    ADD COLUMN IF NOT EXISTS origin_currency_id UUID
        REFERENCES currencies (currency_id);

ALTER TABLE group_currencies
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill existing rows: treat them as buckets whose origin == master.
UPDATE group_currencies
SET origin_currency_id = master_currency_id
WHERE origin_currency_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_group_currencies_group_origin_master
    ON group_currencies (groupid, origin_currency_id, master_currency_id);

-- At most one ACTIVE (group, origin, master) row. Partial unique index so the
-- daily FX-refresh job can UPSERT by (group, origin, master) cleanly.
CREATE UNIQUE INDEX IF NOT EXISTS uq_group_currencies_group_origin_master
    ON group_currencies (groupid, origin_currency_id, master_currency_id)
    WHERE origin_currency_id IS NOT NULL AND is_active = TRUE;
