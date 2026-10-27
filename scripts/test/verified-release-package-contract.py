#!/usr/bin/env python3
"""Offline packaging contracts; cryptography remains in the existing verifier."""
import hashlib
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('packager', ROOT / 'scripts/runbooks/prepare-verified-release.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PackagingTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.evidence = self.root / 'evidence'
        self.evidence.mkdir()
        self.output = self.root / 'output'
        self.notes = self.root / 'notes.md'
        self.notes.write_text('# Reviewed release notes\n')
        self.names = ['backend-auth', 'backend-admin', 'backend-app', 'backend-submission',
                      'backend-notification', 'backend-search', 'backend-judge', 'console', 'management']
        (self.source / '.github').mkdir()
        (self.source / '.github/services-matrix.json').write_text(json.dumps([{'name': n} for n in self.names]))
        (self.source / 'LICENSE').write_text('Test license\n')
        self.git('init', '--quiet')
        self.git('config', 'user.name', 'Fixture')
        self.git('config', 'user.email', 'fixture@localhost.test')
        self.git('add', '.')
        self.git('commit', '--quiet', '-m', 'fixture')
        self.sha = self.git('rev-parse', 'HEAD')
        services = []
        refs = []
        for name in sorted(self.names):
            directory = self.evidence / f'candidate-evidence-{name}'
            directory.mkdir()
            ref = module.PREFIX + name + '@sha256:' + 'a' * 64
            hashes = {}
            for suffix in ['image-ref', 'release.json', 'spdx.json', 'provenance.json', 'trivy.json', 'version', 'source-commit']:
                content = (self.sha if suffix == 'source-commit' else
                           ('1.0.5' if name.startswith('backend-') else '') if suffix == 'version' else '{}') + '\n'
                path = directory / f'{name}.{suffix}'
                path.write_text(content)
                hashes[suffix] = module.sha256(path)
            tags = [f'sha-{self.sha[:7]}'] + (['v1.0.5'] if name.startswith('backend-') else [])
            services.append({'name': name, 'image_ref': ref, 'tags': tags, 'evidence_sha256': hashes})
            refs.append(f"{name.upper().replace('-', '_')}_IMAGE_REF={ref}\n")
        (self.evidence / 'release-set.json').write_text(json.dumps({'source_commit': self.sha, 'services': services}))
        (self.evidence / 'release-manifest.txt').write_text(''.join(refs))

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.source), *args], stderr=subprocess.DEVNULL).decode().strip()

    def prepare(self):
        return module.prepare(self.source, self.evidence, self.sha, 'v1.0.5', self.notes, '123', self.output)

    def test_eight_deterministic_assets_and_idempotence(self):
        with patch.object(module, 'verify_published_set') as verify:
            self.assertIn('eight', self.prepare())
            before = {p.name: p.read_bytes() for p in self.output.iterdir()}
            self.assertEqual(len(before), 8)
            self.assertIn('identical', self.prepare())
            self.assertEqual(before, {p.name: p.read_bytes() for p in self.output.iterdir()})
            self.assertEqual(verify.call_count, 2)
        for line in (self.output / 'SHA256SUMS').read_text().splitlines():
            digest, name = line.split('  ', 1)
            self.assertEqual(module.sha256(self.output / name), digest)
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.sha)

    def test_modified_existing_output_is_never_overwritten(self):
        with patch.object(module, 'verify_published_set'):
            self.prepare()
            (self.output / 'LICENSE').write_text('keep this changed file')
            with self.assertRaisesRegex(ValueError, 'no overwrite'):
                self.prepare()
        self.assertEqual((self.output / 'LICENSE').read_text(), 'keep this changed file')

    def test_dirty_source_and_wrong_sha_fail_before_verification(self):
        (self.source / 'LICENSE').write_text('modified')
        with patch.object(module, 'verify_published_set') as verify:
            with self.assertRaises(ValueError): self.prepare()
            verify.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_tampered_evidence_fails_closed(self):
        path = self.evidence / 'candidate-evidence-console/console.spdx.json'
        path.write_text('tampered')
        with patch.object(module, 'verify_published_set') as verify:
            with self.assertRaisesRegex(ValueError, 'checksum'): self.prepare()
            verify.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_missing_service_fails_before_crypto_or_output(self):
        path = self.evidence / 'release-set.json'
        release = json.loads(path.read_text())
        release['services'].pop()
        path.write_text(json.dumps(release))
        with patch.object(module, 'verify_published_set') as verify:
            with self.assertRaisesRegex(ValueError, 'Incomplete'):
                self.prepare()
            verify.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_symlink_evidence_is_rejected(self):
        path = self.evidence / 'candidate-evidence-console/console.trivy.json'
        content = path.read_bytes()
        path.unlink()
        other = self.root / 'external'
        other.write_bytes(content)
        path.symlink_to(other)
        with self.assertRaisesRegex(ValueError, 'regular'): self.prepare()

    def test_unverified_run_or_crypto_failure_cannot_create_assets(self):
        with patch.object(module, 'verify_published_set', side_effect=ValueError('verification failed')):
            with self.assertRaises(ValueError): self.prepare()
        self.assertFalse(self.output.exists())

    def test_verifier_requires_successful_matching_main_and_read_only_command(self):
        run = {'id': 123, 'path': '.github/workflows/ci.yml', 'repository': {'full_name': module.REPOSITORY},
               'head_sha': self.sha, 'conclusion': 'success', 'event': 'push', 'head_branch': 'main'}
        proof = b'candidate set validated: 9 services; mode=dry-run'
        with patch.object(module, 'command', side_effect=[json.dumps(run).encode(), proof] + [b'sha256:' + b'a' * 64] * 16) as command:
            module.verify_published_set(self.source, self.evidence, self.sha, '123')
            self.assertEqual(command.call_count, 18)
            self.assertTrue(all('--execute' not in call.args[0] for call in command.call_args_list))
            self.assertEqual(command.call_args.args[0][1:4], ['buildx', 'imagetools', 'inspect'])
        run['head_sha'] = 'b' * 40
        with patch.object(module, 'command', return_value=json.dumps(run).encode()) as command:
            with self.assertRaises(ValueError): module.verify_published_set(self.source, self.evidence, self.sha, '123')
            self.assertEqual(command.call_count, 1)

    def test_unrelated_successful_workflow_is_rejected(self):
        run = {'id': 123, 'path': '.github/workflows/unrelated.yml', 'repository': {'full_name': module.REPOSITORY},
               'head_sha': self.sha, 'conclusion': 'success', 'event': 'push', 'head_branch': 'main'}
        with patch.object(module, 'command', return_value=json.dumps(run).encode()) as command:
            with self.assertRaises(ValueError): module.verify_published_set(self.source, self.evidence, self.sha, '123')
            self.assertEqual(command.call_count, 1)

    def test_missing_or_different_promoted_tag_fails_without_writes(self):
        run = {'id': 123, 'path': '.github/workflows/ci.yml', 'repository': {'full_name': module.REPOSITORY},
               'head_sha': self.sha, 'conclusion': 'success', 'event': 'push', 'head_branch': 'main'}
        for readback in [b'', b'sha256:' + b'b' * 64]:
            with self.subTest(readback=readback), patch.object(module, 'command', side_effect=[
                    json.dumps(run).encode(), b'candidate set validated: 9 services; mode=dry-run', readback]) as command:
                with self.assertRaisesRegex(ValueError, 'tag readback'):
                    module.verify_published_set(self.source, self.evidence, self.sha, '123')
                self.assertEqual(command.call_count, 3)
                self.assertTrue(all('--execute' not in call.args[0] for call in command.call_args_list))

    def test_incomplete_promoted_tag_inventory_is_rejected(self):
        path = self.evidence / 'release-set.json'
        release = json.loads(path.read_text())
        release['services'][0]['tags'] = []
        path.write_text(json.dumps(release))
        with patch.object(module, 'verify_published_set') as verify:
            with self.assertRaisesRegex(ValueError, 'tag inventory'): self.prepare()
            verify.assert_not_called()


if __name__ == '__main__':
    unittest.main()
