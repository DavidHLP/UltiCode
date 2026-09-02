#!/usr/bin/env bash
set -euo pipefail

# P4-LEGACY-010: keep the Judge execution runtime out of the App production
# compile closure while retaining it for the independent Judge worker.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVICES_DIR="$ROOT_DIR/services"
APP_POM="$SERVICES_DIR/app/app-web/pom.xml"
JUDGE_POM="$SERVICES_DIR/judge/pom.xml"
APP_MAIN="$SERVICES_DIR/app/app-web/src/main/java"
RUNTIME_MAIN="$SERVICES_DIR/judge-runtime/src/main/java"
MAVEN_WRAPPER="$SERVICES_DIR/mvnw"

fail() {
  echo "app-judge-runtime-dependency-contract: FAIL: $*" >&2
  exit 1
}

for required_path in "$APP_POM" "$JUDGE_POM" "$APP_MAIN" "$RUNTIME_MAIN" "$MAVEN_WRAPPER"; do
  [[ -e "$required_path" ]] || fail "missing required path: $required_path"
done

# Keep the source boundary and direct dependency scopes explicit. The Maven
# trees below prove the resolved closure; this check makes a test-only bridge
# reviewable even when the test fixture is changed later.
python3 - "$ROOT_DIR" <<'PY'
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}


def direct_dependencies(path: Path) -> dict[tuple[str, str], str]:
    document = ET.parse(path).getroot()
    dependencies = document.find("m:dependencies", namespace)
    if dependencies is None:
        raise SystemExit(f"{path}: missing direct dependencies section")
    result: dict[tuple[str, str], str] = {}
    for dependency in dependencies.findall("m:dependency", namespace):
        group = (dependency.findtext("m:groupId", default="", namespaces=namespace) or "").strip()
        artifact = (dependency.findtext("m:artifactId", default="", namespaces=namespace) or "").strip()
        scope = (dependency.findtext("m:scope", default="compile", namespaces=namespace) or "compile").strip()
        result[(group, artifact)] = scope
    return result


def require_dependency(
    dependencies: dict[tuple[str, str], str],
    artifact: str,
    expected_scope: str,
    owner: str,
) -> None:
    key = ("com.ulticode", artifact)
    actual_scope = dependencies.get(key)
    if actual_scope is None:
        raise SystemExit(f"{owner}: missing direct dependency {artifact}")
    if actual_scope != expected_scope:
        raise SystemExit(
            f"{owner}: {artifact} must use {expected_scope} scope, found {actual_scope}"
        )


app_dependencies = direct_dependencies(root / "services/app/app-web/pom.xml")
judge_dependencies = direct_dependencies(root / "services/judge/pom.xml")

if ("com.ulticode", "backend-judge-runtime") in app_dependencies:
    raise SystemExit("services/app/app-web/pom.xml: backend-judge-runtime must not be direct")
require_dependency(app_dependencies, "backend-app-api", "compile", "App POM")
require_dependency(app_dependencies, "backend-submission-api", "compile", "App POM")
require_dependency(app_dependencies, "backend-judge", "test", "App POM")
for artifact in ("backend-judge-runtime", "backend-app-api", "backend-submission-api"):
    require_dependency(judge_dependencies, artifact, "compile", "Judge POM")

runtime_root = root / "services/judge-runtime/src/main/java"
runtime_types: set[str] = set()
class_pattern = re.compile(
    r"^\s*(?:public\s+|protected\s+|private\s+|abstract\s+|final\s+|static\s+|sealed\s+|non-sealed\s+)*"
    r"(?:@interface|class|interface|enum|record)\s+([A-Za-z_$][A-Za-z0-9_$]*)"
)
package_pattern = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)\s*;")
for source in sorted(runtime_root.rglob("*.java")):
    lines = source.read_text(encoding="utf-8").splitlines()
    package = next(
        (match.group(1) for line in lines
         if (match := package_pattern.match(line))),
        None,
    )
    if package is None:
        continue
    for line in lines:
        if match := class_pattern.match(line):
            runtime_types.add(f"{package}.{match.group(1)}")


runtime_type_prefixes = tuple(f"{runtime_type}." for runtime_type in runtime_types)

def is_runtime_type(name: str) -> bool:
    return name in runtime_types or any(
        name.startswith(prefix) for prefix in runtime_type_prefixes
    )

if runtime_types:
    representative = next(iter(runtime_types))
    if not is_runtime_type(representative + ".Nested"):
        raise SystemExit("nested runtime type prefix guard is not active")

app_root = root / "services/app/app-web/src/main/java"
import_pattern = re.compile(r"^\s*import\s+(?:static\s+)?([A-Za-z0-9_.*]+)\s*;")
fqn_pattern = re.compile(r"\bcom\.ulticode\.[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+\b")
violations: list[str] = []
for source in sorted(app_root.rglob("*.java")):
    lines = source.read_text(encoding="utf-8").splitlines()
    relative = source.relative_to(root)
    for line_number, line in enumerate(lines, 1):
        imported = import_pattern.match(line)
        if imported:
            name = imported.group(1)
            if name.endswith(".*"):
                package = name[:-2]
                if any(
                    runtime_type == package or runtime_type.startswith(package + ".")
                    for runtime_type in runtime_types
                ):
                    violations.append(f"{relative}:{line_number}: runtime wildcard import {name}")
            elif is_runtime_type(name):
                violations.append(f"{relative}:{line_number}: runtime import {name}")
        for match in fqn_pattern.finditer(line):
            if is_runtime_type(match.group(0)):
                violations.append(
                    f"{relative}:{line_number}: runtime-qualified reference {match.group(0)}"
                )

if violations:
    raise SystemExit("App production source references Judge runtime:\n" + "\n".join(violations))

print("App source/dependency boundary: PASS")
PY

if command -v mise >/dev/null 2>&1; then
  MAVEN=(mise exec java@zulu-17.68.203.0 -- bash "$MAVEN_WRAPPER")
else
  MAVEN=(bash "$MAVEN_WRAPPER")
fi

TEMP_FILES=()
cleanup() {
  if ((${#TEMP_FILES[@]} > 0)); then
    rm -f -- "${TEMP_FILES[@]}"
  fi
}
trap cleanup EXIT

maven_tree() {
  local module="$1"
  local includes="$2"
  local output_file="$3"
  (
    cd "$SERVICES_DIR"
    "${MAVEN[@]}" \
      -pl "$module" \
      -am \
      -Dscope=compile \
      -Dincludes="$includes" \
      dependency:tree \
      -B
  ) >"$output_file" 2>&1
}

extract_project_tree() {
  local output_file="$1"
  local artifact_id="$2"
  python3 - "$output_file" "$artifact_id" <<'PY'
from pathlib import Path
import re
import sys

output = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
artifact_id = re.escape(sys.argv[2])
markers = list(re.finditer(rf"@ {artifact_id} ---", output))
if not markers:
    raise SystemExit(f"Maven output has no dependency tree for {sys.argv[2]}")
print(output[markers[-1].start():], end="")
PY
}

app_tree_file="$(mktemp)"
judge_tree_file="$(mktemp)"
TEMP_FILES+=("$app_tree_file" "$judge_tree_file")

if ! maven_tree "app/app-web" "com.ulticode:backend-judge-runtime" "$app_tree_file"; then
  printf '%s\n' "$(<"$app_tree_file")" >&2
  fail "App compile dependency tree could not be resolved"
fi
app_tree="$(extract_project_tree "$app_tree_file" "backend-app-web")" || fail "could not isolate App dependency tree"
if [[ "$app_tree" == *"com.ulticode:backend-judge-runtime:"* ]]; then
  fail "App compile dependency tree contains backend-judge-runtime"
fi

judge_includes="com.ulticode:backend-judge-runtime,com.ulticode:backend-app-api,com.ulticode:backend-submission-api"
if ! maven_tree "judge" "$judge_includes" "$judge_tree_file"; then
  printf '%s\n' "$(<"$judge_tree_file")" >&2
  fail "Judge compile dependency tree could not be resolved"
fi
judge_tree="$(extract_project_tree "$judge_tree_file" "backend-judge")" || fail "could not isolate Judge dependency tree"
for coordinate in \
  "com.ulticode:backend-judge-runtime" \
  "com.ulticode:backend-app-api" \
  "com.ulticode:backend-submission-api"; do
  [[ "$judge_tree" == *"$coordinate:"* ]] \
    || fail "Judge compile dependency tree is missing $coordinate"
done

printf 'App compile tree excludes backend-judge-runtime; Judge compile tree retains runtime and API contracts: PASS\n'
printf 'app-judge-runtime-dependency-contract: PASS\n'
