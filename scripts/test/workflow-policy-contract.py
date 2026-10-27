#!/usr/bin/env python3
"""Exercise workflow policy with malformed and unsafe workflow fixtures."""

import copy
import importlib.util
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("workflow_policy", ROOT / "scripts/ci/check-workflows.py")
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)

WORKFLOW = {
    "name": "Fixture",
    "on": {"pull_request": None},
    "permissions": {"contents": "read"},
    "concurrency": {"group": "fixture-${{ github.ref }}", "cancel-in-progress": True},
    "jobs": {
        "test": {
            "runs-on": "ubuntu-24.04",
            "timeout-minutes": 10,
            "steps": [{
                "uses": "actions/checkout@" + "a" * 40,
                "with": {"persist-credentials": False},
            }],
        },
    },
}


class WorkflowPolicyContract(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / ".github/workflows").mkdir(parents=True)

    def check(self, data, filename="ci.yml"):
        (self.root / ".github/workflows" / filename).write_text(json.dumps(data))
        return policy.check_repository(self.root)

    def test_real_acceptance_cannot_be_removed_from_required_summary(self):
        import shutil
        shutil.copytree(ROOT / '.github', self.root / '.github', dirs_exist_ok=True)
        self.assertEqual(policy.check_repository(self.root), [])
        path = self.root / '.github/workflows/ci.yml'
        original = policy.yaml.load(path.read_text(), Loader=policy.WorkflowLoader)
        for mutation in ['needs', 'condition', 'output']:
            data = copy.deepcopy(original)
            if mutation == 'needs': data['jobs']['ci-ok']['needs'].remove('acceptance')
            elif mutation == 'condition': data['jobs']['acceptance']['if'] = '${{ false }}'
            else: del data['jobs']['changes']['outputs']['acceptance']
            path.write_text(json.dumps(data))
            self.assertTrue(any('acceptance' in error for error in policy.check_repository(self.root)))

    def test_safe_workflow_and_yaml_on_key(self):
        self.assertEqual(self.check(WORKFLOW), [])
        parsed = policy.yaml.load("on:\n  push:\npermissions: {}\nenabled: false\n", Loader=policy.WorkflowLoader)
        self.assertIn("on", parsed)
        self.assertIs(parsed["enabled"], False)

    def test_empty_and_duplicate_documents_fail(self):
        self.assertTrue(policy.check_repository(self.root))
        target = self.root / ".github/workflows/ci.yml"
        for document in ("name: one\nname: two\n", "[]", "on: [push]\njobs: invalid\n", "{broken"):
            with self.subTest(document=document):
                target.write_text(document)
                self.assertTrue(policy.check_repository(self.root))

    def test_permission_escalation_and_unsafe_events_fail(self):
        for permissions in (None, "write-all", {"contents": "write"}, {"id-token": "write"}):
            with self.subTest(permissions=permissions):
                data = copy.deepcopy(WORKFLOW)
                data["permissions"] = permissions
                self.assertTrue(self.check(data))
        for event in ("pull_request_target", "workflow_run"):
            data = copy.deepcopy(WORKFLOW)
            data["on"] = {event: None}
            self.assertTrue(self.check(data))
        data = copy.deepcopy(WORKFLOW)
        data["jobs"]["test"]["permissions"] = {"packages": "write"}
        self.assertTrue(self.check(data))

    def test_release_write_permissions_are_job_scoped(self):
        data = copy.deepcopy(WORKFLOW)
        data["on"] = {"workflow_call": None}
        data["jobs"]["build-and-push"] = data["jobs"].pop("test")
        data["jobs"]["build-and-push"]["permissions"] = {"packages": "write", "id-token": "write"}
        self.assertEqual(self.check(data, "docker-publish.yml"), [])
        data["jobs"]["build-and-push"]["permissions"]["contents"] = "write"
        self.assertTrue(self.check(data, "docker-publish.yml"))

    def test_actions_require_immutable_references(self):
        for reference in ("actions/checkout@v4", "org/action@main", "docker://alpine:latest", "./missing", "./../../outside"):
            with self.subTest(reference=reference):
                data = copy.deepcopy(WORKFLOW)
                data["jobs"]["test"]["steps"][0]["uses"] = reference
                self.assertTrue(self.check(data))
        data = copy.deepcopy(WORKFLOW)
        data["jobs"]["test"]["steps"][0]["uses"] = "docker://alpine@sha256:" + "a" * 64
        self.assertEqual(self.check(data), [])

    def test_checkout_tokens_and_shell_injection_fail(self):
        data = copy.deepcopy(WORKFLOW)
        del data["jobs"]["test"]["steps"][0]["with"]
        self.assertTrue(self.check(data))
        for expression in ("inputs.path", "inputs['path']", "secrets.HOST", "secrets['HOST']",
                           "github.event.pull_request.title", "github['event'].pull_request.title",
                           "github.head_ref", "github.ref_name", "github[ 'head_ref' ]"):
            with self.subTest(expression=expression):
                data = copy.deepcopy(WORKFLOW)
                data["jobs"]["test"]["steps"].append({"run": "echo '${{ " + expression + " }}'"})
                self.assertTrue(self.check(data))
                data["jobs"]["test"]["steps"][-1] = {
                    "run": 'printf "%s\\n" "$VALUE"', "env": {"VALUE": "${{ " + expression + " }}"},
                }
                self.assertEqual(self.check(data), [])

    def test_composite_actions_are_not_a_policy_bypass(self):
        action = self.root / ".github/actions/fixture/action.yml"
        action.parent.mkdir(parents=True)
        action.write_text(json.dumps({"runs": {"using": "composite", "steps": [{"uses": "org/action@main"}]}}))
        data = copy.deepcopy(WORKFLOW)
        data["jobs"]["test"]["steps"].append({"uses": "./.github/actions/fixture"})
        self.assertTrue(self.check(data))
        action.write_text(json.dumps({"runs": {"using": "composite", "steps": [{"run": "true", "shell": "bash"}]}}))
        self.assertEqual(self.check(data), [])

    def test_unbounded_jobs_masked_failures_and_inherited_secrets_fail(self):
        for field, value in (("timeout-minutes", None), ("timeout-minutes", 0), ("timeout-minutes", True),
                             ("timeout-minutes", 121), ("continue-on-error", True), ("secrets", "inherit")):
            with self.subTest(field=field, value=value):
                data = copy.deepcopy(WORKFLOW)
                data["jobs"]["test"][field] = value
                self.assertTrue(self.check(data))
        data = copy.deepcopy(WORKFLOW)
        data.pop("concurrency")
        self.assertTrue(self.check(data))
        data["on"] = {"workflow_call": None}
        self.assertEqual(self.check(data), [])
        data["jobs"]["test"]["steps"].append({"run": "false", "continue-on-error": True})
        self.assertTrue(self.check(data))


class WorkflowExecutionContract(unittest.TestCase):
    def test_backend_feature_matrix_preserves_every_flag(self):
        workflow = policy.yaml.load((ROOT / ".github/workflows/_backend.yml").read_text(), Loader=policy.WorkflowLoader)
        step = next(step for step in workflow["jobs"]["backend-test-features"]["steps"]
                    if step.get("name") == "Inject feature flag env vars from matrix")
        flags = {"USE_NEW_CONTEST_SYSTEM", "REALTIME_RANKING_ENABLED", "FIRST_SOLVE_NOTIFICATIONS_ENABLED",
                 "ANTICHEAT_ENABLED", "CONTEST_ANALYTICS_ENABLED"}
        for profile, enabled in (("on", "true"), ("off", "false")):
            with self.subTest(profile=profile), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "env"
                subprocess.run(["bash", "-euo", "pipefail", "-c", step["run"]], check=True,
                               env={**os.environ, "FEATURES": profile, "GITHUB_ENV": str(output)})
                self.assertEqual(set(output.read_text().splitlines()), {f"{flag}={enabled}" for flag in flags})


if __name__ == "__main__":
    unittest.main()
