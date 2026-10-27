#!/usr/bin/env python3
"""Check repository workflow trust boundaries; actionlint owns Actions syntax."""

import argparse
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is required: python3 -m pip install -r scripts/ci/requirements.txt")


class WorkflowLoader(yaml.SafeLoader):
    pass


# YAML 1.1 treats the Actions `on` key as a boolean.
WorkflowLoader.yaml_implicit_resolvers = {
    key: [(tag, pattern) for tag, pattern in resolvers if tag != "tag:yaml.org,2002:bool"]
    for key, resolvers in yaml.SafeLoader.yaml_implicit_resolvers.items()
}
WorkflowLoader.add_implicit_resolver(
    "tag:yaml.org,2002:bool", re.compile(r"^(?:true|false)$", re.IGNORECASE), list("tTfF")
)


def unique_mapping(loader, node):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node)
        if key in mapping:
            raise ValueError(f"duplicate YAML key {key!r} at line {key_node.start_mark.line + 1}")
        mapping[key] = loader.construct_object(value_node)
    return mapping


WorkflowLoader.add_constructor("tag:yaml.org,2002:map", unique_mapping)

WRITE_PERMISSIONS = {
    ("ci.yml", "publish"): {"packages", "id-token"},
    ("docker-publish.yml", "build-and-push"): {"packages", "id-token"},
    ("docker-publish.yml", "promote"): {"packages"},
}
UNTRUSTED_EXPRESSION = re.compile(
    r"\$\{\{[^}]*\b(?:inputs\b|secrets\b|github\s*(?:"
    r"\.\s*(?:event|head_ref|ref_name)\b|\[\s*['\"](?:event|head_ref|ref_name)['\"]\s*\]))[^}]*\}\}",
    re.DOTALL,
)


def check_repository(root):
    root = root.resolve()
    errors = []
    workflows = sorted((root / ".github/workflows").glob("*.y*ml"))
    actions = sorted((root / ".github/actions").rglob("action.y*ml"))
    if not workflows:
        return ["no GitHub workflows found"]

    for path in workflows + actions:
        location = str(path.relative_to(root))

        def reject(message):
            errors.append(f"{location}: {message}")

        def check_permissions(permissions, allowed, context):
            if not isinstance(permissions, dict):
                reject(f"{context} must declare a permission map, not inherit repository defaults")
                return
            for name, access in permissions.items():
                if access not in ("read", "write", "none"):
                    reject(f"{context}: invalid permission {name}={access!r}")
                elif access == "write" and name not in allowed:
                    reject(f"{context}: {name}: write is not authorized for this job")

        def check_use(use, context):
            if not isinstance(use, str):
                reject(f"{context}: uses must be a literal reference")
            elif use.startswith("./"):
                target = (root / use).resolve()
                if not target.is_relative_to(root):
                    reject(f"{context}: local action escapes repository")
                elif not target.is_file() and not any((target / name).is_file() for name in ("action.yml", "action.yaml")):
                    reject(f"{context}: local action or workflow does not exist: {use}")
            elif use.startswith("docker://"):
                if not re.fullmatch(r"docker://[^\s@]+@sha256:[0-9a-f]{64}", use):
                    reject(f"{context}: container action must use a sha256 digest")
            elif not re.fullmatch(r"[^\s@]+@[0-9a-f]{40}", use):
                reject(f"{context}: external action must use a full commit SHA: {use}")

        def check_steps(steps, context):
            for index, step in enumerate(steps):
                label = f"{context} step {index + 1}"
                if "uses" in step:
                    check_use(step["uses"], label)
                    if step["uses"].startswith("actions/checkout@"):
                        if step.get("with", {}).get("persist-credentials") not in (False, "false"):
                            reject(f"{label}: checkout must disable persist-credentials")
                if UNTRUSTED_EXPRESSION.search(str(step.get("run", ""))):
                    reject(f"{label}: pass inputs, secrets and event text through env, not shell source")
                if step.get("continue-on-error") not in (None, False, "false"):
                    reject(f"{label}: continue-on-error may hide a required failure")

        try:
            data = yaml.load(path.read_text(), Loader=WorkflowLoader)
            if not isinstance(data, dict):
                raise ValueError("expected a YAML mapping")
            if path in actions:
                runs = data.get("runs", {})
                if runs.get("using") == "composite":
                    check_steps(runs.get("steps", []), "composite")
                continue

            events = data.get("on", {})
            events = [events] if isinstance(events, str) else events
            if not isinstance(events, (dict, list)) or not events:
                reject("workflow must declare triggers")
                continue
            for event in ("pull_request_target", "workflow_run"):
                if event in events:
                    reject(f"privileged cross-workflow trigger {event} is not allowed")
            if set(events) != {"workflow_call"} and "concurrency" not in data:
                reject("entry workflow must declare concurrency")
            check_permissions(data.get("permissions"), set(), "workflow permissions")
            if path.name == "ci.yml" and (root / ".github/services-matrix.json").exists():
                jobs = data.get("jobs", {})
                gate = jobs.get("acceptance", {})
                if (gate.get("uses") != "./.github/workflows/_acceptance.yml"
                        or gate.get("if") != "${{ !cancelled() && needs.changes.outputs.acceptance == 'true' }}"):
                    reject("real acceptance must use the selected required reusable gate")
                if "acceptance" not in jobs.get("ci-ok", {}).get("needs", []):
                    reject("ci-ok must depend on real acceptance")
                if jobs.get("changes", {}).get("outputs", {}).get("acceptance") != "${{ steps.select.outputs.acceptance }}":
                    reject("changes must expose the acceptance selection")
                steps = jobs.get("ci-ok", {}).get("steps", [])
                if not any('needs.changes.outputs.acceptance' in str(step.get('env', {}).get('SELECTED', '')) for step in steps):
                    reject("ci-ok must validate the acceptance selection")
            for name, job in data.get("jobs", {}).items():
                if "permissions" in job:
                    check_permissions(job["permissions"], WRITE_PERMISSIONS.get((path.name, name), set()), f"job {name} permissions")
                if job.get("secrets") == "inherit":
                    reject(f"job {name}: pass only named secrets, not secrets: inherit")
                if job.get("continue-on-error") not in (None, False, "false"):
                    reject(f"job {name}: continue-on-error may hide a required failure")
                if "runs-on" in job:
                    timeout = job.get("timeout-minutes")
                    if type(timeout) is not int or not 1 <= timeout <= 120:
                        reject(f"job {name}: runner timeout must be an integer between 1 and 120 minutes")
                if "uses" in job:
                    check_use(job["uses"], f"job {name}")
                check_steps(job.get("steps", []), f"job {name}")
        except (yaml.YAMLError, ValueError, TypeError, AttributeError, OSError) as error:
            reject(f"invalid workflow: {error}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    errors = check_repository(args.root)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Workflow trust-boundary policy: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
