#!/usr/bin/env bash
set -u
LAB_DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$LAB_DIR/lib/common.sh"

scenario_header "04-stale-route" "Evento antigo não sobrescreve estado novo" "I1,I4,I10"

wait_healthy 60 || die "stack did not become healthy in 60s"

PKG="$(pkg_create 89010000 89201000)" || die "pkg_create failed"
[ -n "$PKG" ] || die "pkg_create returned empty packageId"

wait_for_status "$PKG" ROUTE_CALCULATED 90 || die "initial route never arrived"

pkg_change_destination "$PKG" 01310100 || die "destination change failed"

wait_for_status "$PKG" ROUTE_CALCULATED 90 || die "recalculated route never arrived"

FRESH="$(pkg_get "$PKG")"
FRESH_CEP="$(echo "$FRESH" | jq -r .recipientCep)"
[ "$FRESH_CEP" = "01310100" ] || die "precondition: recipientCep is '$FRESH_CEP', expected 01310100"

BASE_DLQ="$(dlq_visible_count log)"
BASE_DLQ="${BASE_DLQ:-0}"

E="$(uuid)"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
STALE_ENVELOPE="{\"eventId\":\"$E\",\"eventType\":\"route.calculated\",\"occurredAt\":\"$NOW\",\"source\":\"resilience-lab\",\"version\":\"1.0\",\"payload\":{\"packageId\":\"$PKG\",\"destinationCep\":\"89201000\",\"totalDistanceKm\":1.0,\"estimatedTransitHours\":1,\"hops\":[{\"name\":\"Hub Lab Stale\"}]}}"

sqs_send log "$STALE_ENVELOPE" "$PKG" "$(uuid)" || die "could not send stale route.calculated"

if poll_until 60 service_logs_grep package-service "Stale route for package"; then
  pass I4 "stale event detected and skipped (log: 'Stale route for package')"
else
  fail I4 "no 'Stale route for package' log within 60s"
fi

AFTER="$(pkg_get "$PKG")"

assert_eq I4 "recipientCep unchanged" "01310100" "$(echo "$AFTER" | jq -r .recipientCep)"
assert_eq I1 "status did not regress" "ROUTE_CALCULATED" "$(echo "$AFTER" | jq -r .status)"

HUBS="$(echo "$AFTER" | jq -r '(.routeInfo.hubs // []) | join(",")')"
case "$HUBS" in
  *"Hub Lab Stale"*)
    fail I10 "routeInfo was overwritten by the stale event (hubs: $HUBS)"
    ;;
  *)
    pass I10 "route info still reflects current destination (hubs: $HUBS)"
    ;;
esac

AFTER_DLQ="$(dlq_visible_count log)"
AFTER_DLQ="${AFTER_DLQ:-0}"
assert_eq I10 "DLQ delta" 0 "$((AFTER_DLQ - BASE_DLQ))"

scenario_footer
