#!/usr/bin/env python3
"""Prepare eight durable assets from an already published, verified image set.

Never build images, mutate registry tags, create Git tags or publish a Release.
Publication requires a separate explicit human authorization.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "DavidHLP/UltiCode"
PREFIX = "ghcr.io/davidhlp/ulticode/"


def command(args, **kwargs):
    # Identify the failed tool without exposing arguments, headers or logs.
    result = subprocess.run(args, check=False, capture_output=True, timeout=900, **kwargs)
    if result.returncode:
        raise ValueError(f"Verification/packaging command {Path(args[0]).name} failed (exit {result.returncode})")
    return result.stdout


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def regular(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"Not a regular evidence file: {path.name}")
    return path


def validate_source(root, sha):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Source must be a full lowercase commit SHA")
    git = lambda *args: command(["git", "-C", str(root), *args]).decode().strip()
    if Path(git("rev-parse", "--show-toplevel")).resolve() != root.resolve():
        raise ValueError("Source root must be the repository root")
    if git("rev-parse", "HEAD") != sha or git("status", "--porcelain", "--untracked-files=no"):
        raise ValueError("Source checkout must be clean and at the exact verified SHA")
    matrix = json.loads(git("show", f"{sha}:.github/services-matrix.json"))
    names = {entry["name"] for entry in matrix}
    if len(matrix) != 9 or len(names) != 9:
        raise ValueError("Source must declare exactly nine services")
    return names


def validate_evidence(evidence, sha, names):
    if evidence.is_symlink() or not evidence.is_dir():
        raise ValueError("Evidence must be a real directory")
    release = json.loads(regular(evidence / "release-set.json").read_text())
    services = release.get("services", [])
    if release.get("source_commit") != sha or len(services) != 9 or {s["name"] for s in services} != names:
        raise ValueError("Incomplete or mismatched source/service evidence")
    files = {"release-set.json", "release-manifest.txt"}
    directories = {f"candidate-evidence-{name}" for name in names}
    if {p.name for p in evidence.iterdir()} != files | directories:
        raise ValueError("Unexpected or incomplete evidence root")
    suffixes = {"image-ref", "release.json", "spdx.json", "provenance.json", "trivy.json", "version", "source-commit"}
    refs = []
    for service in sorted(services, key=lambda item: item["name"]):
        name = service["name"]
        ref = service["image_ref"]
        if not re.fullmatch(re.escape(PREFIX + name) + r"@sha256:[0-9a-f]{64}", ref):
            raise ValueError("Invalid immutable image reference")
        refs.append(f"{name.upper().replace('-', '_')}_IMAGE_REF={ref}\n")
        directory = evidence / f"candidate-evidence-{name}"
        if directory.is_symlink() or not directory.is_dir():
            raise ValueError("Candidate directory must not be a symlink")
        if set(service["evidence_sha256"]) != suffixes:
            raise ValueError("Incomplete evidence hash inventory")
        if {p.name for p in directory.iterdir()} != {f"{name}.{suffix}" for suffix in suffixes}:
            raise ValueError("Unexpected or missing candidate evidence")
        for suffix, expected in service["evidence_sha256"].items():
            path = regular(directory / f"{name}.{suffix}")
            if sha256(path) != expected:
                raise ValueError(f"Evidence checksum mismatch: {path.name}")
        if (directory / f"{name}.source-commit").read_text().strip() != sha:
            raise ValueError("Candidate source does not match")
        version = (directory / f"{name}.version").read_text().strip()
        tags = [f"sha-{sha[:7]}"]
        if name.startswith("backend-"):
            if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?", version):
                raise ValueError("Backend evidence requires a valid service version")
            tags.append(f"v{version}")
        elif version:
            raise ValueError("Frontend evidence must not invent a service version")
        if service.get("tags") != tags:
            raise ValueError("Promoted tag inventory does not match source/version evidence")
    if regular(evidence / "release-manifest.txt").read_text() != "".join(refs):
        raise ValueError("Manifest does not exactly match the complete release set")
    return release


def verify_published_set(root, evidence, sha, run_id):
    if not re.fullmatch(r"[1-9][0-9]*", run_id):
        raise ValueError("Workflow run must be a numeric ID")
    run = json.loads(command(["gh", "api", f"repos/{REPOSITORY}/actions/runs/{run_id}"]))
    if (run.get("id") != int(run_id) or run.get("path") != ".github/workflows/ci.yml"
            or run.get("repository", {}).get("full_name") != REPOSITORY
            or run.get("head_sha") != sha or run.get("conclusion") != "success"
            or run.get("event") != "push" or run.get("head_branch") != "main"):
        raise ValueError("Workflow must be a successful main push at the exact source")
    # Reuse the source revision's signature, SPDX/SLSA, scan, version and tag checks.
    # No --execute, credential creation, registry writes or new image builds.
    result = command(["bash", str(root / "scripts/runbooks/promote-release.sh"),
                      str(evidence.resolve()), sha, PREFIX.rstrip("/")]).decode()
    if "candidate set validated: 9 services; mode=dry-run" not in result:
        raise ValueError("Complete signed release verification did not succeed")
    # Promotion dry-run also permits tags that would still need creating.
    # Packaging a deployable release requires every planned tag to EXIST now
    # and resolve to its already signed immutable digest; never create it here.
    release = json.loads((evidence / "release-set.json").read_text())
    for service in release["services"]:
        image, digest = service["image_ref"].split("@", 1)
        for tag in service["tags"]:
            actual = command([os.environ.get("DOCKER_BIN", "docker"), "buildx", "imagetools", "inspect",
                              f"{image}:{tag}", "--format", '{{printf "%s" .Manifest.Digest}}']).decode().strip()
            if actual != digest:
                raise ValueError(f"Published tag readback mismatch: {service['name']}:{tag}")


def prepare(root, evidence, sha, version, notes, run_id, output):
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?", version):
        raise ValueError("Version must be an explicit v-prefixed semantic version")
    names = validate_source(root, sha)
    release = validate_evidence(evidence, sha, names)
    regular(notes)
    if notes.stat().st_size > 131072 or not notes.read_text().strip():
        raise ValueError("Supply nonempty UTF-8 release notes of at most 128 KiB")
    verify_published_set(root, evidence, sha, run_id)
    if output.is_symlink():
        raise ValueError("Output must not be a symlink")
    with tempfile.TemporaryDirectory(prefix="ulticode-release-") as temporary:
        stage = Path(temporary)
        archive_name = f"UltiCode-{sha}.tar.gz"
        command(["git", "-C", str(root), "archive", "--format=tar.gz", f"--prefix=UltiCode-{sha}/",
                 "-o", str(stage / archive_name), sha])
        (stage / "LICENSE").write_bytes(command(["git", "-C", str(root), "show", f"{sha}:LICENSE"]))
        (stage / "release-notes.md").write_bytes(notes.read_bytes())
        for name in ("release-set.json", "release-manifest.txt"):
            (stage / name).write_bytes((evidence / name).read_bytes())
        with zipfile.ZipFile(stage / f"release-evidence-{version}.zip", "w", zipfile.ZIP_DEFLATED) as z:
            for path in sorted(evidence.rglob("*")):
                if path.is_file():
                    info = zipfile.ZipInfo(path.relative_to(evidence).as_posix(), (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    z.writestr(info, regular(path).read_bytes())
        report = {"version": version, "source_commit": sha,
                  "workflow_run": f"https://github.com/{REPOSITORY}/actions/runs/{run_id}",
                  "service_count": len(release["services"]), "verification": "complete signed set and promoted tags passed",
                  "publication": "not performed; exact tag/Release publication requires separate explicit human authorization"}
        (stage / "verification.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
        (stage / "SHA256SUMS").write_text("".join(f"{sha256(p)}  {p.name}\n" for p in sorted(stage.iterdir())))
        if output.exists():
            if not output.is_dir() or {p.name for p in output.iterdir()} != {p.name for p in stage.iterdir()}:
                raise ValueError("Existing output is incomplete or different; no overwrite")
            for p in stage.iterdir():
                if sha256(regular(output / p.name)) != sha256(p):
                    raise ValueError("Existing output differs; no overwrite")
            return "identical existing assets verified"
        output.mkdir(parents=True, exist_ok=False)
        for p in stage.iterdir():
            with (output / p.name).open("xb") as target:
                target.write(p.read_bytes())
        return "eight unpublished verified assets prepared"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_commit")
    parser.add_argument("evidence", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--workflow-run", required=True)
    parser.add_argument("--source-root", type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        result = prepare(args.source_root, args.evidence, args.source_commit, args.version,
                         args.notes, args.workflow_run, args.output)
    except (ValueError, OSError, KeyError, json.JSONDecodeError, subprocess.SubprocessError) as error:
        # Do not echo subprocess logs, headers or authentication details.
        parser.exit(1, f"Verified release packaging failed: {type(error).__name__}: {str(error) if isinstance(error, ValueError) else 'input or verifier command failed'}\n")
    print(result + "; no tags, images or Releases changed")


if __name__ == "__main__":
    main()
