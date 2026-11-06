#!/usr/bin/env python3
"""No Docker required: verify fixture ownership, fail-closed orchestration and cleanup.

Doubles test the launcher only; they are not real Nacos integration evidence.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NacosIntegrationLauncherTest(unittest.TestCase):
    def run_fixture(self, failure=""):
        with tempfile.TemporaryDirectory(prefix="nacos-launcher-test-") as directory:
            root = Path(directory)
            repo = root / "repo with spaces"
            for relative in ("scripts/test", "scripts/security", "services", "tmp", "bin"):
                (repo / relative).mkdir(parents=True)
            shutil.copy2(ROOT / "scripts/test/nacos-client-integration.sh", repo / "scripts/test")
            (repo / ".env").write_text("touch MUST_NOT_EXECUTE\n")
            calls = root / "calls.jsonl"
            bootstrap = repo / "scripts/security/bootstrap-nacos-user.sh"
            bootstrap.write_text('''#!/usr/bin/env bash
set -euo pipefail
[[ "$NACOS_EXPECTED_DOCKER_PROJECT" == ulticode-nacos-client-* ]]
[[ "$MYSQL_CONTAINER" == fixture-mysql ]]
[[ "$NACOS_NAMESPACE" == protobuf-fixture ]]
[[ ${#AUTH_NACOS_PASSWORD} -ge 32 ]]
[[ "$NACOS_PASSWORD" != "$AUTH_NACOS_PASSWORD" ]]
[[ "$AUTH_NACOS_PASSWORD" != "$NACOS_FIXTURE_WRONG_PASSWORD" ]]
''')
            maven = repo / "services/mvnw"
            maven.write_text('''#!/usr/bin/env bash
set -euo pipefail
[[ "$*" != *"use_unsafe"* ]]
[[ "$FAILURE" != build ]] || exit 23
for argument in "$@"; do
  if [[ "$argument" == -Dmdep.outputFile=* ]]; then
    printf '/fixture/dependencies.jar' > "${argument#*=}"
  fi
done
''')
            maven.chmod(0o755)
            docker = repo / "bin/docker"
            docker.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ['CALLS'], 'a') as output:
    output.write(json.dumps(args) + '\\n')
if args[:2] == ['context', 'inspect']:
    print('ssh://forbidden' if os.environ['FAILURE'] == 'remote' else 'unix:///fixture.sock')
elif args == ['compose', 'version']:
    pass
elif args[0] == 'compose':
    assert '--project-name' in args and '--env-file' in args, args
    project = args[args.index('--project-name') + 1]
    assert project.startswith('ulticode-nacos-client-'), project
    env_file = pathlib.Path(args[args.index('--env-file') + 1])
    assert env_file.name == 'fixture.env' and env_file.stat().st_mode & 0o077 == 0
    assert 'NACOS_AUTH_TOKEN=' in env_file.read_text()
    if 'ps' in args:
        print('fixture-mysql')
    elif 'up' in args:
        print(os.environ['NACOS_AUTH_TOKEN'])  # must remain in private log
        if os.environ['FAILURE'] == 'start': sys.exit(29)
    elif 'stop' in args:
        assert args[-1] == 'nacos'
    elif 'down' in args:
        assert '--volumes' in args and '--remove-orphans' in args
        if os.environ['FAILURE'] == 'cleanup': sys.exit(31)
    else:
        raise SystemExit('Unexpected compose action')
elif args[:3] == ['exec', '-i', 'fixture-mysql']:
    lines = sys.stdin.read().splitlines()
    assert lines[0] == os.environ['MYSQL_ROOT_PASSWORD']
    assert 'INSERT INTO tenant_info' in lines[1]
else:
    raise SystemExit('Unexpected Docker command')
''')
            docker.chmod(0o755)
            java = repo / "bin/java"
            java.write_text('''#!/usr/bin/env python3
import os, pathlib, sys, time
assert '-Dcom.google.protobuf.error_on_unsafe_pre22_gencode=true' in sys.argv
assert not any('use_unsafe' in arg for arg in sys.argv)
assert os.environ['HOME'] != os.environ['ORIGINAL_HOME']
control = pathlib.Path(os.environ['NACOS_FIXTURE_CONTROL'])
print(os.environ['AUTH_NACOS_PASSWORD'])  # must remain in private log
if os.environ['FAILURE'] == 'client':
    (control / 'failure').write_text('phase=wrong-auth-rejection\\nexception=java.lang.AssertionError\\nline=123\\n' + os.environ['AUTH_NACOS_PASSWORD'])
    sys.exit(37)
(control / 'ready-for-outage').touch()
for requested, observed in [('server-stopped', 'observed-disconnect'), ('server-restarted', 'finished')]:
    deadline = time.monotonic() + 15
    while not (control / requested).exists():
        assert time.monotonic() < deadline
        time.sleep(0.01)
    (control / observed).touch()
''')
            java.chmod(0o755)
            env = {**os.environ, "PATH": f"{repo / 'bin'}:{os.environ['PATH']}",
                   "TMPDIR": str(repo / "tmp"), "CALLS": str(calls), "FAILURE": failure,
                   "ORIGINAL_HOME": os.environ.get("HOME", ""), "DOCKER_HOST": ""}
            result = subprocess.run(["bash", str(repo / "scripts/test/nacos-client-integration.sh")],
                                    cwd=repo, env=env, capture_output=True, text=True, timeout=30)
            events = [json.loads(line) for line in calls.read_text().splitlines()]
            self.assertFalse((repo / "MUST_NOT_EXECUTE").exists())
            self.assertFalse(list((repo / "tmp").iterdir()), "temporary secrets/logs leaked")
            self.assertNotRegex(result.stdout + result.stderr, r"[a-f0-9]{64}", "secret printed")
            self.assertNotIn("dependencies.jar", result.stdout + result.stderr)
            return result, events

    def test_success_restarts_only_owned_fixture_and_cleans(self):
        result, events = self.run_fixture()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PASS:", result.stdout)
        self.assertEqual(sum("stop" in event for event in events), 1)
        self.assertIn("down", events[-1])
        projects = {event[event.index('--project-name') + 1] for event in events if '--project-name' in event}
        self.assertEqual(len(projects), 1)

    def test_failures_are_not_skips_or_success(self):
        for failure in ("build", "start", "client", "cleanup"):
            with self.subTest(failure=failure):
                result, events = self.run_fixture(failure)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("PASS:", result.stdout)
                self.assertIn("down", events[-1])
                if failure == "client":
                    self.assertIn("Client diagnostic: phase=wrong-auth-rejection", result.stderr)
                    self.assertIn("Client diagnostic: exception=java.lang.AssertionError", result.stderr)
                if failure in ("build", "start", "client"):
                    self.assertFalse(any("stop" in event for event in events))

    def test_remote_context_is_rejected_before_mutation(self):
        result, events = self.run_fixture("remote")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(events), 1)
        self.assertIn("Remote Docker contexts are not permitted", result.stderr)


if __name__ == "__main__":
    unittest.main()
