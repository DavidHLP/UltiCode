#!/usr/bin/env python3
import importlib.util
import json
import tempfile
import unittest
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
            canaries = ["private-password-canary", "eyJprivate.token.signature", "source-body-canary"]
            (root / "logs/backend-app.err.log").write_text(
                "Authorization: Bearer " + canaries[1] + "\nCookie: access_token=" + canaries[1]
                + "\njava.lang.IllegalStateException: DB_PASSWORD=" + canaries[0]
                + "\nsource=" + canaries[2]
                + "\n at com.ulticode.app.Probe.submit(Probe.java:42)\n")
            (root / ".local/acceptance/private-launch.log").write_text(
                "org.apache.dubbo.rpc.RpcException: " + canaries[0])
            report = module.build_report(root, "browser-base", 1, "ulticode-acceptance-123-456")
            encoded = json.dumps(report)
            for value in canaries:
                self.assertNotIn(value, encoded)
            self.assertFalse(report["passed"])
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


if __name__ == "__main__":
    unittest.main()
