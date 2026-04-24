-- claim_id is intentionally a free VARCHAR (no FK to claims.claim_id) because
-- audit logs are append-only and outlive their parent records by design.
CREATE TABLE claim_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    claim_id        VARCHAR(30) NOT NULL,
    previous_status VARCHAR(20),
    new_status      VARCHAR(20) NOT NULL,
    reason_codes    TEXT,
    notes           TEXT,
    changed_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    changed_by      VARCHAR(50) NOT NULL DEFAULT 'SYSTEM'
);

CREATE INDEX idx_claim_audit_log_claim_id ON claim_audit_log (claim_id);
