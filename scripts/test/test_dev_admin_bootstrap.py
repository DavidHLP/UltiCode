#!/usr/bin/env python3
"""Run the launcher's actual frozen bootstrap policy without starting services."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
UP = ROOT / "scripts/dev/up.sh"
POLICY = re.compile(r"^dev_admin_bootstrap_enabled\(\) \{\n.*?^readonly -f dev_admin_bootstrap_enabled require_dev_admin_password\n", re.M | re.S)


class BootstrapPasswordTests(unittest.TestCase):
    def run_policy(self, password=None, **overrides):
        source = UP.read_text()
        match = POLICY.search(source)
        self.assertIsNotNone(match, "Actual bootstrap policy must remain directly testable")
        environment = dict(os.environ)
        environment.pop("DEV_SEED_ADMIN_PASSWORD", None)
        environment.update(SKIP_BOOTSTRAP="false", PREPARE_SUBMISSION_OWNER="false",
                           DEV_SEED_USERS_ENABLED="true", PM2_APPS="ulticode-auth,ulticode-app")
        environment.update(overrides)
        if password is not None:
            environment["DEV_SEED_ADMIN_PASSWORD"] = password
        with tempfile.TemporaryDirectory() as directory:
            policy = Path(directory) / "policy.sh"
            policy.write_text(match.group())
            result = subprocess.run(["bash", "-c", '''set -euo pipefail
source "$1"
before="${DEV_SEED_ADMIN_PASSWORD-}"
before_set="${DEV_SEED_ADMIN_PASSWORD+x}"
require_dev_admin_password
[[ "${DEV_SEED_ADMIN_PASSWORD-}" == "$before" ]]
[[ "${DEV_SEED_ADMIN_PASSWORD+x}" == "$before_set" ]]
''', "bootstrap-contract", str(policy)], env=environment, text=True, capture_output=True)
        self.assertEqual(result.stdout, "")
        if password and password.strip():
            self.assertNotIn(password, result.stderr)
        return result

    def test_enabled_bootstrap_rejects_missing_password(self):
        result = self.run_policy()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("DEV_SEED_ADMIN_PASSWORD is required", result.stderr)

    def test_enabled_bootstrap_rejects_empty_password(self):
        self.assertNotEqual(self.run_policy("").returncode, 0)

    def test_enabled_bootstrap_rejects_whitespace_only_password(self):
        for value in (" ", "\t", "\n", "\r\n", " \t\r\n "):
            with self.subTest(kind=repr(value)):
                self.assertNotEqual(self.run_policy(value).returncode, 0)

    def test_java_unicode_whitespace_only_password_is_rejected(self):
        codepoints = (
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x1C, 0x1D, 0x1E, 0x1F, 0x20,
            0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005,
            0x2006, 0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x205F, 0x3000,
        )
        values = [chr(codepoint) for codepoint in codepoints]
        for locale in ("C", "C.UTF-8"):
            for value in values + ["".join(values)]:
                with self.subTest(locale=locale, codepoints=[hex(ord(char)) for char in value]):
                    self.assertNotEqual(self.run_policy(value, LC_ALL=locale).returncode, 0)

    def test_java_nonbreaking_spaces_are_preserved_as_nonblank(self):
        for locale in ("C", "C.UTF-8"):
            for value in ("\u00a0", "\u2007", "\u202f", "测试用非ASCII值", " \u1680有效值\u3000 "):
                with self.subTest(locale=locale, codepoints=[hex(ord(char)) for char in value]):
                    result = self.run_policy(value, LC_ALL=locale)
                    self.assertEqual(result.returncode, 0)
                    self.assertEqual(result.stderr, "")

    def test_explicit_password_is_preserved_without_printing(self):
        for value in ("test-only-explicit-value=$with punctuation", "  test-only-value \t"):
            with self.subTest():
                self.assertEqual(self.run_policy(value).returncode, 0)

    def test_skip_bootstrap_allows_missing_password(self):
        self.assertEqual(self.run_policy(SKIP_BOOTSTRAP="true").returncode, 0)

    def test_prepare_owner_without_bootstrap_allows_missing_password(self):
        self.assertEqual(self.run_policy(PREPARE_SUBMISSION_OWNER="true").returncode, 0)

    def test_disabled_dev_users_allow_empty_password(self):
        self.assertEqual(self.run_policy("", DEV_SEED_USERS_ENABLED="false").returncode, 0)

    def test_no_auth_selection_allows_missing_password(self):
        self.assertEqual(self.run_policy(PM2_APPS="ulticode-app,ulticode-judge").returncode, 0)

    def test_similarly_named_service_is_not_auth(self):
        self.assertEqual(self.run_policy(PM2_APPS="ulticode-auth-other").returncode, 0)

    def test_policy_is_frozen_before_env_and_checked_before_side_effects(self):
        source = UP.read_text()
        self.assertNotRegex(source, r"DEV_SEED_ADMIN_PASSWORD:=")
        frozen = source.index("readonly -f dev_admin_bootstrap_enabled require_dev_admin_password")
        self.assertLess(frozen, source.index("\nload_env_file\n"))
        check = source.index("require_dev_admin_password || exit $?")
        for marker in ("\nmaterialize_redis_acl ", "\nprovision_submission_migration_principal()", "\nif dev_admin_bootstrap_enabled; then", "\npreflight_service_ports\n"):
            self.assertLess(check, source.index(marker), marker)
        self.assertEqual(source.count("if dev_admin_bootstrap_enabled; then"), 1)
        self.assertEqual(source.count("require_dev_admin_password || exit $?"), 1)


if __name__ == "__main__":
    unittest.main()
