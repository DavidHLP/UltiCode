#!/usr/bin/env bash
set -euo pipefail

# P2-TLS-001 contract: verify the production certificate mount/listener profile
# statically and exercise its HTTP redirect + HTTPS HSTS behavior in a minimal
# disposable nginx container with a temporary self-signed certificate.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$(mktemp -d)"
NGINX_CONTAINER="ulticode-tls-profile-$$"
CERT_DIR="$TEST_DIR/certs"
HTML_DIR="$TEST_DIR/html"
MINIMAL_CONF="$TEST_DIR/nginx.conf"

cleanup() {
  docker rm -f "$NGINX_CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$TEST_DIR"
}
trap cleanup EXIT
trap 'printf "tls-profile-contract: FAIL line=%s\n" "$LINENO" >&2' ERR

mkdir -p "$CERT_DIR" "$HTML_DIR"
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -subj /CN=localhost -keyout "$CERT_DIR/privkey.pem" \
  -out "$CERT_DIR/fullchain.pem" >/dev/null 2>&1
printf 'tls profile\n' > "$HTML_DIR/index.html"

for config in apps/console/nginx.conf apps/management/nginx.conf; do
  grep -Fq 'include /etc/nginx/conf.d/includes/tls-listener.conf;' "$ROOT_DIR/$config"
  grep -Fq 'map $scheme $strict_transport_security' "$ROOT_DIR/$config"
done
grep -Fq 'listen 8443 ssl;' "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf"
grep -Fq 'ssl_certificate /etc/nginx/tls/fullchain.pem;' "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf"
grep -Fq 'ssl_certificate_key /etc/nginx/tls/privkey.pem;' "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf"
grep -Fq 'ssl_protocols TLSv1.2 TLSv1.3;' "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf"
grep -Fq 'return 301 https://$host$request_uri;' "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf"
grep -Fq 'Strict-Transport-Security $strict_transport_security always;' "$ROOT_DIR/infrastructure/nginx/includes/security-headers.conf"
grep -Fq '${TLS_CERT_DIR:?TLS_CERT_DIR is required}' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'CONSOLE_HTTPS_PORT' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'MANAGEMENT_HTTPS_PORT' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'https://localhost:8443/' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'JWT_COOKIE_SECURE=true' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:?CORS_ALLOWED_ORIGINS is required for production}' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'FRONTEND_URL=${FRONTEND_URL:?FRONTEND_URL is required for production}' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'JWT_JWKS_URI=https://backend-auth:9101/auth/jwks' "$ROOT_DIR/docker-compose.prod.yml"
for config in \
  services/auth/src/main/resources/application.yml \
  services/admin/src/main/resources/application.yml \
  services/app/app-web/src/main/resources/application.yml \
  services/submission/src/main/resources/application.yml \
  services/notification/src/main/resources/application.yml; do
  grep -Fq 'sslMode=${' "$ROOT_DIR/$config"
  grep -Fq 'DB_URL:' "$ROOT_DIR/$config"
done
for prefix in AUTH ADMIN APP SUBMISSION NOTIFICATION; do
  grep -Fq "${prefix}_DB_SSL_MODE:-VERIFY_IDENTITY" "$ROOT_DIR/docker-compose.prod.yml"
done
for spec in \
  "services/auth/src/main/resources/application.yml AUTH" \
  "services/admin/src/main/resources/application.yml ADMIN" \
  "services/app/app-web/src/main/resources/application.yml APP" \
  "services/submission/src/main/resources/application.yml SUBMISSION" \
  "services/notification/src/main/resources/application.yml NOTIFICATION" \
  "services/search/src/main/resources/application.yml SEARCH" \
  "services/judge/src/main/resources/application.yml JUDGE"; do
  read -r config prefix <<< "$spec"
  url_key="url: \${${prefix}_REDIS_URL:"
  enabled_key="enabled: \${${prefix}_REDIS_SSL_ENABLED:false}"
  grep -Fq "$url_key" "$ROOT_DIR/$config"
  grep -Fq "$enabled_key" "$ROOT_DIR/$config"
  ! grep -Fq "bundle: \${${prefix}_REDIS_SSL_BUNDLE:" "$ROOT_DIR/$config"
done
[[ "$(grep -Fc -- '- SPRING_DATA_REDIS_SSL_BUNDLE' "$ROOT_DIR/docker-compose.prod.yml")" -eq 7 ]]
! grep -Fq 'SPRING_DATA_REDIS_SSL_BUNDLE=' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq '#SPRING_DATA_REDIS_SSL_BUNDLE=managed-redis' "$ROOT_DIR/.env.example"
grep -Fq 'address: "${AUTH_REDIS_URL:' "$ROOT_DIR/services/auth/src/main/resources/application.yml"
grep -Fq 'sslEnableEndpointIdentification:' "$ROOT_DIR/services/auth/src/main/resources/application.yml"
grep -Fq 'AUTH_REDIS_SSL_TRUSTSTORE=${AUTH_REDIS_SSL_TRUSTSTORE:-}' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'AUTH_REDIS_SSL_TRUSTSTORE_PASSWORD=${AUTH_REDIS_SSL_TRUSTSTORE_PASSWORD:-}' "$ROOT_DIR/docker-compose.prod.yml"
grep -Fq 'sslTruststore:' "$ROOT_DIR/services/auth/src/main/resources/application.yml"
grep -Fq 'sslTruststorePassword:' "$ROOT_DIR/services/auth/src/main/resources/application.yml"
printf 'managed MySQL/Redis URL, TLS, CA-bundle, and hostname-verification bindings: PASS\n'
! grep -Fq '${REDIS_URL' "$ROOT_DIR/docker-compose.prod.yml"
! grep -Fq '#REDIS_URL=' "$ROOT_DIR/.env.example"
! grep -Fq 'docker/redis/users.acl' "$ROOT_DIR/docker-compose.prod.yml"
printf 'TLS certificate mount, HTTPS listener, HSTS, cookie, and JWKS static contract: PASS\n'

for config in apps/console/nginx.conf apps/management/nginx.conf; do
  docker run --rm \
    --add-host backend-auth:127.0.0.1 \
    --add-host backend-admin:127.0.0.1 \
    --add-host backend-app:127.0.0.1 \
    --add-host backend-notification:127.0.0.1 \
    -v "$ROOT_DIR/$config:/etc/nginx/conf.d/default.conf:ro" \
    -v "$ROOT_DIR/infrastructure/nginx/includes/backend-proxy.conf:/etc/nginx/conf.d/includes/backend-proxy.conf:ro" \
    -v "$ROOT_DIR/infrastructure/nginx/includes/security-headers.conf:/etc/nginx/conf.d/includes/security-headers.conf:ro" \
    -v "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf:/etc/nginx/conf.d/includes/tls-listener.conf:ro" \
    -v "$CERT_DIR:/etc/nginx/tls:ro" \
    nginx:1.27-alpine nginx -t >/dev/null
done
printf 'console/management production gateway syntax: PASS\n'

cat > "$MINIMAL_CONF" <<'EOF'
events {}
http {
    map $scheme $strict_transport_security {
        default "";
        https "max-age=31536000; includeSubDomains";
    }
    server {
        listen 8080;
        root /usr/share/nginx/html;
        include /etc/nginx/tls-listener.conf;
        include /etc/nginx/security-headers.conf;
        location / { try_files $uri /index.html; }
    }
}
EOF

docker run --rm --name "$NGINX_CONTAINER-test" \
  -v "$MINIMAL_CONF:/etc/nginx/nginx.conf:ro" \
  -v "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf:/etc/nginx/tls-listener.conf:ro" \
  -v "$ROOT_DIR/infrastructure/nginx/includes/security-headers.conf:/etc/nginx/security-headers.conf:ro" \
  -v "$CERT_DIR:/etc/nginx/tls:ro" \
  -v "$HTML_DIR:/usr/share/nginx/html:ro" \
  nginx:1.27-alpine nginx -t >/dev/null

docker run -d --name "$NGINX_CONTAINER" \
  -p 127.0.0.1::8080 -p 127.0.0.1::8443 \
  -v "$MINIMAL_CONF:/etc/nginx/nginx.conf:ro" \
  -v "$ROOT_DIR/infrastructure/nginx/includes/tls-listener.prod.conf:/etc/nginx/tls-listener.conf:ro" \
  -v "$ROOT_DIR/infrastructure/nginx/includes/security-headers.conf:/etc/nginx/security-headers.conf:ro" \
  -v "$CERT_DIR:/etc/nginx/tls:ro" \
  -v "$HTML_DIR:/usr/share/nginx/html:ro" \
  nginx:1.27-alpine nginx -g 'daemon off;' >/dev/null
HTTP_PORT="$(docker port "$NGINX_CONTAINER" 8080/tcp | sed -E 's/.*:([0-9]+)$/\1/')"
HTTPS_PORT="$(docker port "$NGINX_CONTAINER" 8443/tcp | sed -E 's/.*:([0-9]+)$/\1/')"
for _ in $(seq 1 20); do
  if curl -fsSI "http://127.0.0.1:$HTTP_PORT/" >/dev/null 2>&1; then break; fi
  sleep 0.1
done
HTTP_HEADERS="$(curl -fsSI "http://127.0.0.1:$HTTP_PORT/")"
HTTPS_HEADERS="$(curl -kfsSI "https://127.0.0.1:$HTTPS_PORT/")"
grep -Fq 'HTTP/1.1 301' <<< "$HTTP_HEADERS"
grep -Fq 'Location: https://' <<< "$HTTP_HEADERS"
grep -Fq 'HTTP/1.1 200' <<< "$HTTPS_HEADERS"
grep -Fq 'Strict-Transport-Security: max-age=31536000; includeSubDomains' <<< "$HTTPS_HEADERS"
printf 'HTTPS redirect/HSTS live nginx contract: PASS\n'
printf 'tls-profile-contract: PASS\n'
