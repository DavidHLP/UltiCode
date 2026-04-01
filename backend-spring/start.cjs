const { spawn } = require('child_process');

const proc = spawn('./mvnw', ['spring-boot:run'], {
  cwd: __dirname,
  stdio: 'inherit',
  env: { ...process.env, JAVA_HOME: '/home/davidhlp/.vfox/cache/java/v-17.0.1+12/java-17.0.1+12' },
});

proc.on('close', (code) => process.exit(code));
