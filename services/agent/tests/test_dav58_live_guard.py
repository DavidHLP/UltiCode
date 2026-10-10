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


@pytest.mark.parametrize("identity,config", [("same-identity", None), (None, "same-config"),
    ("", "same-config"), ("same-identity", "")])
def test_fresh_guard_rejects_partial_binding_before_writing(tmp_path, identity, config):
    path = tmp_path / "increment.json"
    with pytest.raises(ValueError):
        IncrementalGuard(path, period_identity=identity, config_sha256=config)
    assert not path.exists()
    assert not path.with_suffix(".json.lock").exists()


def test_fresh_dav53_binding_survives_close_and_resume(tmp_path):
    import hashlib
    path = tmp_path / "increment.json"
    guard = IncrementalGuard(path, period_identity="same-identity", config_sha256="same-config")
    initial = json.loads(path.read_text())
    assert initial["period_identity"] == "same-identity"
    assert initial["config_sha256"] == "same-config"
    async def call(current, content):
        model = DeepseekModel("dummy", tool_specs={}, model="deepseek-flash", max_tokens=1000,
                              thinking_type="disabled", transport=GuardedTransport(
                                  current, "dav53_scenarios",
                                  httpx.MockTransport(lambda r: httpx.Response(200, json=receipt()))))
        async with model:
            await model.decide([{"role": "user", "content": content}])
    try:
        asyncio.run(call(guard, "first"))
    finally:
        guard.close()
    before = path.read_bytes()
    prior = json.loads(before)
    sha = hashlib.sha256(before).hexdigest()
    with pytest.raises(ValueError):
        IncrementalGuard(path, resume_sha256=sha, period_identity="wrong-identity", config_sha256="same-config")
    assert path.read_bytes() == before
    resumed = IncrementalGuard(path, resume_sha256=sha, period_identity="same-identity", config_sha256="same-config")
    try:
        asyncio.run(call(resumed, "second"))
        assert resumed.state["receipts"][:1] == prior["receipts"]
        assert len(resumed.state["receipts"]) == 2
        assert all(r["lane"] == "dav53_scenarios" for r in resumed.state["receipts"])
        assert prior["settled_peak_micro_usd"] == 54
        assert resumed.state["settled_peak_micro_usd"] == 108
        assert resumed.state["pending_micro_usd"] == 0
        assert json.loads(path.read_text()) == resumed.state
    finally:
        resumed.close()


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


@pytest.mark.parametrize("error,expected", [
    (httpx.ConnectError("private-token private-request"), "ConnectError"),
    (httpx.ReadTimeout("private-token private-request"), "ReadTimeout"),
    (RuntimeError("private-token private-request"), "transport_failure"),
    (type("private_token", (httpx.ConnectError,), {})("private-token private-request"), "transport_failure"),
])
def test_network_failure_records_safe_type_without_message_or_retry(tmp_path, error, expected):
    requests = []
    def handler(request):
        requests.append(request)
        raise error
    guard, loop, judge = models(tmp_path, handler)
    try:
        async def run():
            for model in (loop, judge):
                with pytest.raises(ModelBudgetExceeded) as failure:
                    await model.decide([{"role": "user", "content": "case"}])
                assert "private-token" not in str(failure.value)
        asyncio.run(run())
        saved = json.loads(guard.path.read_text())
        assert len(requests) == len(saved["receipts"]) == 1
        assert saved["receipts"][0]["network_error_class"] == expected
        assert saved["receipts"][0]["reason"] == "network_or_read_failure"
        assert saved["receipts"][0]["status"] == "unknown_or_unsafe"
        assert saved["halted"] is True
        assert saved["pending_micro_usd"] == ENVELOPE_MICRO_USD
        assert "private-token" not in guard.path.read_text()
        assert "private-request" not in guard.path.read_text()
    finally:
        guard.close()


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


@pytest.mark.parametrize("lane", ["u03", "u04", "u03_analysis", "u04_demo"])
def test_unknown_product_lane_has_zero_http(tmp_path, lane):
    requests = []
    guard = IncrementalGuard(tmp_path / "increment.json")
    model = DeepseekModel("dummy", tool_specs={}, model="deepseek-flash", max_tokens=1000,
                          thinking_type="disabled", transport=GuardedTransport(
                              guard, lane, httpx.MockTransport(lambda r: requests.append(r))))
    try:
        with pytest.raises(ModelBudgetExceeded):
            asyncio.run(model.decide([{"role": "user", "content": "synthetic only"}]))
        assert requests == []
        assert guard.state["receipts"] == []
        assert guard.state["pending_micro_usd"] == 0
    finally:
        guard.close()


@pytest.mark.parametrize("lane", ["dav58_loop", "dav58_judge", "dav53_scenarios"])
def test_current_lane_caps_match_trusted_declaration(tmp_path, lane):
    from authorized_budget_period import POLICY
    guard = IncrementalGuard(tmp_path / "increment.json")
    cap = POLICY["lanes"][lane]["completion_token_cap"]
    body = {"model": "deepseek-flash", "messages": [{"role": "user", "content": "x"}],
            "max_tokens": cap, "temperature": 0, "thinking": {"type": "disabled"}}
    try:
        body["max_tokens"] = cap + 1
        with pytest.raises(ModelBudgetExceeded):
            guard.begin(httpx.Request("POST", "https://api.deepseek.com/chat/completions", json=body), lane)
        body["max_tokens"] = cap
        reserved = guard.begin(httpx.Request("POST", "https://api.deepseek.com/chat/completions", json=body), lane)
        assert reserved["completion_token_cap"] == cap
        assert reserved["prompt_token_cap"] == POLICY["prompt_token_cap"]
        guard.finish(reserved, httpx.Response(200, json=receipt(usage={
            "prompt_tokens": POLICY["prompt_token_cap"], "completion_tokens": cap,
            "total_tokens": POLICY["prompt_token_cap"] + cap})))
        assert reserved["status"] == "settled"
    finally:
        guard.close()


@pytest.mark.parametrize("prompt,completion", [(321, 123), (True, 123), (321, True),
    (0, 123), (321, 0), (-1, 123), (321, -1), (1_048_577, 123),
    (321, 393_217), ("321", 123), (321, 1.5)])
def test_synthetic_only_trusted_policy_lane_no_real_grant(tmp_path, monkeypatch, prompt, completion):
    import authorized_budget_period as period
    monkeypatch.setattr(period, "POLICY", {**period.POLICY, "prompt_token_cap": prompt,
        "lanes": {**period.POLICY["lanes"], "synthetic_only": {"completion_token_cap": completion}}})
    guard = IncrementalGuard(tmp_path / "increment.json")
    body = {"model": "deepseek-flash", "messages": [{"role": "user", "content": "x"}],
            "max_tokens": 123, "temperature": 0, "thinking": {"type": "disabled"}}
    try:
        request = httpx.Request("POST", "https://api.deepseek.com/chat/completions", json=body)
        if (prompt, completion) == (321, 123):
            guard.continuation_start = 0
            with pytest.raises(ModelBudgetExceeded):
                guard.begin(request, "synthetic_only")
            del guard.continuation_start
            reserved = guard.begin(request, "synthetic_only")
            assert reserved["prompt_token_cap"] == 321
            assert reserved["completion_token_cap"] == 123
            with pytest.raises(ModelBudgetExceeded):
                guard.finish(reserved, httpx.Response(200, json=receipt(usage={
                    "prompt_tokens": 322, "completion_tokens": 20, "total_tokens": 342})))
            assert guard.state["halted"] is True
        else:
            with pytest.raises(ModelBudgetExceeded):
                guard.begin(request, "synthetic_only")
            assert guard.state["receipts"] == []
    finally:
        guard.close()


@pytest.mark.parametrize("lane,cap", [("dav58_loop", 2000), ("dav58_judge", 2000), ("dav53_scenarios", 1000)])
def test_legacy_receipt_caps_survive_current_policy_change(tmp_path, monkeypatch, lane, cap):
    import hashlib
    import authorized_budget_period as period
    path, saved, _ = settled_journal(tmp_path)
    prior = saved["receipts"][0]
    prior.pop("prompt_token_cap")
    prior.pop("completion_token_cap")
    prior.update(lane=lane, prompt_tokens=24000, completion_tokens=cap, total_tokens=24000 + cap,
                 peak_micro_usd=(24000 * 3 + cap * 12 + 9) // 10)
    saved["settled_peak_micro_usd"] = prior["peak_micro_usd"]
    path.write_text(json.dumps(saved))
    before = path.read_bytes()
    monkeypatch.setattr(period, "POLICY", {**period.POLICY, "prompt_token_cap": 100,
        "lanes": {lane: {"completion_token_cap": 10}}})
    resumed = IncrementalGuard(path, resume_sha256=hashlib.sha256(before).hexdigest(),
                              period_identity="same-identity", config_sha256="same-config")
    try:
        assert resumed.state["receipts"] == saved["receipts"]
        assert path.read_bytes() == before
    finally:
        resumed.close()


@pytest.mark.parametrize("with_caps", [False, True])
def test_unknown_lane_receipt_cannot_authorize_resume(tmp_path, with_caps):
    import hashlib
    path, saved, _ = settled_journal(tmp_path)
    saved["receipts"][0]["lane"] = "u03"
    if not with_caps:
        saved["receipts"][0].pop("prompt_token_cap")
        saved["receipts"][0].pop("completion_token_cap")
    path.write_text(json.dumps(saved))
    before = path.read_bytes()
    with pytest.raises(ValueError):
        IncrementalGuard(path, resume_sha256=hashlib.sha256(before).hexdigest(),
                         period_identity="same-identity", config_sha256="same-config")
    assert path.read_bytes() == before


@pytest.mark.parametrize("field,value", [("prompt_token_cap", 24001),
    ("completion_token_cap", 2001), ("prompt_token_cap", 23999),
    ("completion_token_cap", 1999)])
def test_resume_rejects_receipt_caps_differing_from_trusted_policy(tmp_path, field, value):
    import hashlib
    path, saved, _ = settled_journal(tmp_path)
    saved["receipts"][0][field] = value
    path.write_text(json.dumps(saved))
    before = path.read_bytes()
    with pytest.raises(ValueError, match="prior caps differ from trusted policy"):
        IncrementalGuard(path, resume_sha256=hashlib.sha256(before).hexdigest(),
                         period_identity="same-identity", config_sha256="same-config")
    assert path.read_bytes() == before
