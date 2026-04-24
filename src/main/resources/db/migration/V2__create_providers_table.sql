CREATE TABLE providers (
    id            BIGSERIAL PRIMARY KEY,
    npi           VARCHAR(10)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    specialty     VARCHAR(100),
    is_in_network BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_providers_npi ON providers (npi);
