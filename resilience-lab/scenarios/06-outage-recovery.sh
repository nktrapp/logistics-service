#!/usr/bin/env bash
set -u
LAB_DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$LAB_DIR/lib/common.sh"

scenario_header "06-outage-recovery" "Recuperação após indisponibilidade de consumidor e de broker" "I5,I6,I8,I10"

# ===================== Part A: consumer outage =====================
wait_healthy 60 || die "stack not healthy within 60s"
# Drain first so the baseline is exact even if a previous scenario left an
# in-flight (temporarily invisible) message in the DLQ.
drain_queue pkg-dlq >/dev/null
BASE_DLQ="$(dlq_visible_count pkg)"
BASE_DLQ="${BASE_DLQ:-0}"

container_stop logistics-service
push_cleanup "container_start logistics-service"

BASE_DEPTH="$(queue_depth pkg)"
BASE_DEPTH="${BASE_DEPTH:-0}"

PKG1="$(pkg_create 89010000 89201000)" || die "pkg_create #1 failed"
PKG2="$(pkg_create 89010000 80010000)" || die "pkg_create #2 failed"
PKG3="$(pkg_create 89010000 01310100)" || die "pkg_create #3 failed"
[ -n "$PKG1" ] && [ -n "$PKG2" ] && [ -n "$PKG3" ] || die "pkg_create returned empty packageId"

sleep 5
for ID in "$PKG1" "$PKG2" "$PKG3"; do
  assert_eq I8 "status of $ID while consumer down" "CREATED" "$(pkg_status "$ID")"
done

queue_grew() {
  CUR="$(queue_depth pkg)"
  [ -n "$CUR" ] && [ "$CUR" -gt "$BASE_DEPTH" ]
}
if poll_until 30 queue_grew; then
  pass I5 "events accumulated in broker while consumer down (depth $BASE_DEPTH -> $(queue_depth pkg))"
else
  skip I5 "queue depth did not grow within 30s (before=$BASE_DEPTH, after=$(queue_depth pkg)); emulator may not report ApproximateNumberOfMessages"
fi

container_start logistics-service
wait_healthy 120 || die "logistics-service did not become healthy within 120s after restart"

for ID in "$PKG1" "$PKG2" "$PKG3"; do
  if wait_for_status "$ID" ROUTE_CALCULATED 120; then
    pass I8 "$ID converged after consumer recovery"
  else
    fail I8 "$ID did not reach ROUTE_CALCULATED within 120s after recovery (status=$(pkg_status "$ID"))"
  fi
  assert_eq I10 "exactly one route for $ID" 1 "$(routes_count_for "$ID")"
done

assert_eq I8 "DLQ delta after recovery" "$BASE_DLQ" "$(dlq_visible_count pkg)"

# ===================== Part B: broker outage (outbox holds the event) =====================
container_pause ministack
push_cleanup "container_unpause ministack"

PKG="$(pkg_create 89010000 80010000)" || die "REST create failed while broker down (local commit must not depend on broker)"
[ -n "$PKG" ] || die "pkg_create returned empty packageId while broker down"
pass I6 "local write succeeded while broker down (packageId=$PKG)"

# NOTE: no sqs_* calls while ministack is paused — Mongo-only checks here.
# The relay claims the entry within ~2s, and with a paused broker the publish call
# hangs, leaving the document IN_PROGRESS (not PENDING) — so the parked check is
# group-scoped: events exist for the group and none is PUBLISHED yet.
group_outbox_total() {
  mongo_eval package_db "print(db.outbox.countDocuments({groupId:'$PKG'}))"
}
group_outbox_published() {
  mongo_eval package_db "print(db.outbox.countDocuments({groupId:'$PKG', status:'PUBLISHED'}))"
}
outbox_parked() {
  TOTAL="$(group_outbox_total)"
  PUBLISHED="$(group_outbox_published)"
  [ -n "$TOTAL" ] && [ "${TOTAL:-0}" -ge 1 ] && [ "${PUBLISHED:-0}" -eq 0 ]
}
if poll_until 30 outbox_parked; then
  pass I5 "event parked in outbox while broker unreachable (group $PKG: $(group_outbox_total) event(s), 0 published)"
else
  fail I5 "outbox did not retain the event while broker down (group $PKG: total=$(group_outbox_total), published=$(group_outbox_published))"
fi

sleep 10
container_unpause ministack

outbox_drained() {
  TOTAL="$(group_outbox_total)"
  PUBLISHED="$(group_outbox_published)"
  [ -n "$TOTAL" ] && [ "${TOTAL:-0}" -ge 1 ] && [ "${PUBLISHED:-0}" -eq "${TOTAL:-0}" ]
}
if poll_until 90 outbox_drained; then
  pass I6 "outbox drained after broker recovery (group $PKG: all $(group_outbox_total) event(s) PUBLISHED)"
else
  fail I6 "outbox did not publish all group events within 90s after recovery (total=$(group_outbox_total), published=$(group_outbox_published))"
fi

if wait_for_status "$PKG" ROUTE_CALCULATED 120; then
  pass I8 "package converged end-to-end after broker recovery"
else
  fail I8 "package $PKG did not reach ROUTE_CALCULATED within 120s after broker recovery (status=$(pkg_status "$PKG"))"
fi
assert_eq I10 "exactly one route" 1 "$(routes_count_for "$PKG")"

scenario_footer
