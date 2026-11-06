#!/usr/bin/env python3
"""Scanner-identity contract, independent of network/database availability."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("nacos_sbom", ROOT / "scripts/ci/check-nacos-sbom.py")
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class NacosSbomTest(unittest.TestCase):
    def setUp(self):
        self.sbom = json.loads((ROOT / "services/platform/nacos-api-patched/src/main/resources/META-INF/nacos-patch/bom.json").read_text())
        self.report = {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-api", "Version": "2.5.1",
                                                "Identifier": {"PURL": CHECK.UPSTREAM}}]}]}

    def test_actual_sbom_and_recognized_package(self):
        CHECK.validate(self.sbom, self.report)

    def test_pedigree_alone_does_not_count(self):
        self.sbom["components"] = []
        with self.assertRaises(ValueError):
            CHECK.validate(self.sbom)

    def test_missing_dependency_edge_fails(self):
        self.sbom["dependencies"] = []
        with self.assertRaises(ValueError):
            CHECK.validate(self.sbom)

    def test_empty_or_wrong_scan_inventory_is_not_a_pass(self):
        for report in ({}, {"Results": []}, {"Results": [{"Vulnerabilities": []}]},
                       {"Results": [{"Packages": [{"Name": "unrelated", "Version": "2.5.1"}]}]}):
            with self.subTest(report=report), self.assertRaises(ValueError):
                CHECK.validate(self.sbom, copy.deepcopy(report))


if __name__ == "__main__":
    unittest.main()
