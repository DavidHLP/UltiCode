"""Payload-free model call metadata for owner-scoped workflow events."""

import asyncio
import re
import time
import uuid

from deepseek_model import ModelBudgetExceeded, ModelProtocolError


def _latest(model, name, before):
    values = getattr(model, name, None)
    return values[-1] if isinstance(values, list) and len(values) > before else None


class ObservedModel:
    def __init__(self, model, record, *, node):
        self.model, self.record, self.node = model, record, node

    @property
    def usage(self):
        return getattr(self.model, "usage", None)

    async def decide(self, messages):
        call_id = str(uuid.uuid4())
        counts = {name: len(values) if isinstance(values := getattr(self.model, name, None), list) else 0
                  for name in ("usage", "response_models", "metering")}
        self.record("model_started", {"callId": call_id, "node": self.node})
        started = time.monotonic()
        outcome = "failed"
        reason = None
        try:
            result = await self.model.decide(messages)
            outcome = "completed"
            return result
        except asyncio.CancelledError:
            outcome = "cancelled"
            reason = "cancelled"
            raise
        except Exception as exc:
            reason = ("budget_blocked" if isinstance(exc, ModelBudgetExceeded) else
                      "model_protocol" if isinstance(exc, ModelProtocolError) else "model_failed")
            raise
        finally:
            usage = _latest(self.model, "usage", counts["usage"])
            usage = usage if isinstance(usage, dict) else {}
            tokens = {name: value if type(value := usage.get(name)) is int and value >= 0 else None
                      for name in ("prompt_tokens", "completion_tokens", "total_tokens")}
            model = _latest(self.model, "response_models", counts["response_models"])
            model = model if isinstance(model, str) and re.fullmatch(r"[A-Za-z0-9._:/-]{1,100}", model) else "unknown"
            meter = _latest(self.model, "metering", counts["metering"])
            attempt = meter.get("attempt_id") if isinstance(meter, dict) else None
            attempt = attempt if isinstance(attempt, str) and re.fullmatch(
                r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", attempt) else None
            self.record("model_completed", {"callId": call_id, "node": self.node,
                "outcome": outcome, "elapsedMs": max(0, int((time.monotonic() - started) * 1000)),
                "reason": reason,
                "model": model, "attemptId": attempt, "usage": tokens,
                "usageStatus": "known" if all(value is not None for value in tokens.values()) else "unavailable"})
