#!/usr/bin/env python3
import importlib.util
import json
import tempfile
import unittest
from unittest.mock import patch
import contextlib
import io
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
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
                    f"Started Backend{name}Application in 1.0 seconds\n"
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


if __name__ == "__main__":
    unittest.main()
