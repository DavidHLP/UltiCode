"""Audit retained history as a liability, never as fresh acceptance receipts."""

import hashlib
import json
import os
from pathlib import Path
import sqlite3
import stat

# Fingerprints read from the retained originals during the approved takeover.
# These identify history only; they are not acceptance evidence.
HISTORY_SOURCE_SHA256 = frozenset({
    "da248763599da0f8411c03afd181268a479c33d3ede058e3845876e832e09ed8",
    "f44806b5762f67eb3554c5cd0afd891ede5cc030b2f8c5d4c5ab0c701c86a45d",
    "b5b7bc94896e2f28df24c3f6c345948d4b91617388719a9eeb4dbfdc3239367d",
})


def validate_history(sources):
    from authorized_budget_period import PeriodError, REVALIDATION_HISTORY, _parent, _file

    if (not isinstance(sources, dict) or set(sources) != {"ledgers", "guard"}
            or not isinstance(sources["ledgers"], list) or len(sources["ledgers"]) != 2):
        raise PeriodError("two retained ledgers and their original guard required")
    hashes = {}
    ids = set()
    known_cost = unknown = attempts = 0
    ledger_counts = []
    ledger_costs = []
    for name in [*sources["ledgers"], sources["guard"]]:
        path = Path(name)
        with _parent(path) as directory:
            fd = _file(directory, path.name, os.O_RDONLY)
            try:
                info = os.fstat(fd)
                if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
                    raise PeriodError("private retained history required")
                with os.fdopen(os.dup(fd), "rb") as stream:
                    raw = stream.read(8_388_609)
                if len(raw) > 8_388_608:
                    raise PeriodError("retained history too large")
            finally:
                os.close(fd)
            if str(path) in hashes:
                raise PeriodError("duplicate history source")
            hashes[str(path)] = hashlib.sha256(raw).hexdigest()
            if hashes[str(path)] not in HISTORY_SOURCE_SHA256:
                raise PeriodError("history source is not a retained original")
            if name == sources["guard"]:
                guard = json.loads(raw)
                continue
            db = sqlite3.connect(":memory:")
            try:
                db.deserialize(raw)
                rows = db.execute("SELECT attempt_id,actual_micro_usd,usage_known,settled FROM attempts").fetchall()
                counters = db.execute("SELECT attempts,actual_micro_usd FROM budget WHERE singleton=1").fetchall()
            finally:
                db.close()
            total = sum(row[1] for row in rows if row[2:] == (1, 1) and type(row[1]) is int)
            if counters != [(len(rows), total)]:
                raise PeriodError("historical ledger counters disagree")
            for attempt, actual, usage_known, settled in rows:
                if not isinstance(attempt, str) or not attempt or attempt in ids:
                    raise PeriodError("historical attempts must be disjoint")
                ids.add(attempt)
                if (usage_known, settled) == (1, 1) and type(actual) is int and actual >= 0:
                    known_cost += actual
                elif usage_known == 0 and actual is None and settled in (0, 1):
                    unknown += 1
                else:
                    raise PeriodError("invalid historical usage")
            ledger_counts.append(len(rows))
            ledger_costs.append((len(rows), total))
            attempts += len(rows)
    receipts = guard.get("receipts")
    if set(hashes.values()) != HISTORY_SOURCE_SHA256:
        raise PeriodError("all retained original sources required")
    if not isinstance(receipts, list) or len(receipts) not in ledger_counts:
        raise PeriodError("original guard history missing")
    pending = [r for r in receipts if r.get("status") in {"pending", "unknown_or_unsafe"}]
    settled = [r for r in receipts if r.get("status") == "settled"]
    if any(type(r.get("peak_micro_usd")) is not int or r["peak_micro_usd"] < 0 for r in settled):
        raise PeriodError("invalid historical guard costs")
    guard_cost = sum(r["peak_micro_usd"] for r in settled)
    if (len(pending) != 1 or len(settled) + 1 != len(receipts)
            or (len(receipts), guard_cost) not in ledger_costs
            or guard.get("settled_peak_micro_usd") != guard_cost
            or pending[0].get("reserved_micro_usd") != REVALIDATION_HISTORY["unknown_encumbrance_micro_usd"]
            or guard.get("pending_micro_usd") != REVALIDATION_HISTORY["unknown_encumbrance_micro_usd"]
            or (attempts, known_cost, unknown) != (REVALIDATION_HISTORY["attempts"],
                REVALIDATION_HISTORY["known_actual_micro_usd"], REVALIDATION_HISTORY["unknown_attempts"])):
        raise PeriodError("retained historical liability differs from approved baseline")
    return {"sources": sources, "sha256": hashes, "baseline": dict(REVALIDATION_HISTORY),
            "acceptance_evidence": False, "unknown_released": False}
