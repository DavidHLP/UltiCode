#!/usr/bin/env bash
set -euo pipefail

# TEST-COV-001: static coverage wiring plus a real negative JaCoCo fixture.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
services_pom = (root / "services/pom.xml").read_text(encoding="utf-8")
coverage_properties = dict(
    re.findall(
        r"<(coverage\.[A-Za-z0-9_.]+\.minimum)>"
        r"([^<]+)</\1>",
        services_pom,
    )
)


def fail(message: str) -> None:
    raise SystemExit(f"coverage-contract: FAIL: {message}")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def pom_plugin(pom: Path) -> ET.Element:
    tree = ET.parse(pom)
    for element in tree.iter():
        element.tag = local_name(element.tag)
    for plugin in tree.findall(".//plugin"):
        if plugin.findtext("artifactId") == "jacoco-maven-plugin":
            return plugin
    fail(f"{pom.relative_to(root)} does not configure jacoco-maven-plugin")


pom_specs = {
    "services/platform/web-security/pom.xml": (
        "com.ulticode.websecurity.jwt",
        "com.ulticode.websecurity.csrf",
    ),
    "services/auth/pom.xml": (
        "com.ulticode.auth.security",
        "com.ulticode.auth.idempotency",
    ),
    "services/admin/pom.xml": ("com.ulticode.admin.security",),
    "services/app/app-web/pom.xml": (
        "com.ulticode.app.security",
        "com.ulticode.app.idempotency",
    ),
    "services/submission/pom.xml": (
        "com.ulticode.submission.security",
        "com.ulticode.submission.idempotency",
    ),
    "services/notification/pom.xml": (
        "com.ulticode.notification.security",
        "com.ulticode.notification.idempotency",
    ),
}
allowed_excludes = {"**/*Application.class", "**/*Configuration.class"}

for relative, critical_packages in pom_specs.items():
    pom = root / relative
    if not pom.is_file():
        fail(f"missing POM: {relative}")
    module_properties = dict(
        re.findall(
            r"<(coverage\.[A-Za-z0-9_.]+\.minimum)>"
            r"([^<]+)</\1>",
            pom.read_text(encoding="utf-8"),
        )
    )
    plugin = pom_plugin(pom)
    executions = plugin.find("executions")
    if executions is None:
        fail(f"{relative} has no JaCoCo executions")
    execution_map = {
        execution.findtext("id") or "": execution
        for execution in executions.findall("execution")
    }
    for execution_id in ("prepare-agent", "report", "check"):
        if execution_id not in execution_map:
            fail(f"{relative} is missing JaCoCo execution {execution_id}")
        goals = [
            goal.text.strip()
            for goal in execution_map[execution_id].findall("./goals/goal")
            if goal.text
        ]
        if goals != [execution_id]:
            fail(f"{relative} JaCoCo execution {execution_id} must run goal {execution_id}")
    check = execution_map["check"]
    if check.findtext("phase") != "verify":
        fail(f"{relative} JaCoCo check must run in verify")
    if execution_map["report"].findtext("phase") != "test":
        fail(f"{relative} JaCoCo report must run in test before the verify check")
    check_text = ET.tostring(check, encoding="unicode")
    raw_minimums = re.findall(r"<minimum>([^<]+)</minimum>", check_text)
    if not raw_minimums:
        fail(f"{relative} has no JaCoCo minimum rules")
    minimums = []
    for value in raw_minimums:
        value = value.strip()
        if value.startswith("${") and value.endswith("}"):
            property_name = value[2:-1]
            value = module_properties.get(
                property_name,
                coverage_properties.get(property_name, ""),
            )
        if re.fullmatch(r"\d+(?:\.\d+)?", value):
            minimums.append(float(value))
        else:
            fail(f"{relative} has an unresolved JaCoCo minimum: {value}")
    if not minimums or any(value <= 0 for value in minimums):
        fail(f"{relative} has a zero or missing JaCoCo minimum")
    for package in critical_packages:
        for pattern in (package, f"{package}.*"):
            if pattern not in check_text:
                fail(f"{relative} has no recursive critical coverage rule for {pattern}")
    configuration = plugin.find("configuration")
    if configuration is not None:
        for exclude in configuration.findall(".//exclude"):
            value = (exclude.text or "").strip()
            if value not in allowed_excludes:
                fail(f"{relative} excludes non-approved source pattern: {value}")

for relative in (
    "apps/console/package.json",
    "apps/management/package.json",
    "packages/auth-core/package.json",
):
    package = json.loads((root / relative).read_text(encoding="utf-8"))
    coverage_script = package.get("scripts", {}).get("test:coverage", "")
    if not coverage_script:
        fail(f"{relative} has no test:coverage script")
    if "passWithNoTests" in coverage_script:
        fail(f"{relative} coverage script allows an empty test run")
    if "--run" not in coverage_script or "--coverage" not in coverage_script:
        fail(f"{relative} test:coverage must execute a one-shot coverage run")
    if "@vitest/coverage-v8" not in package.get("devDependencies", {}):
        fail(f"{relative} does not declare @vitest/coverage-v8")

for relative in (
    "apps/console/vitest.config.ts",
    "apps/management/vitest.config.ts",
    "packages/auth-core/vitest.config.ts",
):
    text = (root / relative).read_text(encoding="utf-8")
    if not re.search(r"provider:\s*[\"']v8[\"']", text):
        fail(f"{relative} does not select the V8 coverage provider")
    if re.search(r"\ball\s*:", text):
        fail(f"{relative} uses the removed Vitest coverage.all option")
    if not re.search(r"include:\s*\[[^\]]*\*\*[^\]]*(?:ts|vue)", text, re.DOTALL):
        fail(f"{relative} does not configure an explicit production source include")
    for excluded in ("**/*.d.ts", "**/__tests__/**", "**/*.spec.*", "**/*.test.*"):
        if excluded not in text:
            fail(f"{relative} does not exclude test/type declaration source: {excluded}")
    if "**/coverage/**" not in text:
        fail(f"{relative} does not exclude generated coverage artifacts")
    if "reportsDirectory: \"../coverage\"" not in text and "reportsDirectory: '../coverage'" not in text:
        fail(f"{relative} does not pin the coverage report directory")
    threshold_match = re.search(r"thresholds:\s*\{(.*?)\n\s*\}", text, re.DOTALL)
    if not threshold_match:
        fail(f"{relative} has no coverage thresholds")
    thresholds = {
        name: float(value)
        for name, value in re.findall(
            r"(statements|branches|functions|lines):\s*(\d+(?:\.\d+)?)",
            threshold_match.group(1),
        )
    }
    if set(thresholds) != {"statements", "branches", "functions", "lines"}:
        fail(f"{relative} must configure all four coverage thresholds")
    if any(value <= 0 for value in thresholds.values()):
        fail(f"{relative} has a zero coverage threshold")
    remediated_floor = {
        "apps/console/vitest.config.ts":
            {"statements": 22, "branches": 18, "functions": 17, "lines": 23},
        "apps/management/vitest.config.ts":
            {"statements": 12, "branches": 8, "functions": 10, "lines": 12},
        "packages/auth-core/vitest.config.ts":
            {"statements": 46, "branches": 43, "functions": 39, "lines": 49},
    }[relative]
    for metric, floor in remediated_floor.items():
        if thresholds[metric] < floor:
            fail(
                f"{relative} {metric} threshold {thresholds[metric]} is below "
                f"the remediated floor {floor}"
            )

wrapper = (root / "scripts/dev/test.sh").read_text(encoding="utf-8")
for required in (
    "scripts/test/coverage-contract.sh",
    "\"${MAVEN[@]}\" verify -B",
    "pnpm test:coverage",
):
    if required not in wrapper:
        fail(f"scripts/dev/test.sh does not execute {required}")

if "<coverage.bundle.minimum>" not in services_pom:
    fail("services/pom.xml has no shared coverage threshold property")
if "<coverage.bundle.submission.minimum>" not in services_pom:
    fail("services/pom.xml has no explicit Submission coverage threshold property")
submission_pom = (root / "services/submission/pom.xml").read_text(encoding="utf-8")
if "${coverage.bundle.submission.minimum}" not in submission_pom:
    fail("services/submission/pom.xml must use the explicit Submission coverage threshold")
if submission_pom.count("${coverage.bundle.submission.minimum}") != 1:
    fail("services/submission/pom.xml must reference the Submission threshold exactly once")
if "<coverage.bundle.minimum>" in submission_pom or "${coverage.bundle.minimum}" in submission_pom:
    fail("services/submission/pom.xml must not override the shared coverage threshold property")
if "<contract.compat.oldVersion>__missing_contract_baseline__</contract.compat.oldVersion>" not in services_pom:
    fail("services/pom.xml must fail closed when no compatibility baseline is supplied")

if os.environ.get("ULTI_STATIC_ONLY") == "1":
    print("Maven coverage fixtures: SKIPPED_STATIC_ONLY")
    print("coverage-contract: PASS (static source/config assertions)")
    raise SystemExit(0)

maven = ["./mvnw"]
if shutil.which("mise"):
    maven = ["mise", "exec", "java@zulu-17.68.203.0", "--", "./mvnw"]

sentinel_command = maven + [
    "-pl",
    "api/auth-api",
    "-am",
    "-P",
    "contract-compat",
    "-DskipTests",
    "validate",
    "-B",
]
with tempfile.TemporaryFile(mode="w+") as log:
    sentinel_result = subprocess.run(
        sentinel_command,
        cwd=root / "services",
        stdout=log,
        stderr=subprocess.STDOUT,
        text=True,
    )
    log.seek(0)
    sentinel_output = log.read()
if sentinel_result.returncode == 0:
    fail("contract compatibility sentinel unexpectedly passed without a baseline")
if "contract.compat.oldVersion" not in sentinel_output:
    fail("contract compatibility sentinel failed without identifying the missing baseline")

command = maven + [
    "-pl",
    "auth",
    "-am",
    "-Dtest=JwtTokenProviderTest",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "-Dcoverage.bundle.minimum=1.0",
    "verify",
    "-B",
]
with tempfile.TemporaryFile(mode="w+") as log:
    result = subprocess.run(
        command,
        cwd=root / "services",
        stdout=log,
        stderr=subprocess.STDOUT,
        text=True,
    )
    log.seek(0)
    output = log.read()
if result.returncode == 0:
    fail("negative fixture unexpectedly passed at a 100% bundle threshold")
if "Rule violated" not in output and "violated" not in output.lower():
    fail("negative fixture failed for a non-coverage reason")
print("coverage gate wiring: PASS")
print("coverage negative fixture: PASS (JaCoCo rejected the 100% threshold)")
print("contract baseline sentinel: PASS (missing baseline rejected)")
print("coverage-contract: PASS")
PY
