const { spawn } = require('child_process');

const mvnCmd = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';

const proc = spawn(mvnCmd, ['-pl', 'recommend-web', 'spring-boot:run'], {
  cwd: __dirname,
  stdio: 'inherit',
  env: { ...process.env, SERVER_PORT: process.env.SERVER_PORT || '9005', NACOS_PORT: process.env.NACOS_PORT || '28848' },
});

proc.on('close', (code) => process.exit(code));
