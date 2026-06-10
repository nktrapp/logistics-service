#!/usr/bin/env bash
# resilience-lab common library — bash 3.2 compatible (sourced, not executed)

# ---------- globals ----------
LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$LAB_DIR/.." && pwd)"
PKG_HOST="http://localhost:8081"
LOG_HOST="http://localhost:8082"
POLL_INTERVAL="${POLL_INTERVAL:-2}"
RESULTS_FILE="${RESULTS_FILE:-$LAB_DIR/.results/latest.results}"
LAB_RUN_ID="lab-$(date +%s)-$$"
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
SCENARIO_ID="${SCENARIO_ID:-unknown}"
LOG_SINCE="${LOG_SINCE:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
CLEANUP_STACK=""
HTTP_STATUS=""

# ---------- colors ----------
if [ "${LAB_NO_COLOR:-0}" = "1" ] || [ ! -t 1 ]; then
  C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_RESET=""
else
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_BLUE=$'\033[34m'; C_RESET=$'\033[0m'
fi

# ---------- docker compose wrapper ----------
DC_ARGS=(-f "$REPO_DIR/compose.yml")
if [ -f "$REPO_DIR/compose.override.yml" ]; then
  DC_ARGS=("${DC_ARGS[@]}" -f "$REPO_DIR/compose.override.yml")
fi
DC_ARGS=("${DC_ARGS[@]}" -f "$LAB_DIR/compose.lab.yml")

dc() {
  docker compose "${DC_ARGS[@]}" "$@"
}

# ---------- stack lifecycle ----------
stack_up() {
  dc up -d || return 1
  if [ "$AWS_MODE" = "docker" ]; then
    docker pull amazon/aws-cli >/dev/null 2>&1 || true
  fi
  wait_healthy 240
}

stack_down() {
  dc down
}

wait_healthy() {
  local timeout="${1:-180}"
  local deadline=$(( $(date +%s) + timeout ))
  local pkg_status="" log_status=""
  while [ "$(date +%s)" -lt "$deadline" ]; do
    pkg_status="$(curl -s -o /dev/null -w '%{http_code}' "$PKG_HOST/management/health/liveness" 2>/dev/null)"
    log_status="$(curl -s -o /dev/null -w '%{http_code}' "$LOG_HOST/management/health/liveness" 2>/dev/null)"
    if [ "$pkg_status" = "200" ] && [ "$log_status" = "200" ]; then
      return 0
    fi
    sleep "$POLL_INTERVAL"
  done
  echo "wait_healthy: timeout after ${timeout}s (package-service=$pkg_status logistics-service=$log_status)" >&2
  return 1
}

# ---------- container chaos verbs ----------
container_pause()   { dc pause "$1"; }
container_unpause() { dc unpause "$1"; }
container_stop()    { dc stop "$1"; }
container_start()   { dc start "$1"; }
container_kill()    { dc kill "$1"; }

service_logs_grep() {
  local service="$1" pattern="$2"
  dc logs --since "$LOG_SINCE" "$service" 2>&1 | grep -E -- "$pattern"
}

# ---------- helpers ----------
uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  else
    od -An -tx1 -N16 /dev/urandom | tr -d ' \n' \
      | sed 's/^\(.\{8\}\)\(.\{4\}\)\(.\{4\}\)\(.\{4\}\)\(.\{12\}\)$/\1-\2-\3-\4-\5/'
  fi
}

poll_until() {
  local timeout="$1"
  shift
  local deadline=$(( $(date +%s) + timeout ))
  while true; do
    if "$@"; then
      return 0
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      return 1
    fi
    sleep "$POLL_INTERVAL"
  done
}

# ---------- cleanup stack (LIFO) ----------
push_cleanup() {
  if [ -z "$CLEANUP_STACK" ]; then
    CLEANUP_STACK="$1"
  else
    CLEANUP_STACK="$CLEANUP_STACK
$1"
  fi
}

run_cleanups() {
  [ -z "$CLEANUP_STACK" ] && return 0
  local reversed line
  reversed="$(printf '%s\n' "$CLEANUP_STACK" | sed -n '1!G;h;$p')"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    eval "$line" || true
  done <<EOF
$reversed
EOF
  CLEANUP_STACK=""
  return 0
}

die() {
  echo "${C_RED}FATAL: $*${C_RESET}" >&2
  mkdir -p "$(dirname "$RESULTS_FILE")"
  printf 'RESULT|%s|SETUP|ERROR|%s\n' "$SCENARIO_ID" "$(printf '%s' "$*" | tr -d '|')" >> "$RESULTS_FILE"
  exit 2
}

# ---------- HTTP ----------
http_request() {
  local method="$1" url="$2" body="${3:-}"
  local tmpfile
  tmpfile="$(mktemp)"
  if [ -n "$body" ]; then
    HTTP_STATUS="$(curl -sS -X "$method" -H 'Content-Type: application/json' \
      -d "$body" -o "$tmpfile" -w '%{http_code}' "$url" 2>/dev/null)" || true
  else
    HTTP_STATUS="$(curl -sS -X "$method" \
      -o "$tmpfile" -w '%{http_code}' "$url" 2>/dev/null)" || true
  fi
  [ -n "$HTTP_STATUS" ] || HTTP_STATUS="000"
  HTTP_BODY="$(cat "$tmpfile")"
  rm -f "$tmpfile"
  printf '%s\n' "$HTTP_BODY"
  case "$HTTP_STATUS" in
    2*) return 0 ;;
    *)  return 1 ;;
  esac
}

# ---------- package-service API ----------
pkg_create() {
  local sender="$1" recipient="$2" weight="${3:-1.0}" description="${4:-resilience-lab}"
  local body
  body="{\"senderCep\":\"$sender\",\"recipientCep\":\"$recipient\",\"weight\":$weight,\"description\":\"$description\"}"
  http_request POST "$PKG_HOST/api/v1/packages" "$body" >/dev/null || true
  if [ "$HTTP_STATUS" != "201" ]; then
    return 1
  fi
  printf '%s\n' "$HTTP_BODY" | jq -r .id
}

pkg_get() {
  http_request GET "$PKG_HOST/api/v1/packages/$1" >/dev/null || true
  if [ "$HTTP_STATUS" != "200" ]; then
    return 1
  fi
  printf '%s\n' "$HTTP_BODY"
}

pkg_status() {
  pkg_get "$1" | jq -r .status
}

pkg_change_destination() {
  http_request PATCH "$PKG_HOST/api/v1/packages/$1/destination" "{\"newCep\":\"$2\"}" >/dev/null || true
  printf '%s\n' "$HTTP_BODY"
  [ "$HTTP_STATUS" = "200" ]
}

_status_is() {
  WAIT_LAST_STATUS="$(pkg_status "$1")"
  [ "$WAIT_LAST_STATUS" = "$2" ]
}

wait_for_status() {
  local id="$1" expected="$2" timeout="$3"
  WAIT_LAST_STATUS=""
  if poll_until "$timeout" _status_is "$id" "$expected"; then
    return 0
  fi
  echo "wait_for_status: timeout after ${timeout}s for package $id (last='$WAIT_LAST_STATUS' expected='$expected')" >&2
  return 1
}

route_get_by_package() {
  http_request GET "$LOG_HOST/api/v1/routes?packageId=$1" >/dev/null || true
  if [ "$HTTP_STATUS" != "200" ]; then
    return 1
  fi
  printf '%s\n' "$HTTP_BODY"
}

# ---------- AWS / SQS ----------
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=""
if command -v aws >/dev/null 2>&1; then
  AWS_MODE=host
else
  AWS_MODE=docker
fi

aws_sqs() {
  if [ "$AWS_MODE" = "host" ]; then
    aws --endpoint-url http://localhost:4566 sqs "$@"
  else
    docker run --rm --network logistics-service_default \
      -e AWS_ACCESS_KEY_ID=test -e AWS_SECRET_ACCESS_KEY=test -e AWS_DEFAULT_REGION=us-east-1 \
      amazon/aws-cli --endpoint-url http://ministack:4566 sqs "$@"
  fi
}

queue_url() {
  local logical="$1" name="" cached="" url=""
  case "$logical" in
    pkg)     name="package-events-queue.fifo";  cached="${Q_URL_PKG:-}" ;;
    log)     name="logistics-events-queue.fifo"; cached="${Q_URL_LOG:-}" ;;
    hub)     name="hub-events-queue.fifo";      cached="${Q_URL_HUB:-}" ;;
    pkg-dlq) name="package-events-dlq.fifo";    cached="${Q_URL_PKG_DLQ:-}" ;;
    log-dlq) name="logistics-events-dlq.fifo";  cached="${Q_URL_LOG_DLQ:-}" ;;
    hub-dlq) name="hub-events-dlq.fifo";        cached="${Q_URL_HUB_DLQ:-}" ;;
    *)       name="$logical" ;;
  esac
  if [ -n "$cached" ]; then
    printf '%s\n' "$cached"
    return 0
  fi
  url="$(aws_sqs get-queue-url --queue-name "$name" --query QueueUrl --output text)" || return 1
  case "$logical" in
    pkg)     Q_URL_PKG="$url" ;;
    log)     Q_URL_LOG="$url" ;;
    hub)     Q_URL_HUB="$url" ;;
    pkg-dlq) Q_URL_PKG_DLQ="$url" ;;
    log-dlq) Q_URL_LOG_DLQ="$url" ;;
    hub-dlq) Q_URL_HUB_DLQ="$url" ;;
  esac
  printf '%s\n' "$url"
}

_resolve_queue_url() {
  case "$1" in
    http://*|https://*) printf '%s\n' "$1" ;;
    *) queue_url "$1" ;;
  esac
}

sqs_send() {
  local url
  url="$(_resolve_queue_url "$1")" || return 1
  aws_sqs send-message --queue-url "$url" --message-body "$2" \
    --message-group-id "$3" --message-deduplication-id "$4"
}

sqs_attr() {
  local url value
  url="$(_resolve_queue_url "$1")" || return 1
  value="$(aws_sqs get-queue-attributes --queue-url "$url" \
    --attribute-names "$2" --query "Attributes.$2" --output text)" || return 1
  if [ "$value" = "None" ]; then
    value=""
  fi
  printf '%s\n' "$value"
}

queue_depth() {
  sqs_attr "$1" ApproximateNumberOfMessages
}

dlq_visible_count() {
  queue_depth "$1-dlq"
}

sqs_peek() {
  local url
  url="$(_resolve_queue_url "$1")" || return 1
  aws_sqs receive-message --queue-url "$url" --visibility-timeout 0 \
    --attribute-names ApproximateReceiveCount \
    --max-number-of-messages 1 --wait-time-seconds 1
  return 0
}

drain_queue() {
  # Short visibility + consecutive-empty rounds: on a FIFO queue an in-flight message
  # blocks its whole MessageGroupId, so a single empty receive does NOT mean the queue
  # is drained — a sibling may be hidden behind a not-yet-deleted head.
  local url resp handles handle count=0 i=0 empty_rounds=0 depth
  url="$(_resolve_queue_url "$1")" || return 1
  while [ "$i" -lt 60 ] && [ "$empty_rounds" -lt 4 ]; do
    i=$((i + 1))
    resp="$(aws_sqs receive-message --queue-url "$url" \
      --max-number-of-messages 10 --visibility-timeout 2 --wait-time-seconds 1)"
    handles="$(printf '%s\n' "$resp" | jq -r '.Messages[]?.ReceiptHandle' 2>/dev/null)"
    if [ -z "$handles" ]; then
      empty_rounds=$((empty_rounds + 1))
      depth="$(aws_sqs get-queue-attributes --queue-url "$url" \
        --attribute-names ApproximateNumberOfMessages \
        --query 'Attributes.ApproximateNumberOfMessages' --output text 2>/dev/null)"
      if [ -n "$depth" ] && [ "$depth" != "None" ] && [ "$depth" -gt 0 ] 2>/dev/null; then
        empty_rounds=0
      fi
      sleep 1
      continue
    fi
    empty_rounds=0
    while IFS= read -r handle; do
      [ -z "$handle" ] && continue
      aws_sqs delete-message --queue-url "$url" --receipt-handle "$handle" >/dev/null
      count=$((count + 1))
    done <<EOF
$handles
EOF
  done
  printf '%s\n' "$count"
}

set_visibility() {
  local url
  url="$(_resolve_queue_url "$1")" || return 1
  aws_sqs set-queue-attributes --queue-url "$url" --attributes "VisibilityTimeout=$2" || return 1
  push_cleanup "restore_visibility $1"
}

restore_visibility() {
  local url
  url="$(_resolve_queue_url "$1")" || return 1
  aws_sqs set-queue-attributes --queue-url "$url" --attributes VisibilityTimeout=60
}

# ---------- Mongo ----------
mongo_eval() {
  local db="$1" js="$2"
  dc exec -T mongodb mongosh "$db" --quiet --eval "$js"
}

outbox_count() {
  local db="$1" status="${2:-}" filter="{}"
  if [ -n "$status" ]; then
    filter="{status:'$status'}"
  fi
  mongo_eval "$db" "print(db.outbox.countDocuments($filter))"
}

inbox_count() {
  mongo_eval "$1" "print(db.inbox.countDocuments({}))"
}

routes_count_for() {
  mongo_eval logistics_db "print(db.routes.countDocuments({packageId:'$1'}))"
}

# ---------- reporting ----------
scenario_header() {
  SCENARIO_ID="$1"
  local title="$2" invariants="$3"
  LOG_SINCE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$(dirname "$RESULTS_FILE")"
  echo "${C_BLUE}==============================================================${C_RESET}"
  echo "${C_BLUE} scenario:   $SCENARIO_ID${C_RESET}"
  echo "${C_BLUE} title:      $title${C_RESET}"
  echo "${C_BLUE} invariants: $invariants${C_RESET}"
  echo "${C_BLUE} run id:     $LAB_RUN_ID${C_RESET}"
  echo "${C_BLUE}==============================================================${C_RESET}"
  trap run_cleanups EXIT
}

_record() {
  local tag="$1" verdict="$2" msg
  msg="$(printf '%s' "$3" | tr -d '|')"
  printf 'RESULT|%s|%s|%s|%s\n' "$SCENARIO_ID" "$tag" "$verdict" "$msg" >> "$RESULTS_FILE"
}

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "${C_GREEN}PASS [$1] $2${C_RESET}"
  _record "$1" PASS "$2"
}

fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  echo "${C_RED}FAIL [$1] $2${C_RESET}"
  _record "$1" FAIL "$2"
}

skip() {
  SKIP_COUNT=$((SKIP_COUNT + 1))
  echo "${C_YELLOW}SKIP [$1] $2${C_RESET}"
  _record "$1" SKIP "$2"
}

assert_eq() {
  local tag="$1" label="$2" expected="$3" actual="$4"
  if [ "$expected" = "$actual" ]; then
    pass "$tag" "$label: expected='$expected' actual='$actual'"
  else
    fail "$tag" "$label: expected='$expected' actual='$actual'"
  fi
}

assert_ge() {
  local tag="$1" label="$2" min="$3" actual="$4"
  if [ "$actual" -ge "$min" ] 2>/dev/null; then
    pass "$tag" "$label: actual=$actual >= min=$min"
  else
    fail "$tag" "$label: actual='$actual' is not >= min=$min"
  fi
}

assert_contains() {
  local tag="$1" label="$2" needle="$3" haystack="$4"
  case "$haystack" in
    *"$needle"*) pass "$tag" "$label: contains '$needle'" ;;
    *)           fail "$tag" "$label: does not contain '$needle'" ;;
  esac
}

scenario_footer() {
  echo "scenario $SCENARIO_ID: $PASS_COUNT passed, $FAIL_COUNT failed, $SKIP_COUNT skipped"
  if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
  fi
  exit 0
}
