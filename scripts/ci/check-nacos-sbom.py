#!/usr/bin/env python3
"""Fail closed when the upstream Nacos identity is absent from SBOM/scan inventory."""
import argparse
import json
from pathlib import Path
import sys

UPSTREAM = "pkg:maven/com.alibaba.nacos/nacos-api@2.5.1"
PATCHED = "pkg:maven/com.ulticode/nacos-api-patched@2.5.1-ulticode-1"


def validate(sbom, report=None):
    component = next((item for item in sbom.get("components", []) if item.get("purl") == UPSTREAM), None)
    if component is None or (component.get("group"), component.get("name"), component.get("version")) != (
            "com.alibaba.nacos", "nacos-api", "2.5.1"):
        raise ValueError("Ordinary SBOM components must identify upstream com.alibaba.nacos:nacos-api:2.5.1")
    if not any(item.get("ref") == PATCHED and component.get("bom-ref") in item.get("dependsOn", [])
               for item in sbom.get("dependencies", [])):
        raise ValueError("Patched SBOM component must depend on the upstream identity")
    if report is not None:
        packages = [package for result in report.get("Results", []) for package in result.get("Packages", [])]
        if not any(package.get("Identifier", {}).get("PURL") == UPSTREAM or (
                package.get("Name") == "com.alibaba.nacos:nacos-api" and package.get("Version") == "2.5.1")
                for package in packages):
            raise ValueError("Trivy package inventory did not identify upstream Nacos; no findings is not sufficient")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sbom", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    try:
        validate(json.loads(args.sbom.read_text()), json.loads(args.report.read_text()) if args.report else None)
    except (OSError, ValueError, TypeError, AttributeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print("Nacos upstream SBOM identity" + (" and Trivy recognition" if args.report else "") + ": PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
