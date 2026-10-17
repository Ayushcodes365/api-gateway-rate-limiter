#!/usr/bin/env bash
# Fire N requests at the API and print the status code and remaining tokens.
# Usage: scripts/hammer.sh [count] [api-key]
#   scripts/hammer.sh 15                    # anonymous, limited per IP
#   scripts/hammer.sh 30 free-key-123       # free tier
#   scripts/hammer.sh 30 premium-key-456    # premium tier

COUNT="${1:-15}"
API_KEY="${2:-}"
URL="${BASE_URL:-http://localhost:8080}/api/hello"

for i in $(seq 1 "$COUNT"); do
  if [ -n "$API_KEY" ]; then
    HEADERS=$(curl -s -o /dev/null -D - -H "X-API-Key: $API_KEY" "$URL")
  else
    HEADERS=$(curl -s -o /dev/null -D - "$URL")
  fi
  STATUS=$(echo "$HEADERS" | head -1 | tr -d '\r' | awk '{print $2}')
  REMAINING=$(echo "$HEADERS" | grep -i '^x-ratelimit-remaining' | tr -d '\r' | awk '{print $2}')
  RETRY=$(echo "$HEADERS" | grep -i '^retry-after' | tr -d '\r' | awk '{print $2}')
  printf "request %-3s status=%s remaining=%s%s\n" "$i" "$STATUS" "${REMAINING:-?}" "${RETRY:+ retry-after=${RETRY}s}"
done
