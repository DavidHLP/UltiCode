#!/usr/bin/env python3
"""Exercise the extractor against BuildKit's actual local OCI exports."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("fetch_buildkit_provenance", ROOT / "scripts/runbooks/fetch-buildkit-provenance.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
SOURCE_SHA = "638483a96a0b1dbc102aba14065d98bcea76b2cd"
IMAGE_DIGEST = "a" * 64
REPOSITORY = "DavidHLP/UltiCode/console"


def archive(path):
    with tarfile.open(path) as tar:
        blobs = {}
        raw_blobs = {}
        for member in tar.getmembers():
            if member.isfile() and member.name.startswith("blobs/sha256/"):
                data = tar.extractfile(member).read()
                digest = member.name.removeprefix("blobs/sha256/")
                raw_blobs[digest] = data
                try:
                    blobs[digest] = json.loads(data)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    pass
        root_index = json.load(tar.extractfile("index.json"))
    root = blobs[root_index["manifests"][0]["digest"].removeprefix("sha256:")]
    attestation_desc = next(d for d in root["manifests"] if d.get("annotations", {}).get("vnd.docker.reference.type") == "attestation-manifest")
    image_desc = next(d for d in root["manifests"] if d is not attestation_desc)
    attestation = blobs[attestation_desc["digest"].removeprefix("sha256:")]
    layer = next(l for l in attestation["layers"] if l["annotations"].get("in-toto.io/predicate-type") == MODULE.PROVENANCE)
    statement = blobs[layer["digest"].removeprefix("sha256:")]
    return root, attestation_desc, attestation, layer, statement, image_desc, raw_blobs


def run(source_sha, fixture):
    root, att_desc, att_manifest, layer, statement, image_desc, raw_blobs = fixture
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
            if url.endswith("/blobs/" + layer["digest"]):
                return raw_blobs[layer["digest"].removeprefix("sha256:")]
            raise AssertionError(url)

        MODULE.request = fake_request
        old = os.environ.copy()
        os.environ.update({"DOCKER_CONFIG": str(config_dir), "GITHUB_REPOSITORY": "DavidHLP/UltiCode"})
        try:
            sys.argv = ["fetch-buildkit-provenance.py", f"ghcr.io/{REPOSITORY}@sha256:{IMAGE_DIGEST}", source_sha, str(output)]
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
fixture = archive(ROOT / ".local/cicd-provenance-check/v02.tar")
assert fixture[4]["_type"] == "https://in-toto.io/Statement/v1"
assert fixture[4]["subject"] == []
result = run(SOURCE_SHA, fixture)
assert result == (True, fixture[4]["predicate"]), result
assert run("e" * 40, fixture)[0] is False
print("BuildKit v0.2 OCI provenance extraction and source contract: PASS")
