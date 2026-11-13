#!/usr/bin/env python3
"""Fail closed when the upstream Nacos identity is absent from SBOM/scan inventory."""
import argparse
import json
from pathlib import Path
import sys

UPSTREAM = "pkg:maven/com.alibaba.nacos/nacos-api@2.5.3"
PATCHED = "pkg:maven/com.ulticode/nacos-api-patched@2.5.3-ulticode-1"


def validate(sbom, report=None, artifact="nacos-api"):
    if artifact not in ("nacos-api", "nacos-client", "nacos-common"):
        raise ValueError("Unsupported Nacos upstream artifact")
    upstream = f"pkg:maven/com.alibaba.nacos/{artifact}@2.5.3"
    revision = "2" if artifact == "nacos-client" else "1"
    patched = f"pkg:maven/com.ulticode/{artifact}-patched@2.5.3-ulticode-{revision}"
    component = next((item for item in sbom.get("components", []) if item.get("purl") == upstream), None)
    if component is None or (component.get("group"), component.get("name"), component.get("version")) != (
            "com.alibaba.nacos", artifact, "2.5.3"):
        raise ValueError(f"Ordinary SBOM components must identify upstream com.alibaba.nacos:{artifact}:2.5.3")
    if not any(item.get("ref") == patched and component.get("bom-ref") in item.get("dependsOn", [])
               for item in sbom.get("dependencies", [])):
        raise ValueError("Patched SBOM component must depend on the upstream identity")
    if report is not None:
        packages = [package for result in report.get("Results", []) for package in result.get("Packages", [])]
        if not any(package.get("Identifier", {}).get("PURL") == upstream or (
                package.get("Name") == f"com.alibaba.nacos:{artifact}" and package.get("Version") == "2.5.3")
                for package in packages):
            raise ValueError("Trivy package inventory did not identify upstream Nacos; no findings is not sufficient")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sbom", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--artifact", choices=("nacos-api", "nacos-client", "nacos-common"), default="nacos-api")
    args = parser.parse_args()
    try:
        validate(json.loads(args.sbom.read_text()), json.loads(args.report.read_text()) if args.report else None, args.artifact)
    except (OSError, ValueError, TypeError, AttributeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print("Nacos upstream SBOM identity" + (" and Trivy recognition" if args.report else "") + ": PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
