const path = require('path')

const ROOT = __dirname
const BACKEND_CWD = path.join(ROOT, 'services')
const BACKEND_ENV_FILE = path.join(ROOT, '.env')

// Map NACOS_* → DUBBO_REGISTRY_* ONLY when the launching shell actually
// exported them (the supported path: scripts/dev/up.sh sources .env first).
// When they are absent, DUBBO_REGISTRY_* must stay UNSET so Spring's
// `${DUBBO_REGISTRY_PASSWORD:${NACOS_PASSWORD:}}` resolves the next fallback
// (NACOS_PASSWORD, always injected by env_file). The previous
// `process.env.NACOS_PASSWORD || ''` form set an EMPTY password when the
// launcher lacked NACOS_*, and Spring treats a present-but-empty
// DUBBO_REGISTRY_PASSWORD as definitive — so the fallback never fired, the
// Dubbo Nacos client logged in with no password ("Required request parameter
// 'password' ... is not present" in the Nacos log) and the owner service
// crash-looped on "Failed to create nacos config service client".
function dubboRegistryEnv() {
  const env = {}
  if (process.env.NACOS_USERNAME) env.DUBBO_REGISTRY_USERNAME = process.env.NACOS_USERNAME
  if (process.env.NACOS_PASSWORD) env.DUBBO_REGISTRY_PASSWORD = process.env.NACOS_PASSWORD
  return env
}

module.exports = {
  apps: [
    {
      name: 'ulticode-auth',
      cwd: BACKEND_CWD,
      script: 'mvn',
      args: '-f auth/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: BACKEND_ENV_FILE,
      env: {
        SERVER_PORT: '9101',
        ...dubboRegistryEnv(),
      },
      out_file: path.join(ROOT, 'logs', 'backend-auth.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-auth.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '1G',
    },
    {
      name: 'ulticode-admin',
      cwd: BACKEND_CWD,
      script: 'mvn',
      args: '-f admin/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: BACKEND_ENV_FILE,
      env: { SERVER_PORT: '9102' },
      out_file: path.join(ROOT, 'logs', 'backend-admin.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-admin.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '1G',
    },
    {
      name: 'ulticode-app',
      cwd: BACKEND_CWD,
      script: 'mvn',
      args: '-f app/app-web/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: BACKEND_ENV_FILE,
      env: {
        SERVER_PORT: '9103',
        APP_FEATURES_USE_JUDGE_OUTBOX: 'true',
        APP_FEATURES_USE_GENERATION_FENCE: 'true',
        APP_FEATURES_JUDGE_QUEUE_USE_PORT: 'true',
        APP_FEATURES_JUDGE_QUEUE_ENVELOPE_VERSION: '2',
        ...dubboRegistryEnv(),
      },
      out_file: path.join(ROOT, 'logs', 'backend-app.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-app.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '1G',
    },
    {
      name: 'ulticode-notification',
      cwd: BACKEND_CWD,
      script: 'mvn',
      args: '-f notification/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: BACKEND_ENV_FILE,
      env: {
        SERVER_PORT: '9105',
        NOTIFICATION_WORKER_ENABLED: 'true',
        ...dubboRegistryEnv(),
      },
      out_file: path.join(ROOT, 'logs', 'backend-notification.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-notification.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '1G',
    },
    {
      name: 'ulticode-judge',
      cwd: BACKEND_CWD,
      script: 'mvn',
      args: '-f judge/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: BACKEND_ENV_FILE,
      env: {
        JUDGE_SERVER_PORT: '9104',
        JUDGE_DUBBO_PORT: '20884',
        APP_RUNTIME_ROLE: 'judge',
        APP_FEATURES_USE_JUDGE_OUTBOX: 'true',
        APP_FEATURES_USE_GENERATION_FENCE: 'true',
        APP_FEATURES_JUDGE_QUEUE_USE_PORT: 'true',
        APP_FEATURES_JUDGE_QUEUE_ENVELOPE_VERSION: '2',
        ...dubboRegistryEnv(),
      },
      out_file: path.join(ROOT, 'logs', 'backend-judge.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-judge.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '2G',
    },
    {
      name: 'ulticode-9002',
      cwd: path.join(ROOT, 'apps', 'console'),
      script: 'pnpm',
      args: 'dev',
      interpreter: 'none',
      env: { NODE_ENV: 'development', VITE_API_BASE_URL: '/api' },
      out_file: path.join(ROOT, 'logs', 'console.out.log'),
      error_file: path.join(ROOT, 'logs', 'console.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '512M',
    },
    {
      name: 'ulticode-9003',
      cwd: path.join(ROOT, 'apps', 'management'),
      script: 'pnpm',
      args: 'dev',
      interpreter: 'none',
      env: { NODE_ENV: 'development', VITE_API_BASE_URL: '/api' },
      out_file: path.join(ROOT, 'logs', 'management.out.log'),
      error_file: path.join(ROOT, 'logs', 'management.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '512M',
    },
  ],
}
