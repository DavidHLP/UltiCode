#!/usr/bin/env python3
import importlib.util
import json
import os
import subprocess
import time
import tempfile
import sys
import unittest
import contextlib
import io
from unittest.mock import patch
from unittest import mock
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/ci"))
spec = importlib.util.spec_from_file_location("acceptance_report", ROOT / "scripts/ci/acceptance_report.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RedactionTests(unittest.TestCase):
    def test_only_public_types_and_source_coordinates_are_serialized(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            (root / ".local/acceptance").mkdir(parents=True)
            canaries = ["private-" + "password-canary", ".".join(("eyJprivate", "token", "signature")), "source-body-canary"]
            (root / "logs/backend-app.err.log").write_text(
                "Authorization: Bearer " + canaries[1] + "\nCookie: access_token=" + canaries[1]
                + "\njava.lang.IllegalStateException: DB_PASSWORD=" + canaries[0]
                + "\norg.apache.dubbo.rpc.RpcException: Failed to deserialize " + canaries[0]
                + "\nsource=" + canaries[2]
                + "\n at com.ulticode.app.Probe.submit(Probe.java:42)\n")
            (root / ".local/acceptance/private-launch.log").write_text(
                "org.apache.dubbo.rpc.RpcException: " + canaries[0])
            report = module.build_report(root, "browser-base", 1, "ulticode-acceptance-123-456")
            encoded = json.dumps(report)
            for value in canaries:
                self.assertNotIn(value, encoded)
            self.assertFalse(report["passed"])
            self.assertTrue(report["service_diagnostics"]["app"]["rpc_failure_categories"]["serialization"])
            self.assertFalse(report["raw_logs_published"])
            self.assertIn("org.apache.dubbo.rpc.RpcException", report["exception_classes"])
            self.assertEqual(report["service_diagnostics"]["app"]["public_source_frames"], [])
            self.assertTrue(report["service_diagnostics"]["app"]["unlisted_source_frame_observed"])


    def test_symlinks_unrelated_files_and_untrusted_metadata_are_excluded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            outside = root / "private-input"
            outside.write_text("java.lang.OutOfMemoryError: private-canary")
            (root / "logs/backend-app.err.log").symlink_to(outside)
            (root / "logs/unrelated.log").write_text(outside.read_text())
            report = module.build_report(root, "private-canary", 1, "private-canary")
            self.assertEqual(report["service_diagnostics"]["app"]["coverage"]["files_excluded"], 1)
            self.assertEqual(report["service_diagnostics"]["app"]["exception_classes"], [])
            self.assertEqual(report["phase"], "unknown")
            self.assertEqual(report["project"], "unknown")
            self.assertNotIn("private-canary", json.dumps(report))


    def test_discovery_observations_are_strict_bounded_and_do_not_expose_messages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            payload = ("SubmissionDiscoveryObservation outcome=deadline elapsed_ms=120000 interface=problem_facts "
                       "state=INVOKER_PRESENT invoker_kind=SERVICE_DISCOVERY_MIGRATION directory_kind=SERVICE_DISCOVERY "
                       "destroyed=FALSE all=1 valid=0 reconnect=1 disabled=1")
            invalid = [payload + " password=secret-canary", payload.replace("INVOKER_PRESENT", "secret-canary"),
                       payload.replace("120000", "99999999"), payload.replace("120000", "-1"),
                       payload.replace("deadline", "https://secret-canary"), payload.replace("120000", "1e5"), payload + " " + payload,
                       payload.replace("all=1", "all=10001"), payload.replace("valid=0", "valid=-1"),
                       payload.replace("valid=0", "valid=00"), payload.replace("reconnect=1", "reconnect=1e5"),
                       payload.replace("disabled=1", "disabled=secret-canary"),
                       payload.replace("interface=problem_facts", "interface=secret-canary"),
                       payload.replace("invoker_kind=SERVICE_DISCOVERY_MIGRATION", "invoker_kind=secret-canary"),
                       payload.replace("directory_kind=SERVICE_DISCOVERY", "directory_kind=secret-canary"),
                       payload.replace("destroyed=FALSE", "destroyed=secret-canary"),
                       payload.replace("state=INVOKER_PRESENT", "state=AVAILABLE")]
            (root / "logs/backend-submission.out.log").write_text(
                "\n".join(invalid + ["private-prefix-canary " + payload] * 20))
            report = module.build_report(root, "launch", 1, "ulticode-acceptance-123-456")
            entries = report["submission_discovery_observations"]
            self.assertEqual(len(entries), 16)
            self.assertEqual(entries[0], {"outcome": "deadline", "elapsed_ms": 120000,
                                         "interface": "problem_facts", "state": "INVOKER_PRESENT",
                                         "invoker_kind": "SERVICE_DISCOVERY_MIGRATION", "directory_kind": "SERVICE_DISCOVERY",
                                         "destroyed": "FALSE", "all": 1, "valid": 0, "reconnect": 1, "disabled": 1})
            self.assertNotIn("canary", json.dumps(report))
            self.assertEqual(report["submission_discovery_scope"], "historical-bounded-tail-not-process-correlated")
            # Malformed records alone do not become evidence.
            (root / "logs/backend-submission.out.log").write_text("\n".join(invalid))
            self.assertEqual(module.discovery_observations(root), [])


    def test_discovery_observations_exclude_symlinks_and_truncated_records(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            outside = root / "private-input"
            payload = ("SubmissionDiscoveryObservation outcome=ready elapsed_ms=1 interface=user_read "
                       "state=INVOKER_PRESENT invoker_kind=CLUSTER directory_kind=DYNAMIC "
                       "destroyed=TRUE all=0 valid=UNKNOWN reconnect=OVERFLOW disabled=ERROR")
            outside.write_text(payload)
            log = root / "logs/backend-submission.out.log"
            log.symlink_to(outside)
            self.assertEqual(module.discovery_observations(root), [])
            log.unlink()
            log.write_text(payload)
            observation = module.discovery_observations(root)[0]
            self.assertEqual(observation["all"], 0)
            self.assertEqual(observation["valid"], "UNKNOWN")
            self.assertEqual(observation["reconnect"], "OVERFLOW")
            self.assertEqual(observation["disabled"], "ERROR")
            log.write_text(payload.replace("all=0", "all=10000"))
            self.assertEqual(module.discovery_observations(root)[0]["all"], 10000)
            log.write_text("x" * module.LIMIT + payload)
            self.assertEqual(module.discovery_observations(root), [])


    def test_app_discovery_is_strict_bounded_and_redacted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            log = root / "logs/backend-app.out.log"
            payload = ("AppDiscoveryObservation interface=submission_user_query "
                       "state=INVOKER_PRESENT invoker_kind=MIGRATION directory_kind=SERVICE_DISCOVERY "
                       "destroyed=FALSE all=1 valid=1 reconnect=0 disabled=0")
            invalid = [payload + " password=secret-canary", payload + " " + payload,
                       payload.replace("interface=submission_user_query", "interface=secret-canary"),
                       payload.replace("state=INVOKER_PRESENT", "state=AVAILABLE"),
                       payload.replace("invoker_kind=MIGRATION", "invoker_kind=secret-canary"),
                       payload.replace("directory_kind=SERVICE_DISCOVERY", "directory_kind=secret-canary"),
                       payload.replace("destroyed=FALSE", "destroyed=secret-canary"),
                       payload.replace("all=1", "all=10001"), payload.replace("all=1", "all=00"),
                       payload.replace("valid=1", "valid=-1"), payload.replace("reconnect=0", "reconnect=1e5"),
                       payload.replace("disabled=0", "disabled=https://secret-canary"),
                       payload.replace("interface=", "outcome=ready elapsed_ms=1 interface="),
                       payload.replace("AppDiscoveryObservation", "SubmissionDiscoveryObservation")]
            log.write_text("\n".join(invalid))
            self.assertEqual(module.app_discovery_observations(root), [])
            valid = [payload.replace("all=1", f"all={index}") for index in range(20)]
            log.write_text("\n".join(invalid + ["private-prefix-canary " + item for item in valid]))
            report = module.build_report(root, "browser-base", 1, "ulticode-acceptance-123-456")
            entries = report["app_discovery_observations"]
            self.assertEqual(len(entries), 16)
            self.assertEqual([item["all"] for item in entries], list(range(4, 20)))
            self.assertEqual(entries[0], {"interface": "submission_user_query", "state": "INVOKER_PRESENT",
                                         "invoker_kind": "MIGRATION", "directory_kind": "SERVICE_DISCOVERY",
                                         "destroyed": "FALSE", "all": 4, "valid": 1, "reconnect": 0, "disabled": 0})
            self.assertEqual(report["app_discovery_scope"], "historical-bounded-tail-not-process-correlated")
            self.assertEqual(report["submission_discovery_observations"], [])
            self.assertNotIn("canary", json.dumps(report))
            self.assertNotIn("https://", json.dumps(report))


    def test_app_discovery_excludes_symlinks_missing_and_truncated_records(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            self.assertEqual(module.app_discovery_observations(root), [])
            outside = root / "private-input"
            payload = ("AppDiscoveryObservation interface=auth_account_query "
                       "state=INVOKER_PRESENT invoker_kind=CLUSTER directory_kind=DYNAMIC "
                       "destroyed=TRUE all=10000 valid=UNKNOWN reconnect=OVERFLOW disabled=ERROR")
            outside.write_text(payload)
            log = root / "logs/backend-app.out.log"
            log.symlink_to(outside)
            self.assertEqual(module.app_discovery_observations(root), [])
            log.unlink()
            log.write_text(payload)
            item = module.app_discovery_observations(root)[0]
            self.assertEqual(item["interface"], "auth_account_query")
            self.assertEqual(item["all"], 10000)
            self.assertEqual(item["valid"], "UNKNOWN")
            self.assertEqual(item["reconnect"], "OVERFLOW")
            self.assertEqual(item["disabled"], "ERROR")
            log.write_text("x" * module.LIMIT + payload)
            self.assertEqual(module.app_discovery_observations(root), [])
            log.write_text("x" * module.LIMIT + "\n" + payload + "\n" + payload[:-5])
            self.assertEqual(module.app_discovery_observations(root), [item])
            log.write_text(payload.replace("all=10000", "all=1000" + "\ufffd"))
            self.assertEqual(module.app_discovery_observations(root), [])


    def test_startup_diagnostics_are_fixed_booleans_not_exception_messages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            (root / ".local/acceptance").mkdir(parents=True)
            canaries = ["secret-value-canary", "https://private-user:private-password@private-host.invalid/path?token=private-token",
                        "Authorization: Bearer private-bearer", "DB_PASSWORD=private-db", "private-stack-body"]
            untrusted = "\n".join(canaries)
            (root / "logs/backend-submission.err.log").write_text(
                "java.lang.IllegalStateException: Submission projection providers were not discovered before readiness deadline\n"
                "jakarta.validation.NoProviderFoundException: " + untrusted)
            (root / "logs/backend-app.out.log").write_text(
                "Started BackendAppApplication in 12.0 seconds\n" + untrusted)
            (root / ".local/acceptance/private-launch.log").write_text(
                "Application readiness check timed out for: " + untrusted)
            report = module.build_report(root, "launch", 1, "ulticode-acceptance-123-456")
            self.assertTrue(report["launcher_signals"]["stack_readiness_timed_out"])
            submission = report["service_diagnostics"]["submission"]
            self.assertTrue(submission["startup_signals"]["submission_projection_discovery_deadline"])
            self.assertTrue(submission["rpc_failure_categories"]["validation_provider"])
            self.assertTrue(report["service_diagnostics"]["app"]["startup_signals"]["spring_context_started"])
            for owner in report["service_diagnostics"].values():
                self.assertTrue(all(type(value) is bool for value in owner["startup_signals"].values()))
            encoded = json.dumps(report)
            for canary in canaries:
                self.assertNotIn(canary, encoded)
            self.assertNotIn("private-host", encoded)
            self.assertNotIn("private-password", encoded)
            self.assertFalse(report["passed"])


    def test_generic_validation_exception_does_not_prove_discovery_deadline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            (root / "logs/backend-submission.err.log").write_text(
                "jakarta.validation.NoProviderFoundException: optional validation provider absent")
            report = module.build_report(root, "launch", 1, "ulticode-acceptance-123-456")
            self.assertFalse(report["service_diagnostics"]["submission"]["startup_signals"]["submission_projection_discovery_deadline"])
            self.assertFalse(report["launcher_signals"]["stack_readiness_timed_out"])


    def test_forged_java_types_and_frames_never_escape_fixed_vocabulary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            canaries = ["java.syntheticPrivateCanaryException", "com.ulticode.privateCanary.PasswordError",
                        "com.ulticode.secretCanary.method", "SecretCanary.java:123",
                        "SubmissionDiscoveryStartupGate.java:987654321"]
            (root / "logs/backend-submission.err.log").write_text(
                canaries[0] + ": password=secret-canary\n" + canaries[1]
                + "\n at " + canaries[2] + "(" + canaries[3] + ")"
                + "\n at com.ulticode.submission.config.SubmissionDiscoveryStartupGate.await(" + canaries[4] + ")"
                + "\n java.lang.IllegalStateException: secret-canary"
                + "\n at com.ulticode.submission.config.SubmissionDiscoveryStartupGate.await(SubmissionDiscoveryStartupGate.java:40)")
            report = module.build_report(root, "launch", 1, "private-project-canary")
            item = report["service_diagnostics"]["submission"]
            self.assertEqual(item["exception_classes"], ["java.lang.IllegalStateException"])
            self.assertEqual(item["public_source_frames"], [{
                "symbol": "com.ulticode.submission.config.SubmissionDiscoveryStartupGate.await",
                "location": "SubmissionDiscoveryStartupGate.java:40"}])
            self.assertTrue(item["unlisted_exception_type_observed"])
            self.assertTrue(item["unlisted_source_frame_observed"])
            for canary in canaries + ["secret-canary", "private-project-canary"]:
                self.assertNotIn(canary, json.dumps(report))


    def test_missing_logs_are_unknown_coverage_not_omitted_services(self):
        with tempfile.TemporaryDirectory() as directory:
            report = module.build_report(Path(directory), "launch", 1, "ulticode-acceptance-1-2")
            self.assertEqual(set(report["service_diagnostics"]), set(module.OWNERS))
            for item in report["service_diagnostics"].values():
                self.assertEqual(item["coverage"]["files_expected"], 2)
                self.assertEqual(item["coverage"]["files_seen"], 0)
                self.assertEqual(item["coverage"]["files_missing"], 2)
                self.assertFalse(item["coverage"]["complete"])
            self.assertEqual(report["launcher_coverage"]["files_missing"], len(module.PRIVATE_LOGS))


    def test_truncated_prefix_does_not_claim_absence_or_complete_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "log"
            path.write_text("Submission projection providers discovered\n" + "x" * 256)
            with patch.object(module, "LIMIT", 128):
                item = module.summary([path])
            self.assertTrue(item["coverage"]["tail_truncated"])
            self.assertFalse(item["coverage"]["complete"])
            self.assertFalse(item["startup_signals"]["submission_projection_providers_discovered"])
            path.write_text("x" * 256 + "\nSubmission projection providers discovered\n")
            with patch.object(module, "LIMIT", 128):
                item = module.summary([path])
            self.assertTrue(item["coverage"]["tail_truncated"])
            self.assertTrue(item["startup_signals"]["submission_projection_providers_discovered"])


    def test_read_error_has_bounded_count_without_exception_detail(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "log"
            path.write_text("unused")
            with patch.object(Path, "open", side_effect=PermissionError("private-file-canary")):
                item = module.summary([path])
            self.assertEqual(item["coverage"]["read_errors"], 1)
            self.assertEqual(item["coverage"]["files_seen"], 0)
            self.assertFalse(item["coverage"]["complete"])
            self.assertNotIn("private-file-canary", json.dumps(item))


    def test_notification_search_and_positive_submission_discovery(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            for owner, name in (("notification", "Notification"), ("search", "Search"), ("submission", "Submission")):
                (root / f"logs/backend-{owner}.out.log").write_text(
                    f"Started {'SearchWorkerApplication' if owner == 'search' else 'Backend' + name + 'Application'} in 1.0 seconds\n"
                    + ("Submission projection providers discovered\n" if owner == "submission" else ""))
                (root / f"logs/backend-{owner}.err.log").write_text("")
            report = module.build_report(root, "complete", 0, "ulticode-acceptance-1-2")
            self.assertTrue(report["passed"])
            for owner in ("notification", "search", "submission"):
                self.assertTrue(report["service_diagnostics"][owner]["coverage"]["complete"])
                self.assertTrue(report["service_diagnostics"][owner]["startup_signals"]["spring_context_started"])
            self.assertTrue(report["service_diagnostics"]["submission"]["startup_signals"]["submission_projection_providers_discovered"])
            self.assertFalse(report["service_diagnostics"]["submission"]["startup_signals"]["submission_projection_discovery_deadline"])


    def test_mixed_restart_success_and_failure_are_observations_not_current_health(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            (root / "logs/backend-submission.out.log").write_text(
                "Started BackendSubmissionApplication in 1 second\n"
                "Submission projection providers discovered\n"
                "APPLICATION FAILED TO START\n"
                "Submission projection providers were not discovered before readiness deadline\n")
            report = module.build_report(root, "launch", 1, "ulticode-acceptance-1-2")
            item = report["service_diagnostics"]["submission"]["startup_signals"]
            self.assertTrue(item["spring_context_started"])
            self.assertTrue(item["submission_projection_providers_discovered"])
            self.assertTrue(item["submission_projection_discovery_deadline"])
            self.assertFalse(report["passed"])
            self.assertEqual(report["signals_semantics"], "observed_in_bounded_log_tails_not_current_health")


    def test_each_startup_signal_has_positive_and_negative_coverage(self):
        examples = {
            "spring_context_started": "Started BackendAppApplication in 1 second",
            "application_failed": "APPLICATION FAILED TO START",
            "submission_projection_providers_discovered": "Submission projection providers discovered",
            "submission_projection_discovery_deadline": "Submission projection providers were not discovered before readiness deadline",
            "nacos_client_starting": "Client not connected, current status: STARTING",
            "nacos_client_unhealthy": "Client not connected, current status:UNHEALTHY",
            "connection_refused": "Connection refused",
            "reactor_build_failed": "Error: reactor install failed; fix compilation errors before starting the stack.",
            "auth_readiness_failed": "ulticode-auth did not become healthy; cannot run dev-admin bootstrap.",
            "app_readiness_failed": "backend-app did not become healthy; starting ulticode-judge anyway",
            "stack_readiness_timed_out": "Application readiness check timed out for:",
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "log"
            path.write_text("ordinary non-diagnostic output")
            self.assertFalse(any(module.summary([path])["startup_signals"].values()))
            for key, example in examples.items():
                with self.subTest(key=key):
                    path.write_text(example + "\nhttps://secret-user:secret-password@private.invalid/token")
                    item = module.summary([path])
                    self.assertTrue(item["startup_signals"][key])
                    self.assertNotIn("secret-user", json.dumps(item))
                    self.assertNotIn("secret-password", json.dumps(item))
            self.assertEqual(set(examples), set(item["startup_signals"]))


    def test_current_fail_closed_dependency_messages_are_observations(self):
        messages = {
            "auth_readiness_failed": "backend-auth did not become healthy; not starting App or its deferred consumers. Fix Auth readiness and re-run up.sh.",
            "app_readiness_failed": "backend-app did not become healthy; not starting deferred Submission/Judge services. Fix App readiness and re-run up.sh.",
        }
        source = (ROOT / "scripts/dev/up.sh").read_text()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "log"
            for key, message in messages.items():
                self.assertIn(message, source)
                path.write_text(message + "\nprivate-message-canary")
                item = module.summary([path])
                self.assertTrue(item["startup_signals"][key])
                self.assertNotIn("private-message-canary", json.dumps(item))

    def test_success_main_writes_bounded_report_without_failure_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(Path, "cwd", return_value=root), patch.dict(os.environ, {
                    "ACCEPTANCE_PHASE": "complete", "ACCEPTANCE_EXIT": "0", "COMPOSE_PROJECT_NAME": "ulticode-acceptance-1-2"}), contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(module.main(), 0)
            self.assertEqual(output.getvalue(), "")
            report = json.loads((root / ".local/acceptance/installation.json").read_text())
            self.assertTrue(report["passed"])
            self.assertFalse(report["raw_logs_published"])
            self.assertEqual(report["project"], "disposable")




class AdditionalObservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "logs").mkdir()
        (self.root / ".local/acceptance").mkdir(parents=True)
        self.project = f"ulticode-acceptance-{int(time.time())}-456"
        self.env = mock.patch.dict(os.environ, {"ACCEPTANCE_DISPOSABLE": "true"})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.body = self.root / ".local/acceptance" / module.ADMIN_FILE

    def launched(self):
        (self.body.parent / "launch-readiness.json").write_text(json.dumps({
            "schema_version": 1, "run_project": self.project, "collection": "ok",
            "services": {"ulticode-admin": {"pm2_collection": "ok", "status": "online"}}}))

    def observe(self, phase="launch", project=None):
        return module.admin_readiness_observation(self.root, phase, project or self.project)

    def test_application_discovery_schema_bounds_aliases_and_secrets(self):
        log = self.root / "logs/backend-app.out.log"
        payload = ("AppApplicationDiscoveryObservation interface=submission_user_query "
                   "state=INVOKER_PRESENT invoker_kind=SERVICE_DISCOVERY_MIGRATION directory_kind=SERVICE_DISCOVERY "
                   "destroyed=FALSE all=1 valid=0 reconnect=OVERFLOW disabled=ERROR "
                   "listener_state=PRESENT mapped_apps=1 observed_apps=UNKNOWN instances=10000")
        invalid = [payload + " password=secret-canary", payload + " " + payload,
                   payload.replace("listener_state=PRESENT", "listener_state=secret-canary"),
                   payload.replace("AppApplicationDiscoveryObservation", "AppDiscoveryObservation"),
                   payload.replace("submission_user_query", "secret-canary")]
        for name in ("all", "valid", "reconnect", "disabled", "mapped_apps", "observed_apps", "instances"):
            for value in ("-1", "00", "10001", "999999", "1e2", "secret-canary", "1.0"):
                import re
                invalid.append(re.sub(rf"{name}=\S+", f"{name}={value}", payload))
        log.write_text("\n".join(invalid))
        self.assertEqual(module.app_application_discovery_observations(self.root), [])
        log.write_text("\n".join(invalid + ["secret-prefix-canary " + payload] * 20))
        report = module.build_report(self.root, "launch", 1, self.project)
        entries = report["app_application_discovery_observations"]
        self.assertEqual(len(entries), 16)
        self.assertEqual(entries[0]["mapped_apps"], 1)
        self.assertEqual(entries[0]["observed_apps"], "UNKNOWN")
        self.assertEqual(entries[0]["instances"], 10000)
        self.assertEqual(entries[0]["listener_state"], "PRESENT")
        self.assertNotIn("canary", json.dumps(report))
        for state in ("MISSING", "PRESENT", "UNSUPPORTED", "ERROR"):
            log.write_text(payload.replace("PRESENT mapped", state + " mapped")
                           .replace("submission_user_query", "auth_account_query"))
            self.assertEqual(module.app_application_discovery_observations(self.root)[0]["listener_state"], state)
        log.write_text("x" * module.LIMIT + payload)
        self.assertEqual(module.app_application_discovery_observations(self.root), [])
        log.write_text("x" * module.LIMIT + "\n" + payload)
        self.assertEqual(len(module.app_application_discovery_observations(self.root)), 1)
        log.unlink()
        self.assertEqual(module.app_application_discovery_observations(self.root), [])
        target = self.root / "target"
        target.write_text(payload)
        log.symlink_to(target)
        self.assertEqual(module.app_application_discovery_observations(self.root), [])
        log.unlink()
        (self.root / "logs").rmdir()
        (self.root / "logs").symlink_to(self.root)
        (self.root / "backend-app.out.log").write_text(payload)
        self.assertEqual(module.app_application_discovery_observations(self.root), [])

    def test_admin_success_and_503_payload_only_allowlisted_fields(self):
        for status in ("UP", "DOWN"):
            self.body.write_text(json.dumps({"status": status, "message": "secret-canary",
                "url": "http://secret-canary", "components": {"db": "UP", "redis": "UP", "storage": "UP",
                "problemOwnerRpc": status, "secret-canary": "secret-canary"}}))
            observation = self.observe()
            self.assertEqual(observation, {"status": status, "components": {
                "db": "UP", "redis": "UP", "storage": "UP", "problemOwnerRpc": status}})
            report = module.build_report(self.root, "launch", 1, self.project)
            self.assertFalse(report["passed"])
            self.assertNotIn("canary", json.dumps(report))
            self.assertEqual(report["admin_post_launch_readiness"], observation)
            self.assertIn("not-same-timestamp", report["admin_post_launch_readiness_scope"])

    def test_admin_missing_malformed_oversize_aliases_and_invalid_context(self):
        unknown = self.observe()
        self.assertEqual(set(unknown), {"status", "components"})
        for value in ("{", "[]", "null", "x" * (module.ADMIN_LIMIT + 1),
                      '{"status":"secret-canary","components":{"db":true,"redis":"up","storage":[],"problemOwnerRpc":{}}}',
                      '{"status":"READY","components":{"database":"UP","problem_owner_rpc":"UP"}}'):
            self.body.write_text(value)
            self.assertEqual(self.observe(), unknown)
        self.body.write_text('{"status":"UP","components":{"db":"UP"}}')
        self.assertEqual(self.observe("environment"), unknown)
        self.assertEqual(self.observe(project="secret-canary"), unknown)
        with mock.patch.dict(os.environ, {"ACCEPTANCE_DISPOSABLE": "false"}):
            self.assertEqual(self.observe(), unknown)
        os.utime(self.body, (1, 1))
        self.assertEqual(self.observe(), unknown)

    def test_admin_refuses_symlinks_directories_and_fifos(self):
        unknown = self.observe()
        target = self.root / "target"
        target.write_text('{"status":"UP"}')
        self.body.symlink_to(target)
        self.assertEqual(self.observe(), unknown)
        self.body.unlink()
        self.body.mkdir()
        self.assertEqual(self.observe(), unknown)
        self.body.rmdir()
        os.mkfifo(self.body)
        self.assertEqual(self.observe(), unknown)
        self.body.unlink()
        self.body.parent.rmdir()
        self.body.parent.symlink_to(self.root)
        (self.root / module.ADMIN_FILE).write_text('{"status":"UP"}')
        self.assertEqual(self.observe(), unknown)

    def test_capture_is_once_bounded_credentials_free_and_preserves_body(self):
        with mock.patch.object(module.subprocess, "run") as call:
            module.capture_admin_readiness(self.root, "launch", self.project)
            call.assert_not_called()
        self.launched()
        for status in ("UP", "DOWN"):
            def run(command, **kwargs):
                self.assertEqual(command[:2], ["curl", "-q"])
                self.assertIn("--noproxy", command)
                self.assertIn("--max-filesize", command)
                self.assertEqual(command[command.index("--max-time") + 1], "3")
                self.assertEqual(command[-1], "http://127.0.0.1:9102/api/v1/admin/health/ready")
                for forbidden in ("--fail", "--location", "--retry", "--netrc", "--user"):
                    self.assertNotIn(forbidden, command)
                kwargs["stdout"].write(json.dumps({"status": status}).encode())
                return subprocess.CompletedProcess(command, 0)
            with mock.patch.object(module.subprocess, "run", side_effect=run) as call:
                module.capture_admin_readiness(self.root, "launch", self.project)
                self.assertEqual(call.call_count, 1)
                self.assertEqual(self.observe()["status"], status)
                self.assertEqual(self.body.stat().st_mode & 0o777, 0o600)
        with mock.patch.object(module.subprocess, "run") as call:
            module.capture_admin_readiness(self.root, "environment", self.project)
            call.assert_not_called()
        script = (ROOT / "scripts/ci/business-acceptance.sh").read_text()
        self.assertEqual(script.count("--capture-admin-readiness"), 1)
        self.assertLess(script.index("--capture-admin-readiness"), script.index("  python3 scripts/ci/acceptance_report.py\n"))
        self.assertIn('if [[ "$launch_started" == true ]]', script)
        self.assertIn("launch_started=false", script)

    def test_capture_failure_does_not_publish_partial_body(self):
        self.launched()
        with mock.patch.object(module.subprocess, "run", return_value=subprocess.CompletedProcess([], 28)):
            module.capture_admin_readiness(self.root, "launch", self.project)
        self.assertFalse(self.body.exists())
        with mock.patch.object(module.subprocess, "run", side_effect=subprocess.TimeoutExpired("curl", 4)):
            with self.assertRaises(subprocess.TimeoutExpired):
                module.capture_admin_readiness(self.root, "launch", self.project)
        self.assertEqual([p.name for p in self.body.parent.iterdir()], ["launch-readiness.json"])


if __name__ == "__main__":
    unittest.main()
