#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${NACOS_USERNAME:?NACOS_USERNAME is required}"
: "${NACOS_PASSWORD:?NACOS_PASSWORD is required}"

if [[ ! "$NACOS_USERNAME" =~ ^[A-Za-z0-9._-]{3,50}$ ]]; then
  echo "NACOS_USERNAME must contain only letters, digits, dot, underscore, or hyphen" >&2
  exit 1
fi

if (( ${#NACOS_PASSWORD} < 16 )); then
  echo "NACOS_PASSWORD must be at least 16 characters" >&2
  exit 1
fi

password_hash="$(
  printf '%s\n' "$NACOS_PASSWORD" |
    docker run --rm -i httpd:2.4-alpine htpasswd -niBC 12 "$NACOS_USERNAME" |
    sed 's/^[^:]*://' |
    tr -d '\r\n'
)"

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

UPDATE users SET enabled = FALSE
WHERE username = 'nacos' AND username <> '$NACOS_USERNAME';
DELETE FROM roles
WHERE username = 'nacos' AND username <> '$NACOS_USERNAME';
SQL

echo "Nacos administrator provisioned without exposing the password."
