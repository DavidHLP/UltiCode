#!/usr/bin/env python3
import importlib.util
import json
import tempfile
import sys
import unittest
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
            self.assertEqual(report["service_diagnostics"]["app"]["public_source_frames"],
                             [{"symbol": "com.ulticode.app.Probe.submit", "location": "Probe.java:42"}])

    def test_symlinks_unrelated_files_and_untrusted_metadata_are_excluded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            outside = root / "private-input"
            outside.write_text("java.lang.OutOfMemoryError: private-canary")
            (root / "logs/backend-app.err.log").symlink_to(outside)
            (root / "logs/unrelated.log").write_text(outside.read_text())
            report = module.build_report(root, "private-canary", 1, "private-canary")
            self.assertEqual(report["service_diagnostics"], {})
            self.assertEqual(report["phase"], "unknown")
            self.assertEqual(report["project"], "unknown")
            self.assertNotIn("private-canary", json.dumps(report))

    def test_discovery_observations_are_strict_bounded_and_do_not_expose_messages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "logs").mkdir()
            payload = "SubmissionDiscoveryObservation outcome=deadline elapsed_ms=120000 problem_facts=UNAVAILABLE user_read=AVAILABLE"
            invalid = [payload + " password=secret-canary", payload.replace("UNAVAILABLE", "secret-canary"),
                       payload.replace("120000", "99999999"), payload.replace("120000", "-1"),
                       payload.replace("deadline", "https://secret-canary"), payload.replace("120000", "1e5"), payload + " " + payload]
            (root / "logs/backend-submission.out.log").write_text(
                "\n".join(invalid + ["private-prefix-canary " + payload] * 20))
            report = module.build_report(root, "launch", 1, "ulticode-acceptance-123-456")
            entries = report["submission_discovery_observations"]
            self.assertEqual(len(entries), 16)
            self.assertEqual(entries[0], {"outcome": "deadline", "elapsed_ms": 120000,
                                         "problem_facts": "UNAVAILABLE", "user_read": "AVAILABLE"})
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
            payload = "SubmissionDiscoveryObservation outcome=ready elapsed_ms=1 problem_facts=AVAILABLE user_read=AVAILABLE"
            outside.write_text(payload)
            log = root / "logs/backend-submission.out.log"
            log.symlink_to(outside)
            self.assertEqual(module.discovery_observations(root), [])
            log.unlink()
            log.write_text("x" * module.LIMIT + payload)
            self.assertEqual(module.discovery_observations(root), [])


if __name__ == "__main__":
    unittest.main()
