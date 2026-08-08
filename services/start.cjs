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

// services/ is a Maven reactor with three independently bootable
// owner services. Direct startup defaults to the app service; callers may
// select another owner with SERVICE_MODULE and SERVER_PORT.
const serviceModule = process.env.SERVICE_MODULE || 'app/app-web';
const servicePath = serviceModule;
const servicePort = process.env.SERVER_PORT || '9103';
const proc = spawn(
  bash,
  [mvnw, '-pl', servicePath, '-am', 'spring-boot:run', '-Dmaven.test.skip=true', '-Dspring-boot.run.jvmArguments=-XX:-UseContainerSupport'],
  {
    cwd: __dirname,
    stdio: 'inherit',
    env: { ...process.env, PATH: newPath, SERVER_PORT: servicePort },
  },
);

proc.on('close', (code) => process.exit(code));
