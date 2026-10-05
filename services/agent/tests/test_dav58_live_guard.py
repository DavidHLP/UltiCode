import asyncio
import json

import httpx
import pytest

from deepseek_model import DeepseekModel, ModelBudgetExceeded
from dav58_live_guard import ENVELOPE_MICRO_USD, IncrementalGuard, GuardedTransport


def receipt(**overrides):
    payload = {"model": "deepseek-v4.1-flash", "usage": {"prompt_tokens": 100, "completion_tokens": 20, "total_tokens": 120},
               "choices": [{"finish_reason": "stop", "message": {"content": '{"answer":"ok"}'}}]}
    payload.update(overrides)
    return payload


def models(tmp_path, handler):
    guard = IncrementalGuard(tmp_path / "increment.json")
    loop = DeepseekModel("dummy", tool_specs={}, model="deepseek-flash", max_tokens=2000, thinking_type="disabled",
                         transport=GuardedTransport(guard, "dav58_loop", httpx.MockTransport(handler)))
    judge = DeepseekModel("dummy", tool_specs={}, model="deepseek-flash", max_tokens=2000, thinking_type="disabled",
                          transport=GuardedTransport(guard, "dav58_judge", httpx.MockTransport(handler)))
    return guard, loop, judge


def test_full_provider_envelope_avoids_framing_estimate(tmp_path):
    guard, loop, judge = models(tmp_path, lambda request: httpx.Response(200, json=receipt()))
    assert ENVELOPE_MICRO_USD == 786432
    async def run():
        await loop.decide([{"role": "user", "content": "汉字😀"}])
        await judge.decide([{"role": "user", "content": "probe"}])
    asyncio.run(run())
    assert guard.state["settled_peak_micro_usd"] == 108
    assert guard.state["pending_micro_usd"] == 0
    assert [r["lane"] for r in guard.state["receipts"]] == ["dav58_loop", "dav58_judge"]
    assert all(r["reserved_micro_usd"] == 786432 for r in guard.state["receipts"])
    assert json.loads(guard.path.read_text()) == guard.state


@pytest.mark.parametrize("usage", [None, {}, {"prompt_tokens": 1, "completion_tokens": 2},
    {"prompt_tokens": True, "completion_tokens": 2, "total_tokens": 3},
    {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 4},
    {"prompt_tokens": 24001, "completion_tokens": 2, "total_tokens": 24003},
    {"prompt_tokens": 1, "completion_tokens": 2001, "total_tokens": 2002},
    {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3, "completion_tokens_details": {"reasoning_tokens": 1}}])
def test_unknown_usage_stops_all_lanes_and_probe_without_retry(tmp_path, usage):
    requests = []
    def handler(request):
        requests.append(request)
        return httpx.Response(200, json=receipt(usage=usage))
    guard, loop, judge = models(tmp_path, handler)
    async def run():
        for model in (loop, judge, loop, judge):
            with pytest.raises(ModelBudgetExceeded):
                await model.decide([{"role": "user", "content": "case or paid probe"}])
    asyncio.run(run())
    assert len(requests) == 1
    assert guard.state["halted"] is True
    assert guard.state["pending_micro_usd"] == 786432


@pytest.mark.parametrize("failure", ["http", "invalid_json", "network", "reasoning", "model"])
def test_failure_retains_full_envelope_and_blocks_other_adapter(tmp_path, failure):
    requests = []
    def handler(request):
        requests.append(request)
        if failure == "network": raise httpx.ConnectError("offline injected failure")
        if failure == "http": return httpx.Response(503)
        if failure == "invalid_json": return httpx.Response(200, content=b"invalid")
        if failure == "model": return httpx.Response(200, json=receipt(model="unknown"))
        return httpx.Response(200, json=receipt(choices=[{"message": {"content": "ok", "reasoning_content": "unexpected"}}]))
    guard, loop, judge = models(tmp_path, handler)
    async def run():
        with pytest.raises(ModelBudgetExceeded): await loop.decide([{"role": "user", "content": "case"}])
        with pytest.raises(ModelBudgetExceeded): await judge.decide([{"role": "user", "content": "negative probe"}])
    asyncio.run(run())
    assert len(requests) == 1
    assert guard.state["pending_micro_usd"] == 786432


def test_total_increment_ceiling_checks_before_transport(tmp_path):
    requests = []
    guard, loop, judge = models(tmp_path, lambda request: requests.append(request) or httpx.Response(200, json=receipt()))
    guard.state["settled_peak_micro_usd"] = 213569
    async def run():
        with pytest.raises(ModelBudgetExceeded): await loop.decide([{"role": "user", "content": "case"}])
        with pytest.raises(ModelBudgetExceeded): await judge.decide([{"role": "user", "content": "probe"}])
    asyncio.run(run())
    assert requests == []


@pytest.mark.parametrize("changes", [{"model": "deepseek-v4-pro"}, {"thinking": {"type": "enabled"}},
    {"max_tokens": 2001}, {"stream": True}, {"tools": []}])
def test_unexpected_paid_payload_rejected_before_send(tmp_path, changes):
    guard = IncrementalGuard(tmp_path / "increment.json")
    body = {"model": "deepseek-flash", "messages": [{"role": "user", "content": "x"}], "max_tokens": 2000,
            "temperature": 0, "thinking": {"type": "disabled"}}
    body.update(changes)
    with pytest.raises(ModelBudgetExceeded):
        guard.begin(httpx.Request("POST", "https://api.deepseek.com/chat/completions", json=body), "dav58_loop")
    assert guard.state["receipts"] == []


def test_existing_journal_cannot_reset(tmp_path):
    path = tmp_path / "increment.json"
    IncrementalGuard(path)
    with pytest.raises(FileExistsError): IncrementalGuard(path)


@pytest.mark.parametrize("phase", ["before_send", "after_response"])
def test_persistence_failure_latches_all_lanes(tmp_path, monkeypatch, phase):
    requests = []
    guard, loop, judge = models(tmp_path, lambda request: requests.append(request) or httpx.Response(200, json=receipt()))
    save = guard._save
    count = 0
    def failing_save():
        nonlocal count
        count += 1
        if count == (1 if phase == "before_send" else 2): raise OSError("offline disk failure")
        save()
    monkeypatch.setattr(guard, "_save", failing_save)
    async def run():
        with pytest.raises(ModelBudgetExceeded): await loop.decide([{"role": "user", "content": "case"}])
        with pytest.raises(ModelBudgetExceeded): await judge.decide([{"role": "user", "content": "probe"}])
    asyncio.run(run())
    assert len(requests) == (0 if phase == "before_send" else 1)
    assert guard.state["halted"] is True


@pytest.mark.parametrize("unknown", [False, True])
def test_guard_does_not_refund_original_period_reservations(tmp_path, monkeypatch, unknown):
    import model_budget as accounting
    import authorized_budget_period as period
    slot = tmp_path / "slot"
    slot.mkdir()
    monkeypatch.setattr(accounting, "_authorization_slot", lambda: slot)
    identity = period.prepare_period(slot / "period", "offline-test", accounting.authorized_period_config_sha256()).identity
    budget = accounting.ModelBudget.bind_prepared(identity)
    budget.activate()  # isolated offline test period only
    guard, loop, judge = models(tmp_path, lambda request: httpx.Response(200, json=receipt(usage=None) if unknown else receipt()))
    loop._budget = judge._budget = budget
    loop._budget_purpose, judge._budget_purpose = "dav58_loop", "dav58_judge"
    async def run():
        for model in (loop, judge):
            if unknown:
                with pytest.raises(ModelBudgetExceeded): await model.decide([{"role": "user", "content": "case/probe"}])
            else:
                await model.decide([{"role": "user", "content": "case/probe"}])
    asyncio.run(run())
    # Existing adapter reserves before transport; even a guard-rejected call is
    # kept conservatively. The independent envelope never refunds this ledger.
    # A first call whose usage never came back leaves an unknown attempt, so the
    # bound ledger refuses to reserve for the second lane at all.
    assert budget.snapshot()["reserved_micro_usd"] == (9600 if unknown else 19200)
    assert guard.state["settled_peak_micro_usd"] == (0 if unknown else 108)
    assert len(guard.state["receipts"]) == (1 if unknown else 2)


def settled_journal(tmp_path):
    import hashlib
    guard, loop, _ = models(tmp_path, lambda request: httpx.Response(200, json=receipt()))
    guard.state.update(period_identity='same-identity', config_sha256='same-config')
    asyncio.run(loop.decide([{'role': 'user', 'content': 'prior'}]))
    path = guard.path
    prior = json.loads(path.read_text())
    guard.close()
    return path, prior, hashlib.sha256(path.read_bytes()).hexdigest()


def test_explicit_resume_preserves_receipts_and_accumulates(tmp_path):
    path, prior, sha = settled_journal(tmp_path)
    resumed = IncrementalGuard(path, resume_sha256=sha, period_identity='same-identity', config_sha256='same-config')
    model = DeepseekModel('dummy', tool_specs={}, model='deepseek-flash', max_tokens=2000, thinking_type='disabled',
                         transport=GuardedTransport(resumed, 'dav58_judge', httpx.MockTransport(lambda r: httpx.Response(200, json=receipt()))))
    asyncio.run(model.decide([{'role': 'user', 'content': 'new'}]))
    assert resumed.state['receipts'][:1] == prior['receipts']
    assert resumed.state['settled_peak_micro_usd'] == 108
    assert resumed.state['pending_micro_usd'] == 0
    resumed.close()


@pytest.mark.parametrize('damage', ['pending', 'halted', 'identity', 'config', 'model', 'cost', 'tokens', 'policy', 'hash'])
def test_resume_rejects_unknown_and_corrupted_history(tmp_path, damage):
    import hashlib
    path, saved, sha = settled_journal(tmp_path)
    if damage == 'pending': saved['pending_micro_usd'] = 786432
    if damage == 'halted': saved['halted'] = True
    if damage == 'identity': saved['period_identity'] = 'other'
    if damage == 'config': saved['config_sha256'] = 'other'
    if damage == 'model': saved['receipts'][0]['request_model'] = 'other'
    if damage == 'cost': saved['settled_peak_micro_usd'] = 0
    if damage == 'tokens': saved['receipts'][0]['total_tokens'] += 1
    if damage == 'policy': saved['policy'] = {'model': 'other'}
    path.write_text(json.dumps(saved))
    if damage != 'hash': sha = hashlib.sha256(path.read_bytes()).hexdigest()
    before = path.read_bytes()
    with pytest.raises(ValueError):
        IncrementalGuard(path, resume_sha256=sha, period_identity='same-identity', config_sha256='same-config')
    assert path.read_bytes() == before


def test_resume_accepts_uppercase_response_model_without_rewriting_receipt(tmp_path):
    path, saved, _ = settled_journal(tmp_path)
    saved["receipts"][0]["response_model"] = "DEEPSEEK-V4.1-FLASH"
    path.write_text(json.dumps(saved) + "\n")
    before = path.read_bytes()
    import hashlib
    resumed = IncrementalGuard(
        path,
        resume_sha256=hashlib.sha256(before).hexdigest(),
        period_identity="same-identity",
        config_sha256="same-config",
    )
    assert resumed.state["receipts"][0]["response_model"] == "DEEPSEEK-V4.1-FLASH"
    assert path.read_bytes() == before
    resumed.close()


def test_concurrent_owner_cannot_resume(tmp_path):
    path, _, sha = settled_journal(tmp_path)
    guard = IncrementalGuard(path, resume_sha256=sha, period_identity='same-identity', config_sha256='same-config')
    with pytest.raises(BlockingIOError):
        IncrementalGuard(path, resume_sha256=sha, period_identity='same-identity', config_sha256='same-config')
    guard.close()



def test_continuation_guard_rejects_over_cap_usage_retains_envelope(tmp_path):
    guard, loop, judge = models(tmp_path, lambda request: httpx.Response(200, json=receipt(
        usage={"prompt_tokens": 8001, "completion_tokens": 20, "total_tokens": 8021})))
    guard.continuation_start = 0
    async def run():
        with pytest.raises(ModelBudgetExceeded): await loop.decide([{"role": "user", "content": "case"}])
        with pytest.raises(ModelBudgetExceeded): await judge.decide([{"role": "user", "content": "probe"}])
    asyncio.run(run())
    assert len(guard.state["receipts"]) == 1
    assert guard.state["pending_micro_usd"] == ENVELOPE_MICRO_USD
    assert guard.state["halted"] is True


def test_continuation_guard_enforces_shared_lane_ceiling_before_send(tmp_path):
    requests = []
    def handler(request):
        requests.append(request)
        return httpx.Response(200, json=receipt())
    guard, loop, judge = models(tmp_path, handler)
    judge._max_calls = 25
    guard.continuation_start = 0
    async def run():
        for _ in range(19): await judge.decide([{"role": "user", "content": "probe"}])
        with pytest.raises(ModelBudgetExceeded): await judge.decide([{"role": "user", "content": "extra"}])
        await loop.decide([{"role": "user", "content": "case"}])
    asyncio.run(run())
    assert len(requests) == 20
    assert guard.state["pending_micro_usd"] == 0
