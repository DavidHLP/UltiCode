#!/usr/bin/env bash
set -euo pipefail

# Local Compose historically used MySQL root for Nacos. Production overrides
# the Nacos container with NACOS_DB_USER/NACOS_DB_PASSWORD; create that account
# during a fresh MySQL initialization without putting a password in SQL files.
if [[ -z "${NACOS_DB_USER:-}" || -z "${NACOS_DB_PASSWORD:-}" ]]; then
  echo "NACOS_DB_USER/NACOS_DB_PASSWORD not set; keeping the local root bootstrap." >&2
  exit 0
fi

if [[ ! "${NACOS_DB_USER}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "NACOS_DB_USER must contain only letters, digits, or underscores." >&2
  exit 1
fi

# init-env.sh generates hex-based passwords. Escape the two characters that
# have meaning inside a MySQL string literal so operator-managed passwords also
# remain data rather than SQL.
nacos_password_sql="$(printf '%s' "${NACOS_DB_PASSWORD}" | sed "s/\\\\/\\\\\\\\/g; s/'/''/g")"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" nacos_config <<SQL
CREATE USER IF NOT EXISTS '${NACOS_DB_USER}'@'%' IDENTIFIED BY '${nacos_password_sql}';
ALTER USER '${NACOS_DB_USER}'@'%' IDENTIFIED BY '${nacos_password_sql}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON nacos_config.* TO '${NACOS_DB_USER}'@'%';
SQL
