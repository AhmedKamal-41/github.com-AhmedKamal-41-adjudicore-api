-- Drops redundant non-unique indexes on columns already covered by an implicit
-- unique index from their UNIQUE constraint. The duplicates were pure overhead.

DROP INDEX IF EXISTS idx_members_member_id;
DROP INDEX IF EXISTS idx_providers_npi;
DROP INDEX IF EXISTS idx_claims_claim_id;
