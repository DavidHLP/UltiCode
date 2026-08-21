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
      env: {
        SERVER_PORT: '9102',
        APP_FEATURES_CONTEST_DUBBO_CUTOVER: process.env.APP_FEATURES_CONTEST_DUBBO_CUTOVER || 'true',
        APP_FEATURES_SUBMISSION_DUBBO_CUTOVER: process.env.APP_FEATURES_SUBMISSION_DUBBO_CUTOVER || 'false',
      },
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
        APP_RUNTIME_MODE: process.env.APP_RUNTIME_MODE || 'dev-lite',
        APP_FEATURES_USE_JUDGE_OUTBOX: process.env.APP_FEATURES_USE_JUDGE_OUTBOX || 'false',
        APP_FEATURES_USE_GENERATION_FENCE: process.env.APP_FEATURES_USE_GENERATION_FENCE || 'false',
        APP_FEATURES_JUDGE_QUEUE_USE_PORT: process.env.APP_FEATURES_JUDGE_QUEUE_USE_PORT || 'false',
        APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED: process.env.APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED || 'false',
        APP_SEARCH_READ_MODE: process.env.APP_SEARCH_READ_MODE || 'database',
        APP_SEARCH_FALLBACK_TO_DATABASE: process.env.APP_SEARCH_FALLBACK_TO_DATABASE || 'false',
        APP_SEARCH_BACKFILL_ENABLED: process.env.APP_SEARCH_BACKFILL_ENABLED || 'false',
        SEARCH_WORKER_ENABLED: process.env.SEARCH_WORKER_ENABLED || 'false',
        MEILISEARCH_ENABLED: process.env.MEILISEARCH_ENABLED || 'false',
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
      name: 'ulticode-submission',
      cwd: BACKEND_CWD,
      script: 'mvn',
      args: '-f submission/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: BACKEND_ENV_FILE,
      env: {
        // SERVER_PORT must be pinned like the other services: the shared
        // .env still carries the legacy SERVER_PORT=9001, and Spring Boot's
        // relaxed binding gives the env var precedence over
        // ${SUBMISSION_SERVER_PORT:9106} in application.yml.
        SERVER_PORT: '9106',
        SUBMISSION_SERVER_PORT: '9106',
        SUBMISSION_DUBBO_PORT: '20886',
        APP_RUNTIME_MODE: process.env.APP_RUNTIME_MODE || 'dev-lite',
        APP_FEATURES_USE_JUDGE_OUTBOX: process.env.APP_FEATURES_USE_JUDGE_OUTBOX || 'false',
        APP_FEATURES_USE_GENERATION_FENCE: process.env.APP_FEATURES_USE_GENERATION_FENCE || 'false',
        APP_FEATURES_JUDGE_QUEUE_USE_PORT: process.env.APP_FEATURES_JUDGE_QUEUE_USE_PORT || 'false',
        ...dubboRegistryEnv(),
      },
      out_file: path.join(ROOT, 'logs', 'backend-submission.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-submission.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '768M',
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
        APP_RUNTIME_MODE: process.env.APP_RUNTIME_MODE || 'dev-lite',
        APP_FEATURES_USE_JUDGE_OUTBOX: process.env.APP_FEATURES_USE_JUDGE_OUTBOX || 'false',
        APP_FEATURES_USE_GENERATION_FENCE: process.env.APP_FEATURES_USE_GENERATION_FENCE || 'false',
        APP_FEATURES_JUDGE_QUEUE_USE_PORT: process.env.APP_FEATURES_JUDGE_QUEUE_USE_PORT || 'false',
        ...dubboRegistryEnv(),
      },
      out_file: path.join(ROOT, 'logs', 'backend-judge.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-judge.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '2G',
    },
    {
      // Search is a production worker and an explicit local opt-in. The
      // supported local launcher intentionally omits it from its default app
      // list because MeiliSearch is not required for ordinary App development.
      name: 'ulticode-search',
      cwd: BACKEND_CWD,
      script: 'mvn',
      args: '-f search/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      interpreter: 'none',
      env_file: BACKEND_ENV_FILE,
      env: {
        SEARCH_SERVER_PORT: '9107',
        SEARCH_WORKER_ENABLED: 'true',
      },
      out_file: path.join(ROOT, 'logs', 'backend-search.out.log'),
      error_file: path.join(ROOT, 'logs', 'backend-search.err.log'),
      merge_logs: true,
      time: true,
      max_memory_restart: '1G',
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
