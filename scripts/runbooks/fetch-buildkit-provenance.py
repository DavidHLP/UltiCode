#!/usr/bin/env python3
"""Fetch and validate the BuildKit SLSA v0.2 statement attached to an OCI image."""
from __future__ import annotations

import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

PROVENANCE = "https://slsa.dev/provenance/v0.2"
STATEMENT_TYPE = "https://in-toto.io/Statement/v1"
ACCEPT = ", ".join((
    "application/vnd.oci.image.index.v1+json",
    "application/vnd.docker.distribution.manifest.list.v2+json",
    "application/vnd.oci.image.manifest.v1+json",
    "application/vnd.docker.distribution.manifest.v2+json",
))


def request(url: str, *, headers: dict[str, str] | None = None) -> bytes:
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=20) as response:
            return response.read()
    except (OSError, urllib.error.URLError) as exc:
        raise ValueError(f"registry request failed: {url}: {exc}") from exc


def validate_predicate(predicate: object, source_sha: str, repository: str, dockerfile: str) -> dict:
    expected_sources = {
        f"git@github.com:{repository}.git",
        f"https://github.com/{repository}",
        f"https://github.com/{repository}.git",
    }
    try:
        if not isinstance(predicate, dict):
            raise ValueError
        invocation = predicate["invocation"]
        parameters = invocation["parameters"]
        args = parameters["args"]
        locals_ = parameters["locals"]
        expected_dockerfile_dir = str(Path(dockerfile.removeprefix("./")).parent)
        metadata = predicate["metadata"]["https://mobyproject.org/buildkit@v1#metadata"]
        vcs = metadata["vcs"]
        build_config = predicate["buildConfig"]
        source_infos = metadata["source"]["infos"]
        llb = build_config["llbDefinition"]
        if (predicate.get("buildType") != "https://mobyproject.org/buildkit@v1"
                or not isinstance(invocation.get("configSource"), dict)
                or invocation["configSource"].get("entryPoint") != "Dockerfile"
                or not isinstance(parameters.get("frontend"), str) or not parameters["frontend"]
                or not isinstance(args, dict)
                or "root" in parameters
                or any(key.startswith("vcs:") for key in args)
                or not isinstance(locals_, list) or not locals_
                or not all(isinstance(local, dict) and isinstance(local.get("name"), str) and local["name"] for local in locals_)
                or vcs.get("localdir:dockerfile") != expected_dockerfile_dir
                or vcs.get("source") not in expected_sources
                or vcs.get("revision") != source_sha
                or not isinstance(build_config.get("digestMapping"), dict)
                or not build_config["digestMapping"]
                or not all(isinstance(digest, str) and re.fullmatch(r"sha256:[0-9a-f]{64}", digest)
                           and isinstance(step, str) and step for digest, step in build_config["digestMapping"].items())
                or not isinstance(llb, list) or not llb
                or not isinstance(source_infos, list)
                or not any(isinstance(info, dict) and info.get("filename") == "Dockerfile"
                           and info.get("language") == "Dockerfile" and isinstance(info.get("data"), str)
                           and info["data"] for info in source_infos)):
            raise ValueError
    except (KeyError, AttributeError, TypeError):
        raise ValueError("BuildKit provenance source, revision, recipe, or build type mismatch") from None
    except ValueError:
        raise ValueError("BuildKit provenance source, revision, recipe, or build type mismatch") from None
    return predicate


def main() -> int:
    if len(sys.argv) != 5:
        raise ValueError("usage: fetch-buildkit-provenance.py IMAGE@sha256:DIGEST SOURCE_SHA OUTPUT DOCKERFILE")
    image_ref, source_sha, output, dockerfile = sys.argv[1:]
    match = re.fullmatch(r"ghcr\.io/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*)@sha256:([0-9a-f]{64})", image_ref)
    if not match or not re.fullmatch(r"[0-9a-f]{40}", source_sha) or not dockerfile or dockerfile.startswith("/") or ".." in Path(dockerfile).parts:
        raise ValueError("image ref or source commit is invalid")
    repository, image_digest = match.groups()
    config = json.loads(Path(os.environ.get("DOCKER_CONFIG", str(Path.home() / ".docker")) + "/config.json").read_text())
    auth = config.get("auths", {}).get("https://ghcr.io", {}).get("auth") or config.get("auths", {}).get("ghcr.io", {}).get("auth")
    if not isinstance(auth, str):
        raise ValueError("Docker config has no GHCR credentials")
    username, password = base64.b64decode(auth).decode().split(":", 1)
    query = urllib.parse.urlencode({"service": "ghcr.io", "scope": f"repository:{repository}:pull"})
    token_raw = request(f"https://ghcr.io/token?{query}", headers={
        "Authorization": "Basic " + base64.b64encode(f"{username}:{password}".encode()).decode(),
    })
    token_data = json.loads(token_raw)
    token = token_data.get("token") or token_data.get("access_token")
    if not isinstance(token, str) or not token:
        raise ValueError("GHCR returned no pull token")
    base = f"https://ghcr.io/v2/{repository}"
    headers = {"Authorization": f"Bearer {token}", "Accept": ACCEPT}
    index = json.loads(request(f"{base}/manifests/sha256:{image_digest}", headers=headers))
    manifests = index.get("manifests") if isinstance(index, dict) else None
    if not isinstance(manifests, list):
        raise ValueError("candidate digest is not an image index with BuildKit attestations")
    image_manifests = [d for d in manifests if isinstance(d, dict)
                       and d.get("annotations", {}).get("vnd.docker.reference.type") != "attestation-manifest"]
    attestation_descriptors = [d for d in manifests if isinstance(d, dict)
                               and d.get("annotations", {}).get("vnd.docker.reference.type") == "attestation-manifest"]
    if not image_manifests or not attestation_descriptors:
        raise ValueError("candidate index has no image and BuildKit attestation descriptors")
    found = []
    for descriptor in attestation_descriptors:
        manifest = json.loads(request(f"{base}/manifests/{descriptor['digest']}", headers=headers))
        attached_subject = manifest.get("subject") if isinstance(manifest, dict) else None
        if not isinstance(attached_subject, dict) or attached_subject.get("digest") not in {
                item.get("digest") for item in image_manifests}:
            continue
        for layer in manifest.get("layers", []):
            if (layer.get("mediaType") != "application/vnd.in-toto+json"
                    or layer.get("annotations", {}).get("in-toto.io/predicate-type") != PROVENANCE):
                continue
            blob = request(f"{base}/blobs/{layer['digest']}", headers=headers)
            if len(blob) != layer.get("size"):
                raise ValueError("BuildKit provenance blob size mismatch")
            statement = json.loads(blob)
            if (not isinstance(statement, dict) or statement.get("_type") != STATEMENT_TYPE
                    or statement.get("predicateType") != PROVENANCE):
                continue
            subjects = statement.get("subject")
            if subjects != []:
                if (not isinstance(subjects, list) or len(subjects) != 1
                        or not isinstance(subjects[0], dict)
                        or not isinstance(subjects[0].get("name"), str) or not subjects[0]["name"]
                        or not isinstance(subjects[0].get("digest"), dict)
                        or subjects[0]["digest"].get("sha256") != attached_subject["digest"].removeprefix("sha256:")):
                    raise ValueError("BuildKit provenance subject does not match attached image manifest")
            found.append(validate_predicate(statement.get("predicate"), source_sha, os.environ["GITHUB_REPOSITORY"], dockerfile))
    if len(found) != 1:
        raise ValueError("expected exactly one BuildKit provenance statement for candidate digest")
    Path(output).write_text(json.dumps(found[0], sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        print(f"fetch-buildkit-provenance: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
