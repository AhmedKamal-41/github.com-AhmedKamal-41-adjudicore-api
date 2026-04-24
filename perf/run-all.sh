#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-target/k6-reports}"
mkdir -p "$OUTPUT_DIR"

echo "Running k6 scenarios against $BASE_URL"

for scenario in perf/scenarios/*.js; do
    name=$(basename "$scenario" .js)
    echo ""
    echo "=== $name ==="
    k6 run \
        --env BASE_URL="$BASE_URL" \
        --summary-export="$OUTPUT_DIR/${name}-summary.json" \
        --out "json=$OUTPUT_DIR/${name}-raw.json" \
        "$scenario"
done

echo ""
echo "Reports in $OUTPUT_DIR"
