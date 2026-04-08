const { spawn } = require('child_process');
const { resolve } = require('path');

// Try common Maven wrapper paths
const mvnw = resolve(__dirname, 'mvnw');

const proc = spawn(mvnw, ['spring-boot:run', `-Dspring-boot.run.jvmArguments=-XX:-UseContainerSupport`], {
  cwd: __dirname,
  stdio: 'inherit',
});

proc.on('close', (code) => process.exit(code));
