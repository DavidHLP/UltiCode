#!/usr/bin/env python3
"""Prepare an unpublished source bundle; never create tags, images or a Release."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source_commit', help='Full immutable Git commit SHA')
    parser.add_argument('output', type=Path, help='New output directory; existing paths are refused')
    args = parser.parse_args()
    sha = args.source_commit
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        parser.error('source_commit must be a full lowercase SHA')
    if git('rev-parse', '--verify', f'{sha}^{{commit}}').decode().strip() != sha:
        parser.error('source_commit must identify the exact commit object')
    matrix = json.loads(git('show', f'{sha}:.github/services-matrix.json'))
    if len(matrix) != 9 or len({entry['name'] for entry in matrix}) != 9:
        parser.error('source must declare exactly nine unique deployable services')
    ns = '{http://maven.apache.org/POM/4.0.0}'
    properties = ET.fromstring(git('show', f'{sha}:services/pom.xml')).find(f'{ns}properties')
    services = []
    for entry in matrix:
        prop = entry.get('maven_version_property')
        version = properties.findtext(f'{ns}{prop}') if prop else None
        if prop and not version:
            parser.error(f'missing version for {entry["name"]}')
        services.append({'name': entry['name'], 'planned_version': version, 'image_ref': None})
    notes = git('show', f'{sha}:CHANGELOG.md')
    license_text = git('show', f'{sha}:LICENSE')
    # No replacement or cleanup: an interrupted directory is preserved for review.
    args.output.mkdir(parents=True, exist_ok=False)
    archive = args.output / f'UltiCode-{sha}.tar.gz'
    subprocess.run(['git', '-C', str(ROOT), 'archive', '--format=tar.gz',
                    f'--prefix=UltiCode-{sha}/', '-o', str(archive.resolve()), sha], check=True)
    (args.output / 'release-notes.md').write_bytes(notes)
    (args.output / 'LICENSE').write_bytes(license_text)
    plan = {'status': 'source-only-unpublished', 'source_commit': sha,
            'image_manifest_available': False, 'services': services,
            'remaining_gate': 'Approved publisher must produce and verify all nine immutable image references; this plan is not deployment input.'}
    (args.output / 'release-plan.json').write_text(json.dumps(plan, indent=2, sort_keys=True) + '\n')
    paths = sorted(args.output.iterdir())
    checksums = []
    for path in paths:
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        checksums.append(f'{digest}  {path.name}\n')
    (args.output / 'SHA256SUMS').write_text(''.join(checksums))
    print(f'Unpublished source bundle prepared: {args.output}')


if __name__ == '__main__':
    main()
