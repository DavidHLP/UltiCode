// Load .env for PM2 process environment
require('dotenv').config();

module.exports = {
  apps: [
    // ============================================
    // Application Services
    // ============================================
    // Backend - Spring Boot (port 9001)
    {
      name: 'ulticode-9001',
      cwd: './backend-spring',
      script: 'start.cjs',
      out_file: '/tmp/ulticode-9001-out.log',
      error_file: '/tmp/ulticode-9001-error.log',
      time: true,
      env: { NODE_ENV: 'development', SPRING_PROFILES_ACTIVE: 'dev', NACOS_PORT: process.env.NACOS_PORT || '28848', REDIS_PASSWORD: process.env.REDIS_PASSWORD || '', JWT_SECRET: process.env.JWT_SECRET || '', NACOS_USERNAME: process.env.NACOS_USERNAME || '', NACOS_PASSWORD: process.env.NACOS_PASSWORD || '' },
    },
    // Frontend - Console (port 9002)
    {
      name: 'ulticode-9002',
      cwd: './console',
      script: 'node_modules/vite/bin/vite.js',
      args: '--port 9002',
      out_file: '/tmp/ulticode-9002-out.log',
      error_file: '/tmp/ulticode-9002-error.log',
      time: true,
      env: { NODE_ENV: 'development' },
    },
    // Frontend - Management (port 9003)
    {
      name: 'ulticode-9003',
      cwd: './management',
      script: 'node_modules/vite/bin/vite.js',
      args: '--port 9003',
      out_file: '/tmp/ulticode-9003-out.log',
      error_file: '/tmp/ulticode-9003-error.log',
      time: true,
      env: { NODE_ENV: 'development' },
    },
  ],
}
