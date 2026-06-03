// 共用日志配置：显式锚定到 /tmp/，避免 PM2 默认切到 ~/.pm2/logs/ 造成路径分裂
const LOG_OUT = (name) => `/tmp/ulticode-${name}-out.log`
const LOG_ERR = (name) => `/tmp/ulticode-${name}-error.log`
const logConfig = (name) => ({
  out_file: LOG_OUT(name),
  error_file: LOG_ERR(name),
  merge_logs: true,
  log_date_format: 'YYYY-MM-DD HH:mm:ss',
  // 单文件 20MB，自动保留 5 个历史文件
  max_size: '20M',
  // 写满后保留 N 个轮转文件
  max_files: 5
})

module.exports = {
  apps: [
    {
      name: 'ulticode-9001',
      script: './mvnw',
      args: 'spring-boot:run -Dmaven.test.skip=true',
      cwd: './backend-spring',
      interpreter: 'bash',
      instance_var: 'INSTANCE_ID',
      ...logConfig('9001'),
      // env 完全覆盖父进程环境，必须显式声明应用依赖的所有外部变量
      env: {
        // 应用配置
        SPRING_PROFILES_ACTIVE: 'dev',
        // MySQL
        DB_HOST: 'localhost',
        DB_PORT: '23306',
        DB_USER: 'ulticode',
        DB_PASSWORD: 'CHANGE_ME_strong_password',
        DB_NAME: 'ulticode',
        // Redis
        REDIS_HOST: 'localhost',
        REDIS_PORT: '26379',
        REDIS_PASSWORD: 'CHANGE_ME_redis_password',
        REDIS_DB: '0',
        // JWT
        JWT_SECRET: '5GXMfun06YtfZSSV5h3M7yNA9fmuagbY5dITQyqSVDfcgebV-DqD9upy0zsSpPbKVKdRh4kllefbUFaTDuvpSA',
        // CORS
        CORS_ALLOWED_ORIGINS: 'http://localhost:9002,http://localhost:9003',
        // Nacos
        NACOS_SERVER_ADDR: 'localhost:28848',
        NACOS_HOST: 'localhost',
        NACOS_PORT: '28848',
        NACOS_NAMESPACE: 'public',
        NACOS_GROUP: 'DEFAULT_GROUP',
        NACOS_USERNAME: 'nacos',
        NACOS_PASSWORD: 'nacos',
        // Judge Container
        JUDGE_CONTAINER_ENABLED: 'true',
        JUDGE_CONTAINER_IMAGE: 'ulticode-judge:latest',
        JUDGE_CONTAINER_POOL_SIZE: '5',
        JUDGE_CONTAINER_MAX_CONTAINERS: '10',
        JUDGE_DEFAULT_TIME_LIMIT: '2000',
        JUDGE_DEFAULT_MEMORY_LIMIT: '256',
        DOCKER_SOCKET_PATH: '/var/run/docker.sock',
        // OAuth (留空，由前端/外部流程提供)
        GITHUB_CLIENT_ID: 'your_github_client_id',
        GITHUB_CLIENT_SECRET: 'your_github_client_secret',
        GITHUB_REDIRECT_URI: 'http://localhost:9001/auth/github/callback',
        GOOGLE_CLIENT_ID: 'your_google_client_id',
        GOOGLE_CLIENT_SECRET: 'your_google_client_secret',
        GOOGLE_REDIRECT_URI: 'http://localhost:9001/auth/google/callback',
        // Stripe (开发占位)
        STRIPE_SECRET_KEY: 'sk_test_your_stripe_secret_key',
        STRIPE_WEBHOOK_SECRET: 'whsec_your_webhook_secret',
        // SMTP
        SMTP_HOST: 'smtp.example.com',
        SMTP_PORT: '587',
        SMTP_USER: 'your_smtp_user',
        SMTP_PASSWORD: 'your_smtp_password',
        EMAIL_ENABLED: 'false'
      }
    },
    {
      name: 'ulticode-9002',
      script: './node_modules/vite/bin/vite.js',
      args: '--port 9002',
      cwd: './console',
      interpreter: 'none',
      ...logConfig('9002'),
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
      ...logConfig('9003'),
      env: {
        NODE_ENV: 'development'
      }
    },
    {
      // 数据库迁移服务: 一次性跑 Flyway migrate, 成功即退出 (exit 0)
      // 启动顺序: 先 `pm2 start ulticode-init-db` 等 stopped, 再 `pm2 start ulticode-9001`
      name: 'ulticode-init-db',
      script: 'mvn',
      args: 'flyway:migrate -Dflyway.configFiles=flyway.conf --no-transfer-progress -B',
      cwd: './init-db',
      interpreter: 'none',
      autorestart: false,
      ...logConfig('init-db'),
      env: {
        // flyway.conf 已包含完整 JDBC URL/用户/密码, 此处仅兜底
        DB_HOST: 'localhost',
        DB_PORT: '23306',
        DB_USER: 'ulticode',
        DB_PASSWORD: 'CHANGE_ME_strong_password',
        DB_NAME: 'ulticode'
      }
    }
  ]
}