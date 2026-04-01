module.exports = {
  apps: [
    // Frontend - Console
    {
      name: 'ulticode-9002',
      cwd: './console',
      script: 'node_modules/vite/bin/vite.js',
      args: '--port 9002',
      env: { NODE_ENV: 'development' },
    },
    // Frontend - Management
    {
      name: 'ulticode-9003',
      cwd: './management',
      script: 'node_modules/vite/bin/vite.js',
      args: '--port 9003',
      env: { NODE_ENV: 'development' },
    },
    // Backend - Spring Boot
    {
      name: 'ulticode-9001',
      cwd: './backend-spring',
      script: 'start.cjs',
      env: { NODE_ENV: 'development' },
    },
    // Recommendation - Provider
    {
      name: 'ulticode-9004',
      cwd: './recommendation',
      script: 'start-provider.cjs',
      env: { NODE_ENV: 'development' },
    },
    // Recommendation - Web
    {
      name: 'ulticode-9005',
      cwd: './recommendation',
      script: 'start-web.cjs',
      env: { NODE_ENV: 'development' },
    },
  ],
}
