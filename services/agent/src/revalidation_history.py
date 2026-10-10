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

ROLLOVER_IDENTITY = {
    "period_id": "revalidation-20261008",
    "identity": "c40d43be75664951b0b3272081a222f5",
    "config_sha256": "38374da506a36779c4c48e5582c9cf1bb2e571e2fb775e0dbb0f255f9c70701c",
    "policy_id": "acceptance-revalidation-v1",
}
# Read after canonical receipt audit and permanent halt; not fresh acceptance.
ROLLOVER_SHA256 = {
    "budget.sqlite3": "cb708b4ffcaba41e241ea0a84f8e1969b3f0f853f82f063f872bb6ee7338058f",
    "binding.json": "02dae1dff3ced4f6379a12421fa313810f6f35cf9a86120e057fd384a63aeebb",
    "dav58-increment-c40d43be75664951b0b3272081a222f5.json": "62d66f25b487503d236ece4a26b3b95c38554ef1f9a91a8fdb269d4c850f254d",
}

UNKNOWN_ROLLOVER_IDENTITY = {
    "period_id": "revalidation-v2-20261008",
    "identity": "c565e38221bf4b428ec74665b4e3131f",
    "config_sha256": "9c687594f2e7404e290146831c1d49788326c0d0acdc7f6fbdd242a31d323ce1",
    "policy_id": "acceptance-revalidation-v2",
}
UNKNOWN_ROLLOVER_SHA256 = {
    "budget.sqlite3": "3c1b89132455f08cc9ac1130fbe589b76a539d5ed71f88ffc1d4c66402ef7e7d",
    "binding.json": "4b575707075c9d4fc5a731aba9854379f263469b01e11b7c148f3f84bf82e582",
    "dav58-increment-c565e38221bf4b428ec74665b4e3131f.json": "4ad00e146cdb2d7ccbe3a5f04c7fc07c1e9b828f53516e32977cce77af83f1eb",
}


def _validate_unknown_rollover_history(sources):
    from authorized_budget_period import PeriodError, PeriodIdentity, REVALIDATION_V3_HISTORY, _parent, _file
    from model_budget import ModelBudget, authorization_slot
    from dav58_live_guard import IncrementalGuard

    previous = _validate_rollover_history(sources)
    expected = PeriodIdentity(**UNKNOWN_ROLLOVER_IDENTITY)
    accounting = authorization_slot(expected) / "accounting"
    hashes, files = {}, {}
    for name, digest in UNKNOWN_ROLLOVER_SHA256.items():
        path = accounting / name
        with _parent(path) as directory:
            fd = _file(directory, path.name, os.O_RDONLY)
            try:
                info = os.fstat(fd)
                if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
                    raise PeriodError("private unknown rollover history required")
                with os.fdopen(os.dup(fd), "rb") as stream:
                    raw = stream.read(8_388_609)
            finally:
                os.close(fd)
        if len(raw) > 8_388_608 or hashlib.sha256(raw).hexdigest() != digest:
            raise PeriodError("sealed unknown rollover history changed")
        hashes[str(path)], files[name] = digest, raw
    budget = ModelBudget._sealed_history_view(expected, previous)
    snapshot = budget.snapshot()
    guard = json.loads(files[f"dav58-increment-{expected.identity}.json"])
    _check_unknown_rollover(snapshot, guard)
    # Validate only the settled historical prefix; this view never authorizes resume.
    known = {**guard, "halted": False, "pending_micro_usd": 0, "receipts": guard["receipts"][:-1]}
    try:
        IncrementalGuard._validate_resume(known, expected.identity, expected.config_sha256, budget.policy, budget)
    except ValueError as error:
        raise PeriodError("sealed unknown rollover receipts invalid") from error
    db = sqlite3.connect(":memory:")
    try:
        db.deserialize(files["budget.sqlite3"])
        rows = db.execute("SELECT a.attempt_id,a.purpose,a.actual_micro_usd,a.usage_known,a.settled,d.request_sha256 "
                          "FROM attempts a LEFT JOIN dispatches d ON d.attempt_id=a.attempt_id "
                          "WHERE a.usage_known!=1 OR a.settled!=1").fetchall()
    finally:
        db.close()
    last = guard["receipts"][-1]
    if rows != [(last["attempt_id"], last["lane"], None, 0, 1, last["request_sha256"])]:
        raise PeriodError("retained unknown request binding differs")
    return {"sources": sources, "sha256": {**previous["sha256"], **hashes},
            "baseline": dict(REVALIDATION_V3_HISTORY), "acceptance_evidence": False,
            "unknown_released": False}


def _check_unknown_rollover(snapshot, guard):
    from authorized_budget_period import PeriodError

    if (snapshot["state"] != "halted" or snapshot["sql_gate"] != "halted"
            or snapshot["halted"] != 1 or snapshot["attempts"] != 25
            or snapshot["actual_micro_usd"] != 5928 or snapshot["committed_micro_usd"] != 240_000
            or snapshot["unknown_usage_attempts"] != 1 or snapshot["unsettled_attempts"] != 0
            or guard.get("halted") is not True or guard.get("pending_micro_usd") != 786_432
            or not isinstance(guard.get("receipts"), list) or len(guard["receipts"]) != 25):
        raise PeriodError("previous unknown liability must remain sealed")
    last = guard["receipts"][-1]
    if (last.get("status") != "unknown_or_unsafe" or last.get("reason") != "network_or_read_failure"
            or last.get("attempt_id") != "630b0d60-4270-47ab-b42c-4e407df78a6a"
            or last.get("lane") != "prior_development" or last.get("reserved_micro_usd") != 786_432
            or last.get("request_sha256") != "26a76e0d7c1289b4a3b8c17a18aae3ca8f2015ec857c213782372ab8cf426dec"):
        raise PeriodError("retained unknown identity differs")


SETTLED_ROLLOVER_IDENTITY = {
    "period_id": "revalidation-v3-20261008",
    "identity": "7ac6d260692b43bea0ea9e3c56bc3043",
    "config_sha256": "ade2d5e3609fce97c300aee447a82c9ca996151ba7b4e8a3f61ca0021b9b70c5",
    "policy_id": "acceptance-revalidation-v3",
}
SETTLED_ROLLOVER_SHA256 = {
    "budget.sqlite3": "5f824cadffc4eafebbab8f8dbd388f2d3d757bde2b47e316da53d8c6949302c8",
    "binding.json": "121f200e1aff16d51fa7d65194b60c75678c12365afa34b86a426cc2282ad23f",
    "dav58-increment-7ac6d260692b43bea0ea9e3c56bc3043.json": "42bc536128d190f959b1d886d9f46d08227123e1713e97de02e1e702301b5292",
}


V4_ROLLOVER_IDENTITY = {
    "period_id": "revalidation-v4-20261009",
    "identity": "7e6f5f0c77384be4afa632d00252c931",
    "config_sha256": "e8e44fec0df79bd9c4cc60f5c2ddac00f81788eaee0d530187817acc1c12bd97",
    "policy_id": "acceptance-revalidation-v4",
}
V4_ROLLOVER_SHA256 = {
    "budget.sqlite3": "e42fff2c94f6fe7a9f531febd0a1ee9587d39196d745a9f282a4571f4ddcf72d",
    "binding.json": "cd195be5ab3af6011134088c813a4e2e928db3babed5300eed6bdcc5b12c40bd",
    "dav58-increment-7e6f5f0c77384be4afa632d00252c931.json": "3d95ecc832fa5d60b948cd3bfd3a43f35d2b3ee1bfb84c3506306d1b90065d39",
}


V5_ROLLOVER_IDENTITY = {
    "period_id": "revalidation-v5-20261009",
    "identity": "2ac0f42493924aad8b8b763812042bf4",
    "config_sha256": "adb0679bc61db06df47674fa5e2a97d8ee5f6f7671fa6cf23fdda49dc39fd888",
    "policy_id": "acceptance-revalidation-v5",
}
V5_ROLLOVER_SHA256 = {
    "budget.sqlite3": "56a0ca6068baac216dc26634d9b27679b2175ad1df899b7d8b9e91a88c819c43",
    "binding.json": "3b0c1e0e1646a9f3e48cd9d457e8256b1a69d277edcd473e16d9149a8e483e32",
    "dav58-increment-2ac0f42493924aad8b8b763812042bf4.json": "0168c26cc264ecd22e7ba7c853c5dd4dcb57237aa03fc75eef6e73bf655f7c56",
}


V6_ROLLOVER_IDENTITY = {
    "period_id": "revalidation-v6-20261009",
    "identity": "6e18a8ccca1541a6953b849f4200fd86",
    "config_sha256": "35175b6be002b4f14695f0ed13ee2f49e6a2a357d18a8106c34ca387cb5bd506",
    "policy_id": "acceptance-revalidation-v6",
}
V6_ROLLOVER_SHA256 = {
    "budget.sqlite3": "d9938bcc8ae6410189e5a953a25ec21f6f6209682f073a91118a0ed9fa6edf9d",
    "binding.json": "4ac0023aab255fd28c2afe976bee8374c9d4ea5843b8a588835350ffd02d2fe9",
    "dav58-increment-6e18a8ccca1541a6953b849f4200fd86.json": "1d0e0b885e73ace9fa74d423fcf8d5e4ea090baa5e196debce2802e2648ec5e9",
}


def _validate_rollover_history(sources, *, settled_policy="v1"):
    from authorized_budget_period import PeriodError, PeriodIdentity, REVALIDATION_V2_HISTORY, REVALIDATION_V4_HISTORY, REVALIDATION_V5_HISTORY, REVALIDATION_V6_HISTORY, REVALIDATION_V7_HISTORY, _parent, _file
    from model_budget import ModelBudget, authorization_slot
    from dav58_live_guard import IncrementalGuard

    if settled_policy == "v6":
        original = _validate_rollover_history(sources, settled_policy="v5")
        identity, fingerprints = V6_ROLLOVER_IDENTITY, V6_ROLLOVER_SHA256
        totals, history = (105, 28_533, 928_800), REVALIDATION_V7_HISTORY
    elif settled_policy == "v5":
        original = _validate_rollover_history(sources, settled_policy="v4")
        identity, fingerprints = V5_ROLLOVER_IDENTITY, V5_ROLLOVER_SHA256
        totals, history = (79, 20_433, 758_400), REVALIDATION_V6_HISTORY
    elif settled_policy == "v4":
        original = _validate_rollover_history(sources, settled_policy="v3")
        identity, fingerprints = V4_ROLLOVER_IDENTITY, V4_ROLLOVER_SHA256
        totals, history = (35, 9123, 336_000), REVALIDATION_V5_HISTORY
    elif settled_policy == "v3":
        original = _validate_unknown_rollover_history(sources)
        identity, fingerprints = SETTLED_ROLLOVER_IDENTITY, SETTLED_ROLLOVER_SHA256
        totals, history = (115, 32_484, 976_800), REVALIDATION_V4_HISTORY
    elif settled_policy == "v1":
        original = validate_history(sources)
        identity, fingerprints = ROLLOVER_IDENTITY, ROLLOVER_SHA256
        totals, history = (117, 26_993, 1_123_200), REVALIDATION_V2_HISTORY
    else:
        raise PeriodError("unsupported sealed rollover policy")
    expected = PeriodIdentity(**identity)
    accounting = authorization_slot(expected) / "accounting"
    hashes, guard = {}, None
    for name, digest in fingerprints.items():
        path = accounting / name
        with _parent(path) as directory:
            fd = _file(directory, path.name, os.O_RDONLY)
            try:
                info = os.fstat(fd)
                if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
                    raise PeriodError("private rollover history required")
                with os.fdopen(os.dup(fd), "rb") as stream:
                    raw = stream.read(8_388_609)
            finally:
                os.close(fd)
        if len(raw) > 8_388_608 or hashlib.sha256(raw).hexdigest() != digest:
            raise PeriodError("sealed rollover history changed")
        hashes[str(path)] = digest
        if name.endswith(".json") and name.startswith("dav58-increment-"):
            guard = json.loads(raw)
    budget = ModelBudget._sealed_history_view(expected, original)
    snapshot = budget.snapshot()
    if (snapshot["state"] != "halted" or snapshot["sql_gate"] != "halted"
            or snapshot["halted"] != 1
            or (snapshot["attempts"], snapshot["actual_micro_usd"], snapshot["committed_micro_usd"]) != totals
            or snapshot["unknown_usage_attempts"] or snapshot["unsettled_attempts"]):
        raise PeriodError("previous period must be sealed with all liabilities retained")
    try:
        IncrementalGuard._validate_resume(guard, expected.identity, expected.config_sha256, budget.policy, budget)
    except ValueError as error:
        raise PeriodError("sealed rollover receipts invalid") from error
    return {"sources": sources, "sha256": {**original["sha256"], **hashes},
            "baseline": dict(history), "acceptance_evidence": False,
            "unknown_released": False}


def validate_history(sources, *, policy_id="acceptance-revalidation-v1"):
    from authorized_budget_period import PeriodError, REVALIDATION_HISTORY, _parent, _file
    from authorized_budget_period import REVALIDATION_POLICY_ID, REVALIDATION_V2_POLICY_ID, REVALIDATION_V3_POLICY_ID, REVALIDATION_V4_POLICY_ID, REVALIDATION_V5_POLICY_ID, REVALIDATION_V6_POLICY_ID, REVALIDATION_V7_POLICY_ID

    if policy_id == REVALIDATION_V2_POLICY_ID:
        return _validate_rollover_history(sources)
    if policy_id == REVALIDATION_V3_POLICY_ID:
        return _validate_unknown_rollover_history(sources)
    if policy_id == REVALIDATION_V4_POLICY_ID:
        return _validate_rollover_history(sources, settled_policy="v3")
    if policy_id == REVALIDATION_V5_POLICY_ID:
        return _validate_rollover_history(sources, settled_policy="v4")
    if policy_id == REVALIDATION_V6_POLICY_ID:
        return _validate_rollover_history(sources, settled_policy="v5")
    if policy_id == REVALIDATION_V7_POLICY_ID:
        return _validate_rollover_history(sources, settled_policy="v6")
    if policy_id != REVALIDATION_POLICY_ID:
        raise PeriodError("unsupported retained history policy")

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
