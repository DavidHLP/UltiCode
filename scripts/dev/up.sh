#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
SKIP_INSTALL=false

if [[ "${1:-}" == "--skip-install" ]]; then
  SKIP_INSTALL=true
elif [[ $# -gt 0 ]]; then
  echo "Usage: ./scripts/dev/up.sh [--skip-install]" >&2
  exit 2
fi

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

echo "Starting MySQL, Redis, and Nacos..."
"${compose[@]}" up -d
wait_for_health ulticode-mysql
wait_for_health ulticode-redis
wait_for_health ulticode-nacos

echo "Provisioning the local Nacos administrator..."
"$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"

echo "Applying database migrations..."
"$ROOT_DIR/scripts/dev/migrate.sh" migrate

if [[ "$DEV_SEED_USERS_ENABLED" == "true" ]]; then
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
      timeout --kill-after=15 90 ./mvnw spring-boot:run \
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
fi

if [[ "$SKIP_INSTALL" != true ]]; then
  echo "Installing frontend and shared dependencies..."
  for package in console management shared/auth-core; do
    (cd "$ROOT_DIR/$package" && pnpm install --frozen-lockfile)
  done
fi

echo "Starting backend and frontends with PM2..."
(
  cd "$ROOT_DIR"
  pm2 startOrRestart ecosystem.config.cjs \
    --only ulticode-9001,ulticode-9002,ulticode-9003 \
    --update-env
  pm2 save
)

for _ in $(seq 1 90); do
  backend_code="$(curl -sS -o /dev/null -w '%{http_code}' 'http://127.0.0.1:9001/contest?page=1&size=1' 2>/dev/null || true)"
  console_code="$(curl -sS -o /dev/null -w '%{http_code}' 'http://127.0.0.1:9002/' 2>/dev/null || true)"
  management_code="$(curl -sS -o /dev/null -w '%{http_code}' 'http://127.0.0.1:9003/' 2>/dev/null || true)"
  if [[ "$backend_code" == "200" && "$console_code" == "200" && "$management_code" == "200" ]]; then
    cat <<EOF
Development stack is ready.

  Console:    http://localhost:9002
  Management: http://localhost:9003
  Backend:    http://localhost:9001
  Nacos:      http://localhost:28848/nacos
EOF
    if [[ "$DEV_SEED_USERS_ENABLED" == "true" ]]; then
      cat <<EOF

Local development administrator:
  Username: $DEV_SEED_ADMIN_USERNAME
  Password: $DEV_SEED_ADMIN_PASSWORD
EOF
    else
      echo
      echo "Development user initialization is disabled."
    fi
    exit 0
  fi
  sleep 2
done

echo "Application readiness check timed out." >&2
pm2 status >&2 || true
exit 1
