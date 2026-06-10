#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/../lib/common.sh"

scenario_header "01-happy-path" "Fluxo feliz ponta a ponta" "I5,I8"

# ---------- create package (Blumenau -> Joinville) ----------
PKG="$(pkg_create 89010000 89201000)" || die "package creation failed (HTTP $HTTP_STATUS)"
[ -n "$PKG" ] || die "package creation returned an empty id"
echo "created package: $PKG"

# ---------- I8: status converges to ROUTE_CALCULATED ----------
if wait_for_status "$PKG" ROUTE_CALCULATED 90; then
  pass I8 "package $PKG reached ROUTE_CALCULATED"
else
  fail I8 "package $PKG did not reach ROUTE_CALCULATED within 90s (last status='$WAIT_LAST_STATUS')"
fi

# ---------- I8: exactly one route persisted ----------
assert_eq I8 "routes for package" 1 "$(routes_count_for "$PKG")"

# ---------- I8: route info visible through the package API ----------
PKG_JSON="$(pkg_get "$PKG")" || PKG_JSON=""
assert_contains I8 "routeInfo present" '"hubs"' "$PKG_JSON"

# ---------- I5: outbox settles (no PENDING/FAILED left for this group) ----------
outbox_settled() {
  local db count
  for db in package_db logistics_db; do
    count="$(mongo_eval "$db" "print(db.outbox.countDocuments({groupId:'$PKG', status:{\$in:['PENDING','FAILED']}}))")"
    [ "$count" = "0" ] || return 1
  done
  return 0
}
poll_until 30 outbox_settled || true

for db in package_db logistics_db; do
  assert_eq I5 "$db outbox PENDING for group $PKG" 0 \
    "$(mongo_eval "$db" "print(db.outbox.countDocuments({groupId:'$PKG', status:'PENDING'}))")"
  assert_eq I5 "$db outbox FAILED for group $PKG" 0 \
    "$(mongo_eval "$db" "print(db.outbox.countDocuments({groupId:'$PKG', status:'FAILED'}))")"
done

scenario_footer
