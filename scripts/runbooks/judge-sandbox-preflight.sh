#!/usr/bin/env bash
set -euo pipefail

# Validate the production Judge runtime without sourcing .env. Compose is the
# dotenv parser and Python evaluates the rendered service config in memory, so
# spaces, quotes and shell metacharacters in deployment values are inert.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-.env}"

exec python3 - "$ROOT_DIR" "$ENV_FILE" <<'PY'
import json
import os
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1])
env_file = Path(sys.argv[2])
compose = [
    "docker", "compose", "--env-file", str(env_file),
    "-f", str(root / "docker-compose.yml"),
    "-f", str(root / "docker-compose.prod.yml"),
    "config", "--format", "json",
]



def fail(message: str) -> None:
    raise SystemExit(f"judge-sandbox-preflight: FAIL: {message}")
image_ref_names = {
    "BACKEND_AUTH_IMAGE_REF", "BACKEND_ADMIN_IMAGE_REF", "BACKEND_APP_IMAGE_REF",
    "BACKEND_SUBMISSION_IMAGE_REF", "BACKEND_SEARCH_IMAGE_REF",
    "BACKEND_NOTIFICATION_IMAGE_REF", "BACKEND_JUDGE_IMAGE_REF",
    "CONSOLE_IMAGE_REF", "MANAGEMENT_IMAGE_REF",
}

compose_env = os.environ.copy()
for assignment in compose_env.get("IMAGE_REF_LIST", "").splitlines():
    if "=" not in assignment:
        fail("IMAGE_REF_LIST contains a malformed assignment")
    name, value = assignment.split("=", 1)
    if name not in image_ref_names:
        fail("IMAGE_REF_LIST contains an unexpected variable")
    compose_env[name] = value

def run(*args: str) -> str:
    result = subprocess.run(args, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        fail(f"remote Docker command failed: {args[-1]}")
    return result.stdout

rendered = subprocess.run(compose, env=compose_env, capture_output=True, text=True, check=False)
if rendered.returncode != 0:
    fail("production Compose config could not be rendered")
try:
    service = json.loads(rendered.stdout)["services"]["backend-judge"]
except (KeyError, json.JSONDecodeError):
    fail("rendered Compose config has no backend-judge service")

environment = service.get("environment", {})
if isinstance(environment, list):
    environment = dict(item.split("=", 1) for item in environment if "=" in item)

def required(name: str) -> str:
    value = environment.get(name, "")
    if not value:
        fail(f"rendered backend-judge environment lacks {name}")
    return value

docker_host = required("DOCKER_HOST")
if not docker_host.startswith("tcp://"):
    fail("DOCKER_HOST must address a remote Docker endpoint")
if required("DOCKER_TLS_VERIFY") != "1":
    fail("DOCKER_TLS_VERIFY must be 1")
if required("DOCKER_CERT_PATH") != "/run/secrets/judge-docker":
    fail("DOCKER_CERT_PATH must use the mounted client bundle")
sandbox_dir = Path(required("SANDBOX_HOST_DIR"))
if not sandbox_dir.is_absolute():
    fail("SANDBOX_HOST_DIR must be absolute")
if not required("JAVA_TOOL_OPTIONS").endswith(
        f"-Djava.io.tmpdir={sandbox_dir}/workspace"):
    fail("JAVA_TOOL_OPTIONS must end with the shared workspace tempdir")

volumes = service.get("volumes", [])
def volume_for(target: str):
    for volume in volumes:
        if isinstance(volume, dict) and volume.get("target") == target:
            return volume
    fail(f"missing volume target {target}")

cert_volume = volume_for("/run/secrets/judge-docker")
if cert_volume.get("read_only") is not True:
    fail("Docker client bundle must be read-only")
cert_dir = Path(cert_volume.get("source", ""))
if not cert_dir.is_absolute():
    fail("JUDGE_DOCKER_CERT_DIR must resolve to an absolute host path")
for name in ("ca.pem", "cert.pem", "key.pem"):
    path = cert_dir / name
    if not path.is_file() or path.stat().st_size == 0:
        fail(f"remote TLS material is missing or empty: {name}")

workspace_volume = volume_for(str(sandbox_dir))
if workspace_volume.get("read_only") is True:
    fail("shared sandbox workspace must remain writable")
workspace = sandbox_dir / "workspace"
if not workspace.is_dir() or not (workspace.stat().st_mode & 0o200):
    fail("shared sandbox workspace is missing or not writable")
seccomp = sandbox_dir / "seccomp-profile.json"
if not seccomp.is_file() or seccomp.stat().st_size == 0:
    fail("shared seccomp profile is missing or empty")

client = [
    "docker", "--host", docker_host, "--tlsverify",
    "--tlscacert", str(cert_dir / "ca.pem"),
    "--tlscert", str(cert_dir / "cert.pem"),
    "--tlskey", str(cert_dir / "key.pem"),
]
security_options = run(*client, "info", "--format", "{{json .SecurityOptions}}")
if "rootless" not in security_options:
    fail("remote Docker daemon is not proven rootless")
run(*client, "image", "inspect", required("SANDBOX_IMAGE"))
print("remote sandbox prerequisites ready")
PY
