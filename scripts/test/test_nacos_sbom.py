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
        self.report = {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-api", "Version": "2.5.3",
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

    def test_other_upstream_versions_do_not_count(self):
        for version in ("2.5.1", "3.3.0"):
            with self.subTest(version=version):
                sbom = copy.deepcopy(self.sbom)
                sbom["components"][0]["version"] = version
                with self.assertRaises(ValueError):
                    CHECK.validate(sbom)
                report = {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-api",
                                                       "Version": version}]}]}
                with self.assertRaises(ValueError):
                    CHECK.validate(self.sbom, report)

    def test_empty_or_wrong_scan_inventory_is_not_a_pass(self):
        for report in ({}, {"Results": []}, {"Results": [{"Vulnerabilities": []}]},
                       {"Results": [{"Packages": [{"Name": "unrelated", "Version": "2.5.3"}]}]}):
            with self.subTest(report=report), self.assertRaises(ValueError):
                CHECK.validate(self.sbom, copy.deepcopy(report))


class NacosClientSbomTest(unittest.TestCase):
    def setUp(self):
        self.sbom = json.loads((ROOT / "services/platform/nacos-client-patched/src/main/resources/META-INF/nacos-client-patch/bom.json").read_text())
        self.report = {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-client", "Version": "2.5.3",
                                                "Identifier": {"PURL": "pkg:maven/com.alibaba.nacos/nacos-client@2.5.3"}}]}]}

    def test_actual_sbom_and_recognized_package(self):
        CHECK.validate(self.sbom, self.report, "nacos-client")

    def test_client_negative_inventory(self):
        for report in ({}, {"Results": []}, {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-api", "Version": "2.5.3"}]}]},
                       {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-client", "Version": "2.5.1"}]}]}):
            with self.subTest(report=report), self.assertRaises(ValueError):
                CHECK.validate(self.sbom, report, "nacos-client")
        for mutation in ("components", "dependencies"):
            broken = copy.deepcopy(self.sbom)
            broken[mutation] = []
            with self.assertRaises(ValueError):
                CHECK.validate(broken, self.report, "nacos-client")


class NacosCommonSbomTest(unittest.TestCase):
    def setUp(self):
        self.sbom = json.loads((ROOT / "services/platform/nacos-common-patched/src/main/resources/META-INF/nacos-common-patch/bom.json").read_text())
        self.report = {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-common", "Version": "2.5.3",
                                                "Identifier": {"PURL": "pkg:maven/com.alibaba.nacos/nacos-common@2.5.3"}}]}]}

    def test_actual_sbom_and_recognized_package(self):
        CHECK.validate(self.sbom, self.report, "nacos-common")

    def test_common_negative_inventory(self):
        for report in ({}, {"Results": []}, {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-api", "Version": "2.5.3"}]}]},
                       {"Results": [{"Packages": [{"Name": "com.alibaba.nacos:nacos-common", "Version": "2.5.1"}]}]}):
            with self.subTest(report=report), self.assertRaises(ValueError):
                CHECK.validate(self.sbom, report, "nacos-common")
        for mutation in ("components", "dependencies"):
            broken = copy.deepcopy(self.sbom)
            broken[mutation] = []
            with self.assertRaises(ValueError):
                CHECK.validate(broken, self.report, "nacos-common")


if __name__ == "__main__":
    unittest.main()
