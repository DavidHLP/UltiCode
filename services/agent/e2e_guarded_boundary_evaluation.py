"""One-shot DAV-58 entry with a shared USD1 full-provider-envelope guard."""
import asyncio
import hashlib
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))
import e2e_boundary_evaluation as runner
from dav58_live_guard import GuardedTransport, IncrementalGuard
from deepseek_model import DeepseekModel
from model_budget import authorized_model, _authorization_slot, worst_case_micro_usd
from dav58_live_guard import ENVELOPE_MICRO_USD


def journal_path(identity):
    return _authorization_slot() / "accounting" / f"dav58-increment-{identity.identity}.json"


async def run(expected, *, resume_sha256=None):
    # Read-only provenance/budget gates precede the one-shot journal claim.
    provenance = runner._repository_provenance()
    _, budget = authorized_model(expected)
    snapshot = budget.snapshot()
    if snapshot["state"] != "active" or snapshot["sql_gate"] != "active" or snapshot["halted"]:
        raise ValueError("same period must be active before guarded execution")
    guard = IncrementalGuard(journal_path(expected), resume_sha256=resume_sha256,
                             period_identity=expected.identity, config_sha256=expected.config_sha256)
    if resume_sha256 is not None and (snapshot["attempts"] != len(guard.state["receipts"])
            or snapshot["actual_micro_usd"] != guard.state["settled_peak_micro_usd"]):
        guard.close()
        raise ValueError("canonical ledger and journal disagree")
    continuation = budget.continuation()
    if continuation:
        baseline = continuation["usage_before"]
        if (resume_sha256 is None or guard.state.get("continuation_run") is not None
                or snapshot["attempts"] != baseline["attempts"]
                or snapshot["reserved_micro_usd"] != baseline["reserved_micro_usd"]
                or snapshot["actual_micro_usd"] != baseline["actual_micro_usd"]
                or snapshot["committed_micro_usd"] + 43 * worst_case_micro_usd(24000, 2000) > 1_000_000
                or snapshot["remaining_attempts"] < 43
                or guard.state["settled_peak_micro_usd"] + 42 * worst_case_micro_usd(8000, 2000) + ENVELOPE_MICRO_USD > 1_000_000):
            guard.close()
            raise ValueError("complete continuation plan unavailable or already claimed")
        guard.continuation_start = len(guard.state["receipts"])
        guard.state["continuation_run"] = {"audit": continuation, "receipt_start": guard.continuation_start,
                                           "prompt_cap": 8000, "lane_calls": {"dav58_loop": 24, "dav58_judge": 19}}
        os.environ["DEEPSEEK_MAX_PROMPT_TOKENS"] = "8000"
        os.environ["DEEPSEEK_JUDGE_MAX_CALLS"] = "19"
    guard.state["period_identity"] = expected.identity
    guard.state["config_sha256"] = expected.config_sha256
    guard._save()
    agent_root = Path(__file__).resolve().parent
    provenance["continuation_audit"] = continuation
    provenance["execution_entry"] = "e2e_guarded_boundary_evaluation.py"
    provenance["incremental_journal"] = str(guard.path)
    for relative in ("e2e_guarded_boundary_evaluation.py", "src/dav58_live_guard.py"):
        provenance["source_sha256"][relative] = hashlib.sha256((agent_root / relative).read_bytes()).hexdigest()

    def guarded_model(*args, **kwargs):
        if "transport" in kwargs:
            raise ValueError("production guarded entry accepts no transport override")
        lane = kwargs.get("budget_purpose")
        if lane not in {"dav58_loop", "dav58_judge"}:
            raise ValueError("unexpected paid purpose")
        return DeepseekModel(*args, transport=GuardedTransport(guard, lane), **kwargs)

    original_model, original_provenance = runner.DeepseekModel, runner._repository_provenance
    runner.DeepseekModel = guarded_model
    runner._repository_provenance = lambda: provenance
    try:
        return await runner.main(expected)
    finally:
        runner.DeepseekModel, runner._repository_provenance = original_model, original_provenance
        guard.close()


def main_sync(argv=None):
    if os.environ.get(runner.OPT_IN) != "1":
        print("SKIP reason=opt_in_not_set")
        return 0
    try:
        args = list(sys.argv[1:] if argv is None else argv)
        resume_sha256 = None
        if "--resume-journal-sha256" in args:
            index = args.index("--resume-journal-sha256")
            resume_sha256 = args[index + 1]
            del args[index:index + 2]
        expected = runner._parse_identity(args)
        return asyncio.run(run(expected, resume_sha256=resume_sha256))
    except Exception as error:
        print(f"FAIL guarded_preflight={type(error).__name__}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main_sync())
