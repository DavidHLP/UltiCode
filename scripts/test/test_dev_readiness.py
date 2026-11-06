#!/usr/bin/env python3
"""Offline contracts: no live services, raw responses or relaxed readiness."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/ci'))
def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
health = load('health', 'scripts/dev/readiness-diagnostics.py')
report = load('report', 'scripts/ci/acceptance_report.py')
SOURCE = (ROOT / 'scripts/dev/up.sh').read_text()
FUNCTIONS = SOURCE[SOURCE.index('check_url() {'):SOURCE.index('for _ in $(seq 1 "$DEVSTACK_SERVICE_READINESS_ATTEMPTS"); do', SOURCE.index('check_url() {'))]

class ReadinessTests(unittest.TestCase):
    def shell(self, body):
        with tempfile.TemporaryDirectory() as root:
            return subprocess.run(['bash', '-c', 'set -euo pipefail\nROOT_DIR="$1"\n' + FUNCTIONS + '\n' + body, 'test', root], text=True, capture_output=True, check=True).stdout

    def test_ipv4_ipv6_success_and_failure_enums(self):
        result = self.shell('''
check_url() { case "$1" in *127.0.0.1*) echo 503;; *) echo 200;; esac; }
check_port 9103 /fixed
[[ "$READINESS_DETAIL" == http_200 ]]
check_url() { echo 'private-value'; }
if check_port 9103 /fixed; then exit 1; fi
[[ "$READINESS_DETAIL" == http_other/http_other ]]
check_url() { echo 503; }
if check_port 9103 /fixed; then exit 1; fi
[[ "$READINESS_DETAIL" == http_503/http_503 ]]
echo PASS
''')
        self.assertEqual(result.strip(), 'PASS')

    def test_pm2_requires_online_and_current_banner(self):
        self.shell('''
pm2_load_records() { :; }
pm2_record_is_online() { false; }
devstack_readiness_banner() { echo 'Started Worker'; }
if check_pm2_online ulticode-submission; then exit 1; fi
[[ "$READINESS_DETAIL" == pm2_not_online ]]
pm2_record_is_online() { true; }
if check_pm2_online ulticode-submission; then exit 1; fi
[[ "$READINESS_DETAIL" == pm2_log_missing ]]
mkdir -p "$ROOT_DIR/logs"
echo 'Started Worker' > "$ROOT_DIR/logs/backend-submission.out.log"
wc -c < "$ROOT_DIR/logs/backend-submission.out.log" > "$ROOT_DIR/logs/.submission-ready-offset"
if check_pm2_online ulticode-submission; then exit 1; fi
[[ "$READINESS_DETAIL" == pm2_banner_missing ]]
echo 'Started Worker' >> "$ROOT_DIR/logs/backend-submission.out.log"
check_pm2_online ulticode-submission
[[ "$READINESS_DETAIL" == pm2_banner_observed ]]
''')

    def test_last_cycle_replaces_earlier_failure(self):
        self.shell('''
SELECTED_READINESS=('ulticode-app|http|9103|/fixed' 'ulticode-search|pm2|0|')
check_port() { READINESS_DETAIL=http_503/http_503; return 1; }
check_pm2_online() { READINESS_DETAIL=pm2_banner_observed; return 0; }
if check_selected_readiness; then exit 1; fi
[[ "${READINESS_RESULTS[ulticode-app]}" == 'failed http_503/http_503' ]]
check_port() { READINESS_DETAIL=http_200; return 0; }
check_selected_readiness
[[ "${READINESS_RESULTS[ulticode-app]}" == 'ready http_200' ]]
SELECTED_READINESS=('ulticode-app|unknown|0|')
if check_selected_readiness; then exit 1; fi
[[ "${READINESS_RESULTS[ulticode-app]}" == 'failed unknown_probe_kind' ]]
''')

    def test_acceptance_snapshot_maps_each_completed_cycle(self):
        self.shell(''' 
ACCEPTANCE_DISPOSABLE=true
declare -A ACCEPTANCE_LAST_READINESS=()
SELECTED_READINESS=('ulticode-app|http|9103|/fixed' 'ulticode-submission|pm2|0|')
check_port() { READINESS_DETAIL=http_503/http_503; return 1; }
check_pm2_online() { READINESS_DETAIL=pm2_banner_observed; return 0; }
if check_selected_readiness; then exit 1; fi
[[ "${ACCEPTANCE_LAST_READINESS[ulticode-app]}" == not_ready ]]
[[ "${ACCEPTANCE_LAST_READINESS[ulticode-submission]}" == ready ]]
check_port() { READINESS_DETAIL=http_200; return 0; }
check_selected_readiness
[[ "${ACCEPTANCE_LAST_READINESS[ulticode-app]}" == ready ]]
SELECTED_READINESS=('ulticode-submission|unknown|0|')
if check_selected_readiness; then exit 1; fi
[[ "${ACCEPTANCE_LAST_READINESS[ulticode-submission]}" == not_ready ]]
''')

    def test_regular_readiness_does_not_require_acceptance_state(self):
        self.shell('''
unset ACCEPTANCE_DISPOSABLE ACCEPTANCE_LAST_READINESS
SELECTED_READINESS=('ulticode-app|http|9103|/fixed')
check_port() { READINESS_DETAIL=http_200; return 0; }
check_selected_readiness
! declare -p ACCEPTANCE_LAST_READINESS >/dev/null 2>&1
''')

    def test_diagnostics_remain_after_success_exit_and_keep_snapshot_hook(self):
        self.assertEqual(SOURCE.count('trap acceptance_launch_snapshot EXIT'), 1)
        self.assertEqual(SOURCE.count('timeout --kill-after=2s 25s python3 -u'), 1)
        tail = SOURCE[SOURCE.index('check_url() {'):]
        self.assertLess(tail.index('    exit 0'), tail.index('\nemit_final_readiness\n'))
        self.assertTrue(tail.rstrip().endswith('exit 1'))

    def test_hung_diagnostic_is_killed_without_changing_failure_exit(self):
        # Exercise the production TERM/KILL wrapper with shorter test durations.
        wrapper = next(line for line in SOURCE.splitlines() if line.startswith('timeout --kill-after='))
        self.assertIn('--kill-after=2s 25s python3 -u', wrapper)
        with tempfile.TemporaryDirectory() as root:
            script = Path(root) / 'scripts/dev/readiness-diagnostics.py'
            script.parent.mkdir(parents=True)
            script.write_text('import signal,time\nsignal.signal(signal.SIGTERM, signal.SIG_IGN)\nwhile True: time.sleep(0.01)\n')
            command = wrapper.replace('--kill-after=2s 25s', '--kill-after=0.1s 0.2s')
            started = time.monotonic()
            result = subprocess.run(['bash', '-c', 'ROOT_DIR="$1"; PM2_APPS=ulticode-app; ' + command + '; echo cleanup-ran; exit 1', 'test', root], capture_output=True, text=True, timeout=3)
            self.assertEqual(result.returncode, 1)
            self.assertIn('cleanup-ran', result.stdout)
            self.assertLess(time.monotonic() - started, 2)

    def test_known_components_only(self):
        body = json.dumps({'components': {'db':'DOWN', 'redis':'UP', 'private-key':'private-value', 'storage':'private-value'}}).encode()
        status, components = health.classify(503, body, ('db','redis','storage'))
        self.assertEqual((status, components), ('http_503', {'db':'down','redis':'up','storage':'unknown'}))
        self.assertNotIn('private', json.dumps(components))

    def test_invalid_oversize_and_redirect(self):
        for code, body, expected in [(200,b'bad','invalid_response'),(200,b'[]','invalid_response'),(200,b'x'*(health.LIMIT+1),'oversized_response'),(302,b'private-url','http_other')]:
            self.assertEqual(health.classify(code, body, ('db',)), (expected, {'db':'unknown'}))

    def test_probe_has_fixed_destination_limit_and_no_redirect(self):
        seen = []
        class Connection:
            def __init__(self, *args, **kwargs): seen.append((args, kwargs))
            def request(self, *args): seen.append(args)
            def getresponse(self): return self
            status = 302
            def read(self, count): seen.append(count); return b'private-url'
            def close(self): seen.append('closed')
        result = health.probe('ulticode-notification', Connection)
        self.assertEqual(result[0], 'http_other')
        self.assertEqual(seen, [(('127.0.0.1',9105),{'timeout':5}),('GET','/api/v1/notification/health/ready'),health.LIMIT+1,'closed'])

    def test_exception_text_never_emitted(self):
        def fail(*args, **kwargs): raise OSError('https://user:secret@private.example')
        with patch.object(health, 'probe', side_effect=lambda app: health.probe_original(app, fail)), contextlib.redirect_stdout(io.StringIO()) as out:
            health.main(['ulticode-app,private-app'])
        self.assertNotIn('secret', out.getvalue())
        self.assertNotIn('private', out.getvalue())
        self.assertIn('state=unreachable', out.getvalue())

    def test_parser_last_markers_and_fixed_vocabulary(self):
        text = '\n'.join(['ULTICODE_FINAL_READINESS app=ulticode-app state=failed detail=http_503/http_503', 'ULTICODE_FINAL_READINESS app=ulticode-app state=ready detail=http_200', 'ULTICODE_FINAL_READINESS app=private-app state=failed detail=http_503', 'ULTICODE_FINAL_READINESS app=ulticode-app state=failed detail=private-value', 'ULTICODE_FINAL_HEALTH app=ulticode-admin state=http_503', 'ULTICODE_FINAL_COMPONENT app=ulticode-admin component=problemOwnerRpc state=down', 'ULTICODE_FINAL_COMPONENT app=ulticode-admin component=private-key state=up'])
        data = report.final_probes(text)
        self.assertEqual(data['final_readiness']['ulticode-app']['state'], 'ready')
        self.assertEqual(data['final_dependency_probes']['ulticode-admin']['components']['problemOwnerRpc'], 'down')
        self.assertFalse(data['final_readiness']['ulticode-search']['observed'])
        self.assertNotIn('private', json.dumps(data))

    def test_search_actual_entrypoint(self):
        with tempfile.TemporaryDirectory() as root:
            log = Path(root)/'search.log'
            log.write_text('Started SearchWorkerApplication in 1 seconds')
            self.assertTrue(report.summary([log])['startup_signals']['spring_context_started'])

health.probe_original = health.probe
if __name__ == '__main__': unittest.main()
