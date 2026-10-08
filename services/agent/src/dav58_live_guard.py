"""Run-only cost guard; never changes the prepared period or original ledger.

The reservation covers the published full model context/output maxima, not a
local tokenizer/framing guess. Valid usage reconciles this independent envelope;
the original ModelBudget's conservative reservations are never refunded.
"""
import fcntl
import hashlib
import json
import os
import re
import stat
import threading
from datetime import datetime, timezone
from pathlib import Path

import httpx
import authorized_budget_period
from deepseek_model import ModelBudgetExceeded, _reject_duplicate_keys, _reject_json_constant

INPUT_MAX = 1_048_576  # conservatively interpret published 1M context as 2**20
OUTPUT_MAX = 393_216  # published 384K full output, including any reasoning
ENVELOPE_MICRO_USD = (INPUT_MAX * 3 + OUTPUT_MAX * 12 + 9) // 10


def _lane_caps(lane, policy=None):
    policy = authorized_budget_period.POLICY if policy is None else policy
    try:
        completion = policy["lanes"][lane]["completion_token_cap"]
        prompt = policy["lanes"][lane].get("prompt_token_cap", policy["prompt_token_cap"])
    except (KeyError, TypeError):
        raise ModelBudgetExceeded("unexpected paid lane or policy") from None
    if (type(completion) is not int or not 1 <= completion <= OUTPUT_MAX
            or type(prompt) is not int or not 1 <= prompt <= INPUT_MAX):
        raise ModelBudgetExceeded("invalid trusted policy caps")
    return prompt, completion


class IncrementalGuard:
    def __init__(self, path: Path, *, resume_sha256=None, period_identity=None, config_sha256=None, budget=None):
        self.path = path
        self.budget = budget
        self.policy = budget.policy if budget is not None else authorized_budget_period.POLICY
        self.fresh = "history" in self.policy
        if self.fresh and (budget._identity.identity, budget._identity.config_sha256) != (period_identity, config_sha256):
            raise ValueError("guard must match its bound budget")
        if self.fresh and path != budget.path.parent / f"dav58-increment-{period_identity}.json":
            raise ValueError("fresh guard must use the bound accounting directory")
        self.lock = threading.Lock()
        self.state = {"limit_micro_usd": self.policy["limit_micro_usd"], "settled_peak_micro_usd": 0,
                      "pending_micro_usd": 0, "halted": False, "receipts": []}
        if self.fresh:
            self.state["retained_history"] = dict(self.policy["history"])
        if period_identity is not None or config_sha256 is not None:
            if not period_identity or not config_sha256:
                raise ValueError("guard requires both period identity and config")
            self.state.update(period_identity=period_identity, config_sha256=config_sha256)
        lock_path = path.with_suffix(path.suffix + ".lock")
        if self.fresh:
            with authorized_budget_period._parent(lock_path) as parent:
                fd = authorized_budget_period._file(parent, lock_path.name, os.O_RDWR | os.O_CREAT)
            info = os.fstat(fd)
            if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
                os.close(fd)
                raise ValueError("private bound guard lock required")
            self._ownership = os.fdopen(fd, "r+")
        else:
            self._ownership = lock_path.open("a")
        try:
            fcntl.flock(self._ownership, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if resume_sha256 is None:
                fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
                with os.fdopen(fd, "w") as stream:
                    stream.write(json.dumps(self.state) + "\n")
                    stream.flush()
                    os.fsync(stream.fileno())
            else:
                info = os.lstat(path)
                if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or (info.st_mode & 0o777) != 0o600:
                    raise ValueError("guard journal is not private")
                raw = path.read_bytes()
                if hashlib.sha256(raw).hexdigest() != resume_sha256:
                    raise ValueError("resume journal fingerprint mismatch")
                saved = json.loads(raw, object_pairs_hook=_reject_duplicate_keys, parse_constant=_reject_json_constant)
                self._validate_resume(saved, period_identity, config_sha256, self.policy, self.budget)
                self.state = saved
        except BaseException:
            self.close()
            raise

    @staticmethod
    def _validate_resume(saved, identity, config, trusted_policy=None, budget=None):
        trusted_policy = authorized_budget_period.POLICY if trusted_policy is None else trusted_policy
        fresh = "history" in trusted_policy
        if (not identity or not config or saved.get("period_identity") != identity
                or saved.get("config_sha256") != config or saved.get("limit_micro_usd") != trusted_policy["limit_micro_usd"]
                or saved.get("halted") is not False or type(saved.get("pending_micro_usd")) is not int
                or saved["pending_micro_usd"] != 0):
            raise ValueError("unsafe resume identity, limit or pending state")
        if fresh and saved.get("retained_history") != dict(trusted_policy["history"]):
            raise ValueError("historical liability drift")
        policy = {"model": "deepseek-flash", "input_rate_tenths": 3, "output_rate_tenths": 12,
                  "input_max": INPUT_MAX, "output_max": OUTPUT_MAX}
        if "policy" in saved and saved["policy"] != policy:
            raise ValueError("resume model or pricing mismatch")
        receipts = saved.get("receipts")
        if not isinstance(receipts, list) or (not receipts and not fresh):
            raise ValueError("resume requires prior receipts")
        total = 0
        continuation = saved.get("continuation_run")
        continuation_start = continuation.get("receipt_start") if isinstance(continuation, dict) else None
        for index, r in enumerate(receipts):
            response_model = r.get("response_model") if isinstance(r, dict) else None
            lane = r.get("lane") if isinstance(r, dict) else None
            completion_cap = 1000 if lane == "dav53_scenarios" else 2000
            prompt_cap = 8000 if type(continuation_start) is int and index >= continuation_start else 24000
            if isinstance(r, dict) and "prompt_token_cap" in r:
                try:
                    trusted_caps = _lane_caps(lane, trusted_policy)
                except ModelBudgetExceeded:
                    raise ValueError("unsafe prior lane") from None
                prompt_cap, completion_cap = r["prompt_token_cap"], r.get("completion_token_cap")
                if (type(prompt_cap) is not int or not 1 <= prompt_cap <= INPUT_MAX
                        or type(completion_cap) is not int or not 1 <= completion_cap <= OUTPUT_MAX):
                    raise ValueError("invalid prior caps")
                if type(continuation_start) is int and index >= continuation_start:
                    if lane not in {"dav58_loop", "dav58_judge"} or completion_cap != 2000 or prompt_cap != 8000:
                        raise ValueError("invalid continuation caps")
                elif (prompt_cap, completion_cap) != trusted_caps:
                    raise ValueError("prior caps differ from trusted policy")
            if (not isinstance(r, dict) or r.get("status") != "settled"
                    or (lane not in {"dav58_loop", "dav58_judge", "dav53_scenarios"}
                        and "prompt_token_cap" not in r)
                    or r.get("request_model") != "deepseek-flash"
                    or not isinstance(response_model, str)
                    or response_model.casefold() not in {"deepseek-flash", "deepseek-v4.1-flash"}
                    or r.get("reserved_micro_usd") != ENVELOPE_MICRO_USD
                    or not isinstance(r.get("request_sha256"), str) or len(r["request_sha256"]) != 64):
                raise ValueError("unsafe prior receipt")
            if fresh and (not isinstance(r.get("response_sha256"), str)
                          or not re.fullmatch(r"[0-9a-f]{64}", r["response_sha256"])):
                raise ValueError("unbound prior provider response")
            prompt, completion, tokens = (r.get(k) for k in ("prompt_tokens", "completion_tokens", "total_tokens"))
            if (any(type(v) is not int or v < 0 for v in (prompt, completion, tokens))
                    or tokens != prompt + completion or prompt > prompt_cap or completion > completion_cap):
                raise ValueError("invalid prior usage")
            cost = (prompt * 3 + completion * 12 + 9) // 10
            if type(r.get("peak_micro_usd")) is not int or r["peak_micro_usd"] != cost:
                raise ValueError("invalid prior cost")
            total += cost
        if type(saved.get("settled_peak_micro_usd")) is not int or saved["settled_peak_micro_usd"] != total or total > trusted_policy["limit_micro_usd"]:
            raise ValueError("invalid cumulative cost")
        if fresh:
            if budget is None:
                raise ValueError("bound budget required for fresh guard validation")
            budget.verify_guard_history(receipts)
        # Legacy log is pinned by its caller-supplied SHA; receipt history is preserved.
        saved["policy"] = policy

    def close(self):
        self._ownership.close()

    def _save(self):
        temp = self.path.with_suffix(self.path.suffix + ".tmp")
        fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, "w") as stream:
            stream.write(json.dumps(self.state, sort_keys=True) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, self.path)
        fd = os.open(self.path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)

    def begin(self, request: httpx.Request, lane: str, reservation=None):
        with self.lock:
            if self.state["halted"] or self.state["pending_micro_usd"]:
                raise ModelBudgetExceeded("incremental guard stopped or request already pending")
            if (request.method != "POST" or request.url.scheme != "https"
                    or request.url.host != "api.deepseek.com"
                    or request.url.path != "/chat/completions" or request.url.query):
                raise ModelBudgetExceeded("unexpected paid route or lane")
            if hasattr(self, "continuation_start"):
                if lane not in {"dav58_loop", "dav58_judge"}:
                    raise ModelBudgetExceeded("continuation lane is not authorized")
                used = sum(r["lane"] == lane for r in self.state["receipts"][self.continuation_start:])
                if used >= {"dav58_loop": 24, "dav58_judge": 19}[lane]:
                    raise ModelBudgetExceeded("continuation lane allocation exhausted")
                prompt_cap, completion_cap = 8000, 2000
            else:
                prompt_cap, completion_cap = _lane_caps(lane, self.policy)
            if self.fresh:
                if reservation is None or reservation.purpose != lane:
                    raise ModelBudgetExceeded("explicit request/attempt binding required")
                snapshot = self.budget.verify_pending(reservation)
                if (snapshot["committed_micro_usd"] - reservation.reserved_micro_usd + ENVELOPE_MICRO_USD
                        > self.policy["limit_micro_usd"]):
                    raise ModelBudgetExceeded("cumulative budget cannot cover the provider envelope")
                prior_ids = [r.get("attempt_id") for r in self.state["receipts"]]
                if (reservation.attempt_id in prior_ids or snapshot["attempts"] != len(prior_ids) + 1
                        or snapshot["actual_micro_usd"] != self.state["settled_peak_micro_usd"]):
                    raise ModelBudgetExceeded("guard and budget history disagree")
            body = json.loads(request.content)
            if (not isinstance(body, dict)
                    or set(body) != {"model", "messages", "temperature", "max_tokens", "thinking"}
                    or body["model"] != "deepseek-flash"
                    or body["thinking"] != {"type": "disabled"}
                    or type(body["max_tokens"]) is not int
                    or not 1 <= body["max_tokens"] <= completion_cap
                    or body["temperature"] != 0
                    or not isinstance(body["messages"], list) or not body["messages"]):
                raise ModelBudgetExceeded("unexpected model, thinking, output cap or payload")
            if any(not isinstance(m, dict) or set(m) != {"role", "content"}
                   or m["role"] not in {"system", "user", "assistant"}
                   or not isinstance(m["content"], str) for m in body["messages"]):
                raise ModelBudgetExceeded("unexpected message schema")
            if self.state["settled_peak_micro_usd"] + ENVELOPE_MICRO_USD > self.state["limit_micro_usd"]:
                raise ModelBudgetExceeded("insufficient increment for full provider envelope")
            receipt = {"lane": lane, "request_sha256": hashlib.sha256(request.content).hexdigest(),
                       "request_bytes": len(request.content), "request_model": body["model"],
                       "prompt_token_cap": prompt_cap, "completion_token_cap": completion_cap,
                       "reserved_micro_usd": ENVELOPE_MICRO_USD,
                       "started_at_utc": datetime.now(timezone.utc).isoformat(), "status": "pending"}
            if self.fresh:
                receipt["attempt_id"] = reservation.attempt_id
                self.budget.claim_dispatch(reservation, receipt["request_sha256"])
            self.state["receipts"].append(receipt)
            self.state["pending_micro_usd"] = ENVELOPE_MICRO_USD
            try:
                self._save()
            except BaseException:
                self.state["halted"] = True
                raise ModelBudgetExceeded("incremental guard could not persist reservation") from None
            return receipt

    def halt(self, receipt, reason):
        with self.lock:
            receipt["status"] = "unknown_or_unsafe"
            receipt["reason"] = reason
            self.state["halted"] = True
            try:
                self._save()  # retain full envelope; no retry or later lane may send
            except OSError:
                pass  # in-memory latch stays closed; durable pending record already exists

    def finish(self, receipt, response):
        try:
            if response.status_code != 200:
                raise ValueError("non_success_response")
            payload = json.loads(response.content, object_pairs_hook=_reject_duplicate_keys,
                                 parse_constant=_reject_json_constant)
            usage = payload.get("usage")
            if not isinstance(usage, dict):
                raise ValueError("missing_usage")
            prompt, completion, total = (usage.get(k) for k in ("prompt_tokens", "completion_tokens", "total_tokens"))
            prompt_cap, lane_cap = receipt["prompt_token_cap"], receipt["completion_token_cap"]
            if (any(type(v) is not int or v < 0 for v in (prompt, completion, total))
                    or total != prompt + completion or prompt > prompt_cap or completion > lane_cap):
                raise ValueError("unusable_or_over_envelope_usage")
            if hasattr(self, "continuation_start") and prompt > 8000:
                raise ValueError("continuation_input_cap_exceeded")
            details = usage.get("completion_tokens_details", {})
            if not isinstance(details, dict) or details.get("reasoning_tokens", 0) != 0:
                raise ValueError("unexpected_reasoning_usage")
            choices = payload.get("choices")
            if (not isinstance(choices, list) or not choices or not isinstance(choices[0], dict)
                    or not isinstance(choices[0].get("message"), dict)
                    or choices[0]["message"].get("reasoning_content")):
                raise ValueError("unexpected_reasoning_or_response_schema")
            response_model = payload.get("model")
            if not isinstance(response_model, str) or response_model.casefold() not in {"deepseek-flash", "deepseek-v4.1-flash"}:
                raise ValueError("unexpected_or_unknown_response_model")
            actual = (prompt * 3 + completion * 12 + 9) // 10
        except (ValueError, TypeError, AttributeError):
            self.halt(receipt, "unknown_or_unusable_provider_receipt")
            raise ModelBudgetExceeded("incremental guard stopped after unsafe provider receipt") from None
        with self.lock:
            receipt.update(status="settled", prompt_tokens=prompt, completion_tokens=completion,
                           total_tokens=total, peak_micro_usd=actual,
                           response_model=response_model,
                           finished_at_utc=datetime.now(timezone.utc).isoformat())
            if self.fresh:
                receipt["response_sha256"] = hashlib.sha256(response.content).hexdigest()
            self.state["settled_peak_micro_usd"] += actual
            self.state["pending_micro_usd"] = 0
            try:
                self._save()
            except BaseException:
                self.state["halted"] = True
                raise ModelBudgetExceeded("incremental guard could not persist settlement") from None


class GuardedTransport(httpx.AsyncBaseTransport):
    """Enforce shared full-envelope metering for accepted DAV58/DAV53 calls."""
    def __init__(self, guard, lane, inner=None, *, owns_guard=False):
        self.guard, self.lane = guard, lane
        # No redirects, SDK retries or HTTP transport retries are installed.
        self.inner = inner if inner is not None else httpx.AsyncHTTPTransport(retries=0)
        self.reservation = None
        self.owns_guard = owns_guard
        self.exchanges = []

    def bind_reservation(self, reservation):
        self.reservation = reservation

    async def handle_async_request(self, request):
        reservation, self.reservation = self.reservation, None
        receipt = self.guard.begin(request, self.lane, reservation)
        try:
            response = await self.inner.handle_async_request(request)
            await response.aread()
        except BaseException:
            self.guard.halt(receipt, "network_or_read_failure")
            raise ModelBudgetExceeded("incremental guard stopped after network failure") from None
        self.guard.finish(receipt, response)
        if getattr(self.guard, "fresh", False) and self.lane.startswith("prior_"):
            self.exchanges.append({"attempt_id": receipt["attempt_id"],
                                   "request_json": request.content.decode("utf-8"),
                                   "response_hex": response.content.hex()})
        return response

    async def aclose(self):
        try:
            await self.inner.aclose()
        finally:
            if self.owns_guard:
                self.guard.close()
