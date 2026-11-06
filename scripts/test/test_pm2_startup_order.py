#!/usr/bin/env python3
"""Execute the launcher's PM2 stage with fake processes and HTTP readiness."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'scripts/dev/up.sh').read_text()
STARTUP = SOURCE.split('# ===== 步骤 6: PM2 服务 =====', 1)[1].split('# ===== 步骤 7:', 1)[0]
CHECK = SOURCE.split('check_pm2_online() {', 1)[1].split('\nfor _ in ', 1)[0]
CHECK = 'check_pm2_online() {' + CHECK

SETUP = r'''set -euo pipefail
ROOT_DIR="$1"; PM2_APPS="$2"; DEV_SCOPE="$3"; FAILED="$4"; FRESH="$5"
source "$6/scripts/dev/devstack-manifest.sh"
DEVSTACK_SERVICE_READINESS_ATTEMPTS=2
DEVSTACK_READINESS_INTERVAL_SECONDS=0
pm2() {
  echo "pm2 $*" >> "$ROOT_DIR/events"
  if [[ "$1" == startOrRestart ]]; then
    local app owner offset size
    IFS=',' read -ra apps <<< "$4"
    for app in "${apps[@]}"; do
      case "$app" in
        ulticode-submission|ulticode-judge)
          owner="${app#ulticode-}"
          offset="$(cat "$ROOT_DIR/logs/.$owner-ready-offset")"
          size=0
          if [[ -f "$ROOT_DIR/logs/backend-$owner.out.log" ]]; then
            size="$(wc -c < "$ROOT_DIR/logs/backend-$owner.out.log")"
          fi
          [[ "$offset" -eq "$size" ]] || return 42
          echo "offset $owner fresh" >> "$ROOT_DIR/events"
          if [[ "$FRESH" == true ]]; then
            devstack_readiness_banner "$app" >> "$ROOT_DIR/logs/backend-$owner.out.log"
          fi
          ;;
      esac
    done
  fi
}
curl() {
  echo probe >> "$ROOT_DIR/events"
  # Simulate banners emitted by old auto-restarting consumers while App starts.
  mkdir -p "$ROOT_DIR/logs"
  for app in ulticode-submission ulticode-judge; do
    devstack_readiness_banner "$app" >> "$ROOT_DIR/logs/backend-${app#ulticode-}.out.log"
  done
  if [[ "$FAILED" == true ]]; then printf 503; else printf 200; fi
}
sleep() { :; }
pm2_load_records() { :; }
pm2_record_is_online() { return 0; }
'''


class StartupOrderTests(unittest.TestCase):
    def run_stage(self, apps, scope='admin', failed=False, fresh=True, old_logs=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            if old_logs:
                (root / 'logs').mkdir()
                for owner, banner in [('submission', 'Started BackendSubmissionApplication'),
                                      ('judge', 'Started BackendJudgeApplication')]:
                    (root / f'logs/backend-{owner}.out.log').write_text(banner + '\n')
                    (root / f'logs/.{owner}-ready-offset').write_text('0\n')
            checks = r'''
for app in ${PM2_APPS//,/ }; do
  case "$app" in
    ulticode-submission|ulticode-judge)
      if check_pm2_online "$app"; then
        echo "ready $app" >> "$ROOT_DIR/events"
      else
        echo "not_ready $app" >> "$ROOT_DIR/events"
      fi ;;
  esac
done
'''
            run = subprocess.run(['bash', '-c', SETUP + STARTUP + CHECK + checks,
                                  'test', directory, apps, scope, str(failed).lower(),
                                  str(fresh).lower(), str(ROOT)], capture_output=True, text=True)
            events = (root / 'events').read_text().splitlines()
            return run, events

    def starts(self, events):
        return [event.split('--only ', 1)[1].split(' ', 1)[0]
                for event in events if event.startswith('pm2 startOrRestart')]

    def test_app_submission_without_judge(self):
        run, events = self.run_stage('ulticode-auth,ulticode-app,ulticode-submission')
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(self.starts(events), ['ulticode-auth,ulticode-app', 'ulticode-submission'])
        self.assertLess(events.index('probe'), events.index('offset submission fresh'))
        self.assertIn('ready ulticode-submission', events)
        self.assertFalse(any('delete' in event for event in events))

    def test_full_stack_and_selection_order(self):
        for apps in ('ulticode-auth,ulticode-app,ulticode-submission,ulticode-judge,ulticode-search,ulticode-9002',
                     'ulticode-judge,ulticode-submission,ulticode-app'):
            with self.subTest(apps=apps):
                run, events = self.run_stage(apps, scope='full-stack')
                self.assertEqual(run.returncode, 0, run.stderr)
                selected = apps.split(',')
                consumers = [app for app in selected if app in ('ulticode-submission', 'ulticode-judge')]
                self.assertEqual(self.starts(events), [','.join(app for app in selected if app not in consumers)] + consumers)
                self.assertEqual(sum(event == 'probe' for event in events), 1)
                self.assertIn('pm2 save', events)
                self.assertIn('pm2 delete ulticode-admin', events)
                for app in selected:
                    self.assertNotIn('pm2 delete ' + app, events)

    def test_failed_app_does_not_start_submission_or_judge(self):
        for apps in ('ulticode-app,ulticode-submission', 'ulticode-app,ulticode-submission,ulticode-judge'):
            with self.subTest(apps=apps):
                run, events = self.run_stage(apps, failed=True)
                self.assertEqual(run.returncode, 1)
                self.assertEqual(self.starts(events), ['ulticode-app'])
                self.assertEqual(events.count('probe'), 2)
                self.assertNotIn('pm2 save', events)
                self.assertIn('Fix App readiness', run.stderr)

    def test_judge_only_dependency_failure_retains_legacy_fallback(self):
        run, events = self.run_stage('ulticode-app,ulticode-judge', failed=True)
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(self.starts(events), ['ulticode-app', 'ulticode-judge'])
        self.assertIn('Dubbo auto-reconnect', run.stderr)

    def test_partial_selections_without_app_do_not_add_dependency(self):
        for apps in ('ulticode-submission', 'ulticode-judge', 'ulticode-submission,ulticode-judge',
                     'ulticode-core,ulticode-judge', 'ulticode-9002', 'ulticode-app'):
            with self.subTest(apps=apps):
                run, events = self.run_stage(apps, old_logs=False)
                self.assertEqual(run.returncode, 0, run.stderr)
                self.assertEqual(self.starts(events), [apps])
                self.assertNotIn('probe', events)
                self.assertFalse(any('delete' in event for event in events))

    def test_stale_banners_including_during_app_wait_do_not_pass_readiness(self):
        for apps in ('ulticode-app,ulticode-submission,ulticode-judge',
                     'ulticode-submission,ulticode-judge'):
            with self.subTest(apps=apps):
                run, events = self.run_stage(apps, fresh=False)
                self.assertEqual(run.returncode, 0, run.stderr)
                for owner in ('submission', 'judge'):
                    self.assertIn(f'not_ready ulticode-{owner}', events)
                    self.assertNotIn(f'ready ulticode-{owner}', events)


if __name__ == '__main__':
    unittest.main()
