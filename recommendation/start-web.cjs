const { spawn } = require('child_process');

const mvnCmd = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';

const proc = spawn(mvnCmd, ['-pl', 'recommend-web', 'spring-boot:run'], {
  cwd: __dirname,
  stdio: 'inherit',
});

proc.on('close', (code) => process.exit(code));
