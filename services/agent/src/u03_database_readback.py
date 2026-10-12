"""Runner-only, read-only MySQL evidence; never used for Agent business operations."""

import hashlib
import os
import re
import subprocess


def _sha(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def read_business_rows(owner_id: str, key: str, plan_id: str | None) -> dict:
    container = os.environ.get("ULTICODE_U03_MYSQL_CONTAINER", "")
    user = os.environ.get("ULTICODE_U03_MYSQL_USER", "")
    password = os.environ.get("ULTICODE_U03_MYSQL_PASSWORD", "")
    database = os.environ.get("APP_DB_NAME", "")
    if (not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", container)
            or any(not re.fullmatch(r"[A-Za-z0-9_]+", value) for value in (user, database))
            or not password or not owner_id or not key):
        raise ValueError("u03_database_readback_configuration_missing")
    owner_sha, key_sha, plan_sha = _sha(owner_id), _sha(key), _sha(plan_id or "")
    sql = (
        "START TRANSACTION READ ONLY;\nSELECT COUNT(*), "
        f"COALESCE(SUM(SHA2(id,256)='{plan_sha}'),0) FROM learning_plans "
        f"WHERE SHA2(user_id,256)='{owner_sha}' AND SHA2(idempotency_key,256)='{key_sha}';"
        "\nROLLBACK;\n"
    )
    command = [
        "docker", "exec", "-i", "-e", "MYSQL_PWD", container, "mysql",
        "--protocol=TCP", "--host=127.0.0.1", "--user", user, "--database", database,
        "--batch", "--skip-column-names", "--raw",
    ]
    try:
        result = subprocess.run(
            command, input=sql, text=True, capture_output=True, timeout=15,
            env={**os.environ, "MYSQL_PWD": password},
        )
    except (OSError, subprocess.SubprocessError):
        raise ValueError("u03_database_readback_failed") from None
    raw = result.stdout.strip()
    if result.returncode != 0 or not re.fullmatch(r"[0-9]+\t[0-9]+", raw):
        raise ValueError("u03_database_readback_failed")
    rows, matching = (int(value) for value in raw.split("\t"))
    return {
        "schema": "ulticode-u03-mysql-readback-v1",
        "owner_sha256": owner_sha, "business_key_sha256": key_sha,
        "plan_id_sha256": plan_sha, "row_count": rows, "matching_plan_rows": matching,
        "raw_counts": raw, "database_sha256": _sha(database), "container_sha256": _sha(container),
    }


def validate_business_rows(value: object, owner_sha: str, key_sha: str, plan_id: str | None,
                           expected_rows: int = 1) -> None:
    fields = {
        "schema", "owner_sha256", "business_key_sha256", "plan_id_sha256", "row_count",
        "matching_plan_rows", "raw_counts", "database_sha256", "container_sha256",
    }
    if (not isinstance(value, dict) or set(value) != fields
            or value.get("schema") != "ulticode-u03-mysql-readback-v1"
            or value.get("owner_sha256") != owner_sha or value.get("business_key_sha256") != key_sha
            or any(not isinstance(value.get(field), str)
                   or not re.fullmatch(r"[0-9a-f]{64}", value[field])
                   for field in ("plan_id_sha256", "database_sha256", "container_sha256"))
            or ((plan_id is not None or expected_rows == 0)
                and value.get("plan_id_sha256") != _sha(plan_id or ""))
            or type(value.get("row_count")) is not int or value["row_count"] != expected_rows
            or type(value.get("matching_plan_rows")) is not int or value["matching_plan_rows"] != expected_rows
            or value.get("raw_counts") != f"{expected_rows}\t{expected_rows}"):
        raise ValueError("u03_java_database_readback_invalid")
