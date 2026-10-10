"""Lifecycle metadata only: runtime_accounting_connected=False, spend_limit_enforced=False.

Explicit paths and preparation are required. Active metadata grants no spending
allowance. Legacy accounting is never read; its historical usage remains UNKNOWN.
"""

from collections.abc import Mapping
from contextlib import contextmanager
from dataclasses import dataclass, field
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import uuid
from types import MappingProxyType
from budget_binding_migration import RecoverySourceEvidence, resolve_pair

POLICY_ID = "dav58-dav53-v1"
POLICY = MappingProxyType({
    "limit_micro_usd": 1_000_000,
    "attempts": 78,
    "prompt_token_cap": 24_000,
    "lanes": MappingProxyType({
        "dav58_loop": MappingProxyType({"attempts": 24, "completion_token_cap": 2000, "rounds": 4}),
        "dav58_judge": MappingProxyType({"attempts": 42, "completion_token_cap": 2000, "rounds": 1}),
        "dav53_scenarios": MappingProxyType({"attempts": 12, "completion_token_cap": 1000, "rounds": 4}),
    }),
})

# A fresh acceptance cycle retains the old liability outside its own receipts.
# The original policy and its device-bound ledger remain unchanged.
REVALIDATION_POLICY_ID = "acceptance-revalidation-v1"
REVALIDATION_HISTORY = MappingProxyType({
    "attempts": 47, "known_actual_micro_usd": 15_314,
    "unknown_attempts": 1, "unknown_encumbrance_micro_usd": 786_432,
    "cumulative_attempt_limit": 328, "cumulative_limit_micro_usd": 4_100_000,
})
REVALIDATION_POLICY = MappingProxyType({
    "limit_micro_usd": 3_298_254, "attempts": 281, "prompt_token_cap": 24_000,
    "runtime_accounting_connected": True, "spend_limit_enforced": True,
    "u04_authorized_purpose": "u04_post_demo",
    "history": REVALIDATION_HISTORY,
    "lanes": MappingProxyType({
        name: MappingProxyType({"attempts": attempts, "completion_token_cap": output,
                                "prompt_token_cap": prompt, "rounds": rounds})
        for name, attempts, prompt, output, rounds in (
            ("prior_source", 1, 24_000, 2000, 1),
            ("prior_citation_judge", 3, 24_000, 2000, 1),
            ("prior_development", 120, 24_000, 2000, 1),
            ("dav58_loop", 24, 8000, 2000, 4),
            ("dav58_judge", 19, 8000, 2000, 1),
            ("dav53_scenarios", 12, 24_000, 1000, 4),
            ("u03_analysis", 12, 24_000, 2000, 4),
            ("u03_citation_judge", 9, 24_000, 2000, 1),
            ("u04_post_demo", 81, 24_000, 2000, 4),
        )
    }),
})


# Separate immutable allowance after the first revalidation was halted.
REVALIDATION_V2_POLICY_ID = "acceptance-revalidation-v2"
REVALIDATION_V3_POLICY_ID = "acceptance-revalidation-v3"
REVALIDATION_V4_POLICY_ID = "acceptance-revalidation-v4"
REVALIDATION_V5_POLICY_ID = "acceptance-revalidation-v5"
REVALIDATION_V6_POLICY_ID = "acceptance-revalidation-v6"
REVALIDATION_V7_POLICY_ID = "acceptance-revalidation-v7"
REVALIDATION_POLICY_IDS = frozenset({REVALIDATION_POLICY_ID, REVALIDATION_V2_POLICY_ID, REVALIDATION_V3_POLICY_ID, REVALIDATION_V4_POLICY_ID, REVALIDATION_V5_POLICY_ID, REVALIDATION_V6_POLICY_ID, REVALIDATION_V7_POLICY_ID})
REVALIDATION_V2_HISTORY = MappingProxyType({
    "attempts": 164, "known_actual_micro_usd": 42_307,
    "known_committed_micro_usd": 1_138_514,
    "unknown_attempts": 1, "unknown_encumbrance_micro_usd": 786_432,
    "cumulative_attempt_limit": 412, "cumulative_limit_micro_usd": 4_870_000,
})
REVALIDATION_V2_POLICY = MappingProxyType({
    **REVALIDATION_POLICY, "limit_micro_usd": 2_945_054, "attempts": 248,
    "history": REVALIDATION_V2_HISTORY,
    "lanes": MappingProxyType({
        **REVALIDATION_POLICY["lanes"],
        "prior_development": MappingProxyType({
            **REVALIDATION_POLICY["lanes"]["prior_development"], "attempts": 87,
        }),
    }),
})

REVALIDATION_V3_HISTORY = MappingProxyType({
    "attempts": 189, "known_actual_micro_usd": 48_235,
    "known_committed_micro_usd": 1_368_914,
    "unknown_attempts": 2, "unknown_encumbrance_micro_usd": 1_572_864,
    "cumulative_attempt_limit": 430, "cumulative_limit_micro_usd": 5_820_000,
})
REVALIDATION_V3_POLICY = MappingProxyType({
    **REVALIDATION_V2_POLICY, "limit_micro_usd": 2_878_222, "attempts": 241,
    "history": REVALIDATION_V3_HISTORY,
    "lanes": MappingProxyType({
        **REVALIDATION_V2_POLICY["lanes"],
        "prior_development": MappingProxyType({
            **REVALIDATION_V2_POLICY["lanes"]["prior_development"], "attempts": 80,
        }),
    }),
})


REVALIDATION_V4_HISTORY = MappingProxyType({
    "attempts": 304, "known_actual_micro_usd": 80_719,
    "known_committed_micro_usd": 2_345_714,
    "unknown_attempts": 2, "unknown_encumbrance_micro_usd": 1_572_864,
    "cumulative_attempt_limit": 545, "cumulative_limit_micro_usd": 6_800_000,
})
REVALIDATION_V4_POLICY = MappingProxyType({
    **REVALIDATION_V3_POLICY, "limit_micro_usd": 2_881_422,
    "history": REVALIDATION_V4_HISTORY,
})


REVALIDATION_V5_HISTORY = MappingProxyType({
    "attempts": 339, "known_actual_micro_usd": 89_842,
    "known_committed_micro_usd": 2_681_714,
    "unknown_attempts": 2, "unknown_encumbrance_micro_usd": 1_572_864,
    "cumulative_attempt_limit": 580, "cumulative_limit_micro_usd": 7_140_000,
})
REVALIDATION_V5_POLICY = MappingProxyType({
    **REVALIDATION_V4_POLICY, "limit_micro_usd": 2_885_422,
    "history": REVALIDATION_V5_HISTORY,
})


REVALIDATION_V6_HISTORY = MappingProxyType({
    "attempts": 418, "known_actual_micro_usd": 110_275,
    "known_committed_micro_usd": 3_440_114,
    "unknown_attempts": 2, "unknown_encumbrance_micro_usd": 1_572_864,
    "cumulative_attempt_limit": 659, "cumulative_limit_micro_usd": 100_000_000,
})
REVALIDATION_V6_POLICY = MappingProxyType({
    **REVALIDATION_V5_POLICY, "history": REVALIDATION_V6_HISTORY,
})

REVALIDATION_V7_HISTORY = MappingProxyType({
    "attempts": 523, "known_actual_micro_usd": 138_808,
    "known_committed_micro_usd": 4_368_914,
    "unknown_attempts": 2, "unknown_encumbrance_micro_usd": 1_572_864,
    "cumulative_attempt_limit": 764, "cumulative_limit_micro_usd": 100_000_000,
})
REVALIDATION_V7_POLICY = MappingProxyType({
    **REVALIDATION_V6_POLICY, "history": REVALIDATION_V7_HISTORY,
})


def policy_for(policy_id: str):
    if policy_id == POLICY_ID:
        return POLICY
    if policy_id == REVALIDATION_POLICY_ID:
        return REVALIDATION_POLICY
    if policy_id == REVALIDATION_V2_POLICY_ID:
        return REVALIDATION_V2_POLICY
    if policy_id == REVALIDATION_V3_POLICY_ID:
        return REVALIDATION_V3_POLICY
    if policy_id == REVALIDATION_V4_POLICY_ID:
        return REVALIDATION_V4_POLICY
    if policy_id == REVALIDATION_V5_POLICY_ID:
        return REVALIDATION_V5_POLICY
    if policy_id == REVALIDATION_V6_POLICY_ID:
        return REVALIDATION_V6_POLICY
    if policy_id == REVALIDATION_V7_POLICY_ID:
        return REVALIDATION_V7_POLICY
    raise PeriodError("unsupported policy")


class PeriodError(ValueError):
    pass


@dataclass(frozen=True)
class PeriodIdentity:
    period_id: str
    config_sha256: str
    identity: str
    policy_id: str = POLICY_ID


@dataclass(frozen=True)
class PeriodSnapshot:
    """Metadata only: runtime_accounting_connected=False, spend_limit_enforced=False."""

    identity: PeriodIdentity
    state: str
    legacy_history: str = "UNKNOWN"
    runtime_accounting_connected: bool = False
    spend_limit_enforced: bool = False


def _validate(period_id: str, config_sha256: str, policy_id: str) -> None:
    if not isinstance(period_id, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", period_id):
        raise PeriodError("invalid period ID")
    if not isinstance(config_sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", config_sha256):
        raise PeriodError("config must be a lower-case SHA256")
    policy_for(policy_id)


@contextmanager
def _parent(path: Path):
    # Walk with dirfds: neither parent components nor final files may be symlinks.
    if not path.is_absolute() or ".." in path.parts or path.name in ("", ".") or path == Path("/"):
        raise PeriodError("explicit absolute period directory required")
    fd = os.open("/", os.O_RDONLY | os.O_DIRECTORY)
    try:
        for part in path.parts[1:-1]:
            next_fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = next_fd
        yield fd
    finally:
        os.close(fd)


def _file(directory: int, name: str, flags: int) -> int:
    fd = os.open(name, flags | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600, dir_fd=directory)
    if not stat.S_ISREG(os.fstat(fd).st_mode) or os.fstat(fd).st_nlink != 1:
        os.close(fd)
        raise PeriodError("regular, unlinked-to-other-paths metadata required")
    return fd


def _write(fd: int, value: dict) -> None:
    data = (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()
    while data:
        size = os.write(fd, data)
        if size <= 0:
            raise PeriodError("incomplete metadata write")
        data = data[size:]
    os.fsync(fd)


def _read(fd: int) -> list[dict]:
    os.lseek(fd, 0, os.SEEK_SET)
    with os.fdopen(os.dup(fd), "rb") as stream:
        data = stream.read(16_385)
    if not data or len(data) > 16_384 or not data.endswith(b"\n"):
        raise PeriodError("missing or incomplete metadata")
    try:
        return [json.loads(line) for line in data.splitlines()]
    except (ValueError, UnicodeError) as exc:
        raise PeriodError("invalid metadata") from exc


def prepare_period(path: Path, period_id: str, config_sha256: str, *, policy_id: str = POLICY_ID) -> PeriodSnapshot:
    """Prepare only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    _validate(period_id, config_sha256, policy_id)
    path = Path(path)
    identity = PeriodIdentity(period_id, config_sha256, uuid.uuid4().hex, policy_id)
    try:
        with _parent(path) as parent:
            # Retain this one-shot tombstone even if a later write fails.
            os.mkdir(path.name, 0o700, dir_fd=parent)
            directory = os.open(path.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
            try:
                state = _file(directory, "state.jsonl", os.O_RDWR | os.O_CREAT | os.O_EXCL)
                try:
                    _write(state, {"state": "prepared"})
                    marker = _file(directory, "identity.json", os.O_WRONLY | os.O_CREAT | os.O_EXCL)
                    try:
                        _write(marker, {
                            **identity.__dict__, "path": str(path),
                            "directory": [os.fstat(directory).st_dev, os.fstat(directory).st_ino],
                            "state_file": [os.fstat(state).st_dev, os.fstat(state).st_ino],
                        })
                    finally:
                        os.close(marker)
                    os.fsync(directory)
                    os.fsync(parent)
                finally:
                    os.close(state)
            finally:
                os.close(directory)
    except OSError as exc:
        raise PeriodError("period cannot be prepared; existing directories are never reset") from exc
    return PeriodSnapshot(identity, "prepared")


@dataclass
class _LockedPeriod:
    snapshot: PeriodSnapshot
    state_fd: int
    directory_fd: int
    parent_fd: int
    path: Path

    def confirm(self) -> None:
        with _parent(self.path) as parent:
            if (os.fstat(parent).st_dev, os.fstat(parent).st_ino) != (os.fstat(self.parent_fd).st_dev, os.fstat(self.parent_fd).st_ino):
                raise PeriodError("period parent path replaced")
        if os.stat("state.jsonl", dir_fd=self.directory_fd, follow_symlinks=False) != os.fstat(self.state_fd):
            raise PeriodError("state file replaced")
        current = os.stat(self.path.name, dir_fd=self.parent_fd, follow_symlinks=False)
        pinned = os.fstat(self.directory_fd)
        if (current.st_dev, current.st_ino) != (pinned.st_dev, pinned.st_ino):
            raise PeriodError("period directory replaced")
        os.fsync(self.state_fd)

    def append(self, target: str) -> None:
        if (self.snapshot.state, target) not in (
            ("prepared", "active"), ("prepared", "halted"), ("active", "halted"),
        ):
            raise PeriodError("invalid lifecycle transition")
        self.confirm()
        os.lseek(self.state_fd, 0, os.SEEK_END)
        _write(self.state_fd, {"state": target})
        self.snapshot = PeriodSnapshot(self.snapshot.identity, target)


@contextmanager
def _locked_period(path: Path, expected: PeriodIdentity, *, exclusive: bool = True):
    if not isinstance(expected, PeriodIdentity):
        raise PeriodError("expected identity required")
    _validate(expected.period_id, expected.config_sha256, expected.policy_id)
    if not isinstance(expected.identity, str) or not re.fullmatch(r"[0-9a-f]{32}", expected.identity):
        raise PeriodError("invalid identity")
    path = Path(path)
    try:
        with _parent(path) as parent:
            directory = os.open(path.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
            try:
                state = _file(directory, "state.jsonl", os.O_RDWR if exclusive else os.O_RDONLY)
                try:
                    fcntl.flock(state, fcntl.LOCK_EX if exclusive else fcntl.LOCK_SH)
                    marker = _file(directory, "identity.json", os.O_RDONLY)
                    try:
                        records = _read(marker)
                    finally:
                        os.close(marker)
                    if len(records) != 1 or not isinstance(records[0], dict):
                        raise PeriodError("invalid period identity metadata")
                    required = {
                        **expected.__dict__, "path": str(path),
                        "directory": resolve_pair(path.parent, expected.__dict__, "period_directory", records[0].get("directory"), [os.fstat(directory).st_dev, os.fstat(directory).st_ino]),
                        "state_file": resolve_pair(path.parent, expected.__dict__, "state_file", records[0].get("state_file"), [os.fstat(state).st_dev, os.fstat(state).st_ino]),
                    }
                    if records != [required]:
                        raise PeriodError("period identity, config, policy or path drift")
                    states = _read(state)
                    valid = [[{"state": s} for s in history] for history in (
                        ("prepared",), ("prepared", "active"),
                        ("prepared", "halted"), ("prepared", "active", "halted"),
                    )]
                    if states not in valid:
                        raise PeriodError("invalid lifecycle history")
                    yield _LockedPeriod(PeriodSnapshot(expected, states[-1]["state"]), state, directory, parent, path)
                finally:
                    os.close(state)
            finally:
                os.close(directory)
    except OSError as exc:
        raise PeriodError("period metadata unavailable; no files created or repaired") from exc


def _access(path: Path, expected: PeriodIdentity, target: str | None) -> PeriodSnapshot:
    with _locked_period(path, expected, exclusive=target is not None) as locked:
        if target:
            locked.append(target)
        return locked.snapshot


def read_period(path: Path, expected: PeriodIdentity) -> PeriodSnapshot:
    """Read only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    return _access(path, expected, None)


def activate_period(path: Path, expected: PeriodIdentity) -> PeriodSnapshot:
    """Activate metadata only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    return _access(path, expected, "active")


def halt_period(path: Path, expected: PeriodIdentity) -> PeriodSnapshot:
    """Halt metadata only; runtime_accounting_connected=False, spend_limit_enforced=False."""
    return _access(path, expected, "halted")


# Conditional offline recovery plan. Explicit inputs only: no ledger is read,
# nothing is written or applied and no paid authorization is granted. Every
# total is re-derived, so missing lanes, pricing/envelope drift or an approval
# below the computed peak fail closed. The recorded unknown exposure is carried
# as an UNKNOWN legacy encumbrance, never treated as settled spend.
RECOVERY_PURPOSES = ("dav58_loop", "dav58_judge", "dav53_scenarios",
                     "u03_analysis", "u03_citation_judge", "u04_post_demo")
RECOVERY_PRICING_KEYS = ("input_tenths_micro_usd_per_token", "output_tenths_micro_usd_per_token")
RECOVERY_ENVELOPE_KEYS = ("input_context_token_cap", "output_token_cap", "envelope_micro_usd",
                          "unknown_pending_micro_usd", "legacy_unknown_encumbrance_micro_usd")
_RECOVERY_LANE_KEYS = ("attempts", "prompt_token_cap", "completion_token_cap")
_TENTHS_PER_MICRO_USD = 10
# Finite ceiling for every declared integer: JSON-exact, so magnitudes are
# bounded before multiplication instead of merely type-checked.
_MAX_RECOVERY_MAGNITUDE = 2 ** 53
# Reviewed published maxima. The guard derives its per-attempt envelope from the
# same caps, so the plan pins them instead of accepting arbitrary token maxima
# that would silently shrink the reserved envelope and the computed peak.
RECOVERY_CONTEXT_TOKEN_CAP = 1_048_576
RECOVERY_OUTPUT_TOKEN_CAP = 393_216
# Private mint token: a plan is only produced by compile_recovery_plan below.
_RECOVERY_PLAN_TOKEN = object()


@dataclass(frozen=True)
class RecoveryPlan:
    """Conditional plan only: paid_authorized=False and runtime_applied=False."""

    approved_attempts: int
    approved_limit_micro_usd: int
    future_attempts: int
    purpose_caps: Mapping
    u04_demo_attempts: int
    u04_post_demo_attempts: int
    u04_total_attempts: int
    pricing: Mapping
    envelopes: Mapping
    cost_components_micro_usd: Mapping
    peak_micro_usd: int
    remaining_micro_usd: int
    legacy_encumbrance_status: str
    legacy_encumbrance_micro_usd: int
    source_sha256_by_ledger: Mapping
    source_provenance_by_ledger: Mapping
    paid_authorized: bool = False
    runtime_applied: bool = False
    _origin: object = field(default=None, repr=False, compare=False)

    def __post_init__(self):
        # Only compile_recovery_plan may mint a plan; a hand-built instance would
        # otherwise bypass the re-derived caps, pricing and approval checks.
        if self._origin is not _RECOVERY_PLAN_TOKEN:
            raise ValueError("recovery plan must come from compile_recovery_plan")


def _recovery_int(value, name):
    if type(value) is not int or not 0 <= value <= _MAX_RECOVERY_MAGNITUDE:
        raise PeriodError(f"{name} must be a bounded non-negative integer")
    return value


def _recovery_declared(value, keys, name):
    if not isinstance(value, dict) or set(value) != set(keys):
        raise PeriodError(f"{name} must declare exactly {sorted(keys)}")
    return MappingProxyType(dict(value))


def _micro_usd(input_tokens, output_tokens, pricing):
    # The same ceiling the guard applies: a partial tenth of a micro-USD is never
    # free, so reservations and the per-call envelope never round down.
    return (input_tokens * pricing["input_tenths_micro_usd_per_token"]
            + output_tokens * pricing["output_tenths_micro_usd_per_token"] + 9) // _TENTHS_PER_MICRO_USD


def compile_recovery_plan(*, source_evidence, approval, purpose_caps, pricing, envelopes, stage_caps):
    """Compile an immutable conditional recovery plan from explicit inputs only.

    Offline and side-effect free: it reads no ledger, writes nothing, applies
    nothing at runtime and grants no paid authorization. Approval terms, purpose
    caps, pricing and envelopes are explicit required inputs; every total, peak
    and remainder is re-derived from them, and missing lanes, pricing/envelope
    drift or an approval below the computed peak fail closed.
    Caller-declared lane caps are hypothetical, not runtime policy. This result
    must not be consumed as an authorization or device-migration grant: a runtime
    must separately bind and enforce the exact policy, provenance and approval.
    """
    if not isinstance(source_evidence, RecoverySourceEvidence):
        raise PeriodError("validated recovery source evidence required")
    recorded_attempts = _recovery_int(source_evidence.historical_attempts, "recorded attempts")
    recorded_actual = _recovery_int(source_evidence.known_actual_micro_usd, "recorded known actual")
    recorded_pending = _recovery_int(source_evidence.pending_micro_usd, "recorded unknown exposure")
    if not isinstance(approval, dict) or set(approval) != {"cumulative_attempts", "limit_micro_usd"}:
        raise PeriodError("explicit approval terms required")
    approved_attempts = _recovery_int(approval["cumulative_attempts"], "approved attempts")
    approved_limit_micro_usd = _recovery_int(approval["limit_micro_usd"], "approved limit")
    price = _recovery_declared(pricing, RECOVERY_PRICING_KEYS, "pricing")
    for key in RECOVERY_PRICING_KEYS:
        if _recovery_int(price[key], key) <= 0:
            raise PeriodError("pricing must be positive")
    pool = _recovery_declared(envelopes, RECOVERY_ENVELOPE_KEYS, "envelopes")
    for key in RECOVERY_ENVELOPE_KEYS:
        _recovery_int(pool[key], key)
    if (pool["input_context_token_cap"] != RECOVERY_CONTEXT_TOKEN_CAP
            or pool["output_token_cap"] != RECOVERY_OUTPUT_TOKEN_CAP):
        raise PeriodError("envelope caps must be the reviewed published maxima")
    if pool["envelope_micro_usd"] != _micro_usd(
            pool["input_context_token_cap"], pool["output_token_cap"], price):
        raise PeriodError("declared envelope disagrees with its caps and pricing")
    # unknown_pending_micro_usd is a validation input only: it must cover the
    # recorded exposure but is not a cost component. The U component is the
    # legacy unknown encumbrance and the E component is the guard per-call
    # envelope; the two are distinct inputs that merely coincide in some data.
    if pool["unknown_pending_micro_usd"] < recorded_pending:
        raise PeriodError("declared unknown pending understates the recorded exposure")
    if pool["legacy_unknown_encumbrance_micro_usd"] < recorded_pending:
        raise PeriodError("legacy unknown encumbrance understates the recorded exposure")
    if not isinstance(purpose_caps, dict) or set(purpose_caps) != set(RECOVERY_PURPOSES):
        raise PeriodError("every recovery purpose lane needs explicit caps")
    caps = {}
    for name in RECOVERY_PURPOSES:
        lane = purpose_caps[name]
        if not isinstance(lane, dict) or set(lane) != set(_RECOVERY_LANE_KEYS):
            raise PeriodError("purpose lane must declare attempts and both token caps")
        attempts = _recovery_int(lane["attempts"], "lane attempts")
        prompt_cap = _recovery_int(lane["prompt_token_cap"], "lane prompt cap")
        completion_cap = _recovery_int(lane["completion_token_cap"], "lane completion cap")
        if (attempts < 1 or not 1 <= prompt_cap <= pool["input_context_token_cap"]
                or not 1 <= completion_cap <= pool["output_token_cap"]):
            raise PeriodError("purpose lane caps outside the declared envelope")
        caps[name] = MappingProxyType({"attempts": attempts, "prompt_token_cap": prompt_cap,
                                       "completion_token_cap": completion_cap})
    if not isinstance(stage_caps, dict) or set(stage_caps) != {"u04_demo_attempts", "u04_total_attempts"}:
        raise PeriodError("explicit u04 stage caps required")
    demo_attempts = _recovery_int(stage_caps["u04_demo_attempts"], "u04 demo attempts")
    total_attempts = _recovery_int(stage_caps["u04_total_attempts"], "u04 total attempts")
    future_attempts = sum(cap["attempts"] for cap in caps.values())
    if total_attempts != demo_attempts + caps["u04_post_demo"]["attempts"]:
        raise PeriodError("u04 stage caps disagree with the purpose lanes")
    if approved_attempts < recorded_attempts + future_attempts:
        raise PeriodError("approval does not cover the recorded and future attempts")
    reserved_micro_usd = sum(
        cap["attempts"] * _micro_usd(cap["prompt_token_cap"], cap["completion_token_cap"], price)
        for cap in caps.values())
    largest_reservation_micro_usd = max(
        _micro_usd(cap["prompt_token_cap"], cap["completion_token_cap"], price)
        for cap in caps.values())
    smallest_reservation_micro_usd = min(
        _micro_usd(cap["prompt_token_cap"], cap["completion_token_cap"], price)
        for cap in caps.values())
    components = {
        "A": recorded_actual,
        "U": pool["legacy_unknown_encumbrance_micro_usd"],
        "R": reserved_micro_usd,
        "cmin": smallest_reservation_micro_usd,
        "cmax": largest_reservation_micro_usd,
        "E": pool["envelope_micro_usd"],
    }
    # Calls may settle in any order, so the one reservation still outstanding at
    # the peak may be the cheapest call, not the dearest: only cmin is exempt
    # from the peak stack. Subtracting cmax would understate the peak.
    peak_micro_usd = (components["A"] + components["U"]
                      + (components["R"] - components["cmin"]) + components["E"])
    if approved_limit_micro_usd < peak_micro_usd:
        raise PeriodError("approved limit is below the computed recovery peak")
    return RecoveryPlan(
        approved_attempts=approved_attempts,
        approved_limit_micro_usd=approved_limit_micro_usd,
        future_attempts=future_attempts,
        purpose_caps=MappingProxyType(caps),
        u04_demo_attempts=demo_attempts,
        u04_post_demo_attempts=caps["u04_post_demo"]["attempts"],
        u04_total_attempts=total_attempts,
        pricing=price,
        envelopes=pool,
        cost_components_micro_usd=MappingProxyType(components),
        peak_micro_usd=peak_micro_usd,
        remaining_micro_usd=approved_limit_micro_usd - peak_micro_usd,
        legacy_encumbrance_status="UNKNOWN",
        legacy_encumbrance_micro_usd=pool["legacy_unknown_encumbrance_micro_usd"],
        source_sha256_by_ledger=source_evidence.source_sha256_by_ledger,
        source_provenance_by_ledger=source_evidence.source_provenance_by_ledger,
        _origin=_RECOVERY_PLAN_TOKEN,
    )
