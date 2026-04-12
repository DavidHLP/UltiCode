// Load .env for PM2 process environment
try {
  require('dotenv').config({ override: true });
} catch (e) {
  // dotenv not available, env vars must be set externally
}

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
      env: { NODE_ENV: 'development', SPRING_PROFILES_ACTIVE: 'dev', NACOS_PORT: process.env.NACOS_PORT || '28848', RECOMMENDATION_ENABLED: 'true', REDIS_PASSWORD: process.env.REDIS_PASSWORD || '', NACOS_USERNAME: process.env.NACOS_USERNAME || '', NACOS_PASSWORD: process.env.NACOS_PASSWORD || '' },
    },
    // Frontend - Console (port 9002)
    {
      name: 'ulticode-9002',
      cwd: './console',
      script: 'node_modules/vite/bin/vite.js',
      args: '--port 9002',
      env: { NODE_ENV: 'development' },
    },
    // Frontend - Management (port 9003)
    {
      name: 'ulticode-9003',
      cwd: './management',
      script: 'node_modules/vite/bin/vite.js',
      args: '--port 9003',
      env: { NODE_ENV: 'development' },
    },
    // Recommendation - Provider (port 9004)
    {
      name: 'ulticode-9004',
      cwd: './recommendation',
      script: 'start-provider.cjs',
      env: { NODE_ENV: 'development', NACOS_PORT: process.env.NACOS_PORT || '28848', NACOS_USERNAME: process.env.NACOS_USERNAME || '', NACOS_PASSWORD: process.env.NACOS_PASSWORD || '' },
    },
    // Recommendation - Web (port 9005)
    {
      name: 'ulticode-9005',
      cwd: './recommendation',
      script: 'start-web.cjs',
      env: { NODE_ENV: 'development', NACOS_PORT: process.env.NACOS_PORT || '28848', NACOS_USERNAME: process.env.NACOS_USERNAME || '', NACOS_PASSWORD: process.env.NACOS_PASSWORD || '' },
    },
  ],
}
