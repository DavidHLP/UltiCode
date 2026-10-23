import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const input = JSON.parse(readFileSync(0, 'utf8'));
const toolInput = input.tool_input;
const files = [];

if (input.tool_name === 'apply_patch') {
  const patch = typeof toolInput === 'string' ? toolInput : toolInput?.command ?? '';
  for (const match of patch.matchAll(/^\*\*\* (?:Add File|Update File|Move to): (.+)$/gm)) {
    files.push(match[1]);
  }
} else if (['Write', 'Edit'].includes(input.tool_name) && toolInput?.file_path) {
  files.push(toolInput.file_path);
}

for (const filename of new Set(files)) {
  const path = resolve(input.cwd ?? root, filename);
  if (!existsSync(path) || !statSync(path).isFile()) continue;
  const file = realpathSync(path);
  for (const app of ['console', 'management']) {
    const appRoot = resolve(root, 'apps', app);
    const local = relative(appRoot, file);
    if (isAbsolute(local) || local === '..' || local.startsWith(`..${sep}`)) continue;
    execFileSync('pnpm', ['exec', 'prettier', '--write', '--ignore-unknown', '--', local], {
      cwd: appRoot,
      stdio: 'inherit',
    });
    break;
  }
}
