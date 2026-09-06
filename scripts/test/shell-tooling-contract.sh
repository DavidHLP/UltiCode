#!/usr/bin/env bash
# No infrastructure: exercise shared assertions and baseline orchestration
# against temporary files and a Docker double that rejects unknown commands.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

root = Path(sys.argv[1])
with tempfile.TemporaryDirectory(prefix="ulticode-shell-tooling-") as directory:
    work = Path(directory)
    sample = work / "source file"
    sample.write_text("literal [value].*\n-leading-option\n")
    helper = root / "scripts/test/lib/assertions.sh"
    command = '''set -euo pipefail
ROOT_DIR="$1"
fail() { printf 'fixture: %s\\n' "$*" >&2; exit 1; }
source "$2"
"$3" "$4" "$5"
'''
    for operation, name, needle, expected in [
        ("contains", sample.name, "[value].*", 0),
        ("contains", sample.name, "-leading-option", 0),
        ("contains", sample.name, "absent", 1),
        ("not_contains", sample.name, "absent", 0),
        ("not_contains", sample.name, "[value].*", 1),
        ("contains", "missing", "absent", 1),
        ("not_contains", "missing", "absent", 1),
    ]:
        result = subprocess.run(
            ["bash", "-c", command, "fixture", str(work), str(helper),
             operation, name, needle], capture_output=True, text=True,
        )
        assert result.returncode == expected, (operation, result.stderr)
    error_bin = work / "error-bin"
    error_bin.mkdir()
    (error_bin / "grep").write_text("#!/bin/sh\nexit 2\n")
    (error_bin / "grep").chmod(0o755)
    for operation in ("contains", "not_contains"):
        result = subprocess.run(
            ["bash", "-c", command, "fixture", str(work), str(helper),
             operation, sample.name, "absent"], capture_output=True, text=True,
            env={**os.environ, "PATH": f"{error_bin}:{os.environ['PATH']}"},
        )
        assert result.returncode == 1, (operation, "read error passed")
        assert "fixture:" in result.stderr, result.stderr
    print("literal assertions, missing files and read errors: PASS")

    fixture = work / "repo with spaces"
    scripts = fixture / "init-db/scripts"
    scripts.mkdir(parents=True)
    for name in ("generate-baseline.sh", "validate-baseline.sh"):
        shutil.copy2(root / "init-db/scripts" / name, scripts / name)
    owners = ("auth", "admin", "app", "notification", "submission")
    schemas = ("ulticode", *owners)
    for owner in owners:
        shutil.copy2(root / f"init-db/flyway-{owner}.conf", fixture / "init-db")
    baseline = fixture / "init-db/baseline/baseline.sql"
    baseline.parent.mkdir()
    original = "".join(
        f"CREATE DATABASE `{schema}`;\nUSE `{schema}`;\nCREATE TABLE `sample` (id INT);\n"
        for schema in schemas
    )
    baseline.write_text(original)
    fake_bin = work / "fake-bin"
    fake_bin.mkdir()
    docker = fake_bin / "docker"
    docker.write_text('''#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
with open(os.environ["DOCKER_CALLS"], "a") as log:
    log.write(json.dumps(args) + "\\n")
failure = os.environ.get("BASELINE_FAILURE", "")
if args[:3] == ["run", "-d", "--rm"]:
    print("disposable-fixture")
elif args[:2] == ["rm", "-f"]:
    assert args[2:] == ["disposable-fixture"], args
elif args[:1] == ["run"] and "flyway/flyway:10.17.0" in args:
    if failure == "migration": sys.exit(17)
elif args[:2] == ["exec", "disposable-fixture"] and "mysql" in args:
    pass
elif args[:2] == ["exec", "disposable-fixture"] and "mysqldump" in args:
    if failure == "dump": sys.exit(17)
    schema = args[args.index("--databases") + 1]
    table = "changed" if failure == "parity" and schema == "app" else "sample"
    print(f"CREATE DATABASE `{schema}`;\\nUSE `{schema}`;\\nCREATE TABLE `{table}` (id INT);")
else:
    raise SystemExit("unexpected Docker invocation: " + repr(args))
''')
    docker.chmod(0o755)
    temp = work / "temps"
    temp.mkdir()
    calls = work / "docker-calls.jsonl"
    for failure, expected in (("", 0), ("migration", 17), ("dump", 17), ("parity", 1), ("history", 1)):
        calls.write_text("")
        contents = original + ("-- flyway_post_owner_history\n" if failure == "history" else "")
        baseline.write_text(contents)
        result = subprocess.run(
            ["bash", str(scripts / "validate-baseline.sh")], cwd=work,
            env={**os.environ, "PATH": f"{fake_bin}:{os.environ['PATH']}",
                 "TMPDIR": str(temp), "DOCKER_CALLS": str(calls), "BASELINE_FAILURE": failure},
            capture_output=True, text=True, timeout=20,
        )
        assert result.returncode == expected, (failure, result.stdout, result.stderr)
        assert baseline.read_text() == contents, "validation changed the baseline"
        assert not list(temp.iterdir()), (failure, "temporary files leaked")
        events = [json.loads(line) for line in calls.read_text().splitlines()]
        assert events[-1] == ["rm", "-f", "disposable-fixture"], "container cleanup missing"
        if not failure:
            migrations = [event for event in events if "migrate" in event]
            locations = [next(arg for arg in event if arg.startswith("-locations=")) for event in migrations]
            assert locations == [f"-locations=filesystem:/flyway/init-db/{location}" for location in
                                 ("migrations/*.sql", *(f"migrations/{owner}" for owner in owners), "migrations/post-owner")]
            dumps = [event for event in events if "mysqldump" in event]
            assert [event[event.index("--databases") + 1] for event in dumps] == list(schemas)
            for schema, event in zip(schemas, dumps):
                assert f"--ignore-table={schema}.flyway_schema_history" in event
            assert "--ignore-table=ulticode.flyway_post_owner_history" in dumps[0]
    print("baseline generation reuse, order, parity, history rejection and failure cleanup: PASS")
print("shell-tooling-contract: PASS")
PY
