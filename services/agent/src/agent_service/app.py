"""Strict same-origin Agent HTTP API; Java remains the only business writer."""

from __future__ import annotations

import asyncio
import hmac
import httpx
import json
import os
import re
import time
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Callable, Literal

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, ConfigDict, StrictBool, StrictInt, StrictStr, ValidationError
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from langgraph.types import Command
from starlette.exceptions import HTTPException as StarletteHTTPException

from agent_service.gate import GateError, load_u02_gate, require_execution_candidate
from agent_service.graph import ainvoke_untraced, build_workflow_graph
from agent_service.state import WorkflowAction, make_draft, params_digest
from agent_service.store import Conflict, NotFound, WorkflowStore
from sourced_analysis import _ALLOWED_STATUSES, validate_submission_facts
from ulticode_client import (
    UlticodeClient, UlticodeError, UlticodeServiceError, _java_blank, canonical_uuid,
)

MAX_BODY = 128 * 1024
MAX_TITLE = 200
MAX_CONTENT = 16_000


class AgentError(Exception):
    def __init__(self, status: int, reason: str, code: int = 40000):
        self.status, self.reason, self.code = status, reason, code


class _AnalysisSuperseded(asyncio.CancelledError):
    """Stop a stale tool graph without cancelling the HTTP request task."""


class _StrictBody(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid")


class _CreateBody(_StrictBody):
    sourceSubmissionId: StrictStr
    question: StrictStr


class _DraftBody(_StrictBody):
    draftVersion: StrictInt
    title: StrictStr
    content: StrictStr


class _EmptyBody(_StrictBody):
    pass


class _ConfirmBody(_StrictBody):
    draftVersion: StrictInt
    paramsDigest: StrictStr
    confirm: Literal[True]


class _SaveBody(_StrictBody):
    confirmationId: StrictStr


class _RecoverBody(_StrictBody):
    retry: StrictBool


def _validated_body(value: object, model: type[_StrictBody]) -> dict[str, object]:
    try:
        return model.model_validate(value).model_dump(mode="python")
    except ValidationError:
        raise AgentError(400, "validation_error") from None


def _duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate_json_key")
        result[key] = value
    return result


def _constant(_: str) -> object:
    raise ValueError("nonfinite_json_number")


def parse_model_answer(raw: str) -> dict[str, object]:
    """Parse the strict inner answer carried by ``LoopResult.answer``."""
    if not isinstance(raw, str):
        raise ValueError("model_answer_type_invalid")
    try:
        encoded = raw.encode("utf-8")
    except UnicodeEncodeError:
        raise ValueError("model_answer_unicode_invalid") from None
    if len(encoded) > 64 * 1024:
        raise ValueError("model_answer_too_large")
    try:
        inner = json.loads(raw, object_pairs_hook=_duplicate_keys, parse_constant=_constant)
    except (ValueError, RecursionError, UnicodeError):
        raise ValueError("model_answer_json_invalid") from None
    if not isinstance(inner, dict) or set(inner) != {"text", "citations"}:
        raise ValueError("model_answer_schema_invalid")
    text, citations = inner["text"], inner["citations"]
    if not isinstance(text, str) or len(text) > MAX_CONTENT or not isinstance(citations, list) or len(citations) > 3:
        raise ValueError("model_answer_bounds_invalid")
    try:
        text.encode("utf-8")
    except UnicodeEncodeError:
        raise ValueError("model_answer_unicode_invalid") from None
    from boundary_evaluation import CITATION_FIELDS
    if any(not isinstance(item, dict) or set(item) != set(CITATION_FIELDS)
           or any(not isinstance(item[field], str) or not item[field].strip() for field in CITATION_FIELDS)
           for item in citations):
        raise ValueError("model_citation_invalid")
    try:
        for citation in citations:
            for value in citation.values():
                value.encode("utf-8")
    except UnicodeEncodeError:
        raise ValueError("model_citation_invalid") from None
    return {"text": text, "citations": citations}



def _utf8(value: str) -> bool:
    try:
        value.encode("utf-8")
        return True
    except UnicodeEncodeError:
        return False



def _validate_draft(title: object, content: object) -> tuple[str, str]:
    if (not isinstance(title, str) or not isinstance(content, str) or not _utf8(title)
            or not _utf8(content) or _java_blank(title) or _java_blank(content)):
        raise AgentError(400, "draft_empty")
    if len(title) > MAX_TITLE or len(content) > MAX_CONTENT:
        raise AgentError(400, "draft_too_long")
    return title, content


def _uuid(value: object) -> str:
    try:
        return canonical_uuid(value, "uuid")
    except ValueError:
        raise AgentError(400, "validation_error") from None



def _authorized_model_for_purpose(expected: object, purpose: str, guard: object, tool_specs: dict[str, str]):
    """Build only a model purpose authorized by existing policy and shared guard."""
    from authorized_budget_period import policy_for
    from dav58_live_guard import GuardedTransport
    from deepseek_model import DeepseekModel
    from model_budget import authorized_model

    try:
        POLICY = policy_for(expected.policy_id)
        if purpose not in {"u03_analysis", "u03_citation_judge"} or purpose not in POLICY["lanes"]:
            raise AgentError(503, "model_budget_blocked", 50000)
        if guard is None:
            raise AgentError(503, "model_budget_blocked", 50000)
        lane_policy = POLICY["lanes"][purpose]
        model_alias, budget = authorized_model(expected)
        return DeepseekModel(
            os.environ["DEEPSEEK_API_KEY"], model=model_alias, thinking_type="disabled",
            tool_specs=tool_specs, max_tokens=int(lane_policy["completion_token_cap"]),
            max_prompt_tokens=int(POLICY["prompt_token_cap"]), max_calls=int(lane_policy["attempts"]),
            budget=budget, budget_purpose=purpose, transport=GuardedTransport(guard, purpose),
        )
    except AgentError:
        raise
    except Exception:
        raise AgentError(503, "model_budget_blocked", 50000) from None


async def _verify_citations(parsed, retrieved, documents, facts, judge_model):
    from boundary_evaluation import CITATION_FIELDS, judge_citation
    from citation_integrity import check_citations

    citations = parsed["citations"]
    retrieved_fields = {
        tuple(hit.get(field) for field in CITATION_FIELDS if field != "claim")
        for hit in retrieved if isinstance(hit, dict)
    }
    for citation in citations:
        if tuple(citation[field] for field in CITATION_FIELDS if field != "claim") not in retrieved_fields:
            raise ValueError("citation_not_retrieved_in_run")
    checks = check_citations(citations, documents)
    if len(checks) != len(citations) or any(check.verdict != "verified" for check in checks):
        raise ValueError("citation_integrity_failed")
    results = []
    for citation, check in zip(citations, checks):
        usage_before = len(getattr(judge_model, "usage", []))
        supports, derivable = await judge_citation(
            judge_model, claim=citation["claim"], quote=citation["text"], facts=facts,
        )
        usage = getattr(judge_model, "usage", None)
        if isinstance(usage, list) and (
            len(usage) <= usage_before or not isinstance(usage[-1], dict)
            or type(usage[-1].get("prompt_tokens")) is not int
            or type(usage[-1].get("completion_tokens")) is not int
        ):
            raise ValueError("citation_judge_usage_unknown")
        if not supports or not derivable:
            raise ValueError("citation_claim_unverified")
        results.append({"chunk_id": check.chunk_id, "exists": True, "supports": True, "derivable": True})
    return results


def _verify_answer_boundary(parsed, facts, documents, trace, tool_results, *, question: str):
    from boundary_evaluation import (
        _refusal_has_reference, _trace_claims_consistent, _unnegated_claim, load_boundary_cases,
    )

    text = parsed["text"]
    search_events = [event for event in trace if event.get("tool_name") == "search_evidence"]
    if (len(search_events) != len(tool_results)
            or any(
                bool(event.get("failed")) != (
                    isinstance(result.get("result"), dict) and "error" in result["result"]
                )
                for event, result in zip(search_events, tool_results)
            )
            or not _trace_claims_consistent(text, tool_results)):
        raise ValueError("answer_tool_trace_inconsistent")
    cases = load_boundary_cases()
    source_refusal = next(case for case in cases if case.category == "wrong_citation")
    source_pattern = r"源码|源代码|source\s+code|(?:forged|fabricated)\s+(?:source\s+)?(?:evidence|citation)"
    source_question = re.sub(
        r"((?:提交|伪造|实际|原始|本人|我的|该)(?:的)?(?:私有|私人|自己的)*)?(?:源码|源代码)(?:的)?概念"
        r"|\b((?:submission|submitted|forged|fabricated|actual|original|my|this|that)\s+(?:(?:private|own|raw)\s+)*)?source\s+code\s+concepts?\b",
        lambda match: match.group(0) if any(match.groups()) else "",
        question, flags=re.IGNORECASE,
    )
    # Submission source is unavailable to this runtime, irrespective of refusal wording.
    source_request = re.search(source_pattern, source_question, flags=re.IGNORECASE) and re.search(
        r"提交|伪造|获取|读取|展示|查看|引用|访问|提供|\b(?:submission|submitted|forged|fabricated|quote|retrieve|fetch|show|read|access|provide)\b",
        source_question, flags=re.IGNORECASE,
    )
    refusal_markers = (*source_refusal.require_any_markers, "do not have access", "no access",
                       "cannot access", "can't access", "无法访问", "没有权限", "不具备")
    refusal_claim = any(_unnegated_claim(text, marker) for marker in refusal_markers)
    source_denial = re.search(source_pattern, text, flags=re.IGNORECASE) and refusal_claim
    if source_request and not refusal_claim:
        raise ValueError("answer_source_request_not_refused")
    if (source_request or source_denial) and (
        parsed["citations"] or _refusal_has_reference(text, documents)
    ):
        raise ValueError("answer_source_refusal_has_reference")
    actual_status = facts[0].get("status") if facts and isinstance(facts[0], dict) else None
    status_values = "|".join(re.escape(value) for value in sorted(_ALLOWED_STATUSES, key=len, reverse=True))
    status_claim = re.search(
        rf"(?:\bstatus\s*(?:is|=|:)|状态\s*(?:为|是|=|：))\s*({status_values}|[A-Za-z_-]+)\b",
        text, flags=re.IGNORECASE,
    )
    if status_claim and isinstance(actual_status, str) and (
        status_claim.group(1).casefold() != actual_status.casefold()
    ):
        raise ValueError("answer_submission_fact_mismatch")
    source_injection = next(case for case in cases if case.category == "source_injection")
    if any(_unnegated_claim(text, marker) for marker in source_injection.forbid_any_markers):
        raise ValueError("answer_claims_unauthorized_action")
    tool_failure = next(case for case in cases if case.category == "tool_failure")
    if any(_unnegated_claim(text, marker) for marker in tool_failure.forbid_any_markers) and not any(
        event.get("failed") is False for event in search_events
    ):
        raise ValueError("answer_tool_success_unobserved")
    diagnostic_cases = {"missing_id", "no_hit", "tool_failure", "wrong_citation"}
    diagnostic_markers = {
        marker for case in cases if case.category in diagnostic_cases for marker in case.forbid_any_markers
    }
    diagnostic_text = text
    if status_claim and isinstance(actual_status, str):
        # A verified status is metadata, not a diagnosis of unavailable source.
        diagnostic_text = text[:status_claim.start()] + text[status_claim.end():]
    if any(
        _unnegated_claim(diagnostic_text, marker)
        and not any(_unnegated_claim(item["claim"], marker) for item in parsed["citations"])
        for marker in diagnostic_markers
    ):
        raise ValueError("answer_source_diagnosis_unverified")
    source_id = facts[0].get("id") if facts and isinstance(facts[0], dict) else None
    reference_text = re.sub(re.escape(source_id), "", text, flags=re.IGNORECASE) if isinstance(source_id, str) else text
    for citation in parsed["citations"]:
        for field in ("text", "doc_id", "chunk_id", "source_path", "source_position", "version"):
            value = citation.get(field)
            if isinstance(value, str) and value:
                reference_text = reference_text.replace(value, "")
    if not parsed["citations"]:
        # Allow quoted field names in clarifications, not quoted corpus/source material.
        reference_text = re.sub(
            r'["“‘「『](?:submission\s+id|submission_id|提交\s*id|提交(?:编号|标识|ID))["”’」』]',
            "", reference_text, flags=re.IGNORECASE,
        )
    if _refusal_has_reference(reference_text, documents):
        raise ValueError("answer_source_reference_unverified")


def _deterministic_business_error(exc: UlticodeServiceError) -> bool:
    return exc.status_code in {400, 409} or (
        exc.status_code == 200 and exc.code in {40000, 40900}
    )


def _receipt_plan_id(vo: object, row: dict[str, object], version: int) -> str | None:
    if not isinstance(vo, dict):
        return None
    try:
        plan_id = canonical_uuid(vo.get("id"), "plan_id")
        source_id = canonical_uuid(vo.get("sourceSubmissionId"), "sourceSubmissionId")
    except ValueError:
        return None
    if source_id != str(row["source_submission_id"]).lower() or any(
        vo.get(field) != expected for field, expected in {
            "draftVersion": version, "title": row["draft"]["title"], "content": row["draft"]["content"],
        }.items()
    ):
        return None
    return plan_id

def _response(data: object, *, code: int = 0, message: str = "success", status: int = 200) -> JSONResponse:
    return JSONResponse({"code": code, "message": message, "data": data, "traceId": str(uuid.uuid4())}, status_code=status)


def _cookie_headers(request: Request) -> list[str]:
    return request.headers.getlist("cookie")


def _session_values(request: Request, *, unsafe: bool, trusted_origin: str | None) -> tuple[str, str | None]:
    cookies: dict[str, str] = {}
    for header in _cookie_headers(request):
        for part in header.split(";"):
            name, sep, value = part.strip().partition("=")
            if not sep or name not in {"access_token", "csrf_token"}:
                continue
            if name in cookies:
                raise AgentError(401 if not unsafe else 403, "session_rejected", 40100 if not unsafe else 40300)
            cookies[name] = value
    access = cookies.get("access_token", "")
    csrf = cookies.get("csrf_token")
    if not access:
        raise AgentError(401, "session_required", 40100)
    cookie_value = r"[!#$%&'*+\-.^_`|~0-9A-Za-z]+"
    if not re.fullmatch(cookie_value, access):
        raise AgentError(401, "session_rejected", 40100)
    if csrf is not None and not re.fullmatch(cookie_value, csrf):
        raise AgentError(403, "session_rejected", 40300)
    if unsafe:
        if not csrf:
            raise AgentError(403, "csrf_token_required", 40300)
        headers = request.headers.getlist("x-csrf-token")
        if len(headers) != 1 or not headers[0] or not hmac.compare_digest(csrf, headers[0]):
            raise AgentError(403, "csrf_mismatch", 40300)
        origins = request.headers.getlist("origin")
        if origins and (len(origins) != 1 or not trusted_origin or origins[0] != trusted_origin):
            raise AgentError(403, "session_rejected", 40300)
    return access, csrf


async def _body(request: Request) -> dict[str, object]:
    if request.headers.get("content-type", "").split(";", 1)[0].strip().lower() != "application/json":
        raise AgentError(400, "validation_error")
    chunks: list[bytes] = []
    size = 0
    async for part in request.stream():
        size += len(part)
        if size > MAX_BODY:
            raise AgentError(413, "validation_error")
        chunks.append(part)
    try:
        value = json.loads(b"".join(chunks), object_pairs_hook=_duplicate_keys, parse_constant=_constant)
    except (ValueError, RecursionError, UnicodeError):
        raise AgentError(400, "validation_error") from None
    if not isinstance(value, dict):
        raise AgentError(400, "validation_error")
    return value


def create_app(
    *, state_path: Path | str | None = None, store: WorkflowStore | None = None,
    app_base_url: str | None = None, auth_base_url: str | None = None,
    transport: httpx.AsyncBaseTransport | None = None,
    client_factory: Callable[..., UlticodeClient] | None = None,
    model_factory: Callable[..., object] | None = None,
    offline_model_factory: Callable[..., object] | None = None,
    u02_gate_path: Path | str | None = None, expected_head: str | None = None,
    expected_base: str | None = None, candidate_root: Path | str | None = None,
    trusted_origin: str | None = None, clock: Callable[[], float] = time.time,
    fault_hook: Callable[[str, dict[str, object]], None] | None = None,
    fault_thread_ids: frozenset[str] = frozenset(),
    expected_period: object | None = None, budget_guard: object | None = None,
    model_purpose: str | None = None,
) -> FastAPI:
    if candidate_root is not None:
        candidate_root = require_execution_candidate(candidate_root)
    workflow_store = store or WorkflowStore.open(state_path)
    gate: dict[str, object] | None = None
    if u02_gate_path is not None and expected_head:
        try:
            gate = load_u02_gate(
                u02_gate_path, expected_head, expected_base=expected_base,
                candidate_root=candidate_root or Path(__file__).resolve().parents[4],
                runtime=True,
            )
        except (GateError, OSError, ValueError):
            gate = None
    app_url = app_base_url or os.environ.get("ULTICODE_APP_BASE", "")
    auth_url = auth_base_url or os.environ.get("ULTICODE_AUTH_BASE", "")
    allowed_origin = trusted_origin or os.environ.get("ULTICODE_AGENT_ORIGIN")

    def _require_current_gate() -> dict[str, object]:
        if u02_gate_path is None or not expected_head:
            raise AgentError(503, "model_budget_blocked", 50000)
        try:
            return load_u02_gate(
                u02_gate_path, expected_head, expected_base=expected_base,
                candidate_root=candidate_root or Path(__file__).resolve().parents[4],
                runtime=True,
            )
        except (GateError, OSError, ValueError):
            raise AgentError(503, "model_budget_blocked", 50000) from None

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        try:
            async with AsyncSqliteSaver.from_conn_string(str(workflow_store.db_path)) as saver:
                await saver.setup()
                await saver.conn.execute("PRAGMA busy_timeout=5000")
                await saver.conn.execute("PRAGMA synchronous=FULL")
                app.state.workflow = build_workflow_graph(checkpointer=saver)
                app.state.store = workflow_store
                yield
        finally:
            workflow_store.close()

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.store = workflow_store
    app.state.gate = gate

    @app.exception_handler(AgentError)
    async def agent_error_handler(_: Request, exc: AgentError):
        return _response({"reason": exc.reason}, code=exc.code, message=exc.reason, status=exc.status)

    @app.exception_handler(RequestValidationError)
    async def request_validation_handler(_: Request, __: RequestValidationError):
        return _response({"reason": "validation_error"}, code=40000, message="validation_error", status=400)

    @app.exception_handler(StarletteHTTPException)
    async def http_error_handler(_: Request, exc: StarletteHTTPException):
        if exc.status_code == 404:
            reason, code, status = "not_found", 40400, 404
        elif exc.status_code == 405:
            reason, code, status = "method_not_allowed", 40500, 405
        elif exc.status_code < 500:
            reason, code, status = "validation_error", 40000, 400
        else:
            reason, code, status = "upstream_unavailable", 50000, 502
        return _response({"reason": reason}, code=code, message=reason, status=status)

    @app.exception_handler(Exception)
    async def unexpected_handler(_: Request, __: Exception):
        return _response({"reason": "upstream_unavailable"}, code=50000, message="upstream_unavailable", status=502)

    async def with_session(request: Request, *, unsafe: bool, operation):
        access, csrf = _session_values(request, unsafe=unsafe, trusted_origin=allowed_origin)
        try:
            client = client_factory(access_token=access, csrf_token=csrf) if client_factory else UlticodeClient.for_session(
                app_url, auth_url, access_token=access, csrf_token=csrf, transport=transport
            )
            async with client:
                owner = await client.principal()
                return await operation(client, owner)
        except UlticodeServiceError as exc:
            if exc.code in {40100, 40300}:
                raise AgentError(401, "session_rejected", 40100) from None
            raise AgentError(502, "upstream_unavailable", 50000) from None
        except UlticodeError as exc:
            if str(exc) == "identity_unreadable":
                raise AgentError(401, "identity_unreadable", 40100) from None
            raise AgentError(401, "session_rejected", 40100) from None
        except httpx.HTTPError:
            raise AgentError(502, "upstream_unavailable", 50000) from None

    def _owned(thread_id: str, owner: str) -> dict[str, object]:
        row = workflow_store.get(thread_id, owner)
        if row is None:
            raise AgentError(404, "thread_not_found", 40400)
        return row

    async def _source_facts(client: UlticodeClient, source_id: str) -> tuple[str, str]:
        try:
            submission = await client.get_my_submission(source_id)
            source, status = validate_submission_facts(submission)
        except UlticodeServiceError as exc:
            if exc.code == 40400 and exc.status_code == 404:
                raise AgentError(404, "source_not_owned", 40400) from None
            raise
        except (UlticodeError, ValueError):
            raise AgentError(502, "upstream_unavailable", 50000) from None
        source = source.lower()
        if source != source_id.lower():
            raise AgentError(404, "source_not_owned", 40400)
        return source, status

    async def _verify_source(client: UlticodeClient, row: dict[str, object]) -> tuple[str, str]:
        source, status = await _source_facts(client, str(row["source_submission_id"]))
        if row["source_facts"] != {"id": source, "status": status}:
            raise AgentError(409, "confirmation_mismatch", 40900)
        return source, status

    def _reconcile(row: dict[str, object], owner: str) -> dict[str, object]:
        if row["status"] != "analyzing":
            return row
        return workflow_store.transition(str(row["thread_id"]), owner,
            expected_run=str(row["run_id"]), expected_version=int(row["draft_version"]),
            statuses={"analyzing"}, changes={"status": "awaiting_confirmation",
            "confirmation": {}, "failure_reason": "analysis_interrupted"},
            kind="analysis_interrupted")

    async def _checkpoint_resume(
        row: dict[str, object], action: WorkflowAction, execute_action,
        runtime_context: dict[str, object] | None = None,
    ) -> dict[str, object]:
        graph = app.state.workflow
        config = {"configurable": {"thread_id": row["thread_id"]}}
        canonical = {key: row[key] for key in (
            "thread_id", "run_id", "status", "draft_version", "question", "source_facts",
        )}
        snapshot = await graph.aget_state(config)
        current = snapshot.values
        if not current:
            await _checkpoint_init(row)
            snapshot = await graph.aget_state(config)
            current = snapshot.values
        if (tuple(snapshot.next) != ("await_action",)
                or any(current.get(key) != value for key, value in canonical.items())):
            await graph.checkpointer.adelete_thread(str(row["thread_id"]))
            await _checkpoint_init(row)
            snapshot = await graph.aget_state(config)
        if tuple(snapshot.next) != ("await_action",):
            raise Conflict("checkpoint_not_waiting_for_action")
        payload = {**action.payload, "_canonical_state": {
            "status": row["status"], "run_id": row["run_id"], "draft_version": row["draft_version"],
        }}
        server_action = {"kind": action.kind, "payload": payload,
                         "expected_run": action.expected_run, "expected_version": action.expected_version}
        context = {"execute_action": execute_action, **(runtime_context or {})}
        return await ainvoke_untraced(graph, Command(resume=server_action), config, context=context, durability="sync")

    def _thread_response(row: dict[str, object], owner: str) -> dict[str, object]:
        draft = row["draft"]
        confirmation = row["confirmation"]
        source = row["source_submission_id"]
        return {
            "threadId": row["thread_id"], "runId": row["run_id"], "status": row["status"],
            "sourceSubmissionId": source, "question": row["question"],
            "draft": {"draftVersion": row["draft_version"], "title": draft.get("title"),
                      "content": draft.get("content"), "facts": draft.get("facts", []),
                      "hypotheses": draft.get("hypotheses", []), "citations": draft.get("citations", []),
                      "citationChecks": draft.get("citation_checks", []), "sampleScope": draft.get("sample_scope")},
            "paramsDigest": params_digest(owner, str(source), int(row["draft_version"]), str(draft.get("title", "")), str(draft.get("content", ""))),
            "analysis": row["analysis"],
            "confirmation": {key: confirmation[key] for key in ("id", "action", "draftVersion", "paramsDigest", "expiresAt") if key in confirmation},
            "receipt": {"planId": row["plan_id"], "cancelRequested": bool(row["cancel_requested"]),
                        "failureReason": row["failure_reason"]},
        }

    async def _checkpoint_init(row: dict[str, object]) -> None:
        graph = app.state.workflow
        config = {"configurable": {"thread_id": row["thread_id"]}}
        await ainvoke_untraced(graph, {key: row[key] for key in ("thread_id", "run_id", "status", "draft_version", "question", "source_facts")}, config, durability="sync")


    @app.post("/agent/threads")
    async def create_thread(request: Request):
        body = _validated_body(await _body(request), _CreateBody)
        source_id = _uuid(body.get("sourceSubmissionId"))
        question = body.get("question")
        if not isinstance(question, str) or not _utf8(question) or not 2 <= len(question) <= 200 or _java_blank(question):
            raise AgentError(400, "validation_error")

        async def operation(client: UlticodeClient, owner: str):
            source, status = await _source_facts(client, source_id)
            facts = {"id": source, "status": status}
            draft = make_draft(facts, question)
            row = {
                "thread_id": str(uuid.uuid4()), "owner_id": owner, "run_id": str(uuid.uuid4()),
                "status": "awaiting_confirmation", "source_submission_id": source,
                "question": question, "source_facts": facts,
                "corpus_identity": {"scope": "sample-synthetic", "manifest": "checked-in"},
                "draft": draft, "draft_version": 1, "confirmation": {},
                "business_key": str(uuid.uuid4()), "updated_at": clock(),
            }
            created = workflow_store.create(row)
            try:
                await _checkpoint_init(created)
            except Exception:
                workflow_store.cancel(str(created["thread_id"]), owner)
                raise AgentError(503, "state_db_unavailable", 50000) from None
            return _thread_response(created, owner)
        return _response(await with_session(request, unsafe=True, operation=operation))

    @app.get("/agent/threads/{thread_id}")
    async def get_thread(thread_id: str, request: Request):
        _uuid(thread_id)
        async def operation(_: UlticodeClient, owner: str):
            return _thread_response(_owned(thread_id, owner), owner)
        return _response(await with_session(request, unsafe=False, operation=operation))

    @app.get("/agent/threads/{thread_id}/events")
    async def get_events(thread_id: str, request: Request, after: int = 0):
        _uuid(thread_id)
        if not 0 <= after <= 2**63 - 1:
            raise AgentError(400, "validation_error")
        async def operation(_: UlticodeClient, owner: str):
            _owned(thread_id, owner)
            rows, cursor = workflow_store.events(thread_id, after=after, limit=100)
            return {"events": rows, "next": cursor}
        return _response(await with_session(request, unsafe=False, operation=operation))

    @app.get("/agent/threads/{thread_id}/events/stream")
    async def stream_events(thread_id: str, request: Request, after: int = 0):
        _uuid(thread_id)
        if not 0 <= after <= 2**63 - 1:
            raise AgentError(400, "validation_error")
        async def operation(_: UlticodeClient, owner: str):
            return owner, str(_owned(thread_id, owner)["run_id"])
        owner, run_id = await with_session(request, unsafe=False, operation=operation)

        async def generate():
            cursor = after
            deadline = time.monotonic() + 30.0
            def frame(kind, payload):
                return "id: " + run_id + ":" + str(payload.get("seq", cursor)) + ":" + kind + "\nevent: " + kind + "\ndata: " + json.dumps(
                    {"runId": run_id, **payload}, ensure_ascii=False
                ) + "\n\n"
            while not await request.is_disconnected():
                row = _owned(thread_id, owner)
                if row["run_id"] != run_id:
                    yield frame("stream_closed", {"reason": "run_replaced"})
                    return
                events, cursor = workflow_store.events(thread_id, after=cursor, limit=100)
                for event in events:
                    if event["detail"].get("runId") == run_id:
                        yield frame("workflow", event)
                if cursor < int(row["event_seq"]):
                    continue
                row = _owned(thread_id, owner)
                if row["run_id"] != run_id:
                    yield frame("stream_closed", {"reason": "run_replaced"})
                    return
                if cursor < int(row["event_seq"]):
                    continue
                if row["status"] not in {"analyzing", "saving"}:
                    answer = row["analysis"].get("answer")
                    if isinstance(answer, str) and answer and not row["cancel_requested"]:
                        # Only validated, persisted answer text crosses the stream boundary.
                        yield frame("text", {"seq": row["event_seq"], "text": answer})
                    yield frame("terminal", {"status": row["status"],
                        "seq": row["event_seq"],
                        "reason": row["failure_reason"], "cancelRequested": bool(row["cancel_requested"])})
                    return
                if time.monotonic() >= deadline:
                    yield frame("stream_closed", {"reason": "stream_timeout"})
                    return
                await asyncio.sleep(0.1)

        return StreamingResponse(generate(), media_type="text/event-stream",
            headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"})

    @app.put("/agent/threads/{thread_id}/draft")
    async def edit_draft(thread_id: str, request: Request):
        _uuid(thread_id)
        body = _validated_body(await _body(request), _DraftBody)
        version, title, content = body["draftVersion"], body["title"], body["content"]
        if not 1 <= version <= 2_147_483_647:
            raise AgentError(400, "validation_error")
        title, content = _validate_draft(title, content)
        async def operation(_: UlticodeClient, owner: str):
            _owned(thread_id, owner)
            with workflow_store.thread_lock(thread_id):
                row = _reconcile(_owned(thread_id, owner), owner)

                async def execute_action(_, analysis_result):
                    if row["status"] in {"cancelled", "saved", "unknown", "saving", "failed"} or row["save_attempted"] or row["cancel_requested"]:
                        raise AgentError(409, "draft_version_conflict", 40900)
                    if row["draft_version"] != version:
                        raise AgentError(409, "draft_version_conflict", 40900)
                    if version == 2_147_483_647:
                        raise AgentError(400, "validation_error")
                    draft = dict(row["draft"])
                    draft.update({"title": title, "content": content})
                    changed = workflow_store.transition(thread_id, owner, expected_run=str(row["run_id"]), expected_version=version,
                        statuses={"awaiting_confirmation", "confirmed"}, changes={"status": "awaiting_confirmation", "draft": draft,
                        "draft_version": version + 1, "confirmation": {}}, kind="draft_edited")
                    return {"status": changed["status"], "run_id": changed["run_id"],
                            "draft_version": changed["draft_version"],
                            "action_result": {"response": _thread_response(changed, owner)}}

                result = await _checkpoint_resume(row, WorkflowAction("edit", {"draftVersion": version, "title": title, "content": content},
                    str(row["run_id"]), int(row["draft_version"])), execute_action)
                return result["action_result"]["response"]
        return _response(await with_session(request, unsafe=True, operation=operation))

    @app.post("/agent/threads/{thread_id}/analyze")
    async def analyze(thread_id: str, request: Request):
        _uuid(thread_id)
        _validated_body(await _body(request), _EmptyBody)
        async def operation(client: UlticodeClient, owner: str):
            _owned(thread_id, owner)
            with workflow_store.thread_lock(thread_id):
                row = _reconcile(_owned(thread_id, owner), owner)
                if offline_model_factory is None:
                    _require_current_gate()
                if row["status"] not in {"awaiting_confirmation", "confirmed"} or row["save_attempted"] or row["cancel_requested"]:
                    raise AgentError(409, "confirmation_mismatch", 40900)
                source, status = await _verify_source(client, row)
                facts = {"id": source, "status": status}
                version = int(row["draft_version"])
                run_id = str(uuid.uuid4())
                analyzing = workflow_store.transition(thread_id, owner, expected_run=str(row["run_id"]),
                    expected_version=version, statuses={"awaiting_confirmation", "confirmed"},
                    changes={"status": "analyzing", "run_id": run_id, "confirmation": {}, "analysis": {}},
                    kind="analysis_started")
                model = judge_model = None
                model_entered = judge_entered = False
                retrieved: list[dict[str, object]] = []
                documents = ()
                search_results: list[dict[str, object]] = []
                try:
                    async with asyncio.timeout(30.0):
                        if offline_model_factory is not None:
                            runtime_value = offline_model_factory()
                            if hasattr(runtime_value, "__await__"):
                                runtime_value = await runtime_value
                            if not isinstance(runtime_value, tuple) or len(runtime_value) not in {2, 4}:
                                raise ValueError("invalid offline runtime")
                            model, tools = runtime_value[:2]
                            if len(runtime_value) == 4:
                                documents = tuple(runtime_value[2])
                                retrieved = runtime_value[3]
                                if not isinstance(retrieved, list):
                                    raise ValueError("invalid offline evidence recorder")
                            judge_model = model
                        else:
                            _require_current_gate()
                            if expected_period is None or budget_guard is None or model_purpose != "u03_analysis":
                                raise AgentError(503, "model_budget_blocked", 50000)
                            from boundary_evaluation import SEARCH_EVIDENCE_SPEC, search_evidence_tool
                            from retrieval import load_sample_corpus
                            from ulticode_tools import TOOL_SPECS, build_tools
                            documents = load_sample_corpus()
                            specs = {**TOOL_SPECS, "search_evidence": SEARCH_EVIDENCE_SPEC}
                            model = _authorized_model_for_purpose(
                                expected_period, "u03_analysis", budget_guard, specs
                            )
                            judge_model = _authorized_model_for_purpose(
                                expected_period, "u03_citation_judge", budget_guard, {}
                            )
                            tools = build_tools(client)
                            search = search_evidence_tool(documents)

                            async def recorded_search(arguments):
                                try:
                                    result = await search(arguments)
                                    hits = result.get("hits") if isinstance(result, dict) else None
                                    if not isinstance(hits, list):
                                        raise ValueError("invalid evidence tool result")
                                    retrieved.extend(hit for hit in hits if isinstance(hit, dict))
                                    search_results.append(result)
                                    return result
                                except Exception:
                                    search_results.append({"error": "tool_failed"})
                                    raise

                            tools["search_evidence"] = recorded_search
                        def observed_tool(name, handler):
                            async def invoke(arguments):
                                def record(kind, failed=False):
                                    try:
                                        workflow_store.transition(thread_id, owner, expected_run=run_id,
                                            expected_version=version, statuses={"analyzing"}, changes={},
                                            kind=kind, detail={"toolName": name, "failed": failed})
                                    except Conflict:
                                        raise _AnalysisSuperseded from None
                                record("tool_started")
                                failed = True
                                try:
                                    result = await handler(arguments)
                                    failed = isinstance(result, dict) and "error" in result
                                    return result
                                finally:
                                    record("tool_completed", failed)
                            return invoke
                        tools = {name: observed_tool(name, handler) for name, handler in tools.items()}
                        resources = {id(value): value for value in (model, judge_model) if value is not None}
                        for resource in resources.values():
                            if hasattr(resource, "__aenter__"):
                                await resource.__aenter__()
                                if resource is model:
                                    model_entered = True
                                if resource is judge_model:
                                    judge_entered = True
                        async def execute_action(_, analysis_result):
                            parsed = parse_model_answer(analysis_result["answer"])
                            trace = analysis_result["trace"]
                            trace_results = []
                            search_index = 0
                            for event in trace:
                                if event.get("tool_name") == "search_evidence":
                                    observed = (search_results[search_index]
                                                if search_index < len(search_results)
                                                else {"error": "tool_failed"})
                                    trace_results.append({
                                        "tool": "search_evidence",
                                        "failed": bool(event.get("failed")),
                                        "result": observed,
                                    })
                                    search_index += 1
                            _verify_answer_boundary(parsed, (facts,), documents, trace, trace_results,
                                                    question=str(row["question"]))
                            citation_checks = await _verify_citations(
                                parsed, retrieved, documents, (facts,), judge_model,
                            )
                            draft = dict(row["draft"])
                            answer = parsed["text"]
                            content = str(draft["content"]) + "\n\n模型建议（待验证）：" + answer
                            _validate_draft(draft["title"], content)
                            draft.update({"content": content, "model_answer": answer,
                                          "citations": parsed["citations"], "citation_checks": citation_checks})
                            analysis = {"answer": answer, "trace": analysis_result["trace"],
                                        "citation_checks": citation_checks}
                            changed = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                expected_version=version, statuses={"analyzing"},
                                changes={"status": "awaiting_confirmation", "draft": draft,
                                         "draft_version": version + 1, "analysis": analysis},
                                kind="analysis_completed")
                            return {"status": changed["status"], "run_id": changed["run_id"],
                                    "draft_version": changed["draft_version"],
                                    "action_result": {"response": _thread_response(changed, owner)}}

                        result = await _checkpoint_resume(
                            analyzing, WorkflowAction("analyze", {}, run_id, version), execute_action,
                            {"analysis_runtime": (model, tools)},
                        )
                        return result["action_result"]["response"]
                except _AnalysisSuperseded:
                    return _thread_response(_owned(thread_id, owner), owner)
                except Conflict:
                    return _thread_response(_owned(thread_id, owner), owner)
                except AgentError:
                    current = _owned(thread_id, owner)
                    if current["status"] == "analyzing" and current["run_id"] == run_id:
                        workflow_store.transition(thread_id, owner, expected_run=run_id, expected_version=version,
                            statuses={"analyzing"}, changes={"status": "awaiting_confirmation",
                            "failure_reason": "analysis_failed"}, kind="analysis_failed")
                    raise
                except Exception:
                    current = _owned(thread_id, owner)
                    if current["status"] == "analyzing" and current["run_id"] == run_id:
                        workflow_store.transition(thread_id, owner, expected_run=run_id, expected_version=version,
                            statuses={"analyzing"}, changes={"status": "awaiting_confirmation",
                            "failure_reason": "analysis_failed"}, kind="analysis_failed")
                    raise AgentError(502, "upstream_unavailable", 50000) from None
                finally:
                    if judge_entered and judge_model is not model:
                        await judge_model.__aexit__(None, None, None)
                    if model_entered:
                        await model.__aexit__(None, None, None)
        return _response(await with_session(request, unsafe=True, operation=operation))

    if gate is not None:
        @app.post("/agent/threads/{thread_id}/confirm")
        async def confirm(thread_id: str, request: Request):
            _uuid(thread_id)
            body = _validated_body(await _body(request), _ConfirmBody)
            async def operation(client: UlticodeClient, owner: str):
                _owned(thread_id, owner)
                with workflow_store.thread_lock(thread_id):
                    _require_current_gate()
                    row = _reconcile(_owned(thread_id, owner), owner)

                    async def execute_action(_, analysis_result):
                        version = int(row["draft_version"])
                        actual = params_digest(owner, str(row["source_submission_id"]), version, str(row["draft"]["title"]), str(row["draft"]["content"]))
                        if row["status"] not in {"awaiting_confirmation", "confirmed"} or row["save_attempted"] or row["cancel_requested"]:
                            raise AgentError(409, "confirmation_mismatch", 40900)
                        if (body["draftVersion"] != version
                                or not re.fullmatch(r"[0-9a-f]{64}", body["paramsDigest"])
                                or not hmac.compare_digest(body["paramsDigest"], actual)):
                            raise AgentError(409, "confirmation_mismatch", 40900)
                        await _verify_source(client, row)
                        old = row["confirmation"]
                        if (old and old.get("paramsDigest") == actual and old.get("draftVersion") == version
                                and old.get("expiresAt", 0) > clock()):
                            confirmation = old
                        else:
                            confirmation = {"id": str(uuid.uuid4()), "action": "save_learning_plan", "draftVersion": version,
                                            "paramsDigest": actual, "expiresAt": clock() + 1800}
                        changed = workflow_store.transition(thread_id, owner, expected_run=str(row["run_id"]), expected_version=version,
                            statuses={"awaiting_confirmation", "confirmed"}, changes={"status": "confirmed", "confirmation": confirmation}, kind="confirmed")
                        return {"status": changed["status"], "run_id": changed["run_id"],
                                "draft_version": changed["draft_version"],
                                "action_result": {"response": _thread_response(changed, owner)}}

                    result = await _checkpoint_resume(row, WorkflowAction("confirm", {
                        "draftVersion": body["draftVersion"], "paramsDigest": body["paramsDigest"], "confirm": True,
                    }, str(row["run_id"]), int(row["draft_version"])), execute_action)
                    return result["action_result"]["response"]
            return _response(await with_session(request, unsafe=True, operation=operation))

        @app.post("/agent/threads/{thread_id}/save")
        async def save(thread_id: str, request: Request):
            _uuid(thread_id)
            body = _validated_body(await _body(request), _SaveBody)
            async def operation(client: UlticodeClient, owner: str):
                _owned(thread_id, owner)
                with workflow_store.thread_lock(thread_id):
                    _require_current_gate()
                    row = _reconcile(_owned(thread_id, owner), owner)
                    async def execute_action(_, analysis_result):
                        version = int(row["draft_version"])
                        confirmation = row["confirmation"]
                        if row["status"] == "saved":
                            response = _thread_response(row, owner)
                            return {"status": row["status"], "run_id": row["run_id"],
                                    "draft_version": version, "action_result": {"response": response}}
                        digest = params_digest(owner, str(row["source_submission_id"]), version,
                                               str(row["draft"]["title"]), str(row["draft"]["content"]))
                        if row["status"] != "confirmed" or confirmation.get("id") != body["confirmationId"]:
                            raise AgentError(400, "confirmation_required")
                        if row["cancel_requested"] or row["save_attempted"]:
                            raise AgentError(409, "confirmation_mismatch", 40900)
                        if clock() >= float(confirmation.get("expiresAt", 0)):
                            raise AgentError(409, "confirmation_expired", 40900)
                        if (confirmation.get("action") != "save_learning_plan"
                                or confirmation.get("draftVersion") != version
                                or confirmation.get("paramsDigest") != digest):
                            raise AgentError(409, "confirmation_mismatch", 40900)
                        await _verify_source(client, row)
                        saving = workflow_store.transition(thread_id, owner, expected_run=str(row["run_id"]),
                            expected_version=version, statuses={"confirmed"},
                            changes={"status": "saving", "save_attempted": 1}, kind="save_intent")
                        try:
                            _require_current_gate()
                            await _verify_source(client, saving)
                        except Exception:
                            latest = _owned(thread_id, owner)
                            if latest["status"] == "saving":
                                latest = workflow_store.transition(thread_id, owner,
                                    expected_run=str(saving["run_id"]), expected_version=version,
                                    statuses={"saving"}, changes={"status": "unknown",
                                    "failure_reason": "dispatch_preflight_failed"}, kind="save_unknown")
                            response = _thread_response(latest, owner)
                            return {"status": latest["status"], "run_id": latest["run_id"],
                                    "draft_version": latest["draft_version"], "action_result": {"response": response}}
                        allowed = workflow_store.dispatch_allowed(
                            thread_id, owner, str(saving["run_id"]), version,
                            confirmation_id=str(confirmation["id"]), params_digest=digest, clock=clock,
                        )
                        if not allowed:
                            latest = _owned(thread_id, owner)
                            if latest["status"] == "saving":
                                latest = workflow_store.transition(thread_id, owner,
                                    expected_run=str(saving["run_id"]), expected_version=version,
                                    statuses={"saving"}, changes={"status": "unknown",
                                    "failure_reason": "confirmation_expired"}, kind="save_unknown")
                            response = _thread_response(latest, owner)
                            return {"status": latest["status"], "run_id": latest["run_id"],
                                    "draft_version": latest["draft_version"], "action_result": {"response": response}}
                        hook_context = {"thread_id": thread_id, "run_id": str(saving["run_id"])}
                        try:
                            if fault_hook and thread_id in fault_thread_ids:
                                fault_hook("before_java_save_dispatch", hook_context)
                            vo = await client.save_learning_plan(
                                source_submission_id=str(saving["source_submission_id"]), draft_version=version,
                                title=str(saving["draft"]["title"]), content=str(saving["draft"]["content"]),
                                idempotency_key=str(saving["business_key"]),
                            )
                        except UlticodeServiceError as exc:
                            if _deterministic_business_error(exc):
                                workflow_store.transition(thread_id, owner, expected_run=str(saving["run_id"]),
                                    expected_version=version, statuses={"saving"},
                                    changes={"status": "failed", "failure_reason": "business_rejected",
                                             "retry_blocked": 1}, kind="business_save_failed", detail={"code": exc.code})
                                raise AgentError(exc.status_code, "business_rejected", exc.code) from None
                            latest = workflow_store.transition(thread_id, owner, expected_run=str(saving["run_id"]),
                                expected_version=version, statuses={"saving", "unknown"},
                                changes={"status": "unknown", "failure_reason": "save_result_unknown"}, kind="save_unknown")
                            response = _thread_response(latest, owner)
                            return {"status": latest["status"], "run_id": latest["run_id"],
                                    "draft_version": latest["draft_version"], "action_result": {"response": response}}
                        except Exception:
                            latest = _owned(thread_id, owner)
                            if latest["status"] == "saving":
                                latest = workflow_store.transition(thread_id, owner, expected_run=str(saving["run_id"]),
                                    expected_version=version, statuses={"saving"},
                                    changes={"status": "unknown", "failure_reason": "save_result_unknown"},
                                    kind="save_unknown")
                            response = _thread_response(latest, owner)
                            return {"status": latest["status"], "run_id": latest["run_id"],
                                    "draft_version": latest["draft_version"], "action_result": {"response": response}}
                        plan_id = _receipt_plan_id(vo, saving, version)
                        if plan_id is None:
                            latest = workflow_store.transition(thread_id, owner, expected_run=str(saving["run_id"]),
                                expected_version=version, statuses={"saving", "unknown"},
                                changes={"status": "unknown", "failure_reason": "receipt_mismatch",
                                         "retry_blocked": 1}, kind="receipt_mismatch")
                        else:
                            if fault_hook and thread_id in fault_thread_ids:
                                fault_hook("after_java_save_response", {**hook_context, "plan_id": plan_id})
                            latest = workflow_store.transition(thread_id, owner, expected_run=str(saving["run_id"]),
                                expected_version=version, statuses={"saving", "unknown"},
                                changes={"status": "saved", "plan_id": plan_id}, kind="plan_saved")
                        response = _thread_response(latest, owner)
                        return {"status": latest["status"], "run_id": latest["run_id"],
                                "draft_version": latest["draft_version"], "action_result": {"response": response}}

                    result = await _checkpoint_resume(row, WorkflowAction("save", {
                        "confirmationId": body["confirmationId"],
                    }, str(row["run_id"]), int(row["draft_version"])), execute_action)
                    return result["action_result"]["response"]
            return _response(await with_session(request, unsafe=True, operation=operation))

        @app.post("/agent/threads/{thread_id}/recover")
        async def recover(thread_id: str, request: Request):
            _uuid(thread_id)
            body = _validated_body(await _body(request), _RecoverBody)
            async def operation(client: UlticodeClient, owner: str):
                _owned(thread_id, owner)
                with workflow_store.thread_lock(thread_id):
                    _require_current_gate()
                    row = _reconcile(_owned(thread_id, owner), owner)
                    async def execute_action(_, analysis_result):
                        version, run_id = int(row["draft_version"]), str(row["run_id"])

                        def completed(current):
                            return {"status": current["status"], "run_id": current["run_id"],
                                    "draft_version": current["draft_version"],
                                    "action_result": {"response": _thread_response(current, owner)}}

                        if not row["save_attempted"] and row["status"] != "saved":
                            raise AgentError(409, "confirmation_mismatch", 40900)
                        await _verify_source(client, row)
                        try:
                            vo = await client.get_learning_plan_by_key(str(row["business_key"]))
                        except UlticodeServiceError as exc:
                            not_found = exc.status_code == 404 and exc.code == 40400
                            if not not_found:
                                if row["status"] == "saved":
                                    latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                        expected_version=version, statuses={"saved"},
                                        changes={"failure_reason": "readback_unavailable"}, kind="readback_unavailable")
                                    return completed(latest)
                                raise AgentError(502, "upstream_unavailable", 50000) from None
                            vo = None
                        except Exception:
                            if row["status"] == "saved":
                                latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                    expected_version=version, statuses={"saved"},
                                    changes={"failure_reason": "readback_unavailable"}, kind="readback_unavailable")
                                return completed(latest)
                            raise AgentError(502, "upstream_unavailable", 50000) from None
                        if vo is not None:
                            plan_id = _receipt_plan_id(vo, row, version)
                            if plan_id is None:
                                status = "saved" if row["status"] == "saved" else "unknown"
                                latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                    expected_version=version, statuses={"saving", "unknown", "saved"},
                                    changes={"status": status, "failure_reason": "receipt_mismatch",
                                             "retry_blocked": 1}, kind="receipt_mismatch")
                                return completed(latest)
                            if row["status"] != "saved":
                                latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                    expected_version=version, statuses={"saving", "unknown"},
                                    changes={"status": "saved", "plan_id": plan_id, "failure_reason": None},
                                    kind="plan_readback")
                            else:
                                latest = row
                            return completed(latest)
                        if row["status"] == "saved":
                            latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                expected_version=version, statuses={"saved"},
                                changes={"failure_reason": "readback_unavailable"}, kind="readback_unavailable")
                            return completed(latest)
                        if body["retry"] is not True:
                            return completed(row)
                        if row["retry_blocked"]:
                            raise AgentError(409, "idempotency_payload_mismatch", 40900)
                        confirmation = row["confirmation"]
                        digest = params_digest(owner, str(row["source_submission_id"]), version,
                                               str(row["draft"]["title"]), str(row["draft"]["content"]))
                        if (row["cancel_requested"] or row["status"] not in {"saving", "unknown"}
                                or confirmation.get("action") != "save_learning_plan"
                                or confirmation.get("expiresAt", 0) <= clock()
                                or confirmation.get("draftVersion") != version
                                or confirmation.get("paramsDigest") != digest):
                            raise AgentError(409, "confirmation_mismatch", 40900)
                        saving = workflow_store.transition(thread_id, owner, expected_run=run_id,
                            expected_version=version, statuses={"saving", "unknown"},
                            changes={"status": "saving", "save_retry_count": row["save_retry_count"] + 1},
                            kind="explicit_retry_intent")
                        try:
                            _require_current_gate()
                            await _verify_source(client, saving)
                        except Exception:
                            latest = _owned(thread_id, owner)
                            if latest["status"] == "saving":
                                latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                    expected_version=version, statuses={"saving"},
                                    changes={"status": "unknown", "failure_reason": "dispatch_preflight_failed"},
                                    kind="save_unknown")
                            return completed(latest)
                        if not workflow_store.dispatch_allowed(
                            thread_id, owner, run_id, version,
                            confirmation_id=str(confirmation["id"]), params_digest=digest, clock=clock,
                        ):
                            latest = _owned(thread_id, owner)
                            if latest["status"] == "saving":
                                latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                    expected_version=version, statuses={"saving"},
                                    changes={"status": "unknown", "failure_reason": "confirmation_expired"},
                                    kind="save_unknown")
                            return completed(latest)
                        hook_context = {"thread_id": thread_id, "run_id": run_id}
                        try:
                            if fault_hook and thread_id in fault_thread_ids:
                                fault_hook("before_java_save_dispatch", hook_context)
                            vo = await client.save_learning_plan(
                                source_submission_id=str(saving["source_submission_id"]), draft_version=version,
                                title=str(saving["draft"]["title"]), content=str(saving["draft"]["content"]),
                                idempotency_key=str(saving["business_key"]),
                            )
                        except UlticodeServiceError as exc:
                            if _deterministic_business_error(exc):
                                workflow_store.transition(thread_id, owner, expected_run=run_id,
                                    expected_version=version, statuses={"saving"},
                                    changes={"status": "failed", "failure_reason": "business_rejected",
                                             "retry_blocked": 1}, kind="business_save_failed", detail={"code": exc.code})
                                raise AgentError(exc.status_code, "business_rejected", exc.code) from None
                            latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                expected_version=version, statuses={"saving", "unknown"},
                                changes={"status": "unknown", "failure_reason": "save_result_unknown"}, kind="save_unknown")
                            return completed(latest)
                        except Exception:
                            latest = _owned(thread_id, owner)
                            if latest["status"] == "saving":
                                latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                    expected_version=version, statuses={"saving"},
                                    changes={"status": "unknown", "failure_reason": "save_result_unknown"},
                                    kind="save_unknown")
                            return completed(latest)
                        plan_id = _receipt_plan_id(vo, saving, version)
                        if plan_id is None:
                            latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                                expected_version=version, statuses={"saving", "unknown"},
                                changes={"status": "unknown", "failure_reason": "receipt_mismatch",
                                         "retry_blocked": 1}, kind="receipt_mismatch")
                            return completed(latest)
                        if fault_hook and thread_id in fault_thread_ids:
                            fault_hook("after_java_save_response", {**hook_context, "plan_id": plan_id})
                        latest = workflow_store.transition(thread_id, owner, expected_run=run_id,
                            expected_version=version, statuses={"saving", "unknown"},
                            changes={"status": "saved", "plan_id": plan_id, "failure_reason": None}, kind="plan_saved")
                        return completed(latest)

                    result = await _checkpoint_resume(row, WorkflowAction("recover", {
                        "retry": body["retry"],
                    }, str(row["run_id"]), int(row["draft_version"])), execute_action)
                    return result["action_result"]["response"]
            return _response(await with_session(request, unsafe=True, operation=operation))

    @app.post("/agent/threads/{thread_id}/cancel")
    async def cancel(thread_id: str, request: Request):
        _uuid(thread_id)
        _validated_body(await _body(request), _EmptyBody)
        async def operation(_: UlticodeClient, owner: str):
            try:
                row = workflow_store.cancel(thread_id, owner)
            except NotFound:
                raise AgentError(404, "thread_not_found", 40400) from None
            return _thread_response(row, owner)
        return _response(await with_session(request, unsafe=True, operation=operation))

    return app
