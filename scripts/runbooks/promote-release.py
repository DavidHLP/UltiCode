#!/usr/bin/env python3
"""Validate all signed candidates before idempotently promoting release tags."""
from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / ".github/services-matrix.json"
POLICY = ROOT / "scripts/runbooks/image-reference-policy.sh"
PREFIX = "ghcr.io/davidhlp/ulticode"
IDENTITY = "https://github.com/DavidHLP/UltiCode/.github/workflows/docker-publish.yml@refs/heads/main"
ISSUER = "https://token.actions.githubusercontent.com"
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
SHA = re.compile(r"^[0-9a-f]{40}$")
VERSION = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")
SEVERITIES = {"UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL"}


class Reject(Exception):
    pass


def fail(message: str) -> None:
    raise Reject(message)


def run(args: list[str], *, capture: bool = True) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(args, text=True, stdout=subprocess.PIPE if capture else None,
                              stderr=subprocess.PIPE if capture else None, check=False)
    except OSError as exc:
        fail(f"cannot execute {args[0]}: {exc}")


def load_json(path: Path) -> object:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        fail(f"invalid JSON evidence {path.name}: {exc}")


def strict_file(path: Path) -> None:
    if path.is_symlink() or not path.is_file():
        fail(f"evidence must be a regular non-symlink file: {path}")


def validate_trivy(report: object, ref: str) -> None:
    if not isinstance(report, dict) or report.get("SchemaVersion") != 2:
        fail("Trivy report SchemaVersion must be 2")
    if report.get("ArtifactType") != "container_image" or report.get("ArtifactName") != ref:
        fail("Trivy report does not name candidate digest")
    metadata = report.get("Metadata")
    if not isinstance(metadata, dict) or not DIGEST.fullmatch(str(metadata.get("ImageID", ""))):
        fail("Trivy Metadata.ImageID must be a sha256 digest")
    results = report.get("Results")
    if not isinstance(results, list) or not results:
        fail("Trivy Results must be a non-empty array")
    found_os = False
    for result in results:
        if not isinstance(result, dict) or not isinstance(result.get("Target"), str) or not result["Target"]:
            fail("Trivy result Target must be non-empty")
        if result.get("Class") not in {"os-pkgs", "lang-pkgs"}:
            fail("Trivy result Class is invalid")
        if not isinstance(result.get("Type"), str) or not result["Type"]:
            fail("Trivy result Type must be non-empty")
        found_os |= result["Class"] == "os-pkgs"
        vulns = result.get("Vulnerabilities", [])
        if not isinstance(vulns, list):
            fail("Trivy Vulnerabilities must be an array when present")
        for finding in vulns:
            if not isinstance(finding, dict):
                fail("Trivy vulnerability entry must be an object")
            for key in ("VulnerabilityID", "PkgName", "InstalledVersion"):
                if not isinstance(finding.get(key), str) or not finding[key]:
                    fail(f"Trivy finding {key} must be non-empty")
            if finding.get("Severity") not in SEVERITIES:
                fail("Trivy finding Severity is invalid")
            if "FixedVersion" in finding and not isinstance(finding["FixedVersion"], str):
                fail("Trivy finding FixedVersion must be a string")
            if finding["Severity"] in {"HIGH", "CRITICAL"} and finding.get("FixedVersion", ""):
                fail(f"blocking fixed {finding['Severity']} vulnerability {finding['VulnerabilityID']}")
    if not found_os:
        fail("Trivy report must contain at least one os-pkgs result")


def verified_statement(output: str, kind: str, ref: str, source: str) -> object:
    try:
        envelopes = json.loads(output)
    except json.JSONDecodeError as exc:
        fail(f"cosign {kind} verification returned invalid JSON: {exc}")
    if isinstance(envelopes, dict):
        envelopes = [envelopes]
    if not isinstance(envelopes, list):
        fail(f"cosign {kind} verification did not return envelope list")
    statements = []
    for envelope in envelopes:
        if not isinstance(envelope, dict) or not isinstance(envelope.get("payload"), str):
            continue
        try:
            statement = json.loads(base64.b64decode(envelope["payload"], validate=True))
        except (ValueError, json.JSONDecodeError):
            continue
        if not isinstance(statement, dict):
            continue
        subjects = statement.get("subject")
        if not isinstance(subjects, list) or len(subjects) != 1:
            continue
        subject = subjects[0]
        if not isinstance(subject, dict) or subject.get("name") != ref.split("@", 1)[0]:
            continue
        digest_map = subject.get("digest")
        if not isinstance(digest_map, dict) or digest_map.get("sha256") != ref.rsplit("sha256:", 1)[1]:
            continue
        statements.append(statement)
    if len(statements) != 1:
        fail(f"cosign {kind} statement must uniquely match candidate subject")
    statement = statements[0]
    if statement.get("_type") != "https://in-toto.io/Statement/v0.1":
        fail(f"verified {kind} statement type mismatch")
    expected_type = "https://slsa.dev/provenance/v0.2" if kind == "slsaprovenance" else "https://spdx.dev/Document"
    if statement.get("predicateType") != expected_type:
        fail(f"verified {kind} predicate type mismatch")
    if kind == "slsaprovenance":
        uri = statement.get("predicate", {}).get("invocation", {}).get("configSource", {}).get("uri") if isinstance(statement.get("predicate"), dict) else None
        if uri != source:
            fail("verified provenance source commit mismatch")
    return statement.get("predicate")

def assert_outputs_unchanged(evidence: Path, outputs: dict[str, str]) -> None:
    for filename, content in outputs.items():
        path = evidence / filename
        if path.exists() or path.is_symlink():
            strict_file(path)
            if path.read_text(encoding="utf-8") != content:
                fail(f"refusing to overwrite different {filename}")

def write_outputs(evidence: Path, outputs: dict[str, str]) -> None:
    for filename, content in outputs.items():
        path = evidence / filename
        if not path.exists():
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                stream.write(content)

def require_serialized_github_writer() -> None:
    expected_ref = "DavidHLP/UltiCode/.github/workflows/docker-publish.yml@refs/heads/main"
    if (os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("GITHUB_REPOSITORY") != "DavidHLP/UltiCode"
            or os.environ.get("GITHUB_WORKFLOW_REF") != expected_ref
            or os.environ.get("GITHUB_REF") != "refs/heads/main"):
        fail("--execute is restricted to serialized Docker Publish workflow on main; local execution is unsupported")


def canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def ensure_predicate_equal(expected: object, verified: object, label: str) -> None:
    if canonical(expected) != canonical(verified):
        fail(f"verified {label} predicate differs from candidate evidence")


def read_candidate(directory: Path, service: dict[str, object], source_sha: str, expected_version: str) -> dict[str, object]:
    name = str(service["name"])
    expected_dir = directory / f"candidate-evidence-{name}"
    if expected_dir.is_symlink() or not expected_dir.is_dir():
        fail(f"missing candidate evidence directory for {name}")
    allowed = {f"{name}.{suffix}" for suffix in ("image-ref", "release.json", "spdx.json", "provenance.json", "trivy.json", "version", "source-commit")}
    entries = list(expected_dir.iterdir())
    if {entry.name for entry in entries} != allowed:
        fail(f"{name} evidence directory has missing or extra files")
    for path in entries:
        strict_file(path)
    ref_lines = (expected_dir / f"{name}.image-ref").read_text(encoding="utf-8").splitlines()
    if len(ref_lines) != 1 or not re.fullmatch(r"[A-Z][A-Z0-9_]+_IMAGE_REF=.+", ref_lines[0]):
        fail(f"{name} image-ref evidence invalid")
    ref = ref_lines[0].split("=", 1)[1]
    repo, sep, digest = ref.partition("@")
    if not sep or repo != f"{PREFIX}/{name}" or not DIGEST.fullmatch(digest):
        fail(f"{name} image ref must target approved repository and digest")
    source_file = (expected_dir / f"{name}.source-commit").read_text(encoding="utf-8")
    if source_file not in {source_sha, source_sha + "\n"}:
        fail(f"{name} source commit mismatch")
    version = (expected_dir / f"{name}.version").read_text(encoding="utf-8")
    if version.endswith("\n"):
        version = version[:-1]
    if version != expected_version or (version and not VERSION.fullmatch(version)):
        fail(f"{name} release version differs from source POM")
    release = load_json(expected_dir / f"{name}.release.json")
    if release != {"service": name, "image_ref": ref}:
        fail(f"{name} release metadata mismatch")
    sbom = load_json(expected_dir / f"{name}.spdx.json")
    provenance = load_json(expected_dir / f"{name}.provenance.json")
    validate_trivy(load_json(expected_dir / f"{name}.trivy.json"), ref)
    source_uri = f"https://github.com/DavidHLP/UltiCode@{source_sha}"
    if not isinstance(provenance, dict) or provenance.get("invocation", {}).get("configSource", {}).get("uri") != source_uri:
        fail(f"{name} provenance source mismatch")
    return {"name": name, "ref": ref, "version": version, "sbom": sbom, "provenance": provenance,
            "dir": expected_dir, "repo": repo, "digest": digest}


def verify_all(candidate: dict[str, object], source_sha: str) -> None:
    cosign = os.environ.get("COSIGN_BIN", "cosign")
    ref = str(candidate["ref"])
    common = ["--certificate-identity", IDENTITY, "--certificate-oidc-issuer", ISSUER]
    result = run([cosign, "verify", *common, ref])
    if result.returncode:
        fail(f"cosign signature verification failed for {candidate['name']}")
    for kind, expected in (("spdxjson", candidate["sbom"]), ("slsaprovenance", candidate["provenance"])):
        result = run([cosign, "verify-attestation", "--output", "json", "--type", kind, *common, ref])
        if result.returncode:
            fail(f"cosign {kind} attestation verification failed for {candidate['name']}")
        verified = verified_statement(result.stdout or "", kind, ref, f"https://github.com/DavidHLP/UltiCode@{source_sha}")
        ensure_predicate_equal(expected, verified, kind)


def registry_tag(docker: str, image: str, tag: str) -> str | None:
    result = run([docker, "buildx", "imagetools", "inspect", f"{image}:{tag}"])
    if result.returncode:
        error = result.stderr or ""
        if error.strip() == f"ERROR: {image}:{tag}: not found":
            return None
        if "MANIFEST_UNKNOWN" in error or "manifest unknown" in error.lower() or "no such manifest" in error.lower():
            return None
        fail(f"cannot determine existing registry tag {image}:{tag}: {error.strip()}")
    match = re.search(r"(?m)^Digest:\s*(sha256:[0-9a-f]{64})\s*$", result.stdout or "")
    if not match:
        fail(f"registry inspect returned no canonical digest for {image}:{tag}")
    return match.group(1)


def main() -> int:
    if len(sys.argv) not in {4, 5} or (len(sys.argv) == 5 and sys.argv[4] != "--execute"):
        print("Usage: promote-release.sh EVIDENCE_DIR SOURCE_SHA IMAGE_PREFIX [--execute]", file=sys.stderr)
        return 2
    evidence_arg = Path(sys.argv[1])
    if evidence_arg.is_symlink() or not evidence_arg.is_dir():
        fail("EVIDENCE_DIR must be a real directory")
    evidence = evidence_arg.resolve()
    source_sha, prefix = sys.argv[2], sys.argv[3]
    execute = len(sys.argv) == 5
    if execute:
        require_serialized_github_writer()
    if not SHA.fullmatch(source_sha):
        fail("SOURCE_SHA must be a full lowercase 40-character commit SHA")
    if prefix != PREFIX:
        fail(f"IMAGE_PREFIX must equal {PREFIX}")
    services = json.loads(MATRIX.read_text(encoding="utf-8"))
    names = [str(service["name"]) for service in services]
    if len(names) != 9 or len(set(names)) != 9:
        fail("services matrix must contain exactly nine unique deployable services")
    expected_dirs = {f"candidate-evidence-{name}" for name in names}
    actual_dirs = {item.name for item in evidence.iterdir() if item.is_dir() or item.is_symlink()}
    if actual_dirs != expected_dirs:
        fail("evidence root has missing or unexpected candidate service directories")
    pom = ET.parse(ROOT / "services/pom.xml").getroot()
    properties = pom.find("{http://maven.apache.org/POM/4.0.0}properties")
    candidates = []
    for service in services:
        property_name = service.get("maven_version_property")
        expected_version = properties.findtext(f"{{http://maven.apache.org/POM/4.0.0}}{property_name}") if property_name and properties is not None else ""
        if property_name and not expected_version:
            fail(f"version property missing from POM: {property_name}")
        candidates.append(read_candidate(evidence, service, source_sha, expected_version or ""))
    candidates.sort(key=lambda item: str(item["name"]))
    # Reuse the production set validator unchanged; only immutable digest refs enter it.
    refs_text = "".join(f"{candidate['name'].upper().replace('-', '_')}_IMAGE_REF={candidate['ref']}\n" for candidate in candidates)
    env = os.environ.copy()
    env["IMAGE_REF_LIST"] = refs_text
    check = subprocess.run(["bash", str(POLICY), "validate"], env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check.returncode:
        fail(f"existing image-reference-policy rejected candidate set: {check.stderr.strip()}")
    for candidate in candidates:
        verify_all(candidate, source_sha)
    docker = os.environ.get("DOCKER_BIN", "docker")
    planned = []
    for candidate in candidates:
        tags = [f"sha-{source_sha[:7]}"]
        if candidate["version"]:
            tags.append(f"v{candidate['version']}")
        planned.append((candidate, tags, {}))
    manifest = "".join(f"{candidate['name'].upper().replace('-', '_')}_IMAGE_REF={candidate['ref']}\n" for candidate, _, _ in planned)
    release_set = {"source_commit": source_sha, "services": [
        {"name": candidate["name"], "image_ref": candidate["ref"], "tags": tags,
         "evidence_sha256": {suffix: hashlib.sha256((candidate["dir"] / f"{candidate['name']}.{suffix}").read_bytes()).hexdigest()
                             for suffix in ("image-ref", "release.json", "spdx.json", "provenance.json", "trivy.json", "version", "source-commit")}}
        for candidate, tags, _ in planned]}
    outputs = {"release-manifest.txt": manifest, "release-set.json": json.dumps(release_set, sort_keys=True, indent=2) + "\n"}
    if execute:
        assert_outputs_unchanged(evidence, outputs)
    checked = []
    for candidate, tags, _ in planned:
        existing = {}
        for tag in tags:
            digest = registry_tag(docker, str(candidate["repo"]), tag)
            if digest is not None and digest != candidate["digest"]:
                fail(f"refusing to move existing tag {candidate['name']}:{tag} ({digest} != {candidate['digest']})")
            existing[tag] = digest == candidate["digest"]
        checked.append((candidate, tags, existing))
    planned = checked
    if execute:
        for candidate, tags, existing in planned:
            missing = [tag for tag in tags if not existing[tag]]
            if not missing:
                continue
            result = run([docker, "buildx", "imagetools", "create", "--prefer-index=false",
                          *(f"-t={candidate['repo']}:{tag}" for tag in missing), str(candidate["ref"])])
            if result.returncode:
                fail(f"tag promotion failed for {candidate['name']}; promoted services retained")
            for tag in missing:
                actual = registry_tag(docker, str(candidate["repo"]), tag)
                if actual != candidate["digest"]:
                    fail(f"promoted tag readback mismatch for {candidate['name']}:{tag}; promoted services retained")
        for candidate, tags, _ in planned:
            for tag in tags:
                actual = registry_tag(docker, str(candidate["repo"]), tag)
                if actual != candidate["digest"]:
                    fail(f"final tag readback mismatch for {candidate['name']}:{tag}; promoted services retained")
        write_outputs(evidence, outputs)
    print(f"candidate set validated: {len(candidates)} services; mode={'execute' if execute else 'dry-run'}")
    for candidate, tags, existing in planned:
        print(f"{candidate['name']}: {candidate['ref']} -> " + ",".join(f"{tag}{' (existing)' if existing[tag] else ''}" for tag in tags))
    if execute:
        print(f"release manifest: {evidence / 'release-manifest.txt'}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Reject as exc:
        print(f"promote-release: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as exc:
        print(f"promote-release: FAIL: malformed input ({exc})", file=sys.stderr)
        raise SystemExit(1)
