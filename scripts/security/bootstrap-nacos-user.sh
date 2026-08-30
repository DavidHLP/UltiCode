#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${NACOS_USERNAME:?NACOS_USERNAME is required}"
: "${NACOS_PASSWORD:?NACOS_PASSWORD is required}"

readonly NACOS_SERVICE_USER_PREFIXES=(AUTH ADMIN APP SUBMISSION NOTIFICATION JUDGE)

validate_username() {
  local name="$1" label="$2"
  [[ "$name" =~ ^[A-Za-z0-9._-]{3,50}$ ]] || {
    echo "$label must contain only letters, digits, dot, underscore, or hyphen" >&2
    exit 1
  }
}

validate_password() {
  local password="$1" label="$2"
  (( ${#password} >= 16 )) || {
    echo "$label must be at least 16 characters" >&2
    exit 1
  }
}

hash_password() {
  local username="$1" password="$2"
  printf '%s\n' "$password" \
    | docker run --rm -i httpd:2.4-alpine htpasswd -niBC 12 "$username" \
    | sed 's/^[^:]*://' \
    | tr -d '\r\n'
}

provision_admin() {
  local password_hash
  password_hash="$(hash_password "$NACOS_USERNAME" "$NACOS_PASSWORD")"
  docker exec -i \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
    ulticode-mysql \
    mysql -uroot nacos_config <<SQL
INSERT INTO users (username, password, enabled)
VALUES ('$NACOS_USERNAME', '$password_hash', TRUE)
ON DUPLICATE KEY UPDATE password = VALUES(password), enabled = TRUE;
INSERT INTO roles (username, role)
VALUES ('$NACOS_USERNAME', 'ROLE_ADMIN')
ON DUPLICATE KEY UPDATE role = VALUES(role);
UPDATE users SET enabled = FALSE WHERE username = 'nacos';
DELETE FROM roles WHERE username = 'nacos';
SQL
}

provision_service() {
  local prefix="$1" username="$2" password="$3" role password_hash
  role="ROLE_ULTICODE_${prefix}_REGISTRY"
  password_hash="$(hash_password "$username" "$password")"
  docker exec -i \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
    ulticode-mysql \
    mysql -uroot nacos_config <<SQL
INSERT INTO users (username, password, enabled)
VALUES ('$username', '$password_hash', TRUE)
ON DUPLICATE KEY UPDATE password = VALUES(password), enabled = TRUE;
DELETE FROM roles WHERE username = '$username';
INSERT INTO roles (username, role)
VALUES ('$username', '$role')
ON DUPLICATE KEY UPDATE role = VALUES(role);
DELETE FROM permissions WHERE role = '$role';
INSERT INTO permissions (role, resource, action) VALUES
  ('$role', 'nacos:config:*', 'rw'),
  ('$role', 'nacos:service:*', 'rw');
SQL
}

validate_username "$NACOS_USERNAME" NACOS_USERNAME
validate_password "$NACOS_PASSWORD" NACOS_PASSWORD

for prefix in "${NACOS_SERVICE_USER_PREFIXES[@]}"; do
  username_var="${prefix}_NACOS_USERNAME"
  password_var="${prefix}_NACOS_PASSWORD"
  username="${!username_var:-}"
  password="${!password_var:-}"
  if [[ -z "$username" || -z "$password" ]]; then
    echo "$username_var and $password_var are required" >&2
    exit 1
  fi
  validate_username "$username" "$username_var"
  validate_password "$password" "$password_var"
  [[ "$username" != "$NACOS_USERNAME" ]] || {
    echo "$username_var must not reuse NACOS_USERNAME" >&2
    exit 1
  }
  provision_service "$prefix" "$username" "$password"
done

provision_admin
echo "Nacos administrator and service registry users provisioned without exposing passwords."
