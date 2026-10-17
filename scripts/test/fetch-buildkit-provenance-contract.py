#!/usr/bin/env python3
"""Exercise the extractor with a self-contained BuildKit-shaped OCI fixture."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import hashlib

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("fetch_buildkit_provenance", ROOT / "scripts/runbooks/fetch-buildkit-provenance.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
SOURCE_SHA = "638483a96a0b1dbc102aba14065d98bcea76b2cd"
IMAGE_DIGEST = "a" * 64
REPOSITORY = "DavidHLP/UltiCode/console"


def fixture():
    # Synthetic OCI index -> attestation manifest -> in-toto layer chain.
    statement = {"_type": MODULE.STATEMENT_TYPE, "predicateType": MODULE.PROVENANCE,
                "subject": [], "predicate": {
                    "buildType": "https://mobyproject.org/buildkit@v1",
                    "invocation": {"configSource": {"entryPoint": "Dockerfile"},
                                   "parameters": {"root": {
                                       "configSource": {"path": "Dockerfile"},
                                       "request": {"args": {
                                           "vcs:source": "git@github.com:DavidHLP/UltiCode.git",
                                           "vcs:revision": SOURCE_SHA,
                                           "vcs:localdir:context": ".",
                                           "vcs:localdir:dockerfile": "apps/console"}}}}},
                    "buildConfig": {"digestMapping": {"sha256:" + "b" * 64: "step0"},
                                    "llbDefinition": [{"id": "step0"}]},
                    "metadata": {"https://mobyproject.org/buildkit@v1#metadata": {
                        "vcs": {"source": "git@github.com:DavidHLP/UltiCode.git", "revision": SOURCE_SHA},
                        "source": {"infos": [{"filename": "Dockerfile", "language": "Dockerfile", "data": "Rk9P"}]}}}}}
    blob = json.dumps(statement).encode()
    layer_digest = "sha256:" + hashlib.sha256(blob).hexdigest()
    image_desc = {"digest": "sha256:" + "c" * 64}
    attestation = {"subject": {"digest": image_desc["digest"]},
                  "layers": [{"mediaType": "application/vnd.in-toto+json", "digest": layer_digest,
                              "size": len(blob), "annotations": {"in-toto.io/predicate-type": MODULE.PROVENANCE}}]}
    attestation_blob = json.dumps(attestation).encode()
    attestation_desc = {"digest": "sha256:" + "d" * 64,
                        "annotations": {"vnd.docker.reference.type": "attestation-manifest"}}
    image_desc["annotations"] = {"vnd.docker.reference.type": "image-manifest"}
    root = {"manifests": [image_desc, attestation_desc]}
    return root, attestation_desc, attestation, attestation_blob, statement, image_desc, {layer_digest[7:]: blob}

def run(source_sha, fixture):
    root, att_desc, att_manifest, att_blob, statement, image_desc, raw_blobs = fixture
    with tempfile.TemporaryDirectory(prefix="buildkit-provenance-contract-") as temp:
        config_dir = Path(temp) / ".docker"
        config_dir.mkdir()
        (config_dir / "config.json").write_text(json.dumps({"auths": {
            "https://ghcr.io": {"auth": base64.b64encode(b"actor:token").decode()}
        }}))
        output = Path(temp) / "predicate.json"
        original = MODULE.request

        def response_json(obj):
            return json.dumps(obj).encode()

        def fake_request(url, *, headers=None):
            if url.startswith("https://ghcr.io/token?"):
                return b'{"token":"test"}'
            if url.endswith("/manifests/sha256:" + IMAGE_DIGEST):
                return response_json(root)
            if url.endswith("/manifests/" + att_desc["digest"]):
                return response_json(att_manifest)
            if url.endswith("/blobs/" + att_manifest["layers"][0]["digest"]):
                return raw_blobs[att_manifest["layers"][0]["digest"].removeprefix("sha256:")]
            raise AssertionError(url)

        MODULE.request = fake_request
        old = os.environ.copy()
        os.environ.update({"DOCKER_CONFIG": str(config_dir), "GITHUB_REPOSITORY": "DavidHLP/UltiCode"})
        try:
            sys.argv = ["fetch-buildkit-provenance.py", f"ghcr.io/{REPOSITORY}@sha256:{IMAGE_DIGEST}", source_sha, str(output), "./apps/console/Dockerfile"]
            try:
                MODULE.main()
                return True, json.loads(output.read_text())
            except ValueError as exc:
                return False, str(exc)
        finally:
            MODULE.request = original
            os.environ.clear()
            os.environ.update(old)


assert MODULE.PROVENANCE == "https://slsa.dev/provenance/v0.2"
fixture = fixture()
assert fixture[4]["_type"] == "https://in-toto.io/Statement/v1"
assert fixture[4]["subject"] == []
result = run(SOURCE_SHA, fixture)
assert result == (True, fixture[4]["predicate"]), result
assert run("e" * 40, fixture)[0] is False
try:
    MODULE.validate_predicate(fixture[4]["predicate"], SOURCE_SHA, "DavidHLP/UltiCode", "./apps/management/Dockerfile")
    raise AssertionError("accepted mismatched Dockerfile path")
except ValueError:
    pass
print("BuildKit v0.2 OCI provenance extraction and source contract: PASS")
