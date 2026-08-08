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
ENV_FILE="$ROOT_DIR/.env"

# ===== 参数解析 =====
SKIP_INSTALL=false
SKIP_INFRA=false
SKIP_MIGRATE=false
SKIP_BOOTSTRAP=false
QUICK=false
NO_FRONTEND=false
FRONTEND_ONLY=false
ONLY=""

usage() {
  cat <<'EOF'
Usage: ./scripts/dev/up.sh [options]

启动 UltiCode 开发栈: Docker 基础设施 → Nacos 配置 → Flyway 迁移 → 启动 auth →
dev-admin bootstrap (经 Dubbo RPC) → pnpm install → PM2 服务 → 就绪检查。

Options:
  --quick              热重启: 跳过 infra/Nacos/迁移/admin/依赖, 只重启 PM2 服务
                       (改代码后最快路径)
  --skip-infra         跳过 Docker 基础设施 (假设 mysql/redis/nacos 已运行)
  --skip-migrate       跳过 Flyway 迁移
  --skip-bootstrap     跳过 dev-admin bootstrap (省 ~90s, admin 已存在时)
  --skip-install       跳过 pnpm install (依赖未变时)
  --only <apps>        只起指定 PM2 app, 逗号分隔
                       (如 auth,admin,app 或 9101,9102; 前端仍可用 9002/9003)
  --no-frontend        不起前端 (等同 --only auth,admin,app)
  --frontend-only      只起前端 (9002/9003), 并跳过后端栈步骤
  -h, --help           显示此帮助

Examples:
  ./scripts/dev/up.sh                          # 全量冷启动
  ./scripts/dev/up.sh --quick                  # 改代码后热重启 (最快)
  ./scripts/dev/up.sh --only auth              # 只起 Auth
  ./scripts/dev/up.sh --frontend-only          # 只起前端
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

# 把 owner 名、service 名或 9101/9102/9103 统一规范化为 PM2 app 名
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
  PM2_APPS="ulticode-auth,ulticode-admin,ulticode-app"
else
  PM2_APPS="ulticode-auth,ulticode-admin,ulticode-app,ulticode-9002,ulticode-9003"
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
for var in "${required_vars[@]}"; do
  [[ -n "${!var:-}" ]] || {
    echo "Missing required variable in .env: $var" >&2
    exit 1
  }
done

compose=(
  docker compose --env-file "$ENV_FILE"
  -f "$ROOT_DIR/docker-compose.yml"
  -f "$ROOT_DIR/docker-compose.dev.yml"
)

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
  "$ROOT_DIR/scripts/dev/migrate.sh" migrate
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
  for package in console management packages/auth-core; do
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
# 镜像不随仓库分发。缺失时所有 /submissions/run + /submissions 会返回笼统
# "Runtime Error" + memory=0.0MB(CLAUDE.md § Sandbox Harness 的诊断指纹)。
# 仅告警不 exit:启动后端/前端不依赖沙箱,只有判题需要。
if ! docker image inspect "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" >/dev/null 2>&1; then
  echo "[WARN] ${SANDBOX_IMAGE:-ulticode-sandbox:latest} not found — judging will fail with a masked 'Runtime Error' (memory=0.0MB) until built." >&2
  echo "[WARN]   Build runbook: CLAUDE.md § Sandbox Harness  |  Code: docker/sandbox/harness/build.sh" >&2
fi

# ===== 步骤 6: PM2 服务 =====
echo "Starting PM2 services: $PM2_APPS"
(
  cd "$ROOT_DIR"
  pm2 startOrRestart ecosystem.config.cjs \
    --only "$PM2_APPS" \
    --update-env
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
