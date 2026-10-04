"""Run-only cost guard; never changes the prepared period or original ledger.

The reservation covers the published full model context/output maxima, not a
local tokenizer/framing guess. Valid usage reconciles this independent envelope;
the original ModelBudget's conservative reservations are never refunded.
"""
import fcntl
import hashlib
import json
import os
import threading
from datetime import datetime, timezone
from pathlib import Path

import httpx
from deepseek_model import ModelBudgetExceeded, _reject_duplicate_keys, _reject_json_constant

INPUT_MAX = 1_048_576  # conservatively interpret published 1M context as 2**20
OUTPUT_MAX = 393_216  # published 384K full output, including any reasoning
ENVELOPE_MICRO_USD = (INPUT_MAX * 3 + OUTPUT_MAX * 12 + 9) // 10


class IncrementalGuard:
    def __init__(self, path: Path, *, resume_sha256=None, period_identity=None, config_sha256=None):
        self.path = path
        self.lock = threading.Lock()
        self.state = {"limit_micro_usd": 1_000_000, "settled_peak_micro_usd": 0,
                      "pending_micro_usd": 0, "halted": False, "receipts": []}
        self._ownership = path.with_suffix(path.suffix + ".lock").open("a")
        try:
            fcntl.flock(self._ownership, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if resume_sha256 is None:
                with path.open("x") as stream:
                    stream.write(json.dumps(self.state) + "\n")
            else:
                raw = path.read_bytes()
                if hashlib.sha256(raw).hexdigest() != resume_sha256:
                    raise ValueError("resume journal fingerprint mismatch")
                saved = json.loads(raw, object_pairs_hook=_reject_duplicate_keys, parse_constant=_reject_json_constant)
                self._validate_resume(saved, period_identity, config_sha256)
                self.state = saved
        except BaseException:
            self.close()
            raise

    @staticmethod
    def _validate_resume(saved, identity, config):
        if (not identity or not config or saved.get("period_identity") != identity
                or saved.get("config_sha256") != config or saved.get("limit_micro_usd") != 1_000_000
                or saved.get("halted") is not False or type(saved.get("pending_micro_usd")) is not int
                or saved["pending_micro_usd"] != 0):
            raise ValueError("unsafe resume identity, limit or pending state")
        policy = {"model": "deepseek-flash", "input_rate_tenths": 3, "output_rate_tenths": 12,
                  "input_max": INPUT_MAX, "output_max": OUTPUT_MAX}
        if "policy" in saved and saved["policy"] != policy:
            raise ValueError("resume model or pricing mismatch")
        receipts = saved.get("receipts")
        if not isinstance(receipts, list) or not receipts:
            raise ValueError("resume requires prior receipts")
        total = 0
        for r in receipts:
            if (not isinstance(r, dict) or r.get("status") != "settled"
                    or r.get("lane") not in {"dav58_loop", "dav58_judge"}
                    or r.get("request_model") != "deepseek-flash"
                    or r.get("response_model") not in {"deepseek-flash", "deepseek-v4.1-flash"}
                    or r.get("reserved_micro_usd") != ENVELOPE_MICRO_USD
                    or not isinstance(r.get("request_sha256"), str) or len(r["request_sha256"]) != 64):
                raise ValueError("unsafe prior receipt")
            prompt, completion, tokens = (r.get(k) for k in ("prompt_tokens", "completion_tokens", "total_tokens"))
            if (any(type(v) is not int or v < 0 for v in (prompt, completion, tokens))
                    or tokens != prompt + completion or prompt > 24000 or completion > 2000):
                raise ValueError("invalid prior usage")
            cost = (prompt * 3 + completion * 12 + 9) // 10
            if type(r.get("peak_micro_usd")) is not int or r["peak_micro_usd"] != cost:
                raise ValueError("invalid prior cost")
            total += cost
        if type(saved.get("settled_peak_micro_usd")) is not int or saved["settled_peak_micro_usd"] != total or total > 1_000_000:
            raise ValueError("invalid cumulative cost")
        # Legacy log is pinned by its caller-supplied SHA; receipt history is preserved.
        saved["policy"] = policy

    def close(self):
        self._ownership.close()

    def _save(self):
        temp = self.path.with_suffix(self.path.suffix + ".tmp")
        with temp.open("w") as stream:
            stream.write(json.dumps(self.state, sort_keys=True) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, self.path)
        fd = os.open(self.path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)

    def begin(self, request: httpx.Request, lane: str):
        with self.lock:
            if self.state["halted"] or self.state["pending_micro_usd"]:
                raise ModelBudgetExceeded("incremental guard stopped or request already pending")
            if (request.method != "POST" or request.url.scheme != "https"
                    or request.url.host != "api.deepseek.com"
                    or request.url.path != "/chat/completions"
                    or request.url.query or lane not in {"dav58_loop", "dav58_judge"}):
                raise ModelBudgetExceeded("unexpected paid route or lane")
            body = json.loads(request.content)
            if (set(body) != {"model", "messages", "temperature", "max_tokens", "thinking"}
                    or body["model"] != "deepseek-flash"
                    or body["thinking"] != {"type": "disabled"}
                    or type(body["max_tokens"]) is not int or not 1 <= body["max_tokens"] <= 2000
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
                       "reserved_micro_usd": ENVELOPE_MICRO_USD,
                       "started_at_utc": datetime.now(timezone.utc).isoformat(), "status": "pending"}
            self.state["receipts"].append(receipt)
            self.state["pending_micro_usd"] = ENVELOPE_MICRO_USD
            try:
                self._save()  # durable BEFORE the underlying network request
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
            if (any(type(v) is not int or v < 0 for v in (prompt, completion, total))
                    or total != prompt + completion or prompt > 24000 or completion > 2000):
                raise ValueError("unusable_or_over_envelope_usage")
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
            self.state["settled_peak_micro_usd"] += actual
            self.state["pending_micro_usd"] = 0
            try:
                self._save()
            except BaseException:
                self.state["halted"] = True
                raise ModelBudgetExceeded("incremental guard could not persist settlement") from None


class GuardedTransport(httpx.AsyncBaseTransport):
    def __init__(self, guard, lane, inner=None):
        self.guard, self.lane = guard, lane
        # No redirects, SDK retries or HTTP transport retries are installed.
        self.inner = inner if inner is not None else httpx.AsyncHTTPTransport(retries=0)

    async def handle_async_request(self, request):
        receipt = self.guard.begin(request, self.lane)
        try:
            response = await self.inner.handle_async_request(request)
            await response.aread()
        except BaseException:
            self.guard.halt(receipt, "network_or_read_failure")
            raise ModelBudgetExceeded("incremental guard stopped after network failure") from None
        self.guard.finish(receipt, response)
        return response

    async def aclose(self):
        await self.inner.aclose()
