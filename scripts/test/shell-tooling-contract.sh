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
    compose_probe = subprocess.run(
        ["bash", "-c", """set -euo pipefail
source "$1/scripts/dev/lib/common.sh"
devstack_compose_args default
printf '%s\\n' "${default[@]}"
printf '%s\\n' --
devstack_compose_args observability --observability
printf '%s\\n' "${observability[@]}"
printf '%s\\n' --
devstack_compose_args override --base-only "$1/override.yml"
printf '%s\\n' "${override[@]}"
""", "fixture", str(root)], capture_output=True, text=True,
        env={**os.environ, "ENV_FILE": str(root / ".env")},
    )
    assert compose_probe.returncode == 0, compose_probe.stderr
    compose_output = compose_probe.stdout.splitlines()
    sections, section = [], []
    for line in compose_output:
        if line == "--":
            sections.append(section)
            section = []
        else:
            section.append(line)
    sections.append(section)
    base = [
        "docker", "compose", "--project-directory", str(root), "--env-file", str(root / ".env"),
        "-f", str(root / "docker/docker-compose.yml"), "-f", str(root / "docker/docker-compose.dev.yml"),
    ]
    assert sections == [
        base,
        base + ["--profile", "observability", "-f", str(root / "docker/docker-compose.observability.yml")],
        ["docker", "compose", "--project-directory", str(root), "--env-file", str(root / ".env"),
         "-f", str(root / "docker/docker-compose.yml"), "-f", str(root / "override.yml")],
    ], sections
    print("shared Compose command builder and argv order: PASS")

    acl_root = work / "acl-repo"
    (acl_root / "scripts/dev").mkdir(parents=True)
    shutil.copytree(root / "scripts/dev/lib", acl_root / "scripts/dev/lib")
    generator = acl_root / "docker/redis/generate-users-acl.sh"
    generator.parent.mkdir(parents=True)
    shutil.copy2(root / "docker/redis/generate-users-acl.sh", generator)
    generator.chmod(0o755)
    credentials = {"PATH": os.environ["PATH"], "ENV_FILE": str(acl_root / ".env")}
    for prefix in ("AUTH", "ADMIN", "APP", "SUBMISSION", "SEARCH", "NOTIFICATION", "JUDGE", "OPS", "HEALTH"):
        credentials[f"{prefix}_REDIS_PASSWORD"] = f"contract-{prefix.lower()}-password"
    credentials["REDIS_REPLICATION_PASSWORD"] = "contract-replication-password"
    credentials["REDIS_SENTINEL_PASSWORD"] = "contract-sentinel-password"

    acl_probe = subprocess.run(
        ["bash", "-c", """set -euo pipefail
source "$1/scripts/dev/lib/common.sh"
unset REDIS_ACL_DIR REDIS_ACL_FILE
materialize_redis_acl relative-acl
bash -c 'test -n "$REDIS_ACL_DIR" && test -n "$REDIS_ACL_FILE"'
printf 'dir=%s\nfile=%s\nmaterialized=%s\n' "$REDIS_ACL_DIR" "$REDIS_ACL_FILE" "$REDIS_ACL_MATERIALIZED"
""", "fixture", str(acl_root)], capture_output=True, text=True,
        env={**os.environ, **credentials},
    )
    assert acl_probe.returncode == 0, acl_probe.stderr
    acl_output = acl_probe.stdout.splitlines()
    acl_file = acl_root / "relative-acl/users.acl"
    assert acl_output == [
        f"dir={acl_root / 'relative-acl'}",
        f"file={acl_file}",
        "materialized=true",
    ], acl_output
    acl_contents = acl_file.read_text(encoding="utf-8")
    assert "user default off" in acl_contents
    assert all(secret not in acl_contents for secret in credentials.values() if "password" in secret)
    assert acl_file.stat().st_mode & 0o777 == 0o644

    absolute_file = work / "absolute-acl/custom.acl"
    absolute_probe = subprocess.run(
        ["bash", "-c", """set -euo pipefail
source "$1/scripts/dev/lib/common.sh"
REDIS_ACL_DIR="$1/absolute-acl"
REDIS_ACL_FILE="$2"
materialize_redis_acl ignored-default
printf '%s\n%s\n' "$REDIS_ACL_DIR" "$REDIS_ACL_FILE"
""", "fixture", str(acl_root), str(absolute_file)],
        capture_output=True, text=True, env={**os.environ, **credentials},
    )
    assert absolute_probe.returncode == 0, absolute_probe.stderr
    assert absolute_probe.stdout.splitlines() == [str(acl_root / "absolute-acl"), str(absolute_file)]
    assert absolute_file.exists()
    assert (acl_root / "absolute-acl").stat().st_mode & 0o777 == 0o755

    generator.unlink()
    missing_probe = subprocess.run(
        ["bash", "-c", """set -euo pipefail
source "$1/scripts/dev/lib/common.sh"
if materialize_redis_acl missing-acl; then
  exit 99
else
  printf 'status=%s\n' "$?"
fi
""", "fixture", str(acl_root)], capture_output=True, text=True, env={**os.environ, **credentials},
    )
    assert missing_probe.returncode == 0, missing_probe.stderr
    assert "status=1" in missing_probe.stdout
    assert "Missing Redis ACL generator" in missing_probe.stderr

    generator.write_text("#!/usr/bin/env bash\nexit 17\n", encoding="utf-8")
    generator.chmod(0o755)
    failure_probe = subprocess.run(
        ["bash", "-c", """set -euo pipefail
source "$1/scripts/dev/lib/common.sh"
if materialize_redis_acl failure-acl; then
  exit 99
else
  printf 'status=%s\n' "$?"
fi
""", "fixture", str(acl_root)], capture_output=True, text=True, env={**os.environ, **credentials},
    )
    assert failure_probe.returncode == 0, failure_probe.stderr
    assert "status=17" in failure_probe.stdout

    init_root = work / "init-env-repo"
    (init_root / "scripts/dev").mkdir(parents=True)
    shutil.copytree(root / "scripts/dev/lib", init_root / "scripts/dev/lib")
    shutil.copy2(root / "scripts/dev/init-env.sh", init_root / "scripts/dev/init-env.sh")
    init_output = init_root / "private/.env"
    init_probe = subprocess.run(
        ["bash", str(init_root / "scripts/dev/init-env.sh"), "--output", str(init_output), "--force"],
        cwd=init_root, capture_output=True, text=True, timeout=30,
        env={**os.environ, "ENV_FILE": str(init_root / ".env")},
    )
    assert init_probe.returncode == 0, (init_probe.stdout, init_probe.stderr)
    assert "WARNING: docker/redis/generate-users-acl.sh not found" in init_probe.stderr
    assert init_output.exists() and init_output.stat().st_mode & 0o777 == 0o600
    assert not (init_root / ".local/redis/users.acl").exists()

    init_generator = init_root / "docker/redis/generate-users-acl.sh"
    init_generator.parent.mkdir(parents=True)
    shutil.copy2(root / "docker/redis/generate-users-acl.sh", init_generator)
    init_generator.chmod(0o755)
    init_success_output = init_root / "private-success/.env"
    init_success_probe = subprocess.run(
        ["bash", str(init_root / "scripts/dev/init-env.sh"), "--output", str(init_success_output), "--force"],
        cwd=init_root, capture_output=True, text=True, timeout=30,
        env={**os.environ, "ENV_FILE": str(init_root / ".env")},
    )
    assert init_success_probe.returncode == 0, (init_success_probe.stdout, init_success_probe.stderr)
    assert "Materialized Redis ACL file:" in init_success_probe.stdout
    assert (init_root / ".local/redis/users.acl").exists()
    print("Redis ACL helper paths, permissions, warning-only init, hash-only output and failure propagation: PASS")
print("shell-tooling-contract: PASS")
PY
