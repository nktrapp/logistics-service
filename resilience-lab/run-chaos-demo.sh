#!/usr/bin/env bash
# resilience-lab runner: brings the stack up, runs scenarios, aggregates results
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  echo "usage: run-chaos-demo.sh [--scenario <NN>] [--no-up] [--keep] [--down-volumes]" >&2
}

ONLY_SCENARIO=""
NO_UP=0
DOWN_VOLUMES=0

while [ $# -gt 0 ]; do
  case "$1" in
    --scenario)
      if [ $# -lt 2 ]; then
        echo "--scenario requires an argument" >&2
        usage
        exit 2
      fi
      ONLY_SCENARIO="$2"
      shift 2
      ;;
    --no-up)
      NO_UP=1
      shift
      ;;
    --keep)
      # default behavior: stack is kept running; accepted as explicit no-op
      shift
      ;;
    --down-volumes)
      DOWN_VOLUMES=1
      shift
      ;;
    *)
      echo "unknown option: $1" >&2
      usage
      exit 2
      ;;
  esac
done

RESULTS_FILE="$SCRIPT_DIR/.results/run-$(date +%Y%m%d-%H%M%S).results"
export RESULTS_FILE

. "$SCRIPT_DIR/lib/common.sh"

mkdir -p "$LAB_DIR/.results"
: > "$RESULTS_FILE"

if [ "$NO_UP" = "1" ]; then
  wait_healthy 30 || die "stack is not healthy and --no-up was given"
else
  stack_up || die "stack_up failed (services did not become healthy)"
fi

# ---------- run scenarios as separate processes ----------
SCENARIO_EXITS=""
RAN_ANY=0

for s in "$SCRIPT_DIR"/scenarios/[0-9][0-9]-*.sh; do
  [ -e "$s" ] || continue
  base="$(basename "$s")"
  if [ -n "$ONLY_SCENARIO" ]; then
    case "$base" in
      "$ONLY_SCENARIO"-*) ;;
      *) continue ;;
    esac
  fi
  RAN_ANY=1
  echo ""
  echo ">>> running $base"
  bash "$s"
  rc=$?
  SCENARIO_EXITS="$SCENARIO_EXITS$base exit=$rc
"
  if [ "$rc" -ne 0 ]; then
    echo ">>> $base exited with rc=$rc (continuing)"
  fi
done

if [ "$RAN_ANY" = "0" ]; then
  echo "no scenario matched (pattern: scenarios/[0-9][0-9]-*.sh, filter: '${ONLY_SCENARIO}')" >&2
fi

# ---------- aggregate report ----------
echo ""
echo "==================== scenario exit codes ===================="
printf '%s' "$SCENARIO_EXITS"

echo ""
echo "====================== aggregate report ======================"
awk -F'|' '
  BEGIN {
    printf "%-18s %-12s %-8s %s\n", "SCENARIO", "INVARIANT", "VERDICT", "DETAIL"
    printf "%-18s %-12s %-8s %s\n", "--------", "---------", "-------", "------"
  }
  $1 == "RESULT" {
    printf "%-18s %-12s %-8s %s\n", $2, $3, $4, $5
  }
' "$RESULTS_FILE"

echo ""
echo "==================== rollup per invariant ===================="
awk -F'|' '
  $1 == "RESULT" {
    seen[$3] = 1
    if ($4 == "PASS")      p[$3]++
    else if ($4 == "FAIL") f[$3]++
    else if ($4 == "SKIP") s[$3]++
    else                   e[$3]++
  }
  END {
    for (k in seen)
      printf "%-12s pass=%d fail=%d skip=%d error=%d\n", k, p[k]+0, f[k]+0, s[k]+0, e[k]+0
  }
' "$RESULTS_FILE" | sort

TOTALS="$(awk -F'|' '
  $1 == "RESULT" {
    if ($4 == "PASS")      p++
    else if ($4 == "FAIL") f++
    else if ($4 == "SKIP") s++
    else                   e++
  }
  END { printf "%d %d %d %d", p+0, f+0, s+0, e+0 }
' "$RESULTS_FILE")"
TOTAL_PASS="$(echo "$TOTALS" | cut -d' ' -f1)"
TOTAL_FAIL="$(echo "$TOTALS" | cut -d' ' -f2)"
TOTAL_SKIP="$(echo "$TOTALS" | cut -d' ' -f3)"
TOTAL_ERROR="$(echo "$TOTALS" | cut -d' ' -f4)"

echo ""
echo "FINAL: $TOTAL_PASS PASS / $TOTAL_FAIL FAIL / $TOTAL_SKIP SKIP / $TOTAL_ERROR ERROR"

cp "$RESULTS_FILE" "$SCRIPT_DIR/.results/latest.results"

if [ "$DOWN_VOLUMES" = "1" ]; then
  dc down -v
fi

if [ "$TOTAL_FAIL" -gt 0 ] || [ "$TOTAL_ERROR" -gt 0 ]; then
  exit 1
fi
exit 0
