CREATE TABLE members (
    id                BIGSERIAL PRIMARY KEY,
    member_id         VARCHAR(20)  NOT NULL UNIQUE,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    date_of_birth     DATE         NOT NULL,
    plan_code         VARCHAR(20)  NOT NULL,
    eligibility_start DATE         NOT NULL,
    eligibility_end   DATE,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_members_member_id ON members (member_id);
