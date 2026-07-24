#!/usr/bin/env bash
set -euo pipefail

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

启动 UltiCode 开发栈: Docker 基础设施 → Nacos 配置 → Flyway 迁移 → dev-admin
→ pnpm install → PM2 服务 → 就绪检查。

Options:
  --quick              热重启: 跳过 infra/Nacos/迁移/admin/依赖, 只重启 PM2 服务
                       (改代码后最快路径)
  --skip-infra         跳过 Docker 基础设施 (假设 mysql/redis/nacos 已运行)
  --skip-migrate       跳过 Flyway 迁移
  --skip-bootstrap     跳过 dev-admin bootstrap (省 ~90s, admin 已存在时)
  --skip-install       跳过 pnpm install (依赖未变时)
  --only <apps>        只起指定 PM2 app, 逗号分隔
                       (如 9001 或 9001,9002; 可简写或带 ulticode- 前缀)
  --no-frontend        不起前端 (等同 --only 9001)
  --frontend-only      只起前端 (9002/9003), 并跳过后端栈步骤
  -h, --help           显示此帮助

Examples:
  ./scripts/dev/up.sh                          # 全量冷启动
  ./scripts/dev/up.sh --quick                  # 改代码后热重启 (最快)
  ./scripts/dev/up.sh --only 9001              # 只起后端
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

# 把 "9001,9002" 或 "ulticode-9001,ulticode-9002" 统一规范化
normalize_apps() {
  local IFS=','
  local out=""
  for a in $1; do
    a="${a// /}"
    [[ -z "$a" ]] && continue
    case "$a" in
      ulticode-*) out="${out:+$out,}$a" ;;
      *)          out="${out:+$out,}ulticode-$a" ;;
    esac
  done
  echo "$out"
}

# 决定起哪些 PM2 app (--only 优先级最高, 其次 --frontend-only / --no-frontend)
if [[ -n "$ONLY" ]]; then
  PM2_APPS="$(normalize_apps "$ONLY")"
elif [[ "$FRONTEND_ONLY" == true ]]; then
  PM2_APPS="ulticode-9002,ulticode-9003"
elif [[ "$NO_FRONTEND" == true ]]; then
  PM2_APPS="ulticode-9001"
else
  PM2_APPS="ulticode-9001,ulticode-9002,ulticode-9003"
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

# ===== 步骤 4: dev-admin bootstrap =====
if [[ "$SKIP_BOOTSTRAP" != true && "$DEV_SEED_USERS_ENABLED" == "true" ]]; then
  echo "Creating or restoring the documented development administrator..."
  # NOTE: web-application-type=none 关闭 web 容器,只运行 DevUserBootstrapRunner 创建 dev admin。
  # 应用内存在非 daemon 线程(Redisson netty / 调度器),runner 完成后 JVM 不自退,会永久阻塞脚本。
  # 两道保险缺一不可:
  #   1) spring-boot.run.fork=false —— 让应用在 mvn 自身 JVM 内运行(in-process),不 fork 独立 java
  #      子进程。默认 fork=true 会拉起独立 JVM,timeout 杀掉 mvn 后该 JVM 沦为孤儿继续存活(已实测)。
  #   2) timeout --kill-after 限定 —— mvn JVM 仍卡住时,SIGTERM 后 15s SIGKILL 兜底。
  # admin 在 DevUserBootstrapRunner 内已落库;退出码 124(SIGTERM)/137(SIGKILL) 表示超时收尾——
  # 属预期,放行;其他非零退出码才是真失败。
  (
    cd "$ROOT_DIR/backend-spring"
    APP_DEV_USERS_ENABLED=true \
    DEV_SEED_ADMIN_USERNAME="$DEV_SEED_ADMIN_USERNAME" \
    DEV_SEED_ADMIN_EMAIL="$DEV_SEED_ADMIN_EMAIL" \
    DEV_SEED_ADMIN_PASSWORD="$DEV_SEED_ADMIN_PASSWORD" \
    DEV_SEED_ADMIN_ROLE="$DEV_SEED_ADMIN_ROLE" \
    SPRING_PROFILES_ACTIVE=dev \
      timeout --kill-after=15 90 mvn spring-boot:run \
        -Dmaven.test.skip=true \
        -Dspring-boot.run.fork=false \
        -Dspring-boot.run.arguments='--spring.main.web-application-type=none' \
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
  for package in console management shared/auth-core; do
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
  if [[ "$apps_csv" == *",ulticode-9001,"* ]]; then
    check_port 9001 '/contest?page=1&size=1' || all_ok=false
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
  Backend:    http://localhost:9001
  Nacos:      http://localhost:28848/nacos
EOF
    # admin 凭据只在起了后端时显示 (admin 由 dev-admin bootstrap 维护)
    if [[ "$apps_csv" == *",ulticode-9001,"* && "$DEV_SEED_USERS_ENABLED" == "true" ]]; then
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
