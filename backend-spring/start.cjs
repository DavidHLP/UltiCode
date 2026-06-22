const { spawn } = require('child_process');
const { existsSync } = require('fs');
const { resolve } = require('path');

const mvnw = resolve(__dirname, 'mvnw');

// Resolve a shell that can execute the POSIX-shell mvnw script. On
// Windows the preview tooling's spawn sandbox has no `bash` on PATH, so
// `spawn('bash', ...)` would ENOENT. The Git-for-Windows bash is at a
// well-known absolute path; fall back to `bash` (PATH) then `sh` for
// POSIX environments.
const bashCandidates = [
  'C:/Program Files/Git/usr/bin/bash.exe',
  'C:/Program Files/Git/bin/bash.exe',
  'bash',
  'sh',
].filter((p) => p === 'bash' || p === 'sh' || existsSync(p));
const bash = bashCandidates[0] || 'bash';

// The preview tool's spawn sandbox strips the inherited PATH, so
// mvnw's helper commands (uname, dirname, etc.) are not visible to
// the spawned bash. Prepend the Git-for-Windows coreutils directory
// (/mingw64/bin and /usr/bin) to PATH so the script can run. This is
// only required because the preview tool runs in a constrained
// environment; the user's interactive bash already has this on PATH.
const pathAdditions = [
  'C:/Program Files/Git/usr/bin',
  'C:/Program Files/Git/mingw64/bin',
  'C:/Program Files/Git/mingw32/bin',
].filter(existsSync);
const newPath = [...pathAdditions, process.env.PATH || ''].join(';');

const proc = spawn(
  bash,
  [mvnw, 'spring-boot:run', '-Dmaven.test.skip=true', '-Dspring-boot.run.jvmArguments=-XX:-UseContainerSupport'],
  {
    cwd: __dirname,
    stdio: 'inherit',
    env: { ...process.env, PATH: newPath },
  },
);

proc.on('close', (code) => process.exit(code));
