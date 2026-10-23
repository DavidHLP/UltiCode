import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

test('formats only changed app files and passes filenames without shell evaluation', () => {
  const root = mkdtempSync(join(tmpdir(), 'codex-format-'));
  try {
    const hooks = join(root, '.codex/hooks');
    const bin = join(root, 'bin');
    const log = join(root, 'calls.jsonl');
    for (const dir of [hooks, bin, join(root, 'apps/console'), join(root, 'apps/management')]) mkdirSync(dir, { recursive: true });
    cpSync(join(dirname(fileURLToPath(import.meta.url)), 'format-changed-files.mjs'), join(hooks, 'format-changed-files.mjs'));
    writeFileSync(join(bin, 'pnpm'), '#!/usr/bin/env node\nrequire("node:fs").appendFileSync(process.env.CALL_LOG, JSON.stringify({cwd:process.cwd(),args:process.argv.slice(2)})+"\\n");\nprocess.exit(Number(process.env.FORMAT_EXIT ?? 0));\n');
    chmodSync(join(bin, 'pnpm'), 0o755);
    const names = ['apps/console/a $(touch injected).vue', 'apps/management/-options.vue', 'outside.vue'];
    for (const name of names) writeFileSync(join(root, name), '');
    symlinkSync(join(root, 'outside.vue'), join(root, 'apps/console/escape.vue'));
    const run = (input, env = {}) => spawnSync(process.execPath, [join(hooks, 'format-changed-files.mjs')], {
      input: JSON.stringify({ cwd: root, ...input }),
      encoding: 'utf8',
      env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, CALL_LOG: log, ...env },
    });
    assert.equal(run({ tool_name: 'Write', tool_input: { file_path: names[0] } }).status, 0);
    assert.equal(run({ tool_name: 'apply_patch', tool_input: `*** Update File: ${names[1]}\n*** Add File: missing.vue\n*** Update File: outside.vue\n*** Update File: apps/console/escape.vue\n` }).status, 0);
    const calls = readFileSync(log, 'utf8').trim().split('\n').map(JSON.parse);
    assert.deepEqual(calls, [
      { cwd: join(root, 'apps/console'), args: ['exec', 'prettier', '--write', '--ignore-unknown', '--', 'a $(touch injected).vue'] },
      { cwd: join(root, 'apps/management'), args: ['exec', 'prettier', '--write', '--ignore-unknown', '--', '-options.vue'] },
    ]);
    assert.notEqual(run({ tool_name: 'Edit', tool_input: { file_path: names[0] } }, { FORMAT_EXIT: '1' }).status, 0);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
