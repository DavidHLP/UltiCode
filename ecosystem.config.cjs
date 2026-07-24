const path = require('path')

const ROOT = __dirname

module.exports = {
  apps: [
    {
      name: 'ulticode-9001',
      cwd: path.join(ROOT, 'backend-spring'),
      script: 'mvn',
      args: 'spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: path.join(ROOT, '.env'),
      env: { SERVER_PORT: '9001' },
      out_file: path.join(ROOT, 'logs', 'backend-spring.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-spring.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '1G',
    },
    {
      name: 'ulticode-9002',
      cwd: path.join(ROOT, 'console'),
      script: 'pnpm',
      args: 'dev',
      interpreter: 'none',
      env: { NODE_ENV: 'development' },
      out_file: path.join(ROOT, 'logs', 'console.out.log'),
      error_file: path.join(ROOT, 'logs', 'console.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '512M',
    },
    {
      name: 'ulticode-9003',
      cwd: path.join(ROOT, 'management'),
      script: 'pnpm',
      args: 'dev',
      interpreter: 'none',
      env: { NODE_ENV: 'development' },
      out_file: path.join(ROOT, 'logs', 'management.out.log'),
      error_file: path.join(ROOT, 'logs', 'management.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '512M',
    },
  ],
}
