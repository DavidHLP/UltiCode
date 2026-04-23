module.exports = {
  apps: [{
    name: 'ulticode-9001',
    script: './mvnw',
    args: 'spring-boot:run -Dmaven.test.skip=true',
    cwd: '/home/davidhlp/project/UltiCode-Public-Next/backend-spring',
    env: {
      SPRING_PROFILES_ACTIVE: 'dev',
      JWT_SECRET: '5GXMfun06YtfZSSV5h3M7yNA9fmuagbY5dITQyqSVDfcgebV-DqD9upy0zsSpPbKVKdRh4kllefbUFaTDuvpSA'
    },
    interpreter: 'bash',
    instance_var: 'INSTANCE_ID'
  }]
};
