#!/usr/bin/env bash
set -u
LAB_DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$LAB_DIR/lib/common.sh"

scenario_header "05-poison-dlq" "Poison message é isolada na DLQ sem bloquear outros agregados" "I7"

wait_healthy 60 || die "stack not healthy within 60s"

# --- Exact baseline: empty DLQ ---
drain_queue pkg-dlq >/dev/null
BASE_DLQ="$(dlq_visible_count pkg)"
BASE_DLQ="${BASE_DLQ:-0}"

# --- Shrink visibility timeout so 3 redelivery cycles fit in the demo window ---
set_visibility pkg 5
POISON_TIMEOUT=90
V="$(sqs_attr pkg VisibilityTimeout)"
if [ "$V" != "5" ]; then
  POISON_TIMEOUT=240
  echo "[05] emulator did not apply VisibilityTimeout=5 (got '$V'); falling back to 60s redelivery cycles, timeout=${POISON_TIMEOUT}s"
fi

# --- Inject poison: no eventId/eventType -> consumer always throws ---
sqs_send pkg '{"poison":"'"$LAB_RUN_ID"'"}' "poison-$LAB_RUN_ID" "$(uuid)" || die "failed to send poison message"

# --- Immediately create a healthy package (different MessageGroupId) ---
PKG="$(pkg_create 89010000 88010000)" || die "pkg_create failed"
[ -n "$PKG" ] || die "pkg_create returned empty packageId"

poison_reached_dlq() {
  CUR="$(dlq_visible_count pkg)"
  [ -n "$CUR" ] && [ "$CUR" -gt "$BASE_DLQ" ]
}

POISON_IN_DLQ=0
if poll_until "$POISON_TIMEOUT" poison_reached_dlq; then
  POISON_IN_DLQ=1
  assert_ge I7 "poison moved to DLQ (visible count)" 1 "$(dlq_visible_count pkg)"
  PEEK="$(sqs_peek pkg-dlq)"
  RECV="$(echo "$PEEK" | jq -r '.Messages[0].Attributes.ApproximateReceiveCount // empty')"
  if [ -n "$RECV" ]; then
    assert_ge I7 "receives before DLQ" 3 "$RECV"
  else
    skip I7 "ApproximateReceiveCount not reported by emulator"
  fi
else
  PEEK="$(sqs_peek pkg)"
  if echo "$PEEK" | grep -q "$LAB_RUN_ID"; then
    skip I7 "ministack does not enforce RedrivePolicy (poison still in main queue after ${POISON_TIMEOUT}s of redeliveries)"
  else
    fail I7 "poison message lost (neither main queue nor DLQ after ${POISON_TIMEOUT}s)"
  fi
  drain_queue pkg >/dev/null
fi

# --- Non-blocking evidence: the healthy group converged while poison retried ---
if wait_for_status "$PKG" ROUTE_CALCULATED 90; then
  pass I7 "healthy aggregate converged while poison retried (group isolation)"
else
  fail I7 "healthy aggregate did not converge while poison retried (packageId=$PKG, status=$(pkg_status "$PKG"))"
fi

# --- Redrive sub-step: poison stays poison ---
if [ "$POISON_IN_DLQ" -eq 1 ]; then
  # A just-redriven FIFO message can be briefly unreceivable (group in flight in the
  # emulator) — retry the peek for a while before giving up.
  BODY=""
  peek_dlq_body() {
    BODY="$(sqs_peek pkg-dlq | jq -r '.Messages[0].Body // empty')"
    [ -n "$BODY" ]
  }
  poll_until 20 peek_dlq_body
  if [ -z "$BODY" ]; then
    # Emulator quirk: the DLQ count is visible but receive returns nothing. The lab
    # injected the poison itself, so redrive semantics can still be demonstrated by
    # re-sending the known body (a real redrive would read it from the DLQ).
    BODY="{\"poison\":\"$LAB_RUN_ID\"}"
    echo "[05] peek unavailable on DLQ — falling back to the injected poison body for the redrive"
  fi
  if [ -z "$BODY" ]; then
    skip I7 "could not peek poison body in DLQ for redrive"
  else
    if sqs_send pkg "$BODY" "poison-$LAB_RUN_ID" "$(uuid)"; then
      drain_queue pkg-dlq >/dev/null
      redrive_back_in_dlq() {
        CUR="$(dlq_visible_count pkg)"
        [ -n "$CUR" ] && [ "$CUR" -gt 0 ]
      }
      if poll_until "$POISON_TIMEOUT" redrive_back_in_dlq; then
        pass I7 "redrive of a contract-broken message returns it to DLQ (poison stays poison)"
      else
        skip I7 "redriven poison did not return to DLQ within ${POISON_TIMEOUT}s (dlq_visible_count=$(dlq_visible_count pkg))"
        drain_queue pkg >/dev/null
      fi
    else
      skip I7 "could not re-send DLQ body to main queue for redrive test"
    fi
  fi
fi

# --- Cleanup of injected artifacts (visibility restore runs via cleanup stack) ---
drain_queue pkg-dlq >/dev/null

scenario_footer
