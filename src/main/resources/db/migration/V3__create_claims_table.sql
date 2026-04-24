CREATE TABLE claims (
    id              BIGSERIAL PRIMARY KEY,
    claim_id        VARCHAR(30)    NOT NULL UNIQUE,
    member_id       VARCHAR(20)    NOT NULL REFERENCES members (member_id),
    provider_npi    VARCHAR(10)    NOT NULL REFERENCES providers (npi),
    service_date    DATE           NOT NULL,
    submission_date DATE           NOT NULL DEFAULT CURRENT_DATE,
    procedure_code  VARCHAR(10)    NOT NULL,
    diagnosis_code  VARCHAR(10)    NOT NULL,
    billed_amount   NUMERIC(10, 2) NOT NULL CHECK (billed_amount > 0),
    allowed_amount  NUMERIC(10, 2),
    status          VARCHAR(20)    NOT NULL DEFAULT 'SUBMITTED'
        CHECK (status IN ('SUBMITTED', 'VALIDATED', 'REJECTED', 'APPROVED', 'DENIED')),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claims_claim_id     ON claims (claim_id);
CREATE INDEX idx_claims_member_id    ON claims (member_id);
CREATE INDEX idx_claims_status       ON claims (status);
CREATE INDEX idx_claims_service_date ON claims (service_date);
