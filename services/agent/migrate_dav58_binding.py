"""Dedicated reviewed 58-to-59 migration. Dry-run unless --apply is explicit."""
import argparse
import hashlib
import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))
from authorized_budget_period import PeriodIdentity
from budget_binding_migration import migrate, REVIEWED_HASHES, AUTHORIZATION, FILESYSTEM_EVIDENCE


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    expected = PeriodIdentity("dav58-local-20261003T171726Z",
        "3382b52dd15c0f390028defa92d9219e14ad3c2d05bb1a4e90d56948af0d50a7",
        "b7131661377941b3b3627adaefa8d887")
    event = migrate(expected, REVIEWED_HASHES, AUTHORIZATION,
        filesystem_evidence=FILESYSTEM_EVIDENCE,
        dry_run=not args.apply)
    from model_budget import _authorization_slot
    event_path = _authorization_slot() / "binding-migration.json"
    print(json.dumps({"applied": args.apply, "utc": event["utc"], "identity": event["identity"],
                      "objects": event["objects"], "baseline_sha256": event["hashes"],
                      "event_path": str(event_path),
                      "event_sha256": hashlib.sha256(event_path.read_bytes()).hexdigest() if args.apply else None}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FAIL migration={type(error).__name__}")
        raise SystemExit(1)
