#!/usr/bin/env python3
"""Fixed-schema launch evidence, never raw PM2 records or log messages."""
import json
import os
import re
import stat
import sys
import time
from pathlib import Path

SERVICES = tuple('ulticode-' + name for name in (
    'auth', 'admin', 'app', 'core', 'notification', 'submission', 'judge',
    'search', '9002', '9003', '9004'))
STATUSES = ('online', 'stopped', 'stopping', 'launching', 'errored', 'one-launch-status', 'waiting restart')
COLLECTION = ('ok', 'missing', 'failed', 'invalid')
LIMIT = 65536
FILE = 'launch-readiness.json'


def number(value, maximum=2147483647):
    return value if type(value) is int and 0 <= value <= maximum else None


def empty(collection='missing'):
    return {'schema_version': 1, 'collection': collection, 'services': {}}


def sanitize(value):
    if not isinstance(value, dict) or type(value.get('schema_version')) is not int or value.get('schema_version') != 1:
        return empty('invalid')
    result = empty(value.get('collection') if value.get('collection') in COLLECTION else 'invalid')
    items = value.get('services')
    if not isinstance(items, dict):
        return empty('invalid')
    for name in SERVICES:
        item = items.get(name)
        if not isinstance(item, dict):
            continue
        result['services'][name] = {
            'last_readiness': item.get('last_readiness') if item.get('last_readiness') in ('ready', 'not_ready', 'not_checked') else 'unknown',
            'pm2_collection': item.get('pm2_collection') if item.get('pm2_collection') in COLLECTION else 'invalid',
            'status': item.get('status') if item.get('status') in STATUSES else 'unknown',
            'restart_count': number(item.get('restart_count')),
            'exit_code': number(item.get('exit_code'), 255),
            'uptime_seconds': number(item.get('uptime_seconds')),
        }
    return result


def valid_project(project):
    return isinstance(project, str) and re.fullmatch(r'ulticode-acceptance-[0-9]{1,20}-[0-9]{1,20}', project)


def directory_fd(root):
    """Walk relative directories without following symlinks (including races)."""
    fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in ('.local', 'acceptance'):
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = child
        return fd
    except OSError:
        os.close(fd)
        raise


def read_snapshot(root, project):
    try:
        directory = directory_fd(root)
        try:
            fd = os.open(FILE, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        finally:
            os.close(directory)
        with os.fdopen(fd, 'rb') as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_size > LIMIT:
                return empty('invalid')
            raw = stream.read(LIMIT + 1)
        if len(raw) > LIMIT:
            return empty('invalid')
        value = json.loads(raw)
        if not isinstance(value, dict) or value.get('run_project') != project or not valid_project(project):
            return empty('invalid')
        return sanitize(value)
    except FileNotFoundError:
        return empty('missing')
    except (OSError, ValueError, TypeError, RecursionError):
        return empty('invalid')


def pm2_snapshot(stream, collection):
    # Reuse the existing readiness query; never run another PM2 command (which
    # could autostart a daemon). Bound input before parsing and discard raw data.
    if collection != 'ok':
        return 'failed', []
    try:
        raw = stream.read(1048577)
        if len(raw) > 1048576:
            return 'invalid', []
        records = json.loads(raw)
        return ('ok', records) if isinstance(records, list) else ('invalid', [])
    except (OSError, ValueError, RecursionError):
        return 'invalid', []


def capture(selected, observations, collection, records, now_ms):
    result = empty('ok')
    last = {}
    for observation in observations:
        name, _, outcome = observation.partition('=')
        if name in SERVICES and outcome in ('ready', 'not_ready'):
            last[name] = outcome
    for name in SERVICES:
        if name not in selected.split(','):
            continue
        matches = [item for item in records if isinstance(item, dict) and item.get('name') == name]
        env = matches[0].get('pm2_env') if len(matches) == 1 else None
        valid = isinstance(env, dict)
        env = env if valid else {}
        started = number(env.get('pm_uptime'), 9007199254740991)
        result['services'][name] = {
            'last_readiness': last.get(name, 'not_checked'),
            'pm2_collection': collection if collection != 'ok' else ('ok' if valid else 'invalid' if matches else 'missing'),
            'status': env.get('status'),
            'restart_count': env.get('restart_time'),
            'exit_code': env.get('exit_code'),
            'uptime_seconds': (now_ms - started) // 1000 if env.get('status') == 'online' and started is not None and 0 < started <= now_ms else None,
        }
    return sanitize(result)


def submission_first_start(snapshot):
    """A fresh, isolated Submission launch must not rely on PM2 recovery."""
    snapshot = sanitize(snapshot)
    item = snapshot['services'].get('ulticode-submission', {})
    return (snapshot['collection'] == 'ok'
            and item.get('pm2_collection') == 'ok'
            and item.get('last_readiness') == 'ready'
            and item.get('status') == 'online'
            and item.get('restart_count') == 0)


def main():
    if sys.argv[1:2] == ['--require-submission-first-start']:
        if os.environ.get('ACCEPTANCE_DISPOSABLE') != 'true' or len(sys.argv) != 3:
            return 1
        snapshot = read_snapshot(Path(sys.argv[2]), os.environ.get('COMPOSE_PROJECT_NAME', ''))
        passed = submission_first_start(snapshot)
        print('Submission first start without restart: ' + ('PASS' if passed else 'FAIL'))
        return 0 if passed else 1
    if os.environ.get('ACCEPTANCE_DISPOSABLE') != 'true':
        return 0
    try:
        root, selected, collection, *observations = sys.argv[1:]
        collection, records = pm2_snapshot(sys.stdin.buffer, collection)
        result = capture(selected, observations, collection, records, int(time.time() * 1000))
        project = os.environ.get('COMPOSE_PROJECT_NAME', '')
        result['run_project'] = project if valid_project(project) else 'unknown'
        directory = directory_fd(Path(root))
        temporary = f'.launch-readiness-{os.getpid()}.tmp'
        try:
            fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                         0o600, dir_fd=directory)
            try:
                with os.fdopen(fd, 'w') as stream:
                    json.dump(result, stream)
                os.replace(temporary, FILE, src_dir_fd=directory, dst_dir_fd=directory)
            finally:
                try:
                    os.unlink(temporary, dir_fd=directory)
                except FileNotFoundError:
                    pass
        finally:
            os.close(directory)
        return 0
    except (OSError, ValueError, TypeError):
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
