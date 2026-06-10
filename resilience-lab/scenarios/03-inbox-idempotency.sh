#!/usr/bin/env bash
set -u
LAB_DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$LAB_DIR/lib/common.sh"

scenario_header "03-inbox-idempotency" "Inbox torna o consumo idempotente" "I2,I3"

wait_healthy 60 || die "stack did not become healthy in 60s"

# A real package is used (instead of a synthetic packageId) so the route.calculated
# emitted by logistics-service finds its aggregate on the package side — synthetic ids
# would poison the logistics-events queue with PackageNotFoundException retries.
PKGID="$(pkg_create 89010000 89201000)" || die "could not create package"

route_created() {
  ROUTE_COUNT="$(routes_count_for "$PKGID")"
  [ "${ROUTE_COUNT:-0}" -eq 1 ]
}

poll_until 90 route_created || die "first delivery did not produce a route — check ViaCEP/internet"

# Replay the REAL package.created: same eventId (fetched from the outbox), new dedupId
# so the broker dedup window is bypassed and the application inbox is what must dedup.
E="$(mongo_eval package_db "var d=db.outbox.findOne({groupId:'$PKGID',eventType:'package.created'}); print(d ? d.eventId : '')")"
[ -n "$E" ] || die "could not find the package.created eventId in the outbox"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
ENVELOPE="{\"eventId\":\"$E\",\"eventType\":\"package.created\",\"occurredAt\":\"$NOW\",\"source\":\"resilience-lab\",\"version\":\"1.0\",\"payload\":{\"packageId\":\"$PKGID\",\"senderCep\":\"89010000\",\"recipientCep\":\"89201000\"}}"

sqs_send pkg "$ENVELOPE" "$PKGID" "$(uuid)" || die "could not re-send duplicate envelope"

poll_until 15 service_logs_grep logistics-service "already processed, skipping"

assert_eq I2 "routes after duplicate" 1 "$(routes_count_for "$PKGID")"

if service_logs_grep logistics-service "already processed, skipping"; then
  pass I3 "duplicate eventId rejected by inbox (log: 'already processed, skipping')"
else
  fail I3 "no 'already processed, skipping' log after duplicate delivery"
fi

assert_eq I3 "inbox entries for event" 1 "$(mongo_eval logistics_db "print(db.inbox.countDocuments({eventId:'$E'}))")"

scenario_footer
