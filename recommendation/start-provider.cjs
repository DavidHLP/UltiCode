const { spawn } = require('child_process');

const mvnCmd = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';

const proc = spawn(mvnCmd, ['-pl', 'recommend-provider', 'spring-boot:run'], {
  cwd: __dirname,
  env: { ...process.env, SERVER_PORT: process.env.SERVER_PORT || '9004', NACOS_PORT: process.env.NACOS_PORT || '28848', NACOS_USERNAME: process.env.NACOS_USERNAME || '', NACOS_PASSWORD: process.env.NACOS_PASSWORD || '' },
});

proc.on('close', (code) => process.exit(code));
