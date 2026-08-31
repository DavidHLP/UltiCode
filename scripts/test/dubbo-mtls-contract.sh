#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

fail() {
  echo "Dubbo mTLS contract failed: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing file: $file"
  grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file does not contain: $text"
}

not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing file: $file"
  ! grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file contains forbidden value: $text"
}

[[ -f "$COMPOSE_FILE" ]] || fail "missing production Compose override"
contains docker-compose.prod.yml 'DUBBO_MTLS_CERT_DIR:?DUBBO_MTLS_CERT_DIR is required'
contains docker-compose.prod.yml ':/run/secrets/dubbo:ro'
not_contains docker-compose.prod.yml 'DUBBO_MTLS_CERT_DIR:-'

service_files=(
  "backend-auth:services/auth/src/main/resources/application.yml"
  "backend-admin:services/admin/src/main/resources/application.yml"
  "backend-app:services/app/app-web/src/main/resources/application.yml"
  "backend-submission:services/submission/src/main/resources/application.yml"
  "backend-notification:services/notification/src/main/resources/application.yml"
  "backend-judge:services/judge/src/main/resources/application.yml"
)

for mapping in "${service_files[@]}"; do
  service="${mapping%%:*}"
  file="${mapping#*:}"
  contains "$file" 'ssl-enabled: ${DUBBO_PROTOCOL_SSL_ENABLED:false}'
  contains "$file" 'default: ${DUBBO_SSL_DEFAULT:false}'
  contains "$file" 'server-key-cert-chain-path: ${DUBBO_SSL_SERVER_KEY_CERT_CHAIN_PATH:}'
  contains "$file" 'server-private-key-path: ${DUBBO_SSL_SERVER_PRIVATE_KEY_PATH:}'
  contains "$file" 'server-trust-cert-collection-path: ${DUBBO_SSL_SERVER_TRUST_CERT_COLLECTION_PATH:}'
  contains "$file" 'client-key-cert-chain-path: ${DUBBO_SSL_CLIENT_KEY_CERT_CHAIN_PATH:}'
  contains "$file" 'client-private-key-path: ${DUBBO_SSL_CLIENT_PRIVATE_KEY_PATH:}'
  contains "$file" 'client-trust-cert-collection-path: ${DUBBO_SSL_CLIENT_TRUST_CERT_COLLECTION_PATH:}'
  contains docker-compose.prod.yml "DUBBO_MTLS_SERVICE_IDENTITY=$service"
  contains docker-compose.prod.yml "\${DUBBO_MTLS_CERT_DIR:?DUBBO_MTLS_CERT_DIR is required}/$service:/run/secrets/dubbo:ro"
done
for pom in \
  services/auth/pom.xml services/admin/pom.xml services/app/app-web/pom.xml \
  services/submission/pom.xml services/notification/pom.xml services/judge/pom.xml; do
  contains "$pom" '<artifactId>backend-rpc-resilience</artifactId>'
done

contains docker-compose.prod.yml 'DUBBO_MTLS_ALLOWED_CALLERS=backend-admin,backend-app,backend-notification,backend-submission'
contains docker-compose.prod.yml 'DUBBO_MTLS_ALLOWED_CALLERS='
contains docker-compose.prod.yml 'DUBBO_MTLS_ALLOWED_CALLERS=backend-admin,backend-submission,backend-judge'
contains docker-compose.prod.yml 'DUBBO_MTLS_ALLOWED_CALLERS=backend-admin,backend-app,backend-judge'
contains docker-compose.prod.yml 'DUBBO_MTLS_ALLOWED_CALLERS=backend-admin'
contains docker-compose.prod.yml 'DUBBO_MTLS_ALLOWED_CALLERS=backend-app'
for property in \
  DUBBO_PROTOCOL_SSL_ENABLED=true \
  DUBBO_SSL_DEFAULT=true \
  DUBBO_SSL_SERVER_KEY_CERT_CHAIN_PATH=/run/secrets/dubbo/identity.crt \
  DUBBO_SSL_SERVER_PRIVATE_KEY_PATH=/run/secrets/dubbo/identity.key \
  DUBBO_SSL_SERVER_TRUST_CERT_COLLECTION_PATH=/run/secrets/dubbo/trusted-callers.pem \
  DUBBO_SSL_CLIENT_KEY_CERT_CHAIN_PATH=/run/secrets/dubbo/identity.crt \
  DUBBO_SSL_CLIENT_PRIVATE_KEY_PATH=/run/secrets/dubbo/identity.key \
  DUBBO_SSL_CLIENT_TRUST_CERT_COLLECTION_PATH=/run/secrets/dubbo/trusted-services.pem; do
  contains docker-compose.prod.yml "$property"
done

fixture_dir="$(mktemp -d)"
server_pid=""
cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" >/dev/null 2>&1 || true
    wait "$server_pid" >/dev/null 2>&1 || true
  fi
  rm -rf "$fixture_dir"
}
trap cleanup EXIT
chmod 700 "$fixture_dir"

openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 2 \
  -keyout "$fixture_dir/ca.key" -out "$fixture_dir/ca.crt" \
  -subj "/CN=UltiCode Dubbo mTLS test CA" >/dev/null 2>&1

make_certificate() {
  local identity="$1" san_identity="$2"
  local ca_crt="${3:-$fixture_dir/ca.crt}"
  local ca_key="${4:-$fixture_dir/ca.key}"
  cat >"$fixture_dir/$identity.ext" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth,serverAuth
subjectAltName=URI:spiffe://ulticode/service/$san_identity,DNS:$san_identity.ulticode.internal
EOF
  openssl req -new -newkey rsa:2048 -nodes \
    -keyout "$fixture_dir/$identity.key" -out "$fixture_dir/$identity.csr" \
    -subj "/CN=$san_identity" >/dev/null 2>&1
  openssl x509 -req -sha256 -days 1 \
    -in "$fixture_dir/$identity.csr" \
    -CA "$ca_crt" -CAkey "$ca_key" \
    -CAcreateserial -CAserial "$fixture_dir/$identity.srl" \
    -extfile "$fixture_dir/$identity.ext" \
    -out "$fixture_dir/$identity.crt" >/dev/null 2>&1
}

make_certificate server backend-app
make_certificate admin backend-admin
make_certificate wrong-san backend-evil
make_certificate unknown backend-search
make_certificate expired backend-admin
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 2 \
  -keyout "$fixture_dir/untrusted-ca.key" -out "$fixture_dir/untrusted-ca.crt" \
  -subj "/CN=Untrusted Dubbo mTLS test CA" >/dev/null 2>&1
make_certificate unauthorized backend-evil \
  "$fixture_dir/untrusted-ca.crt" "$fixture_dir/untrusted-ca.key"
cat "$fixture_dir/ca.crt" >"$fixture_dir/trusted-callers.pem"
cat "$fixture_dir/ca.crt" >"$fixture_dir/trusted-services.pem"

openssl verify -CAfile "$fixture_dir/trusted-callers.pem" \
  -verify_hostname backend-admin.ulticode.internal "$fixture_dir/admin.crt" >/dev/null
future="$(($(date +%s) + 172800))"
if openssl verify -attime "$future" -CAfile "$fixture_dir/trusted-callers.pem" \
  "$fixture_dir/expired.crt" >/dev/null 2>&1; then
  fail "expired certificate was accepted"
fi
if openssl verify -verify_hostname backend-admin.ulticode.internal \
  -CAfile "$fixture_dir/trusted-callers.pem" "$fixture_dir/unknown.crt" >/dev/null 2>&1; then
  fail "unknown certificate was accepted"
fi
if openssl verify -verify_hostname backend-admin.ulticode.internal \
  -CAfile "$fixture_dir/trusted-callers.pem" "$fixture_dir/wrong-san.crt" >/dev/null 2>&1; then
  fail "wrong-SAN certificate was accepted"
fi
printf '%s\n' "Dubbo mTLS certificate policy: PASS"


port=$((24000 + (RANDOM % 1000)))
openssl s_server -quiet -accept "$port" \
  -cert "$fixture_dir/server.crt" -key "$fixture_dir/server.key" \
  -Verify 1 -verify_return_error -CAfile "$fixture_dir/trusted-callers.pem" \
  -naccept 2 >"$fixture_dir/server.log" 2>&1 &
server_pid=$!
sleep 1
if ! printf 'Q\n' | openssl s_client -brief -connect "127.0.0.1:$port" \
  -cert "$fixture_dir/admin.crt" -key "$fixture_dir/admin.key" \
  -CAfile "$fixture_dir/ca.crt" -verify_return_error >/dev/null 2>&1; then
  fail "authorized client certificate could not complete mTLS handshake"
fi
printf 'Q\n' | openssl s_client -brief -connect "127.0.0.1:$port" \
  -cert "$fixture_dir/unauthorized.crt" -key "$fixture_dir/unauthorized.key" \
  -CAfile "$fixture_dir/ca.crt" -verify_return_error \
  >"$fixture_dir/unauthorized-client.log" 2>&1 || true
wait "$server_pid" >/dev/null 2>&1 || true
server_pid=""
grep -Eq 'verify error|certificate verify failed|unknown ca' \
  "$fixture_dir/server.log" \
  || fail "server did not reject the unauthorized client certificate"
printf '%s\n' "Dubbo mTLS wrong-certificate handshake: PASS"

printf '%s\n' "Dubbo mTLS contract: PASS"
