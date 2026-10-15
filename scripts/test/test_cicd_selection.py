from __future__ import annotations

import importlib.util
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


selection = load("select_checks", ROOT / "scripts/ci/select-checks.py")
results = load("check_results", ROOT / "scripts/ci/check-results.py")


class SelectionTests(unittest.TestCase):
    def test_pull_request_uses_merge_base_and_collects_rename_paths(self):
        base, head, merge_base = "a" * 40, "b" * 40, "c" * 40
        outputs = [
            subprocess.CompletedProcess([], 0, merge_base + "\n", ""),
            subprocess.CompletedProcess([], 0, b"old-name\0new-name\0", b""),
        ]
        calls = []

        def fake_run(args, **kwargs):
            calls.append(args)
            return outputs.pop(0)

        with patch.object(selection.subprocess, "run", side_effect=fake_run):
            paths = selection.changed_paths("pull_request", {
                "pull_request": {"base": {"sha": base}, "head": {"sha": head}}
            }, ".")
        self.assertEqual(paths, {"old-name", "new-name"})
        self.assertEqual(calls[0], ["git", "merge-base", base, head])
        self.assertEqual(calls[1][0:3], ["git", "diff", "--name-only"])
        self.assertIn("--no-renames", calls[1])
        self.assertEqual(calls[1][-3:], [merge_base, head, "--"])

    def test_merge_group_uses_nested_payload(self):
        base, head = "a" * 40, "b" * 40
        with patch.object(selection.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"services/pom.xml\0")) as run:
            paths = selection.changed_paths("merge_group", {
                "merge_group": {"base_sha": base, "head_sha": head}
            }, ".")
        self.assertEqual(paths, {"services/pom.xml"})
        self.assertEqual(run.call_args.args[0][-3:], [base, head, "--"])

    def test_git_options_are_not_revisions(self):
        with patch.object(selection.subprocess, "run") as run:
            self.assertIsNone(selection.changed_paths("push", {"before": "--output=/tmp/file", "after": "b" * 40}, "."))
            run.assert_not_called()

    def test_real_git_rename_keeps_deleted_source_path(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                return subprocess.run(["git", *args], cwd=root, check=True, capture_output=True, text=True).stdout.strip()

            git("init", "--quiet")
            git("config", "diff.renames", "true")
            (root / "services").mkdir()
            (root / "services/example.txt").write_text("unchanged content\n")
            git("add", ".")
            base = git("write-tree")
            (root / "docs").mkdir()
            (root / "services/example.txt").rename(root / "docs/example.txt")
            git("add", "--all")
            head = git("write-tree")
            paths = selection.changed_paths("push", {"before": base, "after": head}, directory)
            self.assertEqual(paths, {"services/example.txt", "docs/example.txt"})
            self.assertTrue(selection.select(paths)["backend"])

    def test_new_push_and_unavailable_refs_fail_safe(self):
        self.assertIsNone(selection.changed_paths("push", {"before": selection.ZERO}, "."))
        self.assertIsNone(selection.changed_paths("merge_group", {}, "."))

    def test_docs_only_skips_expensive_gates(self):
        chosen = selection.select({"docs/index.md"})
        self.assertFalse(any(chosen[key] for key in selection.GATES))
        self.assertFalse(chosen["release"])

    def test_corpus_document_changes_run_agent_integrity_tests(self):
        for path in ("docs/PRODUCT.md", "docs/ARCHITECTURE.md", "docs/DEVELOPMENT.md",
                     "docs/OPERATIONS.md", "docs/REFERENCE.md", "LICENSE"):
            with self.subTest(path=path):
                chosen = selection.select({path})
                self.assertTrue(chosen["agent"])
                self.assertFalse(chosen["release"])

    def test_source_and_contract_categories(self):
        chosen = selection.select({"services/api/auth-api/pom.xml"})
        self.assertTrue(chosen["backend"])
        self.assertTrue(chosen["contract"])
        self.assertTrue(chosen["docker"])

    def test_frontend_categories(self):
        chosen = selection.select({"apps/console/src/main.ts"})
        self.assertTrue(chosen["console"])
        self.assertFalse(chosen["management"])
        self.assertTrue(chosen["release"])

    def test_root_js_lockfile_selects_both_frontends_only(self):
        chosen = selection.select({"pnpm-lock.yaml"})
        self.assertTrue(chosen["console"])
        self.assertTrue(chosen["management"])
        self.assertFalse(chosen["backend"])
        self.assertFalse(chosen["agent"])
        self.assertTrue(chosen["release"])
        self.assertTrue(chosen["docker"])

    def test_shared_image_inputs_and_unknown_paths(self):
        self.assertTrue(selection.select({"infrastructure/nginx/includes/security-headers.conf"})["release"])
        self.assertTrue(selection.select({"scripts/dev/migrate.sh"})["backend"])
        self.assertTrue(selection.select({"scripts/deploy/preflight.sh"})["backend"])
        self.assertTrue(selection.select({"future-service/build.toml"})["full"])
        self.assertTrue(selection.select({"docker/sandbox/harness/java/Main.java"})["backend"])

    def test_agent_uses_dedicated_gate(self):
        chosen = selection.select({"services/agent/tests/test_api.py"})
        self.assertTrue(chosen["agent"])
        self.assertFalse(chosen["backend"])

    def test_tooling_and_empty_diff_fail_safe(self):
        self.assertTrue(all(selection.select({".github/actions/setup-node/action.yml"})[key] for key in selection.GATES))
        self.assertTrue(all(selection.select({"scripts/ci/requirements.txt"})[key] for key in selection.GATES))
        self.assertTrue(selection.select(set())["full"])
        self.assertTrue(selection.select(None)["release"])


class ResultTests(unittest.TestCase):
    def test_selected_skipped_is_failure(self):
        errors = results.validate(
            {"changes": "success", "governance": "success", "security": "success", "backend": "skipped",
             "frontend": "skipped", "contract": "skipped", "docker": "skipped", "agent": "skipped"},
            {"backend": True, "console": False, "management": False,
             "contract": False, "docker": False, "agent": False},
        )
        self.assertTrue(any("selected gate backend" in error for error in errors))

    def test_unselected_skipped_is_valid_but_cancelled_is_not(self):
        decisions = {"backend": False, "console": False, "management": False,
                     "contract": False, "docker": False, "agent": False}
        skipped = {"changes": "success", "governance": "success", "security": "success", "backend": "skipped",
                   "frontend": "skipped", "contract": "skipped", "docker": "skipped", "agent": "skipped"}
        self.assertEqual(results.validate(skipped, decisions), [])
        skipped["agent"] = "cancelled"
        self.assertTrue(any("unselected gate agent" in error for error in results.validate(skipped, decisions)))

    def test_github_needs_shape_and_string_outputs(self):
        needs = {name: {"result": "success"} for name in
                 ("changes", "governance", "security", "backend", "frontend", "contract", "docker", "agent")}
        selected = {"backend": "false", "console": "true", "management": "false",
                    "contract": "false", "docker": "false", "agent": "false"}
        self.assertEqual(results.validate(needs, selected), [])
        needs["frontend"]["result"] = "skipped"
        self.assertTrue(any("selected gate frontend" in error for error in results.validate(needs, selected)))

    def test_all_selection_flags_are_validated(self):
        needs = {name: "success" for name in
                 ("changes", "governance", "security", "backend", "frontend", "contract", "docker", "agent")}
        decisions = dict.fromkeys(selection.GATES, True)
        decisions["management"] = "invalid"
        self.assertTrue(any("selection for management" in error for error in results.validate(needs, decisions)))

    def test_missing_always_on_security_fails(self):
        self.assertTrue(any("security" in error for error in results.validate({"changes": "success"}, {})))


if __name__ == "__main__":
    unittest.main()
