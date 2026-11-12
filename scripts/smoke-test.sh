#!/usr/bin/env bash
# Quick manual check of Phase 1 against a running API (docker compose up --build).
set -euo pipefail
BASE=${BASE:-http://localhost:8080}

echo "== submit"
RESP=$(curl -s -X POST "$BASE/api/v1/jobs" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"jobType":"DATA_PROCESSING","payload":{"numbers":[10,20,30,40]},"maxRetries":3,"priority":5}')
echo "$RESP"
ID=$(echo "$RESP" | sed -E 's/.*"jobId":"([^"]+)".*/\1/')

echo "== replay with same Idempotency-Key (expect same jobId, HTTP 200)"
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/v1/jobs" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"jobType":"DATA_PROCESSING","payload":{"numbers":[10,20,30,40]},"maxRetries":3,"priority":5}'

echo "== get";      curl -s "$BASE/api/v1/jobs/$ID"; echo
echo "== list";     curl -s "$BASE/api/v1/jobs?status=PENDING&size=5"; echo
echo "== attempts"; curl -s "$BASE/api/v1/jobs/$ID/attempts"; echo
echo "== cancel";   curl -s -X POST "$BASE/api/v1/jobs/$ID/cancel"; echo
echo "== swagger UI: $BASE/swagger-ui.html"
