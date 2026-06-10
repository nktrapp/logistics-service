#!/usr/bin/env bash
set -u
LAB_DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$LAB_DIR/lib/common.sh"

scenario_header "02-broker-dedup" "Dedup do broker FIFO não é exactly-once" "I9"

wait_healthy 60 || die "stack did not become healthy in 60s"

container_stop logistics-service
push_cleanup "container_start logistics-service"
push_cleanup "drain_queue pkg"

BASE="$(queue_depth pkg)"
BASE="${BASE:-0}"

PKGID="${LAB_RUN_ID}-p02"
E="$(uuid)"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
ENVELOPE="{\"eventId\":\"$E\",\"eventType\":\"package.created\",\"occurredAt\":\"$NOW\",\"source\":\"resilience-lab\",\"version\":\"1.0\",\"payload\":{\"packageId\":\"$PKGID\",\"senderCep\":\"89010000\",\"recipientCep\":\"89201000\"}}"

depth_pkg_is() {
  CUR_DEPTH="$(queue_depth pkg)"
  [ "${CUR_DEPTH:-0}" -eq "$1" ]
}

D1="$(uuid)"
sqs_send pkg "$ENVELOPE" "$PKGID" "$D1" || die "first send failed"
sqs_send pkg "$ENVELOPE" "$PKGID" "$D1" || die "second send (same dedupId) failed"

if poll_until 30 depth_pkg_is "$((BASE + 1))"; then
  sleep 3
  CUR="$(queue_depth pkg)"
  CUR="${CUR:-0}"
  if [ "$CUR" -eq "$((BASE + 1))" ]; then
    pass I9 "same dedupId deduplicated by broker (depth $BASE -> $CUR after 2 sends)"
  elif [ "$CUR" -ge "$((BASE + 2))" ]; then
    skip I9 "emulator does not implement FIFO dedup window (depth settled at $CUR, base $BASE)"
  else
    fail I9 "unexpected queue depth after duplicate send: $CUR (base $BASE)"
  fi
else
  CUR="$(queue_depth pkg)"
  CUR="${CUR:-0}"
  if [ "$CUR" -ge "$((BASE + 2))" ]; then
    skip I9 "emulator does not implement FIFO dedup window (depth went to $CUR, base $BASE)"
  else
    fail I9 "duplicate send never reached depth $((BASE + 1)): depth is $CUR (base $BASE)"
  fi
fi

DEPTH_BEFORE_THIRD="$(queue_depth pkg)"
DEPTH_BEFORE_THIRD="${DEPTH_BEFORE_THIRD:-0}"
EXPECT_THIRD="$((DEPTH_BEFORE_THIRD + 1))"

sqs_send pkg "$ENVELOPE" "$PKGID" "$(uuid)" || die "third send (new dedupId) failed"

poll_until 30 depth_pkg_is "$EXPECT_THIRD"
ACTUAL_THIRD="$(queue_depth pkg)"
assert_eq I9 "distinct dedupId enqueues again (at-least-once at broker boundary)" "$EXPECT_THIRD" "${ACTUAL_THIRD:-0}"

scenario_footer
