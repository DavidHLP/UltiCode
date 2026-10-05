#!/usr/bin/env python3
"""Offline regression checks for promote-release.sh; no registry access."""
import base64
import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROMOTER = ROOT / "scripts/runbooks/promote-release.sh"
MATRIX = json.loads((ROOT / ".github/services-matrix.json").read_text())
SHA = "c" * 40
PREFIX = "ghcr.io/davidhlp/ulticode"
DIGEST = "sha256:" + "a" * 64
GITHUB_WRITER = {"GITHUB_ACTIONS": "true", "GITHUB_REPOSITORY": "DavidHLP/UltiCode",
                 "GITHUB_WORKFLOW_REF": "DavidHLP/UltiCode/.github/workflows/docker-publish.yml@refs/heads/main",
                 "GITHUB_REF": "refs/heads/main"}


def canonical(path):
    return json.loads(path.read_text())


def seed(base):
    root = base / "evidence"
    root.mkdir()
    for service in MATRIX:
        name = service["name"]
        folder = root / f"candidate-evidence-{name}"
        folder.mkdir()
        ref = f"{PREFIX}/{name}@{DIGEST}"
        var = name.upper().replace("-", "_") + "_IMAGE_REF"
        (folder / f"{name}.image-ref").write_text(f"{var}={ref}\n")
        spdx = {"spdxVersion": "SPDX-2.3", "name": name}
        provenance = {"buildType": "https://slsa.dev/provenance/v1",
                      "builder": {"id": "DavidHLP/UltiCode/.github/workflows/docker-publish.yml@refs/heads/main"},
                      "invocation": {"configSource": {"uri": f"https://github.com/DavidHLP/UltiCode@{SHA}"}}}
        trivy = {"SchemaVersion": 2, "ArtifactType": "container_image", "ArtifactName": ref,
                 "Metadata": {"ImageID": DIGEST},
                 "Results": [{"Target": "os", "Class": "os-pkgs", "Type": "alpine", "Vulnerabilities": []}]}
        (folder / f"{name}.release.json").write_text(json.dumps({"service": name, "image_ref": ref}))
        (folder / f"{name}.spdx.json").write_text(json.dumps(spdx))
        (folder / f"{name}.provenance.json").write_text(json.dumps(provenance))
        (folder / f"{name}.trivy.json").write_text(json.dumps(trivy))
        (folder / f"{name}.version").write_text("1.0.1\n" if service.get("maven_version_property") else "\n")
        (folder / f"{name}.source-commit").write_text(SHA + "\n")
    return root


def stubs(base):
    bindir = base / "bin"
    bindir.mkdir(exist_ok=True)
    state = base / "registry.json"
    log = base / "calls.log"
    state.write_text("{}")
    log.write_text("")
    docker = bindir / "docker"
    docker.write_text(r'''#!/usr/bin/env python3
import json, os, pathlib, re, sys
args=sys.argv[1:]
state=pathlib.Path(os.environ["REGISTRY_STATE"])
log=pathlib.Path(os.environ["CALL_LOG"])
with log.open("a") as f: f.write("docker " + " ".join(args) + "\\n")
data=json.loads(state.read_text())
if args[:3] == ["buildx", "imagetools", "inspect"]:
    image, tag=args[3].rsplit(":", 1); key=image+":"+tag
    if os.environ.get("BAD_FINAL_READBACK") == key and log.read_text().count("inspect " + key) > 1:
        print("Digest: sha256:"+"f"*64); sys.exit(0)
    if key not in data:
        print(os.environ.get("INSPECT_ERROR", f"ERROR: {key}: not found"), file=sys.stderr); sys.exit(1)
    print("Digest: " + data[key]); sys.exit(0)
if args[:3] == ["buildx", "imagetools", "create"]:
    fail_after=os.environ.get("FAIL_CREATE_AFTER")
    if os.environ.get("FAIL_CREATE") or (fail_after and log.read_text().count("imagetools create") >= int(fail_after)):
        sys.exit(3)
    tags=[a.split("=",1)[1] for a in args if a.startswith("-t=")]
    ref=args[-1]; digest=ref.rsplit("@",1)[1]
    for tagref in tags: data[tagref]=digest
    if os.environ.get("BAD_READBACK") and tags:
        data[tags[0]]="sha256:"+"f"*64
    state.write_text(json.dumps(data)); sys.exit(0)
sys.exit(2)
''')
    cosign = bindir / "cosign"
    cosign.write_text(r'''#!/usr/bin/env python3
import base64, json, os, pathlib, sys
args=sys.argv[1:]
with pathlib.Path(os.environ["CALL_LOG"]).open("a") as f: f.write("cosign " + " ".join(args) + "\\n")
if args[0] == "verify":
    sys.exit(1 if os.environ.get("COSIGN_FAIL") == "signature" else 0)
if args[0] == "verify-attestation":
    if os.environ.get("COSIGN_FAIL") == "attestation": sys.exit(1)
    kind=args[args.index("--type")+1]; ref=args[-1]
    name=ref.split("/")[-1].split("@")[0]
    root=pathlib.Path(os.environ["EVIDENCE_ROOT"])/("candidate-evidence-"+name)
    suffix="spdx" if kind == "spdxjson" else "provenance"
    predicate=json.loads((root/(name+"."+suffix+".json")).read_text())
    if kind == "slsaprovenance":
        predicate.pop("subject", None)  # Cosign v2.5.3 unmarshals typed SLSA v0.2 predicate.
    subject=ref.split("@",1)[0]
    if os.environ.get("COSIGN_FAIL") == "wrong-subject": subject=subject+"/other"
    if os.environ.get("COSIGN_FAIL") == "wrong-source" and kind == "slsaprovenance":
        predicate["invocation"]["configSource"]["uri"]="https://github.com/DavidHLP/UltiCode@"+"d"*40
    if os.environ.get("COSIGN_FAIL") == "wrong-predicate" and kind == "spdxjson": predicate["name"]="not-candidate"
    predicate_type="https://spdx.dev/Document" if kind == "spdxjson" else "https://slsa.dev/provenance/v0.2"
    statement={"_type":"https://in-toto.io/Statement/v0.1","predicateType":predicate_type,
               "subject":[{"name":subject,"digest":{"sha256":ref.rsplit("sha256:",1)[1]}}],"predicate":predicate}
    payload=base64.b64encode(json.dumps(statement,separators=(",",":")).encode()).decode()
    print(json.dumps({"payload":payload}))
    sys.exit(0)
sys.exit(2)
''')
    docker.chmod(0o755); cosign.chmod(0o755)
    return bindir, state, log


def call(root, bindir, state, log, *args, **overrides):
    env = os.environ.copy()
    env.update({"PATH": str(bindir) + os.pathsep + env["PATH"], "DOCKER_BIN": str(bindir / "docker"),
                "COSIGN_BIN": str(bindir / "cosign"), "REGISTRY_STATE": str(state), "CALL_LOG": str(log),
                "EVIDENCE_ROOT": str(root)})
    env.update({key: str(value) for key, value in overrides.items()})
    return subprocess.run([str(PROMOTER), str(root), SHA, PREFIX, *args], env=env, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def assert_reject_without_write(root, bindir, state, log, mutate=None, *args, **overrides):
    state.write_text("{}")
    log.write_text("")
    if mutate:
        mutate(root)
    result = call(root, bindir, state, log, *args, **overrides)
    assert result.returncode != 0, result.stdout + result.stderr
    assert not any("imagetools create" in line for line in log.read_text().splitlines()), log.read_text()
    assert not (root / "release-manifest.txt").exists()


def main():
    with tempfile.TemporaryDirectory(prefix="promote-release-contract-") as temp:
        base = Path(temp)
        root = seed(base)
        bindir, state, log = stubs(base)
        result = call(root, bindir, state, log)
        assert result.returncode == 0, result.stdout + result.stderr
        assert not state.read_text() != "{}"
        assert not (root / "release-manifest.txt").exists()

        workflow = (ROOT / ".github/workflows/docker-publish.yml").read_text()
        assert "group: ulticode-release-tag-writer" in workflow and "cancel-in-progress: false" in workflow
        assert_reject_without_write(root, bindir, state, log, None, "--execute")
        assert not log.read_text(), "local --execute must stop before registry access"
        for filename in ("release-manifest.txt", "release-set.json"):
            conflict_path = root / filename
            conflict_path.write_text("different content\n")
            log.write_text("")
            result = call(root, bindir, state, log, "--execute", **GITHUB_WRITER)
            assert result.returncode != 0 and "imagetools" not in log.read_text(), result.stdout + result.stderr
            conflict_path.unlink()
        existing_tags = {}
        for service in MATRIX:
            service_name = service["name"]
            image = f"{PREFIX}/{service_name}"
            existing_tags[f"{image}:sha-{SHA[:7]}"] = DIGEST
            if service.get("maven_version_property"):
                existing_tags[f"{image}:v1.0.1"] = DIGEST
        state.write_text(json.dumps(existing_tags)); log.write_text("")
        key = f"{PREFIX}/{MATRIX[0]['name']}:sha-{SHA[:7]}"
        result = call(root, bindir, state, log, "--execute", **GITHUB_WRITER, BAD_FINAL_READBACK=key)
        assert result.returncode != 0 and "imagetools create" not in log.read_text()
        assert not (root / "release-manifest.txt").exists()

        def mutate_file(name, obj):
            path = next(root.glob(f"candidate-evidence-{name}/{name}.trivy.json"))
            path.write_text(json.dumps(obj))
        name = MATRIX[0]["name"]
        valid = canonical(root / f"candidate-evidence-{name}/{name}.trivy.json")
        missing_results = dict(valid); missing_results.pop("Results")
        bad_results = (None, missing_results, {**valid, "Results": "invalid"},
                       {**valid, "Results": None}, {**valid, "Results": []},
                       {**valid, "Results": [{ "Target": "os", "Class": "os-pkgs", "Type": "alpine", "Vulnerabilities": None }]},
                       {**valid, "Results": [{ "Target": "os", "Class": "os-pkgs", "Type": "alpine", "Vulnerabilities": ["invalid"] }]},
                       {**valid, "Results": [{ "Target": "os", "Class": "os-pkgs", "Type": "alpine", "Vulnerabilities": [{ "VulnerabilityID": "CVE-X", "PkgName": "p", "InstalledVersion": "1", "Severity": "HIGH", "FixedVersion": "2" }] }]})
        for bad in bad_results:
            assert_reject_without_write(root, bindir, state, log, lambda r, bad=bad: mutate_file(name, bad))
            mutate_file(name, valid)

        assert_reject_without_write(root, bindir, state, log, None, "e"*40)
        def wrong_source(r):
            path = r / f"candidate-evidence-{name}/{name}.source-commit"
            path.write_text("e"*40+"\n")
        assert_reject_without_write(root, bindir, state, log, wrong_source)
        import shutil
        shutil.rmtree(root); root=seed(base)
        bindir, state, log = stubs(base)
        def wrong_digest(r):
            path = r / f"candidate-evidence-{name}/{name}.image-ref"
            path.write_text(path.read_text().replace("a"*64,"b"*64))
        assert_reject_without_write(root, bindir, state, log, wrong_digest)
        shutil.rmtree(root); root=seed(base)
        bindir, state, log = stubs(base)
        def duplicate_variable(r):
            path = r / f"candidate-evidence-{name}/{name}.image-ref"
            path.write_text(path.read_text() + path.read_text())
        assert_reject_without_write(root, bindir, state, log, duplicate_variable)
        image_file = root / f"candidate-evidence-{name}/{name}.image-ref"
        image_file.write_text(image_file.read_text().splitlines()[0] + "\n")
        provenance_path = root / f"candidate-evidence-{name}/{name}.provenance.json"
        valid_provenance = provenance_path.read_bytes()
        def wrong_provenance(r):
            proof = canonical(provenance_path)
            proof["invocation"]["configSource"]["uri"] = "https://github.com/DavidHLP/UltiCode@" + "f" * 40
            provenance_path.write_text(json.dumps(proof))
        assert_reject_without_write(root, bindir, state, log, wrong_provenance)
        provenance_path.write_bytes(valid_provenance)
        def extra_provenance_subject(r):
            proof = canonical(provenance_path)
            proof["subject"] = [{"name": f"{PREFIX}/{name}", "digest": {"sha256": DIGEST[7:]}}]
            provenance_path.write_text(json.dumps(proof))
        extra_provenance_subject(root)
        extra_result = call(root, bindir, state, log)
        assert extra_result.returncode != 0
        assert "verified slsaprovenance predicate differs from candidate evidence" in extra_result.stderr
        assert "verify-attestation" in log.read_text() and "imagetools create" not in log.read_text()
        assert not (root / "release-manifest.txt").exists()
        shutil.rmtree(root); root=seed(base)
        bindir, state, log = stubs(base)
        for mode in ("signature", "attestation", "wrong-subject", "wrong-source", "wrong-predicate"):
            assert_reject_without_write(root, bindir, state, log, None, COSIGN_FAIL=mode)

        for error in ("ERROR: unauthorized: HTTP 401", "ERROR: forbidden: HTTP 403",
                      "ERROR: dial tcp: network is unreachable", "ERROR: context deadline exceeded (timeout)",
                      "ERROR: TLS handshake failed", f"ERROR: {PREFIX}/other:v1.0.1: not found",
                      "ERROR: not found"):
            assert_reject_without_write(root, bindir, state, log, None, "--execute", **GITHUB_WRITER, INSPECT_ERROR=error)

        # Extra/missing evidence and unsafe file types are rejected before verification.
        missing = root / f"candidate-evidence-{name}/{name}.trivy.json"
        saved = missing.read_bytes(); missing.unlink()
        assert_reject_without_write(root, bindir, state, log)
        missing.write_bytes(saved)
        extra = root / "candidate-evidence-injected"; extra.mkdir()
        assert_reject_without_write(root, bindir, state, log)
        extra.rmdir()
        linked = root / f"candidate-evidence-{name}/{name}.release.json"
        saved = linked.read_bytes()
        target = base / "linked-release.json"
        target.write_bytes(saved)
        linked.unlink(); linked.symlink_to(target)
        assert_reject_without_write(root, bindir, state, log)
        linked.unlink(); linked.write_bytes(saved)
        # Existing conflicting tag fails before any publication.
        conflict = {f"{PREFIX}/{name}:sha-{SHA[:7]}": "sha256:"+"f"*64}
        state.write_text(json.dumps(conflict)); log.write_text("")
        result = call(root, bindir, state, log, "--execute", **GITHUB_WRITER)
        assert result.returncode != 0 and "imagetools create" not in log.read_text()
        assert not (root / "release-manifest.txt").exists()

        # All-nine happy path creates tags then writes a complete nine-entry manifest.
        state.write_text("{}"); log.write_text("")
        result = call(root, bindir, state, log, "--execute", **GITHUB_WRITER)
        assert result.returncode == 0, result.stdout + result.stderr
        manifest = (root / "release-manifest.txt").read_text().splitlines()
        assert len(manifest) == 9
        assert len(json.loads((root / "release-set.json").read_text())["services"]) == 9
        creates = log.read_text().count("imagetools create")
        assert creates == 9
        # Exact same content is idempotent: no extra tag writes.
        result = call(root, bindir, state, log, "--execute", **GITHUB_WRITER)
        assert result.returncode == 0, result.stdout + result.stderr
        assert log.read_text().count("imagetools create") == creates

        # Partial failure and bad registry readback never emit complete manifest.
        shutil.rmtree(root); root=seed(base); bindir,state,log=stubs(base)
        result = call(root, bindir, state, log, "--execute", FAIL_CREATE_AFTER="2", **GITHUB_WRITER)
        assert result.returncode != 0 and not (root / "release-manifest.txt").exists()
        assert len(json.loads(state.read_text())) >= 2
        shutil.rmtree(root); root=seed(base); bindir,state,log=stubs(base)
        result = call(root, bindir, state, log, "--execute", BAD_READBACK="1", **GITHUB_WRITER)
        assert result.returncode != 0 and not (root / "release-manifest.txt").exists()
        print("candidate promoter negative, dry-run, idempotency, partial-failure contracts: PASS")


if __name__ == "__main__":
    main()
