#!/usr/bin/env python3
import io
import json
import os
import shutil
import subprocess
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/ci'))
import acceptance_readiness as module

PROJECT = 'ulticode-acceptance-123-456'


class ReadinessTests(unittest.TestCase):
    def test_submission_first_start_requires_ready_without_any_restart(self):
        good = module.capture('ulticode-submission', ['ulticode-submission=ready'],
            'ok', [{'name': 'ulticode-submission', 'pm2_env': {
                'status': 'online', 'restart_time': 0, 'pm_uptime': 1000}}], 7000)
        self.assertTrue(module.submission_first_start(good))
        for field, values in {
            'restart_count': (1, None, True),
            'status': ('errored', 'unknown'),
            'last_readiness': ('not_ready', 'not_checked'),
            'pm2_collection': ('failed', 'missing', 'invalid'),
        }.items():
            for value in values:
                with self.subTest(field=field, value=value):
                    bad = json.loads(json.dumps(good))
                    bad['services']['ulticode-submission'][field] = value
                    self.assertFalse(module.submission_first_start(bad))
        for collection in ('missing', 'invalid', 'failed'):
            self.assertFalse(module.submission_first_start(good | {'collection': collection}))
        self.assertFalse(module.submission_first_start(module.empty('ok')))

    def test_first_start_command_rejects_restart_missing_and_stale_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            parent = root / '.local/acceptance'
            parent.mkdir(parents=True)
            good = module.capture('ulticode-submission', ['ulticode-submission=ready'],
                'ok', [{'name': 'ulticode-submission', 'pm2_env': {
                    'status': 'online', 'restart_time': 0, 'pm_uptime': 1000}}], 7000)
            target = parent / module.FILE
            environment = dict(os.environ, COMPOSE_PROJECT_NAME=PROJECT, ACCEPTANCE_DISPOSABLE='true')
            command = [sys.executable, str(ROOT / 'scripts/ci/acceptance_readiness.py'),
                       '--require-submission-first-start', directory]
            for restart, project, expected in ((0, PROJECT, 0), (1, PROJECT, 1),
                                               (0, PROJECT + '1', 1)):
                good['services']['ulticode-submission']['restart_count'] = restart
                target.write_text(json.dumps(good | {'run_project': project}))
                run = subprocess.run(command, env=environment, capture_output=True)
                self.assertEqual(run.returncode, expected, run.stderr.decode())
            target.unlink()
            self.assertEqual(subprocess.run(command, env=environment, capture_output=True).returncode, 1)
            environment['ACCEPTANCE_DISPOSABLE'] = 'false'
            self.assertEqual(subprocess.run(command, env=environment, capture_output=True).returncode, 1)
        source = (ROOT / 'scripts/ci/business-acceptance.sh').read_text()
        self.assertLess(source.index('--rebuild >'), source.index('--require-submission-first-start'))
        self.assertLess(source.index('--require-submission-first-start'), source.index('phase=auth-environment'))

    def test_all_services_and_last_observation_are_independent_of_pm2(self):
        result = module.capture(','.join(module.SERVICES),
            ['ulticode-app=not_ready', 'ulticode-app=ready', 'ulticode-search=not_ready'],
            'ok', [{'name': 'ulticode-search', 'pm2_env': {
                'status': 'online', 'restart_time': 3, 'exit_code': 1, 'pm_uptime': 1000,
                'env': {'PASSWORD': 'secret-canary'}, 'pm_exec_path': 'secret-canary'}}], 7000)
        self.assertEqual(set(result['services']), set(module.SERVICES))
        search = result['services']['ulticode-search']
        self.assertEqual(search, dict(last_readiness='not_ready', pm2_collection='ok',
            status='online', restart_count=3, exit_code=1, uptime_seconds=6))
        self.assertEqual(result['services']['ulticode-app']['last_readiness'], 'ready')
        self.assertEqual(result['services']['ulticode-9002']['last_readiness'], 'not_checked')
        self.assertEqual(result['services']['ulticode-notification']['pm2_collection'], 'missing')
        self.assertNotIn('secret-canary', json.dumps(result))

    def test_malicious_fields_are_not_serialized(self):
        result = module.capture('ulticode-app,secret-canary', ['ulticode-app=secret-canary'],
            'ok', [{'name': 'ulticode-app', 'pm2_env': {'status': ['secret-canary'],
                'restart_time': True, 'exit_code': -1, 'pm_uptime': 1e100}}], 10000)
        self.assertNotIn('secret-canary', json.dumps(result))
        item = result['services']['ulticode-app']
        self.assertEqual(item['status'], 'unknown')
        self.assertIsNone(item['restart_count'])
        self.assertIsNone(item['exit_code'])
        self.assertIsNone(item['uptime_seconds'])
        for value in (None, [], 'secret-canary', {'schema_version': True},
                      {'schema_version': 1, 'collection': [], 'services': {}}):
            self.assertNotIn('secret-canary', json.dumps(module.sanitize(value)))

    def test_collection_failure_duplicate_and_absent_records(self):
        for status in ('failed', 'invalid'):
            item = module.capture('ulticode-app', [], status, [], 1000)['services']['ulticode-app']
            self.assertEqual(item['pm2_collection'], status)
        item = module.capture('ulticode-app', [], 'ok', [{'name': 'ulticode-app'}] * 2, 1000)
        self.assertEqual(item['services']['ulticode-app']['pm2_collection'], 'invalid')
        self.assertEqual(module.pm2_snapshot(io.BytesIO(b''), 'failed'), ('failed', []))
        for raw in (b'not-json-secret-canary', b'{}', b'x' * 1048577):
            self.assertEqual(module.pm2_snapshot(io.BytesIO(raw), 'ok'), ('invalid', []))
        self.assertEqual(module.pm2_snapshot(io.BytesIO(b'[]'), 'ok'), ('ok', []))

    def test_snapshot_rejects_missing_stale_symlink_fifo_oversize_and_bad_json(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            parent = root / '.local/acceptance'
            parent.mkdir(parents=True)
            target = parent / module.FILE
            self.assertEqual(module.read_snapshot(root, PROJECT)['collection'], 'missing')
            value = module.empty('ok') | {'run_project': PROJECT, 'secret': 'secret-canary'}
            target.write_text(json.dumps(value))
            self.assertEqual(module.read_snapshot(root, PROJECT), module.empty('ok'))
            self.assertEqual(module.read_snapshot(root, PROJECT + '1')['collection'], 'invalid')
            for raw in ('secret-canary', 'x' * (module.LIMIT + 1), '[' * 2000):
                target.write_text(raw)
                self.assertEqual(module.read_snapshot(root, PROJECT)['collection'], 'invalid')
            target.unlink()
            target.symlink_to(root / 'private')
            self.assertEqual(module.read_snapshot(root, PROJECT)['collection'], 'invalid')
            target.unlink()
            os.mkfifo(target)
            self.assertEqual(module.read_snapshot(root, PROJECT)['collection'], 'invalid')
            target.unlink()
            parent.rmdir()
            parent.symlink_to(root, target_is_directory=True)
            self.assertEqual(module.read_snapshot(root, PROJECT)['collection'], 'invalid')

    def test_actual_launcher_loop_preserves_probes_exit_and_prepare_isolation(self):
        source = (ROOT / 'scripts/dev/up.sh').read_text()
        hook = source.split('declare -A ACCEPTANCE_LAST_READINESS=()', 1)[1].split('resolve_mysql_container()', 1)[0]
        hook = 'declare -A ACCEPTANCE_LAST_READINESS=()' + hook
        loop = source[source.rindex('for _ in $(seq 1 "$DEVSTACK_SERVICE_READINESS_ATTEMPTS"); do'):]
        for failed, prepare, broken_writer in ((False, False, False), (True, False, False),
                                                (True, True, False), (True, False, True)):
            with self.subTest(failed=failed, prepare=prepare, broken_writer=broken_writer), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / '.local/acceptance').mkdir(parents=True)
                (root / 'scripts/ci').mkdir(parents=True)
                shutil.copy(ROOT / 'scripts/ci/acceptance_readiness.py', root / 'scripts/ci')
                setup = r'''set -euo pipefail
ROOT_DIR="$1"
ACCEPTANCE_DISPOSABLE=true
export ACCEPTANCE_DISPOSABLE COMPOSE_PROJECT_NAME=ulticode-acceptance-123-456
PREPARE_SUBMISSION_OWNER="$2"
FAILED="$3"
PM2_APPS=ulticode-auth,ulticode-search,ulticode-9002
SELECTED_READINESS=('ulticode-auth|http|9101|/' 'ulticode-search|pm2|9107|/' 'ulticode-9002|http|9002|/')
DEVSTACK_SERVICE_READINESS_ATTEMPTS=2
DEVSTACK_READINESS_INTERVAL_SECONDS=0
DEV_SCOPE=full-stack DEV_MODE=dev-full INFRA_TARGETS=''
DEVSTACK_OWNER_MIGRATION_ORDER=() DEV_SEED_USERS_ENABLED=false
check_port() { echo "$1" >> "$ROOT_DIR/probes"; [[ "$FAILED" != true || "$1" != 9002 ]]; }
check_pm2_online() { echo search >> "$ROOT_DIR/probes"; ACCEPTANCE_PM2_JSON='[]'; ACCEPTANCE_PM2_COLLECTION=ok; }
devstack_apps_csv() { :; }
devstack_app_label() { :; }
pm2() { :; }
sleep() { :; }
'''
                if broken_writer:
                    setup += 'python3() { return 42; }\n'
                run = subprocess.run(['bash', '-c', setup + hook + loop, 'test', directory,
                                      str(prepare).lower(), str(failed).lower()], capture_output=True)
                self.assertEqual(run.returncode, int(failed), run.stderr.decode())
                self.assertEqual((root / 'probes').read_text().splitlines(),
                                 ['9101', 'search', '9002'] * (2 if failed else 1))
                snapshot = module.read_snapshot(root, PROJECT)
                if prepare or broken_writer:
                    self.assertEqual(snapshot['collection'], 'missing')
                else:
                    self.assertEqual(snapshot['services']['ulticode-9002']['last_readiness'],
                                     'not_ready' if failed else 'ready')
                    self.assertEqual(snapshot['services']['ulticode-auth']['last_readiness'], 'ready')

    def test_pm2_adapter_reuses_one_query_and_clears_failed_query(self):
        script = r'''set -euo pipefail
source "$1/scripts/dev/lib/pm2.sh"
ACCEPTANCE_DISPOSABLE=true
CALLS="$2"
pm2() { echo called >> "$CALLS"; printf '[]'; }
pm2_load_records
[[ "$ACCEPTANCE_PM2_JSON" == '[]' && "$ACCEPTANCE_PM2_COLLECTION" == ok ]]
pm2() { echo called >> "$CALLS"; return 1; }
pm2_load_records
[[ -z "$ACCEPTANCE_PM2_JSON" && "$ACCEPTANCE_PM2_COLLECTION" == failed ]]
'''
        with tempfile.TemporaryDirectory() as directory:
            calls = Path(directory) / 'calls'
            run = subprocess.run(['bash', '-c', script, 'test', str(ROOT), str(calls)], capture_output=True)
            self.assertEqual(run.returncode, 0, run.stderr.decode())
            self.assertEqual(calls.read_text().splitlines(), ['called', 'called'])

    def test_normal_development_does_not_collect(self):
        with patch.dict(os.environ, {'ACCEPTANCE_DISPOSABLE': 'false'}), \
             patch.object(module, 'pm2_snapshot') as collect:
            self.assertEqual(module.main(), 0)
            collect.assert_not_called()


if __name__ == '__main__':
    unittest.main()
