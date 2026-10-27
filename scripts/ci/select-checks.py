#!/usr/bin/env python3
"""Select CI gates from a GitHub event's exact base/head revisions."""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import subprocess
import sys

ZERO = "0" * 40
GATES = ("backend", "console", "management", "landing", "contract", "docker", "agent", "acceptance")


def valid_sha(value) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None and value != ZERO


def changed_paths(event_name: str, event: dict, repo: str) -> set[str] | None:
    """Return changed paths; None means the event cannot be diffed safely."""
    if not isinstance(event, dict):
        return None
    if event_name == "pull_request":
        pr = event.get("pull_request")
        if not isinstance(pr, dict):
            return None
        base_data, head_data = pr.get("base"), pr.get("head")
        if not isinstance(base_data, dict) or not isinstance(head_data, dict):
            return None
        base, head = base_data.get("sha"), head_data.get("sha")
        if not valid_sha(base) or not valid_sha(head):
            return None
        merge_base = subprocess.run(
            ["git", "merge-base", base, head], cwd=repo, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False,
        )
        if merge_base.returncode:
            return None
        left, right = merge_base.stdout.strip(), head
    elif event_name == "push":
        base, right = event.get("before"), event.get("after")
        if not valid_sha(base) or not valid_sha(right):
            return None
        left = base
    elif event_name == "merge_group":
        group = event.get("merge_group")
        if not isinstance(group, dict):
            return None
        left, right = group.get("base_sha"), group.get("head_sha")
        if not valid_sha(left) or not valid_sha(right):
            return None
    elif event_name == "workflow_dispatch":
        return None
    else:
        return None

    result = subprocess.run(
        ["git", "diff", "--name-only", "--no-renames", "-z", left, right, "--"],
        cwd=repo, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False,
    )
    if result.returncode:
        return None
    return {p.decode("utf-8", "surrogateescape") for p in result.stdout.split(b"\0") if p}


def select(paths: set[str] | None) -> dict[str, bool]:
    # Unknown/new branches and unsupported events deliberately run every gate.
    if paths is None:
        return {**dict.fromkeys(GATES, True), "release": True, "full": True}

    def matches(path: str, *patterns: str) -> bool:
        return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)

    def any_match(*patterns: str) -> bool:
        return any(matches(path, *patterns) for path in paths)

    known = (
        ".github/**", "scripts/**", "services/**", "init-db/**", "docker/**", "infrastructure/**",
        "apps/console/**", "apps/management/**", "apps/landing/**", "packages/**", "shared/**",
        "package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", ".dockerignore",
        "pyproject.toml", "uv.lock", "mise.toml", ".mise.toml",
        "docs/**", "assets/**", "README*", "AGENTS.md", "CONTEXT.md", "LICENSE", ".gitleaks.toml",
        "CHANGELOG.md", "SECURITY.md", "CONTRIBUTING.md", "CODE_OF_CONDUCT.md",
    )
    if not paths or any(not matches(path, *known) for path in paths):
        return {**dict.fromkeys(GATES, True), "release": True, "full": True}
    broad = any_match(
        ".github/workflows/**", ".github/actions/**", "scripts/**", "mise.toml", ".mise.toml",
    )
    frontend_shared = broad or any_match("package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml")
    backend = broad or any(
        matches(path, "services/**", "init-db/**", "docker/redis/**", "docker/sandbox/**",
                "docker/docker-compose*.yml", "infrastructure/observability/**", "infrastructure/nginx/**")
        for path in paths if not path.startswith("services/agent/")
    )
    console = frontend_shared or any_match("apps/console/**", "packages/**", "shared/**")
    management = frontend_shared or any_match("apps/management/**", "packages/**", "shared/**")
    landing = frontend_shared or any_match("apps/landing/**", "packages/**", "shared/**")
    contract = broad or any_match("services/api/**", "services/pom.xml", "services/docs/CONTRACT_COMPAT_GATE.md")
    docker = frontend_shared or any(
        matches(path, "services/**", "apps/console/Dockerfile", "apps/management/Dockerfile", "apps/landing/**",
                "docker/**", "infrastructure/**", ".dockerignore", "apps/console/nginx.conf",
                "apps/management/nginx.conf", "services/docs/OBSERVABILITY_RUNBOOK.md",
                "scripts/runbooks/image-reference-policy.sh", "scripts/runbooks/deployment-integrity.sh",
                ".github/services-matrix.json")
        for path in paths if not path.startswith("services/agent/")
    )
    agent = broad or any_match(
        "services/agent/**", "pyproject.toml", "uv.lock", "LICENSE",
        "docs/PRODUCT.md", "docs/ARCHITECTURE.md", "docs/DEVELOPMENT.md",
        "docs/OPERATIONS.md", "docs/REFERENCE.md",
    )
    release = any(
        matches(path, "services/**", "apps/console/**", "apps/management/**", "apps/landing/**", "packages/**",
                "shared/**", "package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml",
                ".dockerignore", "infrastructure/nginx/**", ".github/services-matrix.json",
                ".github/workflows/docker-publish.yml", "scripts/ci/release-*", "scripts/ci/candidate-*",
                "scripts/runbooks/promote-release.*", "scripts/runbooks/fetch-buildkit-provenance.py")
        for path in paths if not path.startswith("services/agent/")
    )
    selected = dict(backend=backend, console=console, management=management, landing=landing,
                    contract=contract, docker=docker, agent=agent,
                    acceptance=release or backend or console or management or broad,
                    release=release, full=False)
    return selected


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event-file", default=os.environ.get("GITHUB_EVENT_PATH"))
    parser.add_argument("--event-name", default=os.environ.get("GITHUB_EVENT_NAME", ""))
    parser.add_argument("--repo", default=".")
    args = parser.parse_args()
    try:
        if not args.event_file:
            raise ValueError("event file is required")
        with open(args.event_file, encoding="utf-8") as source:
            event = json.load(source)
        decisions = select(changed_paths(args.event_name, event, args.repo))
        output = os.environ.get("GITHUB_OUTPUT")
        if output:
            with open(output, "a", encoding="utf-8") as target:
                for key, value in decisions.items():
                    target.write(f"{key}={'true' if value else 'false'}\n")
        print(json.dumps(decisions, sort_keys=True))
        return 0
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"::error::CI change selection failed safely: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
