#!/usr/bin/env bash
# Load a mix of synthetic claims to demonstrate every adjudication outcome.
# Requires: curl, jq. Target API must be running at BASE_URL.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "═══════════════════════════════════════════════════════════"
echo "AdjudiCore synthetic data loader"
echo "Target: $BASE_URL"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Realistic values drawn from V5 seed + fee schedule
MEMBERS=("M001" "M002" "M003" "M004")
NPI_IN=("1234567890" "2345678901" "3456789012")
NPI_OUT=("4567890123" "5678901234")
CPT_COMMON=("99213" "99214" "99215" "99203" "80050" "36415" "93000" "71046" "85025")
CPT_PRIOR_AUTH=("70553" "27447" "43644")
ICD=("J45.909" "I10" "E11.9" "M54.5" "R51")

submit() {
    local member="$1" npi="$2" cpt="$3" billed="$4"
    local service_date="${5:-$(date +%Y-%m-%d)}"
    local icd="${ICD[RANDOM % ${#ICD[@]}]}"
    local payload
    payload=$(cat <<EOF
{
  "memberId": "$member",
  "providerNpi": "$npi",
  "serviceDate": "$service_date",
  "procedureCode": "$cpt",
  "diagnosisCode": "$icd",
  "billedAmount": $billed
}
EOF
    )
    curl -sS -X POST "$BASE_URL/api/v1/claims" \
        -H "Content-Type: application/json" -d "$payload" | jq -r '.claimId'
}

lifecycle() {
    local cid="$1"
    curl -sS -X POST "$BASE_URL/api/v1/claims/$cid/validate" >/dev/null || true
    curl -sS -X POST "$BASE_URL/api/v1/claims/$cid/adjudicate" >/dev/null || true
}

COUNT=0

echo "── Clean approvals (30 claims) ──────────────────"
for i in $(seq 1 30); do
    m="${MEMBERS[RANDOM % ${#MEMBERS[@]}]}"
    n="${NPI_IN[RANDOM % ${#NPI_IN[@]}]}"
    c="${CPT_COMMON[RANDOM % ${#CPT_COMMON[@]}]}"
    b="$((100 + RANDOM % 300)).00"
    cid=$(submit "$m" "$n" "$c" "$b")
    lifecycle "$cid"
    COUNT=$((COUNT + 1))
    printf "  [%02d/50] clean: %s\n" "$COUNT" "$cid"
done

echo ""
echo "── Duplicate denials (5 claims, all for M001) ───"
for i in $(seq 1 5); do
    cid=$(submit "M001" "1234567890" "99213" "150.00")
    lifecycle "$cid"
    COUNT=$((COUNT + 1))
    printf "  [%02d/50] duplicate: %s\n" "$COUNT" "$cid"
done

echo ""
echo "── Prior-auth pends (3 claims) ──────────────────"
for i in $(seq 1 3); do
    c="${CPT_PRIOR_AUTH[RANDOM % ${#CPT_PRIOR_AUTH[@]}]}"
    cid=$(submit "M002" "2345678901" "$c" "2500.00")
    lifecycle "$cid"
    COUNT=$((COUNT + 1))
    printf "  [%02d/50] prior-auth: %s (CPT %s)\n" "$COUNT" "$cid" "$c"
done

echo ""
echo "── Expired-member rejects (5 claims, M005) ──────"
for i in $(seq 1 5); do
    c="${CPT_COMMON[RANDOM % ${#CPT_COMMON[@]}]}"
    cid=$(submit "M005" "1234567890" "$c" "150.00")
    lifecycle "$cid"
    COUNT=$((COUNT + 1))
    printf "  [%02d/50] expired-member: %s\n" "$COUNT" "$cid"
done

echo ""
echo "── OON + strict-plan rejects (4 claims) ─────────"
for i in $(seq 1 4); do
    m=$([ $((RANDOM % 2)) -eq 0 ] && echo "M002" || echo "M004")
    n="${NPI_OUT[RANDOM % ${#NPI_OUT[@]}]}"
    c="${CPT_COMMON[RANDOM % ${#CPT_COMMON[@]}]}"
    cid=$(submit "$m" "$n" "$c" "200.00")
    lifecycle "$cid"
    COUNT=$((COUNT + 1))
    printf "  [%02d/50] oon-strict: %s\n" "$COUNT" "$cid"
done

echo ""
echo "── Coverage-limit denials (3 claims on EPO_BRONZE) ─"
for i in $(seq 1 3); do
    cid=$(submit "M004" "1234567890" "99215" "500.00")
    lifecycle "$cid"
    COUNT=$((COUNT + 1))
    printf "  [%02d/50] accumulation: %s\n" "$COUNT" "$cid"
done

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "Done. Loaded $COUNT claims."
echo ""
echo "Check distribution:"
echo "  docker exec claimguard-postgres psql -U claimguard -d claimguard \\"
echo "    -c \"SELECT status, COUNT(*) FROM claims GROUP BY status ORDER BY COUNT(*) DESC;\""
echo ""
echo "Or query audit log:"
echo "  docker exec claimguard-postgres psql -U claimguard -d claimguard \\"
echo "    -c \"SELECT reason_codes, COUNT(*) FROM claim_audit_log"
echo "         WHERE reason_codes IS NOT NULL GROUP BY reason_codes;\""
echo "═══════════════════════════════════════════════════════════"
