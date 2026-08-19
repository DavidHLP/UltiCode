#!/usr/bin/env bash
set -euo pipefail

# Sanitize NODE_OPTIONS for this script and every PM2-managed process it
# launches. A polluted NODE_OPTIONS (e.g. a stale `--import` of a hook shim
# that no longer exists) makes every node/pm2 CLI invocation crash with
# ERR_MODULE_NOT_FOUND. The PM2 daemon forks managed apps from ITS own env,
# so they are unaffected — but the pm2 CLI this script drives is a node
# process that inherits the launcher shell's env. Dropping NODE_OPTIONS here
# keeps the supported startup path (`./scripts/dev/up.sh`) runnable from any
# shell, regardless of what the launcher exported.
unset NODE_OPTIONS

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
export ENV_FILE

# Preserve explicit caller-provided migration values while loading .env.
MIGRATION_DB_HOST_WAS_SET="${MIGRATION_DB_HOST+x}"
MIGRATION_DB_PORT_WAS_SET="${MIGRATION_DB_PORT+x}"
MIGRATION_DB_NAME_WAS_SET="${MIGRATION_DB_NAME+x}"
MIGRATION_DB_USER_WAS_SET="${MIGRATION_DB_USER+x}"
MIGRATION_DB_PASSWORD_WAS_SET="${MIGRATION_DB_PASSWORD+x}"
SUBMISSION_MIGRATION_DB_USER_WAS_SET="${SUBMISSION_MIGRATION_DB_USER+x}"
SUBMISSION_MIGRATION_DB_PASSWORD_WAS_SET="${SUBMISSION_MIGRATION_DB_PASSWORD+x}"
MIGRATION_DB_HOST_OVERRIDE="${MIGRATION_DB_HOST-}"
MIGRATION_DB_PORT_OVERRIDE="${MIGRATION_DB_PORT-}"
MIGRATION_DB_NAME_OVERRIDE="${MIGRATION_DB_NAME-}"
MIGRATION_DB_USER_OVERRIDE="${MIGRATION_DB_USER-}"
MIGRATION_DB_PASSWORD_OVERRIDE="${MIGRATION_DB_PASSWORD-}"
SUBMISSION_MIGRATION_DB_USER_OVERRIDE="${SUBMISSION_MIGRATION_DB_USER-}"
SUBMISSION_MIGRATION_DB_PASSWORD_OVERRIDE="${SUBMISSION_MIGRATION_DB_PASSWORD-}"

# ===== 参数解析 =====
SKIP_INSTALL=false
SKIP_INFRA=false
SKIP_MIGRATE=false
SKIP_BOOTSTRAP=false
QUICK=false
NO_FRONTEND=false
FRONTEND_ONLY=false
PREPARE_SUBMISSION_OWNER=false
ONLY=""

usage() {
  cat <<'EOF'
Usage: ./scripts/dev/up.sh [options]

启动 UltiCode 开发栈: Docker 基础设施 → Nacos 配置 → Flyway 迁移 → 启动 auth →
dev-admin bootstrap (经 Dubbo RPC) → pnpm install → PM2 服务 → 就绪检查。
# init-env.sh 默认未完成 Submission cutover；up.sh 会在启动前安全停止，
# 不自动 copy/revoke 数据。完成 runbook 观察并设置 SUBMISSION_CUTOVER_COMPLETE=true 后再启动。

Options:
  --quick              热重启: 跳过 infra/Nacos/迁移/admin/依赖, 只重启 PM2 服务
                       (改代码后最快路径)
  --skip-infra         跳过 Docker 基础设施 (假设 mysql/redis/nacos 已运行)
  --skip-migrate       跳过 Flyway 迁移
  --skip-bootstrap     跳过 dev-admin bootstrap (省 ~90s, admin 已存在时)
  --skip-install       跳过 pnpm install (依赖未变时)
  --only <apps>        只起指定 PM2 app, 逗号分隔
                       (如 auth,admin,app,submission,notification,judge,search 或 9101,9102; 前端仍可用 9002/9003)
  --no-frontend        不起前端 (等同 --only auth,admin,app,submission,notification,judge；Search 需显式 --only search)
  --frontend-only      只起前端 (9002/9003), 并跳过后端栈步骤
  --prepare-submission-owner 只启动基础设施、迁移并 provision/unlock owner，不启动 PM2
  -h, --help           显示此帮助

Examples:
  ./scripts/dev/up.sh                          # 全量冷启动
  ./scripts/dev/up.sh --quick                  # 改代码后热重启 (最快)
  ./scripts/dev/up.sh --only auth              # 只起 Auth
  ./scripts/dev/up.sh --frontend-only          # 只起前端
  ./scripts/dev/up.sh --prepare-submission-owner # 准备 owner，随后执行 cutover runbook
  ./scripts/dev/up.sh --no-frontend --skip-bootstrap
  ./scripts/dev/up.sh --skip-infra --skip-migrate
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-install)   SKIP_INSTALL=true; shift ;;
    --skip-infra)     SKIP_INFRA=true; shift ;;
    --skip-migrate)   SKIP_MIGRATE=true; shift ;;
    --skip-bootstrap) SKIP_BOOTSTRAP=true; shift ;;
    --quick)          QUICK=true; shift ;;
    --no-frontend)    NO_FRONTEND=true; shift ;;
    --frontend-only)  FRONTEND_ONLY=true; shift ;;
    --prepare-submission-owner) PREPARE_SUBMISSION_OWNER=true; shift ;;
    --only)           ONLY="${2:-}"; shift 2 ;;
    -h|--help)        usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; echo >&2; usage >&2; exit 2 ;;
  esac
done


# ===== 预设与语义推导 =====
# --quick: 假设 infra/迁移/admin/依赖都已就绪, 只重启 PM2 服务
if [[ "$QUICK" == true ]]; then
  SKIP_INFRA=true
  SKIP_MIGRATE=true
  SKIP_BOOTSTRAP=true
  SKIP_INSTALL=true
fi

# --frontend-only: 前端进程不需要后端栈步骤
if [[ "$FRONTEND_ONLY" == true ]]; then
  SKIP_INFRA=true
  SKIP_MIGRATE=true
  SKIP_BOOTSTRAP=true
fi

if [[ "$PREPARE_SUBMISSION_OWNER" == true && ("$SKIP_MIGRATE" == true || "$FRONTEND_ONLY" == true) ]]; then
  echo "--prepare-submission-owner requires a backend migration run; remove --quick/--skip-migrate/--frontend-only." >&2
  exit 2
fi
# 把 owner 名、service 名或端口统一规范化为 PM2 app 名
normalize_apps() {
  local IFS=','
  local out=""
  for a in $1; do
    a="${a// /}"
    [[ -z "$a" ]] && continue
    case "$a" in
      auth|backend-auth|ulticode-auth|9101)
        a="ulticode-auth"
        ;;
      admin|backend-admin|ulticode-admin|9102)
        a="ulticode-admin"
        ;;
      app|backend-app|ulticode-app|9103)
        a="ulticode-app"
        ;;
  judge|backend-judge|ulticode-judge|9104)
        a="ulticode-judge"
        ;;
      notification|backend-notification|ulticode-notification|9105)
        a="ulticode-notification"
        ;;
      submission|backend-submission|ulticode-submission|9106)
        a="ulticode-submission"
        ;;
      search|backend-search|ulticode-search|9107)
        a="ulticode-search"
        ;;
      console|ulticode-9002|9002)
        a="ulticode-9002"
        ;;
      management|ulticode-9003|9003)
        a="ulticode-9003"
        ;;
      *)
        echo "Unknown PM2 app alias: $a" >&2
        exit 2
        ;;
    esac
    [[ ",$out," == *",$a,"* ]] || out="${out:+$out,}$a"
  done
  echo "$out"
}

# 决定起哪些 PM2 app (--only 优先级最高, 其次 --frontend-only / --no-frontend)
if [[ -n "$ONLY" ]]; then
  PM2_APPS="$(normalize_apps "$ONLY")"
elif [[ "$FRONTEND_ONLY" == true ]]; then
  PM2_APPS="ulticode-9002,ulticode-9003"
elif [[ "$NO_FRONTEND" == true ]]; then
  PM2_APPS="ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge"
else
  PM2_APPS="ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge,ulticode-9002,ulticode-9003"
fi

# ===== 前置检查 =====
for command in docker mvn pnpm pm2 curl timeout; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command not found: $command" >&2
    exit 1
  }
done

if [[ ! -f "$ENV_FILE" ]]; then
  "$ROOT_DIR/scripts/dev/init-env.sh"
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

[[ -n "$MIGRATION_DB_HOST_WAS_SET" ]] && MIGRATION_DB_HOST="$MIGRATION_DB_HOST_OVERRIDE"
[[ -n "$MIGRATION_DB_PORT_WAS_SET" ]] && MIGRATION_DB_PORT="$MIGRATION_DB_PORT_OVERRIDE"
[[ -n "$MIGRATION_DB_NAME_WAS_SET" ]] && MIGRATION_DB_NAME="$MIGRATION_DB_NAME_OVERRIDE"
[[ -n "$MIGRATION_DB_USER_WAS_SET" ]] && MIGRATION_DB_USER="$MIGRATION_DB_USER_OVERRIDE"
[[ -n "$MIGRATION_DB_PASSWORD_WAS_SET" ]] && MIGRATION_DB_PASSWORD="$MIGRATION_DB_PASSWORD_OVERRIDE"
[[ -n "$SUBMISSION_MIGRATION_DB_USER_WAS_SET" ]] && SUBMISSION_MIGRATION_DB_USER="$SUBMISSION_MIGRATION_DB_USER_OVERRIDE"
[[ -n "$SUBMISSION_MIGRATION_DB_PASSWORD_WAS_SET" ]] && SUBMISSION_MIGRATION_DB_PASSWORD="$SUBMISSION_MIGRATION_DB_PASSWORD_OVERRIDE"

: "${SUBMISSION_MIGRATION_DB_USER:=${DEV_MIGRATION_SUBMISSION_USER:-}}"
: "${SUBMISSION_MIGRATION_DB_PASSWORD:=${DEV_MIGRATION_SUBMISSION_PASSWORD:-}}"
: "${DEV_SEED_USERS_ENABLED:=true}"
: "${DEV_SEED_ADMIN_USERNAME:=admin}"
: "${DEV_SEED_ADMIN_EMAIL:=admin@localhost.test}"
: "${DEV_SEED_ADMIN_PASSWORD:=admin123}"
: "${DEV_SEED_ADMIN_ROLE:=ADMIN}"

required_vars=(
  DB_USER DB_PASSWORD DB_NAME MYSQL_ROOT_PASSWORD REDIS_PASSWORD JWT_SECRET
  NACOS_USERNAME NACOS_PASSWORD NACOS_AUTH_TOKEN
  NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
)
if [[ "$FRONTEND_ONLY" != true ]]; then
  required_vars+=(
    SUBMISSION_DB_HOST SUBMISSION_DB_PORT SUBMISSION_DB_NAME
    SUBMISSION_DB_USER SUBMISSION_DB_PASSWORD APP_SUBMISSION_ROUTING_MODE
    SUBMISSION_MIGRATION_DB_USER SUBMISSION_MIGRATION_DB_PASSWORD
    SUBMISSION_CUTOVER_COMPLETE
  )
fi
for var in "${required_vars[@]}"; do
  [[ -n "${!var:-}" ]] || {
    echo "Missing required variable in .env: $var" >&2
    exit 1
  }
done
if [[ "$FRONTEND_ONLY" != true && ! "$SUBMISSION_MIGRATION_DB_USER" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "SUBMISSION_MIGRATION_DB_USER must contain only letters, digits, or underscore." >&2
  exit 1
fi
if [[ "$FRONTEND_ONLY" != true ]]; then
  [[ "$SUBMISSION_DB_NAME" == "submission" ]] || {
    echo "SUBMISSION_DB_NAME must be submission for the local owner runtime." >&2
    exit 1
  }
  [[ "$SUBMISSION_DB_USER" == "submission_rw" ]] || {
    echo "Local PM2 requires SUBMISSION_DB_USER=submission_rw; provision custom production accounts outside up.sh." >&2
    exit 1
  }
  if [[ "$PREPARE_SUBMISSION_OWNER" != true ]]; then
    [[ "$APP_SUBMISSION_ROUTING_MODE" == "remote" ]] || {
      echo "The direct Submission provider requires APP_SUBMISSION_ROUTING_MODE=remote; use the previous verified artifact for local rollback." >&2
      exit 1
    }

    [[ "$SUBMISSION_CUTOVER_COMPLETE" == "true" ]] || {
      echo "Submission cutover is not marked complete; startup intentionally stops. Run the confirmation-gated schema cutover and grant observation, then set SUBMISSION_CUTOVER_COMPLETE=true." >&2
      exit 1
    }
  fi
fi

if [[ "$FRONTEND_ONLY" != true ]]; then
  # Per-owner shadow-user 迁移 (CREATE USER / 跨库 GRANT / 建库) 需要 DBA 权限,
  # 运行账号 ulticode 只持有 ulticode.* 权限 (官方 mysql 镜像授予), 不能执行。
  # .env 的 DB_ROOT_PASSWORD 由 init-env.sh 生成; 显式 MIGRATION_DB_* 可由
  # 调用者覆盖。migrate.sh 只接收这些显式 migration connection values。
  : "${MIGRATION_DB_HOST:=${DB_HOST}}"
  : "${MIGRATION_DB_PORT:=${DB_PORT}}"
  : "${MIGRATION_DB_NAME:=${SUBMISSION_DB_NAME}}"
  : "${MIGRATION_DB_USER:=root}"
  : "${MIGRATION_DB_PASSWORD:=${DB_ROOT_PASSWORD:-$MYSQL_ROOT_PASSWORD}}"
fi

compose=(
  docker compose --env-file "$ENV_FILE"
  -f "$ROOT_DIR/docker-compose.yml"
  -f "$ROOT_DIR/docker-compose.dev.yml"
)

provision_submission_migration_principal() {
  local container="${MYSQL_CONTAINER:-ulticode-mysql}"
  local escaped_password="$SUBMISSION_MIGRATION_DB_PASSWORD"
  escaped_password="${escaped_password//\\/\\\\}"
  escaped_password="${escaped_password//\'/\'\'}"

  docker exec -e MYSQL_PWD="$MIGRATION_DB_PASSWORD" "$container" \
    mysql --default-character-set=utf8mb4 -u "$MIGRATION_DB_USER" \
    --batch --skip-column-names \
    -e "CREATE USER IF NOT EXISTS '$SUBMISSION_MIGRATION_DB_USER'@'%' IDENTIFIED BY '$escaped_password'; ALTER USER '$SUBMISSION_MIGRATION_DB_USER'@'%' IDENTIFIED BY '$escaped_password'; REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$SUBMISSION_MIGRATION_DB_USER'@'%'; GRANT RELOAD ON *.* TO '$SUBMISSION_MIGRATION_DB_USER'@'%'; GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, GRANT OPTION ON submission.* TO '$SUBMISSION_MIGRATION_DB_USER'@'%'; GRANT CREATE USER ON *.* TO '$SUBMISSION_MIGRATION_DB_USER'@'%'; FLUSH PRIVILEGES;"
}

provision_submission_owner() {
  local container="${MYSQL_CONTAINER:-ulticode-mysql}"
  local escaped_password="$SUBMISSION_DB_PASSWORD"
  escaped_password="${escaped_password//\\/\\\\}"
  escaped_password="${escaped_password//\'/\'\'}"

  docker exec -e MYSQL_PWD="$SUBMISSION_MIGRATION_DB_PASSWORD" "$container" \
    mysql --default-character-set=utf8mb4 -u "$SUBMISSION_MIGRATION_DB_USER" \
    --batch --skip-column-names \
    -e "ALTER USER '$SUBMISSION_DB_USER'@'%' IDENTIFIED BY '$escaped_password'; ALTER USER '$SUBMISSION_DB_USER'@'%' ACCOUNT UNLOCK;"

  if ! docker exec -e MYSQL_PWD="$SUBMISSION_DB_PASSWORD" "$container" \
      mysql --default-character-set=utf8mb4 -u "$SUBMISSION_DB_USER" \
      --batch --skip-column-names -h 127.0.0.1 -P 3306 "$SUBMISSION_DB_NAME" \
      -e "SELECT 1" >/dev/null 2>&1; then
    echo "Submission owner account '$SUBMISSION_DB_USER'@'%' is not unlocked or cannot connect to $SUBMISSION_DB_NAME." >&2
    return 1
  fi
}

wait_for_health() {
  local container="$1"
  local attempts="${2:-60}"
  for ((i = 1; i <= attempts; i++)); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "Container did not become healthy: $container" >&2
  docker logs --tail 100 "$container" >&2 || true
  return 1
}

# ===== 步骤 1: Docker 基础设施 =====
if [[ "$SKIP_INFRA" != true ]]; then
  echo "Starting MySQL, Redis, and Nacos..."
  # --force-recreate: 用当前 .env 重建容器, 避免沿用过期 env
  # (否则 REDIS_PASSWORD 等 env 漂移后容器仍持旧值 → Spring 启动报 RedisWrongPasswordException)
  "${compose[@]}" up -d --force-recreate
  wait_for_health ulticode-mysql
  wait_for_health ulticode-redis
  wait_for_health ulticode-nacos

  # ===== 步骤 2: Nacos 管理员配置 (依赖 Nacos 运行, 故随 infra 一起) =====
  echo "Provisioning the local Nacos administrator..."
  "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
else
  echo "Skipping Docker infrastructure + Nacos provisioning (--skip-infra / --quick / --frontend-only)."
fi

# ===== 步骤 3: Flyway 迁移 =====
if [[ "$SKIP_MIGRATE" != true ]]; then
  echo "Applying database migrations..."
  # The shared chain is the explicit schema bootstrap. Owner preflight requires
  # an existing schema, while owner Flyway configs keep createSchemas=false.
  # Retain the privileged migration account because shared grants need it.
  MIGRATION_SCHEMA= \
    MIGRATION_DB_HOST= \
    MIGRATION_DB_PORT= \
    MIGRATION_DB_NAME= \
    MIGRATION_DB_USER="$MIGRATION_DB_USER" \
    MIGRATION_DB_PASSWORD="$MIGRATION_DB_PASSWORD" \
    "$ROOT_DIR/scripts/dev/migrate.sh" migrate
  echo "Provisioning the DEV-LOCAL/owner Submission migration principal..."
  provision_submission_migration_principal
  echo "Applying Submission owner migrations..."
  MIGRATION_SCHEMA=submission \
    MIGRATION_DB_HOST="$MIGRATION_DB_HOST" \
    MIGRATION_DB_PORT="$MIGRATION_DB_PORT" \
    MIGRATION_DB_NAME="$MIGRATION_DB_NAME" \
    MIGRATION_DB_USER="$SUBMISSION_MIGRATION_DB_USER" \
    MIGRATION_DB_PASSWORD="$SUBMISSION_MIGRATION_DB_PASSWORD" \
    "$ROOT_DIR/scripts/dev/migrate.sh" migrate
  echo "Provisioning the local Submission owner account..."
  provision_submission_owner

if [[ "$PREPARE_SUBMISSION_OWNER" == true ]]; then
  echo "Submission owner prepared; no PM2 service was started. Run the cutover preflight/cutover runbook, set SUBMISSION_CUTOVER_COMPLETE=true, then run ./scripts/dev/up.sh." >&2
  exit 0
fi
else
  echo "Skipping database migrations (--skip-migrate / --quick / --frontend-only)."
fi

# ===== 步骤 4: dev-admin bootstrap (依赖 auth 的 Dubbo provider, 故先启动 auth) =====
if [[ "$SKIP_BOOTSTRAP" != true && "$DEV_SEED_USERS_ENABLED" == "true" ]]; then
  # UserProvisioningAdapter 通过 Dubbo RPC 调用 backend-auth 的
  # AccountManagementService 创建/恢复 admin (check=false: 容器可启动, 调用期才需要 provider)。
  # 所以 bootstrap 前必须先拉起 ulticode-auth 并等待其 Dubbo provider 注册到 Nacos。
  echo "Starting ulticode-auth first (admin provisioning uses Dubbo RPC to backend-auth)..."
  (
    cd "$ROOT_DIR"
    pm2 startOrRestart ecosystem.config.cjs --only ulticode-auth --update-env
    pm2 save
  )
  auth_ready=false
  for _ in $(seq 1 90); do
    auth_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 \
      http://127.0.0.1:9101/api/v1/auth/health 2>/dev/null || true)"
    if [[ "$auth_code" == "200" ]]; then
      auth_ready=true
      break
    fi
    sleep 2
  done
  if [[ "$auth_ready" != true ]]; then
    echo "ulticode-auth did not become healthy; cannot run dev-admin bootstrap." >&2
    pm2 logs ulticode-auth --nostream --lines 50 >&2 || true
    exit 1
  fi
  # Dubbo provider 向 Nacos 的注册滞后于 HTTP 健康端点, 留注册缓冲。
  sleep 5

  echo "Creating or restoring the documented development administrator..."
  # NOTE: web-application-type=none 关闭 web 容器,只运行 DevUserBootstrapRunner 创建 dev admin。
  # 应用内存在非 daemon 线程(Redisson netty / 调度器),runner 完成后 JVM 不自退,会永久阻塞脚本。
  # 两道保险缺一不可:
  #   1) spring-boot.run.fork=false —— 让应用在 mvn 自身 JVM 内运行(in-process),不 fork 独立 java
  #      子进程。默认 fork=true 会拉起独立 JVM,timeout 杀掉 mvn 后该 JVM 沦为孤儿继续存活(已实测)。
  #   2) timeout --kill-after 限定 —— mvn JVM 仍卡住时,SIGTERM 后 15s SIGKILL 兜底。
  # admin 在 DevUserBootstrapRunner 内已落库;退出码 124(SIGTERM)/137(SIGKILL) 表示超时收尾——
  # 属预期,放行;其他非零退出码才是真失败。
  # DUBBO_REGISTRY_USERNAME/PASSWORD 映射自 NACOS_* (与 ecosystem.config.cjs 一致),
  # 否则 Dubbo 注册中心鉴权失败 (错误码 5-10), 消费者发现不到 auth provider。
  # DUBBO_PROTOCOL_PORT=-1: bootstrap 只需消费 RPC, 随机端口避免与 PM2 admin
  # 或残留 bootstrap JVM 抢 20882 (BindException: 地址已在使用)。
  # SecurityAutoConfiguration is excluded by backend-admin application.yml.
  # In WebApplicationType.NONE, SecurityFilterAutoConfiguration is skipped too,
  # so exclude UserDetailsServiceAutoConfiguration to avoid its missing
  # SecurityProperties dependency before DevUserBootstrapRunner can execute.
  # Keep the complete exclusion list because the command-line list replaces the
  # YAML list instead of appending to it.
  (
    cd "$ROOT_DIR/services"
    DUBBO_REGISTRY_USERNAME="$NACOS_USERNAME" \
    DUBBO_REGISTRY_PASSWORD="$NACOS_PASSWORD" \
    DUBBO_PROTOCOL_PORT=-1 \
    APP_DEV_USERS_ENABLED=true \
    DEV_SEED_ADMIN_USERNAME="$DEV_SEED_ADMIN_USERNAME" \
    DEV_SEED_ADMIN_EMAIL="$DEV_SEED_ADMIN_EMAIL" \
    DEV_SEED_ADMIN_PASSWORD="$DEV_SEED_ADMIN_PASSWORD" \
    DEV_SEED_ADMIN_ROLE="$DEV_SEED_ADMIN_ROLE" \
    SERVER_PORT=9102 \
    SPRING_PROFILES_ACTIVE=dev \
      timeout --kill-after=15 90 mvn -f admin/pom.xml spring-boot:run \
        -Dmaven.test.skip=true \
        -Dspring-boot.run.fork=false \
        -Dspring-boot.run.arguments='--spring.main.web-application-type=none --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration' \
        -B
  ) || bootstrap_rc=$?
  case "${bootstrap_rc:-0}" in
    0) ;;
    124|137)
      echo "Bootstrap JVM did not self-exit; timed out and terminated (admin already created), continuing." >&2
      ;;
    *)
      echo "Development administrator bootstrap failed (exit $bootstrap_rc)." >&2
      exit "$bootstrap_rc"
      ;;
  esac
else
  echo "Skipping dev-admin bootstrap (--skip-bootstrap / --quick / --frontend-only)."
fi

# ===== 步骤 5: pnpm install =====
if [[ "$SKIP_INSTALL" != true ]]; then
  echo "Installing frontend and shared dependencies..."
  # node_modules 缺失时跳过 --frozen-lockfile (该 flag 在全新/残缺环境会因 lockfile 未解析而失败)
  for package in apps/console apps/management packages/auth-core; do
    if [[ ! -d "$ROOT_DIR/$package/node_modules" ]]; then
      echo "  $package: node_modules missing, running pnpm install..."
      (cd "$ROOT_DIR/$package" && pnpm install)
    else
      # frozen-lockfile 快且 CI 安全, 但 lockfile 与 package.json 漂移 (如新增依赖未同步 lockfile) 时
      # 会硬失败 ERR_PNPM_OUTDATED_LOCKFILE。历史 bug: 此处失败被吞, PM2 未起却退出 0, 误导运维。
      # 回退普通 install 让 lockfile 自愈并告警; 真正失败则显式 exit 1, 绝不静默成功。
      if ! (cd "$ROOT_DIR/$package" && pnpm install --frozen-lockfile); then
        echo "  $package: frozen-lockfile 不一致, 回退 pnpm install 同步 lockfile..." >&2
        (cd "$ROOT_DIR/$package" && pnpm install) || { echo "Error: pnpm install failed for $package" >&2; exit 1; }
      fi
    fi
  done
else
  echo "Skipping dependency install (--skip-install / --quick / --frontend-only)."
fi

# ===== 步骤 5a: 沙箱镜像前置告警(判题功能依赖;不阻塞启动) =====
# 镜像不随仓库分发。缺失时判题 Worker 会返回 SANDBOX_ERROR。
# 仅告警不 exit: 启动后端/前端不依赖沙箱, 只有判题需要。
if ! docker image inspect "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" >/dev/null 2>&1; then
  echo "[WARN] ${SANDBOX_IMAGE:-ulticode-sandbox:latest} not found — judging will fail with SANDBOX_ERROR until built." >&2
  echo "[WARN]   Build runbook: README.md § Docker  |  Code: docker/sandbox/harness/build.sh" >&2
fi

# ===== 步骤 6: PM2 服务 =====
# Judge 依赖 backend-app 的 Dubbo provider (SubmissionFencePort 等)。
# 若本次同时启动 app 与 judge, 先起 app 并等其健康, 再起 judge,
# 避免 judge 冷启动时 provider 尚未注册导致的连接超时噪音;
# Dubbo 框架本身会自动重连, 此顺序只为让首次启动即干净连接。
echo "Starting PM2 services: $PM2_APPS"
(
  cd "$ROOT_DIR"
  # 记录 judge 启动前日志偏移: PM2 持久 out_file 含历史 banner,
  # 就绪检查必须只认本次启动产生的新内容 (见 check_pm2_online)。
  # 必须在 startOrRestart judge 之前一刻才捕获——若提前到等待 app 前,
  # 等待期间旧 judge 实例 crash 自动重启产生的 banner 会被误算作本次内容。
  # 全新 checkout 时 logs/ 可能不存在, 先 mkdir 保证重定向不因 set -e 中止。
  record_judge_offset() {
    mkdir -p "$ROOT_DIR/logs"
    if ! wc -c < "$ROOT_DIR/logs/backend-judge.out.log" 2>/dev/null | tr -d ' ' \
      > "$ROOT_DIR/logs/.judge-ready-offset"; then
      echo 0 > "$ROOT_DIR/logs/.judge-ready-offset"
    fi
  }
  if [[ ",$PM2_APPS," == *",ulticode-judge,"* && ",$PM2_APPS," == *",ulticode-app,"* ]]; then
    first="${PM2_APPS//ulticode-judge,/}"
    first="${first//,ulticode-judge/}"
    pm2 startOrRestart ecosystem.config.cjs \
      --only "$first" \
      --update-env
    app_ready=false
    for _ in $(seq 1 90); do
      if [[ "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 \
        http://127.0.0.1:9103/api/v1/app/health 2>/dev/null || true)" == "200" ]]; then
        app_ready=true
        break
      fi
      sleep 2
    done
    if [[ "$app_ready" != true ]]; then
      echo "backend-app did not become healthy; starting ulticode-judge anyway (Dubbo auto-reconnect)." >&2
    fi
    record_judge_offset
    pm2 startOrRestart ecosystem.config.cjs \
      --only ulticode-judge \
      --update-env
  else
    if [[ ",$PM2_APPS," == *",ulticode-judge,"* ]]; then
      record_judge_offset
    fi
    pm2 startOrRestart ecosystem.config.cjs \
      --only "$PM2_APPS" \
      --update-env
  fi
  pm2 save
)

# ===== 步骤 7: 就绪检查 (只探测实际启动的服务对应端口) =====
check_url() {
  curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "$1" 2>/dev/null || true
}

# 同时尝试 IPv4 与 IPv6 回环, 任一返回 200 即就绪, 避免绑定地址族导致的假性失败。
check_port() {
  local port="$1" path="${2:-/}"
  [[ "$(check_url "http://127.0.0.1:${port}${path}")" == "200" ]] && return 0
  [[ "$(check_url "http://[::1]:${port}${path}")" == "200" ]] && return 0
  return 1
}

check_pm2_online() {
  local app="$1" jlist status restarts log
  jlist="$(pm2 jlist 2>/dev/null)" || return 1
  # pm2 jlist 是紧凑 JSON: 外层 {"name":..,"pm2_env":{"name":..,"status":..,"restart_time":..},..}。
  # 老实现用 grep -o '"name":"app"[^}]*' 匹配到 pm2_env 内嵌的 '}' 即截断,
  # status/restart_time 在外层对象尾部取不到 → judge 就绪检查永远失败。
  # 用 jq 精确取 pm2_env 下的字段; jq 缺失时退化为进程存在性判断。
  if command -v jq >/dev/null 2>&1; then
    status="$(printf '%s' "$jlist" | jq -r ".[] | select(.name==\"$app\") | .pm2_env.status" 2>/dev/null)"
    restarts="$(printf '%s' "$jlist" | jq -r ".[] | select(.name==\"$app\") | .pm2_env.unstable_restarts" 2>/dev/null)"
  else
    # jq 缺失: 至少验证 PM2 进程存在。pm2 pid 对 stopped 进程返回 "0",
    # 非空检查无法区分, 必须校验为正整数。
    [[ "$(pm2 pid "$app" 2>/dev/null)" =~ ^[1-9][0-9]*$ ]] || return 1
    status="online"
    restarts="0"
  fi
  # 只认 online 状态: crash-loop 期间 pid 存在但 status 会周期性离开 online。
  [[ "$status" == "online" ]] || return 1
  # unstable_restarts 只统计 crash 触发的自动重启; 手动 startOrRestart 不计入。
  # 不能用 restart_time (累计值, 连续多次跑 up.sh 也会增长, 会把健康实例误判为 crash-loop)。
  # 持续增长 = crash-loop, 即使瞬间 online 也不算就绪。
  [[ "${restarts:-0}" -lt 5 ]] || return 1
  # Judge Worker 无 HTTP 端点, 以 Spring 启动完成 banner 为准
  # (pid/status 在 JVM 完全起来之前就可能就位)。
  # ecosystem.config.cjs 将 judge 日志写到 logs/backend-judge.out.log,
  # 不是 PM2 默认目录 (此前 grep 错路径导致 judge 就绪检查永远失败)。
  log="$ROOT_DIR/logs/backend-judge.out.log"
  # 只搜本次启动 (步骤 6 记录偏移) 之后的新内容, 忽略历史 banner,
  # 否则慢启动/重启中的 judge 会被上一次运行的旧 banner 误判就绪。
  local offset=0
  if [[ -f "$ROOT_DIR/logs/.judge-ready-offset" ]]; then
    offset="$(<"$ROOT_DIR/logs/.judge-ready-offset")"
  fi
  # 不用 grep -q: 它在首个匹配即提前退出, tail 收到 SIGPIPE(141),
  # 在 set -o pipefail 下管道非零 → 假阴性。改为读完全部输入并丢弃输出。
  tail -c +$((offset + 1)) "$log" 2>/dev/null \
    | grep "Started BackendJudgeApplication" >/dev/null 2>&1
}

apps_csv=",$PM2_APPS,"
for _ in $(seq 1 90); do
  all_ok=true
  if [[ "$apps_csv" == *",ulticode-auth,"* ]]; then
    check_port 9101 '/api/v1/auth/health' || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-admin,"* ]]; then
    check_port 9102 '/api/v1/admin/health' || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-app,"* ]]; then
    check_port 9103 '/api/v1/app/health' || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-notification,"* ]]; then
    check_port 9105 '/api/v1/notification/health' || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-submission,"* ]]; then
    check_pm2_online ulticode-submission || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-judge,"* ]]; then
    check_pm2_online ulticode-judge || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-search,"* ]]; then
    check_pm2_online ulticode-search || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-9002,"* ]]; then
    check_port 9002 '/' || all_ok=false
  fi
  if [[ "$apps_csv" == *",ulticode-9003,"* ]]; then
    check_port 9003 '/' || all_ok=false
  fi
  if [[ "$all_ok" == true ]]; then
    cat <<EOF
Development stack is ready (services: $PM2_APPS).

  Console:    http://localhost:9002
  Management: http://localhost:9003
  Auth API:   http://localhost:9101
  Admin API:  http://localhost:9102
  App API:    http://localhost:9103
  Notification API: http://localhost:9105
  Submission owner: PM2 ulticode-submission (Dubbo 20886)
  Judge Worker: PM2 ulticode-judge (Dubbo 20884)
  Search Worker: PM2 ulticode-search (opt-in locally; production Compose always defines it)
  Nacos:      http://localhost:28848/nacos
EOF
    # admin 凭据只在起了后端时显示 (admin 由 dev-admin bootstrap 维护)
    if [[ "$apps_csv" == *",ulticode-auth,"* || "$apps_csv" == *",ulticode-admin,"* || "$apps_csv" == *",ulticode-app,"* ]] && [[ "$DEV_SEED_USERS_ENABLED" == "true" ]]; then
      cat <<EOF

Local development administrator:
  Username: $DEV_SEED_ADMIN_USERNAME
  Password: $DEV_SEED_ADMIN_PASSWORD
EOF
    else
      echo
    fi
    exit 0
  fi
  sleep 2
done

echo "Application readiness check timed out for: $PM2_APPS" >&2
pm2 status >&2 || true
exit 1
