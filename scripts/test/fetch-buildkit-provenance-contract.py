#!/usr/bin/env python3
"""Exercise the extractor with a self-contained BuildKit-shaped OCI fixture."""
import base64
import copy
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


def fixture(subject=..., modern=False):
    # Synthetic OCI index -> attestation manifest -> in-toto layer chain.
    statement = {"_type": MODULE.STATEMENT_TYPE, "predicateType": MODULE.PROVENANCE,
                "subject": [] if subject is ... else subject, "predicate": {
                    "buildType": "https://mobyproject.org/buildkit@v1",
                    "invocation": {"configSource": {"entryPoint": "Dockerfile"},
                                   "parameters": {"frontend": "dockerfile.v0",
                                                 "args": {"SERVICE_MODULE": "console"},
                                                 "locals": [{"name": "context"}]}},
                    "buildConfig": {"digestMapping": {"sha256:" + "b" * 64: "step0"},
                                    "llbDefinition": [{"id": "step0"}]},
                    "metadata": {"https://mobyproject.org/buildkit@v1#metadata": {
                        "vcs": {"source": "git@github.com:DavidHLP/UltiCode.git", "revision": SOURCE_SHA,
                                "localdir:dockerfile": "apps/console"},
                        "source": {"infos": [{"filename": "Dockerfile", "language": "Dockerfile", "data": "Rk9P"}]}}}}}
    if modern:
        parameters = statement["predicate"]["invocation"]["parameters"]
        vcs = statement["predicate"]["metadata"]["https://mobyproject.org/buildkit@v1#metadata"]["vcs"]
        parameters["compatibilityVersion"] = 30
        parameters["root"] = {"configSource": {"path": "Dockerfile"}, "request": {
            "args": {**parameters["args"], **{f"vcs:{key}": value for key, value in vcs.items()}}}}
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
fixture_data = fixture()
assert fixture_data[4]["_type"] == "https://in-toto.io/Statement/v1"
assert fixture_data[4]["subject"] == []
result = run(SOURCE_SHA, fixture_data)
assert result == (True, fixture_data[4]["predicate"]), result
named_subject = [{"name": "ghcr.io/DavidHLP/UltiCode/console", "digest": {"sha256": "c" * 64}}]
named_fixture = fixture(subject=named_subject)
assert run(SOURCE_SHA, named_fixture) == (True, named_fixture[4]["predicate"])
for subject in ([{"name": "ghcr.io/DavidHLP/UltiCode/console", "digest": {"sha256": "e" * 64}}],
                [{"name": "ghcr.io/DavidHLP/UltiCode/console", "digest": "c" * 64}],
                [{"digest": {"sha256": "c" * 64}}], [{"name": "", "digest": {"sha256": "c" * 64}}],
                [{"name": "image", "digest": {"sha256": "c" * 64}}, {"name": "other", "digest": {"sha256": "c" * 64}}],
                None, "malformed"):

    rejected = run(SOURCE_SHA, fixture(subject=subject))
    assert rejected[0] is False and "subject does not match" in rejected[1], rejected
assert run("e" * 40, fixture_data)[0] is False
try:
    MODULE.validate_predicate(fixture_data[4]["predicate"], SOURCE_SHA, "DavidHLP/UltiCode", "./apps/management/Dockerfile")
    raise AssertionError("accepted mismatched Dockerfile path")
except ValueError:
    pass
for mutate in (
    lambda p: p["invocation"]["parameters"].pop("frontend"),
    lambda p: p["invocation"]["parameters"].pop("args"),
    lambda p: p["invocation"]["parameters"].pop("locals"),
    lambda p: p["invocation"].pop("configSource"),
    lambda p: p["metadata"]["https://mobyproject.org/buildkit@v1#metadata"]["vcs"].pop("revision"),
    lambda p: p["metadata"]["https://mobyproject.org/buildkit@v1#metadata"]["vcs"].pop("localdir:dockerfile"),
    lambda p: p["buildConfig"]["digestMapping"].clear(),
    lambda p: p["buildConfig"]["digestMapping"].update({"bad-digest": "step0"}),
    lambda p: p["invocation"]["parameters"].update(root={"request": {"args": {}}}),
):
    invalid = copy.deepcopy(fixture_data[4]["predicate"])
    mutate(invalid)
    try:
        MODULE.validate_predicate(invalid, SOURCE_SHA, "DavidHLP/UltiCode", "./apps/console/Dockerfile")
        raise AssertionError("accepted incomplete or malformed SLSA v0.2 provenance")
    except ValueError:
        pass
legacy_shape = copy.deepcopy(fixture_data[4]["predicate"])
legacy_shape["invocation"]["parameters"] = {"root": {"request": {"args": {}}}}
try:
    MODULE.validate_predicate(legacy_shape, SOURCE_SHA, "DavidHLP/UltiCode", "./apps/console/Dockerfile")
    raise AssertionError("accepted non-v0.2 nested invocation parameters")
except ValueError:
    pass
modern_fixture = fixture(modern=True)
assert run(SOURCE_SHA, modern_fixture) == (True, modern_fixture[4]["predicate"])
promoter_spec = importlib.util.spec_from_file_location("promoter", ROOT / "scripts/runbooks/promote-release.py")
promoter = importlib.util.module_from_spec(promoter_spec)
promoter_spec.loader.exec_module(promoter)
promoter.validate_buildkit_predicate(modern_fixture[4]["predicate"], SOURCE_SHA, "./apps/console/Dockerfile")
for mutate in (
    lambda p: p["invocation"]["parameters"].update(compatibilityVersion=31),
    lambda p: p["invocation"]["parameters"]["root"]["configSource"].update(path="elsewhere/Dockerfile"),
    lambda p: p["invocation"]["parameters"]["root"]["request"]["args"].update({"vcs:revision": "e" * 40}),
    lambda p: p["invocation"]["parameters"]["root"]["request"]["args"].update({"vcs:source": "https://github.com/other/repo"}),
    lambda p: p["invocation"]["parameters"]["root"]["request"]["args"].update({"vcs:localdir:dockerfile": "elsewhere"}),
    lambda p: p["invocation"]["parameters"]["root"]["request"]["args"].update(SERVICE_MODULE="other"),
    lambda p: p["invocation"]["parameters"]["root"].pop("request"),
):
    invalid = copy.deepcopy(modern_fixture[4]["predicate"])
    mutate(invalid)
    for validator in (
        lambda: MODULE.validate_predicate(invalid, SOURCE_SHA, "DavidHLP/UltiCode", "./apps/console/Dockerfile"),
        lambda: promoter.validate_buildkit_predicate(invalid, SOURCE_SHA, "./apps/console/Dockerfile"),
    ):
        try:
            validator()
        except (ValueError, promoter.Reject):
            pass
        else:
            raise AssertionError("accepted inconsistent compatibility v30 recipe/VCS metadata")
print("BuildKit v0.2 OCI provenance extraction and source contract (legacy and compatibility v30): PASS")
