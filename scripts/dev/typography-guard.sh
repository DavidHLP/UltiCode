#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# typography-guard.sh — enforce shared typography across apps/console/ and apps/management/
#
# Both frontends MUST source font families, sizes, line heights, weights, and
# letter spacing from packages/theme/src/typography.css. Custom values are not
# allowed except:
#   1. References to the --uc-* / --font-* / --text-* / --leading-* / --tracking-*
#      tokens that typography.css (or its @theme inline aliases) exposes.
#   2. Color and surface tokens (--silver-*, --solarized-*, etc.).
#   3. Third-party code under node_modules / dist / coverage.
#   4. The canonical surface in packages/theme/src/typography.css and
#      packages/design-system/style.css.
#
# See docs/SHARED_TYPOGRAPHY_DESIGN.md §13 (Phase 6 guardrails).
#
# Usage:
#   ./scripts/dev/typography-guard.sh           # human-readable report
#   ./scripts/dev/typography-guard.sh --check   # CI mode: exit 1 on any hit
#   ./scripts/dev/typography-guard.sh --path <dir>
# ---------------------------------------------------------------------------
set -uo pipefail

# --- locate project root (one level up from scripts/dev) -------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# --- defaults ---------------------------------------------------------------
MODE="report"
SCAN_PATHS=(apps/console/src apps/management/src)

# --- args -------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --check) MODE="check"; shift ;;
    --path)  SCAN_PATHS=("$2"); shift 2 ;;
    --help|-h)
      sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

# --- dependency check -------------------------------------------------------
if ! command -v rg >/dev/null 2>&1; then
  echo "ERROR: ripgrep (rg) is required. Install via: sudo apt install ripgrep" >&2
  exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 is required for the post-filter step." >&2
  exit 2
fi

# --- patterns ---------------------------------------------------------------
# Raw typography declarations (font-size, font-family, font-weight, line-height
# literal values; arbitrary tailwind values like text-[Npx], tracking-[Nem],
# font-[NNN]). The post-filter step (below) drops legitimate var(--...) refs
# and third-party highlight.js output, so we keep the patterns broad here.
PATTERN_BLOB=$(cat <<'EOF'
font-size\s*:\s*[0-9]+(?:\.[0-9]+)?(?:px|rem|em|pt)\b
font-family\s*:\s*['"][^'"]+['"]
font-weight\s*:\s*(?:normal|bold|[1-9]00)\b
line-height\s*:\s*[0-9]+(?:\.[0-9]+)?(?:px|rem|em|pt)\b
letter-spacing\s*:\s*-\s*[0-9]+(?:\.[0-9]+)?(?:em|px|rem)\b
text-\[\s*[0-9.]+(?:px|rem|em)\b
tracking-\[\s*[0-9.]+(?:px|rem|em)\b
leading-\[\s*[0-9.]+(?:px|rem|em)\b
font-\[\s*[0-9]+\b
EOF
)

# --- scan -------------------------------------------------------------------
violations_file="$(mktemp)"
trap 'rm -f "$violations_file"' EXIT

for scan_path in "${SCAN_PATHS[@]}"; do
  [[ -d "$scan_path" ]] || continue

  while IFS= read -r pat; do
    [[ -z "$pat" ]] && continue
    rg --no-heading --line-number --color=never \
       --glob '*.vue' \
       --glob '*.ts' \
       --glob '*.tsx' \
       --glob '*.js' \
       --glob '*.jsx' \
       --glob '*.css' \
       --glob '*.scss' \
       --glob '!*.html' \
       --glob '!**/node_modules/**' \
       --glob '!**/dist/**' \
       --glob '!**/coverage/**' \
       --glob '!**/test-results/**' \
       --glob '!**/.turbo/**' \
       "$pat" \
       "$scan_path" 2>/dev/null \
       >> "$violations_file" || true
  done <<< "$PATTERN_BLOB"
done

# Use python to:
#   1. Drop the shared allowlist (packages/theme/typography-allowlist.json — single source).
#   2. Drop lines that are pure var(--uc-*) or var(--font-*) / var(--text-*) etc.
#      references — they ARE shared tokens, not custom font state.
#   3. Drop highlight.js third-party output inside .hljs-* rule blocks.
#   4. De-duplicate (a single line can match multiple patterns).
final_file="$(mktemp)"
python3 - "$violations_file" "$final_file" <<'PY'
import json, re, sys, pathlib

_allow = pathlib.Path("packages/theme/typography-allowlist.json")
try:
    _data = json.loads(_allow.read_text(encoding="utf-8"))
    _paths = _data.get("allowedPaths", [])
    if not isinstance(_paths, list) or not _paths or not all(isinstance(p, str) and p for p in _paths):
        raise ValueError("invalid allowlist")
except Exception:
    _paths = [
        "packages/theme/src/typography.css",
        "packages/design-system/style.css",
        "apps/console/src/style.css",
        "apps/management/src/style.css",
        "apps/console/src/assets/charts.css",
        "apps/console/src/assets/markdown.css",
        "apps/console/src/views/landing/styles/bundle.css",
        "apps/console/src/views/problems/description/DescriptionMarkdown.vue",
    ]
CANONICAL_PREFIXES = tuple(f"{p}:" for p in _paths)
# var(--token-name) references count as compliant — they consume the shared
# token system. We also allow arbitrary CSS values inside var() like
# var(--font-sans, monospace) but those still trace back to a uc-* token.
VAR_REF_RE = re.compile(r'var\(\s*--[a-z][\w-]*')
HLJS_RULE_RE = re.compile(r'^\.hljs[-\w]*\s*[{,]')

src, dst = sys.argv[1], sys.argv[2]
seen = set()
selector_buf: list[str] = []

def flush_drop_selector(sel: str, line: str) -> bool:
    """Return True if `line` should be DROPPED because we're inside a hljs rule."""
    if not sel:
        return False
    # A selector line is "drop-source" if it targets highlight.js.
    return bool(re.search(r'\.hljs', sel)) and bool(
        re.search(r'font-weight\s*:\s*(?:bold|600|700)\b', line)
    )

with open(src) as f, open(dst, 'w') as out:
    current_selector = ''
    for raw in f:
        line = raw.rstrip('\n')

        # Skip canonical surface.
        if any(line.startswith(p) for p in CANONICAL_PREFIXES):
            continue

        # Track the most recent selector line. CSS lines that start at column 0
        # and end in `{` open a new rule. This is a heuristic but works for the
        # hand-written styles in apps/console/ and apps/management/.
        stripped = line.lstrip()
        if stripped and not stripped.startswith(('*', '/', '.', '#', ':', '@')):
            # likely a selector at column 0 — could be `body {`, `.foo {`, etc.
            pass
        sel_m = re.match(r'^([^/{][^{]*)\{', stripped)
        if sel_m:
            current_selector = sel_m.group(1).strip()

        # Drop hljs third-party font-weight.
        if flush_drop_selector(current_selector, line):
            continue

        # Drop pure var() references (line is a single declaration using a token).
        # Regression: letter-spacing with var() elsewhere on same line must not be dropped.
        # e.g. `letter-spacing: -0.02em; content: var(--x)` is a real negative-letter-spacing violation (MJS reports it),
        # so letter-spacing negative must be in the exclusion regex; otherwise the var check would incorrectly drop it.
        # We detect by stripping path:line: prefix and checking the remainder.
        parts = line.split(':', 2)
        if len(parts) >= 3:
            content = parts[2]
        else:
            content = line
        if VAR_REF_RE.search(content) and not re.search(
            r'(text-\[\d|tracking-\[\d|leading-\[\d|font-\[\d|font-size:\s*\d|font-weight:\s*(?:bold|normal|[1-9]00)\b|letter-spacing\s*:\s*-)', content
        ):
            # Line uses var() and has no other typography violation — compliant.
            continue

        # De-duplicate.
        if line in seen:
            continue
        seen.add(line)
        out.write(raw)

PY

# --- report -----------------------------------------------------------------
count=$(wc -l < "$final_file" | tr -d ' ')

if [[ "$count" -eq 0 ]]; then
  echo "✓ typography-guard: no custom font declarations found in ${SCAN_PATHS[*]}"
  exit 0
fi

echo "✗ typography-guard: $count custom font declaration(s) found in ${SCAN_PATHS[*]}"
echo "  (typography tokens live in packages/theme/src/typography.css)"
echo "  (see docs/SHARED_TYPOGRAPHY_DESIGN.md §13 Phase 6 for the allowed list)"
echo
echo "Hits (file:line:snippet):"
sort "$final_file" | head -200 | sed 's/^/  /'

if [[ "$count" -gt 200 ]]; then
  echo "  ... and $((count - 200)) more (truncated)"
fi

if [[ "$MODE" == "check" ]]; then
  echo
  echo "FAIL: typography-guard --check requires zero hits."
  exit 1
fi

exit 0
