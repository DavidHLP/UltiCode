#!/usr/bin/env bash

# P3-LEASE-001: shared MySQL lease protocol for synchronous Ops runbooks.
# The caller provides mysql_query(schema, sql). The server clock and the
# conditional fence-token update are authoritative; local flock remains only
# as a cheap same-host contention shortcut.

FENCED_LEASE_NAME=""
FENCED_LEASE_OWNER=""
FENCED_LEASE_TOKEN=""

fenced_lease_validate_name() {
  [[ "${1:-}" =~ ^[A-Za-z0-9:_-]{1,120}$ ]]
}

fenced_lease_validate_ttl() {
  [[ "${1:-}" =~ ^[0-9]+$ ]] && (( 1000 <= 10#$1 && 10#$1 <= 86400000 ))
}

fenced_lease_acquire() {
  local lease_name="$1" ttl_millis="$2"
  fenced_lease_validate_name "$lease_name" || return 1
  fenced_lease_validate_ttl "$ttl_millis" || return 1

  local lease_micros=$((10#$ttl_millis * 1000))
  local owner_token="run-$(date -u +%Y%m%dT%H%M%S)-$$-${RANDOM}"
  mysql_query admin "
    INSERT INTO fenced_job_leases
      (lease_name, fence_token, owner_token, leased_until, updated_at)
    VALUES
      ('$lease_name', 1, '$owner_token',
       TIMESTAMPADD(MICROSECOND, $lease_micros, CURRENT_TIMESTAMP(3)),
       CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE
      fence_token = IF(leased_until IS NULL
        OR leased_until <= CURRENT_TIMESTAMP(3), fence_token + 1, fence_token),
      owner_token = IF(leased_until IS NULL
        OR leased_until <= CURRENT_TIMESTAMP(3), VALUES(owner_token), owner_token),
      leased_until = IF(leased_until IS NULL
        OR leased_until <= CURRENT_TIMESTAMP(3), VALUES(leased_until), leased_until),
      updated_at = IF(leased_until IS NULL
        OR leased_until <= CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), updated_at);
  " >/dev/null || return 1

  local row actual_owner actual_token
  row="$(mysql_query admin "SELECT owner_token, fence_token FROM fenced_job_leases WHERE lease_name='$lease_name';")" \
    || return 1
  IFS=$'\t' read -r actual_owner actual_token _ <<< "$row"
  if [[ "$actual_owner" != "$owner_token" ]]; then
    return 75
  fi
  [[ "$actual_token" =~ ^[0-9]+$ ]] || return 1
  FENCED_LEASE_NAME="$lease_name"
  FENCED_LEASE_OWNER="$owner_token"
  FENCED_LEASE_TOKEN="$actual_token"
}

fenced_lease_renew() {
  local ttl_millis="$1"
  [[ -n "$FENCED_LEASE_NAME" && -n "$FENCED_LEASE_OWNER" ]] || return 1
  fenced_lease_validate_ttl "$ttl_millis" || return 1
  local lease_micros=$((10#$ttl_millis * 1000))
  mysql_query admin "
    UPDATE fenced_job_leases
    SET leased_until = TIMESTAMPADD(MICROSECOND, $lease_micros, CURRENT_TIMESTAMP(3)),
        updated_at = CURRENT_TIMESTAMP(3)
    WHERE lease_name='$FENCED_LEASE_NAME'
      AND owner_token='$FENCED_LEASE_OWNER'
      AND fence_token=$FENCED_LEASE_TOKEN
      AND leased_until > CURRENT_TIMESTAMP(3);
  " >/dev/null || return 1
  fenced_lease_assert
}

fenced_lease_assert() {
  [[ -n "$FENCED_LEASE_NAME" && -n "$FENCED_LEASE_OWNER" && "$FENCED_LEASE_TOKEN" =~ ^[0-9]+$ ]] \
    || return 1
  [[ "$(mysql_query admin "SELECT COUNT(*) FROM fenced_job_leases WHERE lease_name='$FENCED_LEASE_NAME' AND owner_token='$FENCED_LEASE_OWNER' AND fence_token=$FENCED_LEASE_TOKEN AND leased_until > CURRENT_TIMESTAMP(3);")" == "1" ]]
}

fenced_lease_release() {
  [[ -n "$FENCED_LEASE_NAME" && -n "$FENCED_LEASE_OWNER" && "$FENCED_LEASE_TOKEN" =~ ^[0-9]+$ ]] \
    || return 0
  mysql_query admin "
    UPDATE fenced_job_leases
    SET owner_token = NULL, leased_until = NULL, updated_at = CURRENT_TIMESTAMP(3)
    WHERE lease_name='$FENCED_LEASE_NAME'
      AND owner_token='$FENCED_LEASE_OWNER'
      AND fence_token=$FENCED_LEASE_TOKEN;
  " >/dev/null
}
