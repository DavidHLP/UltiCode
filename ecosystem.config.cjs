module.exports = {
  apps: [
    {
      name: 'ulticode-9001',
      script: './mvnw',
      args: 'spring-boot:run -Dmaven.test.skip=true',
      cwd: './backend-spring',
      interpreter: 'bash',
      instance_var: 'INSTANCE_ID',
      env: {
        SPRING_PROFILES_ACTIVE: 'dev',
        JWT_SECRET: '5GXMfun06YtfZSSV5h3M7yNA9fmuagbY5dITQyqSVDfcgebV-DqD9upy0zsSpPbKVKdRh4kllefbUFaTDuvpSA'
      }
    },
    {
      name: 'ulticode-9002',
      script: './node_modules/vite/bin/vite.js',
      args: '--port 9002',
      cwd: './console',
      interpreter: 'none',
      env: {
        NODE_ENV: 'development'
      }
    },
    {
      name: 'ulticode-9003',
      script: './node_modules/vite/bin/vite.js',
      args: '--port 9003',
      cwd: './management',
      interpreter: 'none',
      env: {
        NODE_ENV: 'development'
      }
    }
  ]
}