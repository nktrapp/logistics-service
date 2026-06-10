#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/../lib/common.sh"

scenario_header "00-baseline" "Pré-condições do stack" "PRE"

# ---------- liveness ----------
wait_healthy 60 || die "liveness probes did not return 200 on both services"
pass PRE "liveness 200 on package-service ($PKG_HOST) and logistics-service ($LOG_HOST)"

# ---------- all 6 queues resolve ----------
for q in pkg log hub pkg-dlq log-dlq hub-dlq; do
  url="$(queue_url "$q")" || url=""
  if [ -n "$url" ]; then
    pass PRE "queue url resolved: $q -> $url"
  else
    fail PRE "queue url did not resolve for: $q"
  fi
done

# ---------- redrive policy on the main package queue ----------
redrive="$(sqs_attr pkg RedrivePolicy)" || redrive=""
if [ -z "$redrive" ]; then
  skip PRE "RedrivePolicy attribute not reported by emulator"
else
  assert_contains PRE "pkg RedrivePolicy" "maxReceiveCount" "$redrive"
  assert_contains PRE "pkg RedrivePolicy" "3" "$redrive"
fi

scenario_footer
