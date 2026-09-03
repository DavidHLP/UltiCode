#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if ! command -v python3 >/dev/null 2>&1; then
  printf 'admin-rpc-budget: BLOCKED_EXTERNAL (python3 is required for static checks)\n' >&2
  exit 1
fi

# Repository-only checks run before the Java toolchain probe. A missing toolchain
# is still a blocker below; it must never be converted into a PASS.
python3 - "$ROOT_DIR" <<'PY'
from __future__ import annotations

from bisect import bisect_right
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])

EXPECTED_IDS = tuple("""
I-DASH-STATS
I-DASH-CHART-OWNER
I-DASH-CHART-USERS
I-USER-LIST
I-USER-DETAIL
I-WS-AUTH
I-CONTEST-LIST
I-CONTEST-DETAIL
I-CONTEST-RANKINGS
I-CONTEST-ANNOUNCEMENTS
I-FORUM-LIST
I-FORUM-DETAIL
I-FORUM-COMMUNITIES
I-NOTIFY-LIST
I-SOLUTION-LIST
I-SOLUTION-DETAIL
I-SUBMISSION-LIST
I-SUBMISSION-DETAIL
I-SUBMISSION-STATS
I-SUBMISSION-FILTERS
I-PROBLEM-READ
I-PROBLEM-SUBMISSIONS
I-TESTCASE-READ
I-PROBLEM-LIST-LIST
I-PROBLEM-LIST-DETAIL
I-COMMENT-TYPED
I-COMMENT-ALL
I-TAG-READ
I-ANALYTICS-OVERVIEW
I-ANALYTICS-ACTIVITY
I-ANALYTICS-PROBLEM
I-ANALYTICS-CONTEST
I-ANALYTICS-REVENUE
I-ANALYTICS-PERFORMANCE
I-AUDIT
I-SETTINGS
W-ONE-SHOT
W-USER-CREATE
W-USER-UPDATE
W-USER-DELETE-RESET
W-USER-PERMISSION
W-PROFILE
W-CONTEST-READBACK
W-CONTEST-ONE
W-PROBLEM-CREATE
W-PROBLEM-UPDATE-STATE
W-PROBLEM-DELETE
W-NOTIFY-CREATE
W-NOTIFY-UPDATE
W-NOTIFY-DELETE
W-SOLUTION-READBACK
W-SOLUTION-DELETE
W-CONTENT-CUTOVER
W-PROBLIST-CREATE
W-PROBLIST-PREFLIGHT
W-TAG-FORUM
W-TAG-PROBLEM
W-TESTCASE-ONE
W-TESTCASE-UPDATE
B-USER-BAN
B-USER-DELETE
B-FORUM-TOGGLE
B-FORUM-DELETE
B-COMMENT-DELETE
B-COMMENT-UNFLAG
B-SOLUTION-SIMPLE
B-SOLUTION-UNFLAG
B-PROBLEM-PUBLISH
B-PROBLEM-DELETE
B-PROBLEM-RESTORE
B-PROBLEM-EDIT
B-PROBLEM-MODERATE
B-PROBLEM-IMPORT
B-TESTCASE-APPEND
B-TESTCASE-REPLACE
B-TESTCASE-REORDER
B-REJUDGE
B-PROBLIST-REPLACE
B-PROBLEM-EXPORT
S-BOOTSTRAP-ADMIN
S-DEV-BOOTSTRAP
S-RECON-FULL
S-RECON-INCREMENTAL
S-RECON-LEASE-BUSY
""".split())
EXPECTED_ID_SET = frozenset(EXPECTED_IDS)
FRESHNESS_CODES = frozenset(("REQ", "NOW", "WM", "CRON", "WRB", "LOCAL"))
POLICIES = frozenset(("Q", "W", "X", "P"))
METRIC_NAMES = {
    "PREFIX": "admin.use_case",
    "LOGICAL_CALLS": "admin.use_case.logical_calls",
    "SERIAL_ROUNDS": "admin.use_case.serial_rounds",
    "DURATION": "admin.use_case.duration",
    "WALL_TIME": "admin.use_case.duration",
    "DEGRADATION": "admin.use_case.degradation",
    "FRESHNESS": "admin.use_case.freshness",
}
ALLOWED_LABELS = frozenset(("use_case", "owner", "degradation", "freshness"))
ALLOWED_BOUNDED_OWNER_LOOP_FILES = frozenset((
    "services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java",
))


def fail(message: str) -> None:
    raise SystemExit(f"admin-rpc-budget: FAIL ({message})")


def read_required(relative: str) -> str:
    path = root / relative
    if not path.is_file():
        fail(f"missing required file {relative}")
    return path.read_text(encoding="utf-8")


def unmark(value: str) -> str:
    return re.sub(r"[`*_]", "", value).strip()


def split_table_row(line: str) -> list[str]:
    if not line.strip().startswith("|") or not line.strip().endswith("|"):
        fail(f"malformed manifest table row: {line}")
    return [cell.strip() for cell in line.strip()[1:-1].split("|")]


def manifest_tables(text: str) -> list[tuple[list[str], list[list[str]]]]:
    lines = text.splitlines()
    tables: list[tuple[list[str], list[list[str]]]] = []
    index = 0
    while index < len(lines):
        if lines[index].strip().startswith("| id |"):
            header = split_table_row(lines[index])
            if index + 1 >= len(lines) or not re.fullmatch(
                r"\|\s*:?-{3,}:?(?:\s*\|\s*:?-{3,}:?)+\s*\|", lines[index + 1].strip()
            ):
                fail(f"manifest table has no separator after header at line {index + 1}")
            rows: list[list[str]] = []
            index += 2
            while index < len(lines) and lines[index].strip().startswith("|"):
                rows.append(split_table_row(lines[index]))
                index += 1
            tables.append((header, rows))
            continue
        index += 1
    return tables


manifest = read_required("docs/architecture/evidence/P3-ADMIN-001-admin-budget-manifest.md")
tables = manifest_tables(manifest)
if len(tables) != 4:
    fail(f"expected four budget tables, found {len(tables)}")

expected_headers = {
    "HTTP/use-case": ("I", "target L / R / wall_budget_ms", "current_shape"),
    "operation family": ("W", "target L / R / wall_budget_ms", "current call shape"),
    "scheduled/batch use-case": ("B", "target L / R / wall_budget_ms", "current shape"),
    "scheduled mode": ("S", "target L / R / wall_budget_ms", "current shape"),
}
manifest_rows: dict[str, tuple[str, list[str]]] = {}
for header, rows in tables:
    if len(header) < 6 or header[0] != "id" or header[-1] != "source":
        fail(f"manifest budget table has incomplete headers: {' | '.join(header)}")
    kind = expected_headers.get(header[1])
    target_header_index = 2 if kind is not None and kind[0] in "IW" else 3
    shape_header_index = target_header_index + 1
    if (
        kind is None
        or len(header) <= shape_header_index
        or header[target_header_index] != kind[1]
        or header[shape_header_index] != kind[2]
    ):
        fail(f"unexpected budget table headers: {' | '.join(header)}")
    if header[0] != "id":
        fail("every budget table must begin with id")
    target_index = header.index("target L / R / wall_budget_ms")
    policy_index = next(
        (i for i, value in enumerate(header) if "policy / freshness" in value), None
    )
    semantics_index = next(
        (i for i, value in enumerate(header) if "semantics" in value), None
    )
    if policy_index is None or semantics_index is None:
        fail(f"budget table {header[1]!r} is missing policy/freshness or semantics")
    for row in rows:
        if len(row) != len(header):
            fail(f"manifest row has {len(row)} cells, expected {len(header)}: {row[0]!r}")
        identifier = unmark(row[0])
        if not re.fullmatch(r"[IWBS]-[A-Z0-9]+(?:-[A-Z0-9]+)*", identifier):
            fail(f"invalid manifest ID {row[0]!r}")
        if not identifier.startswith(kind[0] + "-"):
            fail(f"{identifier} is in the wrong budget table")
        if identifier in manifest_rows:
            fail(f"duplicate manifest ID {identifier}")
        target = unmark(row[target_index])
        shape = re.search(r"(?<!\d)(\d+)\s*/\s*(\d+)\s*/\s*(\d+)(?!\d)", target)
        if not shape or any(int(value) < 0 for value in shape.groups()):
            fail(f"{identifier} has no finite non-negative L/R/wall target")
        if "UNBOUNDED" in target.upper():
            fail(f"{identifier} target is unbounded")
        if kind[0] in "BS" and not unmark(row[2]):
            fail(f"{identifier} is missing its input/scan cap")
        required_indexes = (1, 3, policy_index, semantics_index, len(header) - 1)
        for field_index in required_indexes:
            if not unmark(row[field_index]):
                fail(f"{identifier} is missing manifest field {header[field_index]}")
        policy_text = unmark(row[policy_index])
        if not any(re.search(rf"(?<![A-Z]){re.escape(policy)}(?![A-Z])", policy_text) for policy in POLICIES):
            fail(f"{identifier} has no recognized RPC/local policy")
        has_freshness_code = any(
            re.search(rf"(?<![A-Z]){code}(?![A-Z])", policy_text)
            for code in FRESHNESS_CODES
        )
        if not has_freshness_code and not re.search(r"request[- ]time", policy_text, re.IGNORECASE):
            fail(f"{identifier} has no recognized freshness metadata")
        manifest_rows[identifier] = (header[1], row)

if set(manifest_rows) != EXPECTED_ID_SET:
    missing = sorted(EXPECTED_ID_SET - set(manifest_rows))
    extra = sorted(set(manifest_rows) - EXPECTED_ID_SET)
    fail(f"manifest ID set mismatch; missing={missing}, extra={extra}")
if len(manifest_rows) != len(EXPECTED_IDS):
    fail("manifest ID count is not deterministic")
if "not production SLO" not in manifest.lower() and "not a measured latency objective" not in manifest.lower():
    fail("manifest lacks its repository/non-production boundary")
print(f"manifest: PASS ({len(manifest_rows)} fixed IDs and required fields)")


metrics_relative = "services/admin/src/main/java/com/ulticode/modules/admin/metrics/AdminUseCaseMetrics.java"
metrics_text = read_required(metrics_relative)
constant_values: dict[str, str] = {}
for name in METRIC_NAMES:
    match = re.search(
        rf"public\s+static\s+final\s+String\s+{name}\s*=\s*([^;]+);", metrics_text
    )
    if not match:
        fail(f"{metrics_relative} is missing fixed metric constant {name}")
    raw = match.group(1).strip()
    parts = [part.strip() for part in raw.split("+")]
    resolved: list[str] = []
    for part in parts:
        if len(part) >= 2 and part[0] == part[-1] == '"':
            resolved.append(part[1:-1])
        elif part in constant_values:
            resolved.append(constant_values[part])
        else:
            fail(f"{metrics_relative} has unresolved metric constant {name}: {raw}")
    constant_values[name] = "".join(resolved)
for name, expected in METRIC_NAMES.items():
    if constant_values[name] != expected:
        fail(f"metric {name} is {constant_values[name]!r}, expected {expected!r}")

use_case_match = re.search(
    r"private\s+static\s+final\s+Set<String>\s+USE_CASES\s*=\s*Set\.of\((.*?)\);",
    metrics_text,
    re.DOTALL,
)
if not use_case_match:
    fail(f"{metrics_relative} has no fixed USE_CASES set")
source_ids = frozenset(re.findall(
    r'"([IWBS]-[A-Z0-9]+(?:-[A-Z0-9]+)*)"', use_case_match.group(1)
))
if source_ids != EXPECTED_ID_SET:
    fail(
        f"metric use-case IDs differ from manifest; missing={sorted(EXPECTED_ID_SET - source_ids)}, "
        f"extra={sorted(source_ids - EXPECTED_ID_SET)}"
    )
if 'UNKNOWN_USE_CASE = "UNKNOWN"' not in metrics_text:
    fail(f"{metrics_relative} has no bounded UNKNOWN use-case fallback")
if not re.search(r"USE_CASE_PATTERN\s*=\s*Pattern\.compile", metrics_text):
    fail(f"{metrics_relative} has no use-case shape guard")

# Every metric tag name is a fixed low-cardinality vocabulary. Dynamic names are
# rejected too, so a future request/account identifier cannot become a label.
tag_names: list[str] = []
dynamic_label_names = []
for match in re.finditer(r"\.tag\s*\(\s*(\"[^\"]*\"|[^,\s)]+)", metrics_text):
    token = match.group(1)
    if token.startswith('"') and token.endswith('"'):
        tag_names.append(token[1:-1])
    elif token == "valueTag":
        dynamic_label_names.append(token)
    else:
        fail(f"{metrics_relative} uses an unbounded dynamic metric label name")
if frozenset(tag_names) != frozenset(("use_case", "owner")):
    fail(f"metric labels are {sorted(set(tag_names))}, expected use_case and owner")
if dynamic_label_names and not all(
    marker in metrics_text for marker in ('"degradation"', '"freshness"')
):
    fail(f"{metrics_relative} does not bound its dynamic outcome label names")
print("metrics: PASS (fixed names/IDs and bounded labels)")


def mask_java(text: str) -> str:
    """Mask comments and literals while preserving offsets and newlines."""
    chars = list(text)
    index = 0
    state = "code"
    while index < len(chars):
        char = chars[index]
        next_char = chars[index + 1] if index + 1 < len(chars) else ""
        if state == "code":
            if char == "/" and next_char == "/":
                chars[index] = chars[index + 1] = " "
                index += 2
                state = "line_comment"
                continue
            if char == "/" and next_char == "*":
                chars[index] = chars[index + 1] = " "
                index += 2
                state = "block_comment"
                continue
            if char in ('"', "'"):
                state = "string" if char == '"' else "char"
                chars[index] = " "
                index += 1
                continue
            index += 1
            continue
        if state == "line_comment":
            if char == "\n":
                state = "code"
            else:
                chars[index] = " "
            index += 1
            continue
        if state == "block_comment":
            if char == "*" and next_char == "/":
                chars[index] = chars[index + 1] = " "
                index += 2
                state = "code"
            else:
                if char != "\n":
                    chars[index] = " "
                index += 1
            continue
        if state in ("string", "char"):
            if char == "\\":
                chars[index] = " "
                if index + 1 < len(chars) and chars[index + 1] != "\n":
                    chars[index + 1] = " "
                    index += 2
                else:
                    index += 1
                continue
            if (state == "string" and char == '"') or (state == "char" and char == "'"):
                chars[index] = " "
                index += 1
                state = "code"
            else:
                if char != "\n":
                    chars[index] = " "
                index += 1
    return "".join(chars)


def matching_pairs(masked: str, opening: str, closing: str) -> dict[int, int]:
    stack: list[int] = []
    pairs: dict[int, int] = {}
    for index, char in enumerate(masked):
        if char == opening:
            stack.append(index)
        elif char == closing:
            if not stack:
                fail(f"unbalanced Java {closing!r} delimiter")
            pairs[stack.pop()] = index
    if stack:
        fail(f"unbalanced Java {opening!r} delimiter")
    return pairs


def line_number(text: str, starts: list[int], offset: int) -> int:
    return bisect_right(starts, offset)


def known_reference_fields(text: str) -> set[str]:
    fields = set()
    for match in re.finditer(
        r"@DubboReference\b[\s\S]{0,900}?\b(?:private|protected|public)\s+"
        r"(?:final\s+)?[A-Za-z_$][\w$]*(?:\s*<[^;{}]+>)?\s+([A-Za-z_$][\w$]*)\s*;",
        text,
    ):
        fields.add(match.group(1))
    return fields


def owner_rpc_loop_violations(label: str, text: str) -> list[str]:
    if label in ALLOWED_BOUNDED_OWNER_LOOP_FILES:
        return []
    masked = mask_java(text)
    braces = matching_pairs(masked, "{", "}")
    parens = matching_pairs(masked, "(", ")")
    refs = known_reference_fields(text)
    if not refs:
        return []
    starts = [0]
    starts.extend(index + 1 for index, char in enumerate(masked) if char == "\n")
    violations: list[str] = []
    loop_pattern = re.compile(r"\b(for|while|do)\b")
    for loop in loop_pattern.finditer(masked):
        kind = loop.group(1)
        if kind == "while" and masked[:loop.start()].rstrip().endswith("}"):
            # The trailing while in a do/while is not a second loop body.
            continue
        if kind == "do":
            body_start = next((i for i in range(loop.end(), len(masked)) if not masked[i].isspace()), None)
            close = braces.get(body_start) if body_start is not None else None
            if body_start is None or close is None:
                continue
            open_index = body_start
        else:
            paren_start = masked.find("(", loop.end())
            if paren_start < 0 or paren_start not in parens:
                continue
            after_header = parens[paren_start] + 1
            while after_header < len(masked) and masked[after_header].isspace():
                after_header += 1
            if after_header >= len(masked):
                continue
            if masked[after_header] != "{":
                # Braceless loops are still rejected when their one statement
                # invokes a known owner reference.
                line_end = masked.find("\n", after_header)
                if line_end < 0:
                    line_end = len(masked)
                body = masked[after_header:line_end]
                if any(re.search(rf"\b{re.escape(ref)}\s*\.\s*[A-Za-z_$][\w$]*\s*\(", body) for ref in refs):
                    violations.append(f"{label}:{line_number(text, starts, after_header)} owner RPC in braceless loop")
                continue
            open_index = after_header
            close = braces.get(open_index)
            if close is None:
                continue
        body_start_offset = open_index + 1
        body = masked[body_start_offset:close]
        if "@DubboReference" in body:
            offset = body_start_offset + body.index("@DubboReference")
            violations.append(f"{label}:{line_number(text, starts, offset)} @DubboReference inside loop")
        for ref in sorted(refs):
            call = re.search(
                rf"\b{re.escape(ref)}\s*\.\s*[A-Za-z_$][\w$]*\s*\(", body
            )
            if call:
                offset = body_start_offset + call.start()
                violations.append(f"{label}:{line_number(text, starts, offset)} {ref} RPC inside loop")
    return violations


def metric_hook_violations(label: str, text: str) -> list[str]:
    masked = mask_java(text)
    parens = matching_pairs(masked, "(", ")")
    starts = [0]
    starts.extend(index + 1 for index, char in enumerate(masked) if char == "\n")
    violations: list[str] = []
    for match in re.finditer(r"\b(?:[A-Za-z_$][\w$]*\s*\.\s*)?observe\s*\(", masked):
        open_index = masked.find("(", match.start())
        close = parens.get(open_index)
        if close is None:
            violations.append(f"{label}:{line_number(text, starts, match.start())} unterminated observe hook")
            continue
        line_start = masked.rfind("\n", 0, match.start()) + 1
        if re.search(r"\b(?:public|private|protected)\b[^\n;]*\bobserve\s*$", masked[line_start:match.end() - 1]):
            continue
        arguments = text[open_index + 1:close]
        masked_arguments = masked[open_index + 1:close]
        depth = 0
        split_at = len(arguments)
        for index, char in enumerate(masked_arguments):
            if char in "([{":
                depth += 1
            elif char in ")]}":
                depth -= 1
            elif char == "," and depth == 0:
                split_at = index
                break
        first_argument = arguments[:split_at].strip()
        # The adapter helper forwards a validated literal hook's arguments; it
        # is not a new metric hook and necessarily receives a variable here.
        if first_argument == "useCase":
            continue
        literal = re.fullmatch(r'"([IWBS]-[A-Z0-9]+(?:-[A-Z0-9]+)*)"', first_argument)
        if literal is None:
            violations.append(f"{label}:{line_number(text, starts, match.start())} hook use-case is not a fixed manifest ID")
        elif literal.group(1) not in EXPECTED_ID_SET:
            violations.append(f"{label}:{line_number(text, starts, match.start())} unknown metric use-case {literal.group(1)}")
        freshness = re.findall(r"(?:AdminUseCaseMetrics\.)?Freshness\.([A-Za-z_$][\w$]*)", arguments)
        if not freshness or any(value not in FRESHNESS_CODES for value in freshness):
            violations.append(f"{label}:{line_number(text, starts, match.start())} hook is missing fixed freshness metadata")
    return violations


source_root = root / "services/admin/src/main/java"
if not source_root.is_dir():
    fail(f"missing Admin source scope {source_root.relative_to(root)}")
source_files = sorted(source_root.rglob("*.java"))
if not source_files:
    fail("Admin source scope has no Java files")
loop_violations: list[str] = []
hook_violations: list[str] = []
for path in source_files:
    text = path.read_text(encoding="utf-8")
    label = str(path.relative_to(root))
    loop_violations.extend(owner_rpc_loop_violations(label, text))
    if path != root / metrics_relative:
        hook_violations.extend(metric_hook_violations(label, text))
if loop_violations:
    fail("owner RPC loop detected: " + "; ".join(sorted(loop_violations)))
if hook_violations:
    fail("invalid Admin metric hook: " + "; ".join(sorted(hook_violations)))

# The user-detail budget is a source-level contract. Keep this check tied to
# the observable fan-out shape rather than implementation class names: one
# authoritative account read, then four optional owner reads in parallel.
detail_relative = "services/admin/src/main/java/com/ulticode/modules/admin/query/DefaultAdminUserDetailQuery.java"
detail_text = read_required(detail_relative)
detail_masked = mask_java(detail_text)
if "AdminUserStatsReadPort" in detail_text:
    fail(f"{detail_relative} still depends on the retired per-item stats port")
if re.search(r"\b(?:countSubmissionsByUserId|countAcceptedProblemsByUserId|calculateSubmissionStreak)\s*\(", detail_masked):
    fail(f"{detail_relative} still contains per-item Submission stats calls")
if detail_text.count("loadUserDetailStats(") != 1:
    fail(f"{detail_relative} must issue one Submission detail snapshot call")
if detail_text.count("CompletableFuture.allOf(") != 1:
    fail(f"{detail_relative} must use one bounded optional fan-out barrier")
if detail_text.count("CancellableQueryExecutor.cancel(") < 2:
    fail(f"{detail_relative} must cancel both account and optional work")
if "new ArrayBlockingQueue" in detail_text or "Executors.new" in detail_text:
    fail(f"{detail_relative} must reuse the owned bounded executor")
pool_size = re.search(r"DETAIL_QUERY_POOL_SIZE\s*=\s*(\d+)", detail_masked)
if pool_size is None or int(pool_size.group(1)) > 4:
    fail(f"{detail_relative} has no bounded four-worker detail executor")
call_map = re.search(r"DETAIL_CALLS\s*=\s*Map\.of\((.*?)\);", detail_masked, re.DOTALL)
if call_map is None:
    fail(f"{detail_relative} has no fixed detail logical-call map")
owner_calls = {
    owner: int(calls)
    for owner, calls in re.findall(
        r"Owner\.([A-Z]+)\s*,\s*(\d+)", call_map.group(1))
}
if owner_calls != {"AUTH": 2, "APP": 2, "SUBMISSION": 1}:
    fail(f"{detail_relative} detail call map is {owner_calls}, expected AUTH=2 APP=2 SUBMISSION=1")
if sum(owner_calls.values()) > 5:
    fail(f"{detail_relative} exceeds five logical owner calls")
if not re.search(
        r'"I-USER-DETAIL"[\s\S]*?,\s*2\s*,\s*'
        r'AdminUseCaseMetrics\.Freshness\.REQ', detail_text):
    fail(f"{detail_relative} is missing the two-round Admin metric hook")
for required in (
    "findAccountAuthoritatively",
    "findProfileWithStatus",
    "AuthorizationSnapshotService",
    "SolutionReadPort",
    "AdminSubmissionUserDetailStatsReadPort",
):
    if required not in detail_text:
        fail(f"{detail_relative} is missing required owner seam {required}")

retired_paths = (
    "services/admin/src/main/java/com/ulticode/modules/admin/port/AdminUserStatsReadPort.java",
    "services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminUserStatsReadAdapter.java",
    "services/admin/src/test/java/com/ulticode/modules/admin/port/adapter/AdminUserStatsReadAdapterTest.java",
)
for relative in retired_paths:
    if (root / relative).exists():
        fail(f"retired detail stats path still exists: {relative}")
for scope in (
        root / "services/admin/src/main/java",
        root / "services/admin/src/test/java"):
    for path in scope.rglob("*.java"):
        if "AdminUserStatsReadPort" in path.read_text(encoding="utf-8"):
            fail(f"retired detail stats port is still referenced by {path.relative_to(root)}")
print("detail budget: PASS (5 logical calls, 2 rounds, one Submission snapshot, bounded cancellation)")

# Fixtures make the static boundary executable: local loops are allowed, while
# a known owner reference in a loop and an unknown/missing metric contract fail.
local_loop = """
class LocalLoop {
  void copy(java.util.List<String> ids) {
    for (String id : ids) {
      System.out.println(id.trim());
    }
  }
}
"""
looped_rpc = """
class RpcLoop {
  @DubboReference(group = \"backend-app\")
  private OwnerReadService ownerReadService;
  void load(java.util.List<String> ids) {
    for (String id : ids) {
      ownerReadService.fetch(id);
    }
  }
}
"""
looped_reference = """
class ReferenceLoop {
  void load(java.util.List<String> ids) {
    for (String id : ids) {
      @DubboReference(group = \"backend-app\")
      private OwnerReadService ownerReadService;
    }
  }
}
"""
unknown_hook = """
class UnknownHook {
  void observe() {
    metrics.observe(\"I-NOT-A-MANIFEST-ID\", calls, 1,
        AdminUseCaseMetrics.Freshness.REQ, () -> null);
  }
}
"""
missing_freshness = """
class MissingFreshness {
  void observe() {
    metrics.observe(\"I-DASH-STATS\", calls, 1, () -> null);
  }
}
"""
if owner_rpc_loop_violations("fixture-rpc", local_loop):
    fail("local loop fixture was falsely classified as an owner RPC loop")
if not owner_rpc_loop_violations("fixture-rpc", looped_rpc):
    fail("RPC loop fixture was not rejected")
if not owner_rpc_loop_violations("fixture-reference", looped_reference):
    fail("@DubboReference loop fixture was not rejected")
if not metric_hook_violations("fixture-unknown", unknown_hook):
    fail("unknown metric use-case fixture was not rejected")
if not metric_hook_violations("fixture-freshness", missing_freshness):
    fail("missing freshness fixture was not rejected")
print("static fixtures: PASS (RPC-loop and metric negatives rejected; local loop accepted)")

focus_tests = (
    "services/admin/src/test/java/com/ulticode/modules/admin/metrics/AdminUseCaseMetricsTest.java",
    "services/admin/src/test/java/com/ulticode/modules/admin/port/adapter/DefaultAdminAnalyticsPortAdapterMetricsTest.java",
    "services/admin/src/test/java/com/ulticode/modules/admin/port/adapter/DefaultAdminDashboardReadAdapterMetricsTest.java",
    "services/admin/src/test/java/com/ulticode/modules/admin/query/AdminUserDetailQueryTest.java",
    "services/admin/src/test/java/com/ulticode/modules/admin/AdminUserVOContractTest.java",
    "services/admin/src/test/java/com/ulticode/modules/admin/projection/AdminUserProjectionTest.java",
)
for relative in focus_tests:
    read_required(relative)
print("focused test sources: PASS (Admin metric contract tests present)")
PY

if ! command -v mise >/dev/null 2>&1; then
  printf 'admin-rpc-budget: BLOCKED_EXTERNAL (mise is required for the Java 17 focused gate)\n' >&2
  exit 1
fi
if [[ ! -f "$ROOT_DIR/services/mvnw" ]]; then
  printf 'admin-rpc-budget: FAIL (missing services/mvnw)\n' >&2
  exit 1
fi
if ! command -v mktemp >/dev/null 2>&1; then
  printf 'admin-rpc-budget: BLOCKED_EXTERNAL (mktemp is required for test diagnostics)\n' >&2
  exit 1
fi

TEST_LOG="$(mktemp "${TMPDIR:-/tmp}/admin-rpc-budget.XXXXXX")"
trap 'rm -f -- "$TEST_LOG"' EXIT

if ! (
  cd "$ROOT_DIR/services"
  mise exec java@zulu-17.68.203.0 -- java -version
) >"$TEST_LOG" 2>&1; then
  printf 'admin-rpc-budget: BLOCKED_EXTERNAL (Java 17 toolchain is unavailable)\n' >&2
  exit 1
fi
if ! python3 - "$TEST_LOG" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
raise SystemExit(0 if re.search(r'version\s+"17(?:[.\"]|$)', text) else 1)
PY
then
  printf 'admin-rpc-budget: BLOCKED_EXTERNAL (mise did not provide Java 17)\n' >&2
  exit 1
fi

if (
  cd "$ROOT_DIR/services"
  mise exec java@zulu-17.68.203.0 -- bash ./mvnw \
    -pl :backend-admin -am \
    -Dtest='AdminUseCaseMetricsTest,DefaultAdminAnalyticsPortAdapterMetricsTest,DefaultAdminDashboardReadAdapterMetricsTest,AdminUserDetailQueryTest,AdminUserVOContractTest,AdminUserProjectionTest' \
    test -B
) >"$TEST_LOG" 2>&1; then
  printf 'admin-rpc-budget: focused Admin tests PASS\n'
else
  if python3 - "$TEST_LOG" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace").lower()
blocked = (
    "could not resolve dependencies",
    "could not transfer artifact",
    "non-resolvable parent",
    "plugin .* could not be resolved",
    "unknownhostexception",
    "connection timed out",
    "pkix path building failed",
    "java_home",
    "toolchain",
    "maven wrapper",
    "maven-wrapper",
    "no such file or directory",
    "permission denied",
)
raise SystemExit(0 if any(re.search(pattern, text) for pattern in blocked) else 1)
PY
  then
    printf 'admin-rpc-budget: BLOCKED_EXTERNAL (Maven dependency/toolchain prerequisite unavailable)\n' >&2
  else
    printf 'admin-rpc-budget: FAIL (focused Admin metric tests failed)\n' >&2
  fi
  exit 1
fi

printf 'admin-rpc-budget: PASS (repository/disposable checks; no production claim)\n'
