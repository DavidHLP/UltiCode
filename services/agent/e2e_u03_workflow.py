
"""Evidence-bound U02 gate issuer and opt-in U03 HTTP workflow driver.

The issuer consumes already-published DAV58/DAV53 and prior-five/budget evidence;
it never creates acceptance results. The U03 path talks only to a loopback Agent
HTTP service and requires a real local session plus interactive save confirmation.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import os
import pwd
import re
import stat
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))
from agent_service.gate import (
    GateError, current_budget_anchor, load_u02_gate, require_execution_candidate,
    validate_u02_gate_payload, validate_u03_result,
)
from authorized_budget_period import POLICY
from ulticode_client import UlticodeClient, UlticodeServiceError

_ACTIVE_AGENT_PROCESSES = set()


_SHA = re.compile(r"^[0-9a-f]{64}$")
_HEAD = re.compile(r"^[0-9a-f]{40}$")

_U03_SCENARIOS = (
    "java_same_key_same_payload",
    "java_concurrent_same_key",
    "java_same_key_payload_mismatch",
    "java_foreign_owner_read",
    "confirmation_guards",
    "kill_intent_pre_http",
    "kill_java_commit_response_lost",
    "kill_vo_received_local_not_committed",
    "restart_owner_disk_recovery",
    "cancel_old_run_fence",
)



def _private_evidence_root(path: Path) -> Path:
    if not path.is_absolute() or ".." in path.parts or path.is_symlink():
        raise ValueError("u03_evidence_directory_invalid")
    root = path.resolve(strict=True)
    if root != path:
        raise ValueError("u03_evidence_directory_invalid")
    info = root.lstat()
    if (not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid()
            or stat.S_IMODE(info.st_mode) != 0o700):
        raise ValueError("u03_evidence_directory_permissions")
    return root


def _evidence_output(root: Path, path: Path) -> Path:
    root = _private_evidence_root(root)
    if not path.is_absolute() or ".." in path.parts or path.is_symlink():
        raise ValueError("u03_result_path_invalid")
    target = path.resolve(strict=False)
    if target.parent != root:
        raise ValueError("u03_result_path_outside_evidence_root")
    parent = target.parent.resolve(strict=True)
    if parent != root:
        raise ValueError("u03_result_path_outside_evidence_root")
    return target
def _new_evidence_dir(evidence_parent: Path) -> Path:
    evidence_parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    if evidence_parent != evidence_parent.resolve(strict=True):
        raise ValueError("u03_evidence_directory_invalid")
    parent_info = evidence_parent.lstat()
    if (not stat.S_ISDIR(parent_info.st_mode) or evidence_parent.is_symlink()
            or parent_info.st_uid != os.getuid() or stat.S_IMODE(parent_info.st_mode) != 0o700):
        raise ValueError("u03_evidence_directory_invalid")
    run_dir = evidence_parent / str(uuid.uuid4())
    run_dir.mkdir(mode=0o700)
    run_info = run_dir.lstat()
    if stat.S_IMODE(run_info.st_mode) != 0o700 or run_info.st_uid != os.getuid():
        raise ValueError("u03_evidence_directory_permissions")
    return run_dir


def _private_bytes_no_clobber(path: Path, raw: bytes) -> str:
    if not path.is_absolute():
        raise ValueError("output_must_be_absolute")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC, 0o600)
    try:
        view = memoryview(raw)
        while view:
            count = os.write(fd, view)
            if count <= 0:
                raise OSError("short evidence write")
            view = view[count:]
        os.fsync(fd)
    finally:
        os.close(fd)
    _, saved = _read_json(path)
    if saved != raw:
        raise ValueError("evidence_readback_mismatch")
    return hashlib.sha256(raw).hexdigest()

def _candidate_configuration_fingerprint(root: Path) -> dict[str, str]:
    fixed = (
        "services/agent/pyproject.toml", "services/agent/uv.lock",
        "services/agent/data/corpus_manifest.json", "services/agent/data/boundary_manifest.json",
        "services/agent/data/keyword_cases.json", "services/agent/data/boundary_cases.json",
    )
    root = root.resolve(strict=True)
    manifest_path = root / "services/agent/data/corpus_manifest.json"
    if manifest_path.is_symlink():
        raise ValueError("candidate_corpus_manifest_invalid")
    manifest = json.loads(manifest_path.read_bytes(), object_pairs_hook=_json_pairs, parse_constant=_constant)
    if not isinstance(manifest, list):
        raise ValueError("candidate_corpus_manifest_invalid")
    paths = set(fixed)
    for item in manifest:
        if not isinstance(item, dict) or not isinstance(item.get("source_path"), str):
            raise ValueError("candidate_corpus_manifest_invalid")
        relative = Path(item["source_path"])
        target = (root / relative).resolve(strict=True)
        if relative.is_absolute() or ".." in relative.parts or root not in target.parents:
            raise ValueError("candidate_corpus_path_invalid")
        paths.add(relative.as_posix())
    result = {}
    for relative in sorted(paths):
        target = root / relative
        if target.is_symlink() or not target.is_file():
            raise ValueError("candidate_config_invalid")
        result[relative] = hashlib.sha256(target.read_bytes()).hexdigest()
    return result


def _candidate_inputs_ref(
    root: Path, path: Path, *, head: str, base: str, evidence_dir: Path,
) -> tuple[dict, dict[str, str]]:
    candidate, raw = _read_json(path)
    if (candidate.get("schema") != "ulticode-u04-candidate-inputs-v1"
            or candidate.get("candidate_head") != head or candidate.get("candidate_base") != base
            or candidate.get("source_fingerprint") != _candidate_fingerprint(root, head)
            or candidate.get("configuration_fingerprint") != _candidate_configuration_fingerprint(root)):
        raise ValueError("candidate_inputs_mismatch")
    target = evidence_dir / "candidate-inputs.json"
    digest = _private_bytes_no_clobber(target, raw)
    return candidate, {"path": target.relative_to(evidence_dir.parent).as_posix(), "sha256": digest}

def _scenario_evidence(
    candidate_root: Path, scenario: str, expected_head: str, gate_sha: str,
    observations: dict[str, object], *, started_at: str, completed_at: str,
    evidence_dir: Path, exit_code: int = 0, raw_receipts: list[dict[str, str]] | None = None,
    foreign_read_receipt: dict[str, str] | None = None,
) -> dict[str, str]:
    """Publish one private immutable observation, separate from the checkout."""
    if scenario not in _U03_SCENARIOS:
        raise ValueError("unknown_u03_scenario")
    root = candidate_root.resolve(strict=True)
    run_dir = evidence_dir.resolve(strict=True)
    if run_dir.is_symlink() or run_dir == root or root in run_dir.parents:
        raise ValueError("u03_evidence_directory_invalid")
    run_info = run_dir.lstat()
    if stat.S_IMODE(run_info.st_mode) != 0o700 or run_info.st_uid != os.getuid():
        raise ValueError("u03_evidence_directory_permissions")
    target = run_dir / f"{scenario}.json"
    evidence = {
        "schema": "ulticode-u03-scenario-evidence-v1", "scenario": scenario,
        "candidate_head": expected_head, "u02_gate_sha256": gate_sha,
        "started_at": started_at, "completed_at": completed_at,
        "exit_code": exit_code, "observations": observations,
    }
    if raw_receipts:
        evidence["raw_receipts"] = raw_receipts
    if foreign_read_receipt:
        evidence["foreign_read_receipt"] = foreign_read_receipt
    digest = _private_no_clobber(target, evidence)
    return {"path": target.relative_to(run_dir.parent).as_posix(), "sha256": digest}


def _validated_scenario_status(
    reference: dict[str, str], *, scenario: str, evidence_root: Path,
    head: str, gate_sha: str,
) -> str:
    from agent_service.gate import _check_u03_scenario, _verified_reference

    evidence, _ = _verified_reference(reference, root=evidence_root, label="u03_evidence")
    _check_u03_scenario(evidence, scenario, head, gate_sha, evidence_root=evidence_root)
    return "PASS"


def _record_validated_scenario(
    scenarios: dict[str, dict], reference: dict[str, str], *, scenario: str,
    evidence_root: Path, head: str, gate_sha: str,
) -> dict:
    item = {
        "scenario": scenario,
        "status": _validated_scenario_status(
            reference, scenario=scenario, evidence_root=evidence_root, head=head, gate_sha=gate_sha,
        ),
        "evidence": reference,
    }
    scenarios[scenario] = item
    return item
def _accepted_u03_result(
    *, root: Path, evidence_root: Path, head: str, base: str, gate_sha256: str,
    candidate_inputs_sha256: str, candidate_inputs: dict[str, str],
    source_fingerprint: dict[str, str], started_at: str, completed_at: str,
    scenarios: dict[str, dict], receipts: list[dict], coverage: dict[str, object],
    exit_code: int, deadline_seconds: int,
) -> dict:
    if set(scenarios) != set(_U03_SCENARIOS):
        raise ValueError("u03_scenario_set_incomplete")
    rows = []
    for name in _U03_SCENARIOS:
        item = scenarios[name]
        if not isinstance(item, dict) or item.get("scenario") != name or item.get("status") != "PASS":
            raise ValueError("u03_scenario_set_incomplete")
        rows.append(item)
    payload = {
        "schema": "ulticode-u03-workflow-result-v1", "status": "PASS",
        "candidate_head": head, "candidate_base": base, "u02_gate_sha256": gate_sha256,
        "candidate_inputs_sha256": candidate_inputs_sha256, "candidate_inputs": candidate_inputs,
        "source_fingerprint": source_fingerprint,
        "configuration_fingerprint": _candidate_configuration_fingerprint(root),
        "started_at": started_at, "completed_at": completed_at,
        "deadline_seconds": deadline_seconds, "exit_code": exit_code,
        "coverage": coverage,
        "scenarios": rows, "receipts": receipts,
    }
    return validate_u03_result(
        payload, expected_head=head, expected_base=base, u02_gate_sha256=gate_sha256,
        candidate_root=root, evidence_root=evidence_root,
    )


class _JavaReceiptTransport(httpx.AsyncBaseTransport):
    """Keep real exchanges in memory; persist only redacted fingerprints."""

    def __init__(self, capture_path: Path | None = None) -> None:
        self._inner = httpx.AsyncHTTPTransport(retries=0)
        self.capture_path = capture_path
        self.posts: list[dict[str, object]] = []
        self.principal_evidence: dict[str, object] | None = None
        self.foreign_read: dict[str, object] | None = None
        self.readback_fingerprints: dict[str, str] = {}

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        response = await self._inner.handle_async_request(request)
        path = request.url.path
        if request.method == "GET":
            await response.aread()
            envelope = json.loads(response.content, object_pairs_hook=_json_pairs, parse_constant=_constant)
            if path.rstrip("/") == "/auth/me":
                user = envelope.get("data", {}).get("user", {}) if isinstance(envelope, dict) else {}
                self.principal_evidence = {
                    "user_id": user.get("id"), "response_status": response.status_code,
                    "is_active": user.get("is_active"), "is_banned": user.get("is_banned"),
                    "trace_id": envelope.get("traceId") if isinstance(envelope, dict) else None,
                }
            elif path.startswith("/learning-plans/by-key/"):
                key = path.rsplit("/", 1)[-1]
                self.readback_fingerprints[key] = hashlib.sha256(response.content).hexdigest()
                if response.status_code != 200:
                    self.foreign_read = {
                        "request_path": "/learning-plans/by-key/{redacted}",
                        "request_path_business_key_sha256": hashlib.sha256(key.encode()).hexdigest(),
                        "http_status": response.status_code,
                        "response_body_raw": json.dumps({
                            "code": envelope.get("code"), "message": "redacted",
                            "data": None, "traceId": envelope.get("traceId"),
                        }, ensure_ascii=False, separators=(",", ":")),
                    }
        if request.method == "POST" and path.rstrip("/") == "/learning-plans":
            request_body = json.loads(request.content, object_pairs_hook=_json_pairs, parse_constant=_constant)
            await response.aread()
            envelope = json.loads(response.content, object_pairs_hook=_json_pairs, parse_constant=_constant)
            captured = {
                "request_id": str(uuid.uuid4()), "business_key": request.headers.get("Idempotency-Key"),
                "owner_sha256": hashlib.sha256(
                    str((self.principal_evidence or {}).get("user_id", "")).encode()
                ).hexdigest(),
                "request_payload": request_body, "request_wire_sha256": hashlib.sha256(request.content).hexdigest(),
                "http_status": response.status_code,
                "http_code": envelope.get("code") if isinstance(envelope, dict) else None,
                "response_vo": envelope.get("data") if isinstance(envelope, dict) else None,
                "response_payload_sha256": hashlib.sha256(response.content).hexdigest(),
            }
            self.posts.append(captured)
            if self.capture_path is not None:
                _private_no_clobber(self.capture_path, _safe_java_exchange(captured))
        return response

    async def aclose(self) -> None:
        await self._inner.aclose()


def _field_digest(value: object) -> dict[str, object]:
    if not isinstance(value, str):
        raise ValueError("u03_java_payload_shape_invalid")
    return {"sha256": hashlib.sha256(value.encode("utf-8")).hexdigest(), "codepoints": len(value)}


def _payload_projection(payload: dict[str, object]) -> dict[str, object]:
    if "title_sha256" in payload:
        return {key: payload[key] for key in (
            "sourceSubmissionId", "draftVersion", "title_sha256", "title_codepoints",
            "content_sha256", "content_codepoints",
        )}
    return {
        "sourceSubmissionId": payload.get("sourceSubmissionId"),
        "draftVersion": payload.get("draftVersion"),
        "title_sha256": _field_digest(payload.get("title"))["sha256"],
        "title_codepoints": _field_digest(payload.get("title"))["codepoints"],
        "content_sha256": _field_digest(payload.get("content"))["sha256"],
        "content_codepoints": _field_digest(payload.get("content"))["codepoints"],
    }


def _vo_projection(vo: dict[str, object]) -> dict[str, object]:
    return {"id": vo.get("id"), **_payload_projection(vo)}


def _projection_sha256(value: dict[str, object]) -> str:
    return hashlib.sha256(json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
    ).encode("utf-8")).hexdigest()


def _safe_java_exchange(post: dict[str, object]) -> dict[str, object]:
    payload = _payload_projection(post["request_payload"])
    owner_sha = post["owner_sha256"]
    projection = _projection_sha256({"owner_sha256": owner_sha, **payload})
    vo = post.get("response_vo")
    response_projection = _vo_projection(vo) if isinstance(vo, dict) else None
    return {
        "schema": "ulticode-u03-java-exchange-v1", "request_id": post["request_id"],
        "business_key_sha256": hashlib.sha256(str(post["business_key"]).encode()).hexdigest(),
        "owner_sha256": owner_sha, "request_payload": payload,
        "request_payload_sha256": post["request_wire_sha256"],
        "request_projection_sha256": projection,
        "http_status": post["http_status"], "http_code": post["http_code"],
        "response_vo": response_projection,
        "response_vo_sha256": _projection_sha256(response_projection) if response_projection else None,
        "response_payload_sha256": post["response_payload_sha256"],
    }

def _write_java_receipts(
    *, scenario: str, posts: list[dict[str, object]], owner_id: str, source_id: str,
    thread_id: str | None, run_id: str | None, head: str, gate_sha: str,
    evidence_dir: Path, readbacks: dict[str, tuple[int, dict[str, object]]],
    principal_evidence: dict[str, object], readback_fingerprints: dict[str, str],
    response_received: bool = True,
) -> list[dict[str, str]]:
    refs = []
    owner_sha = hashlib.sha256(owner_id.encode()).hexdigest()
    principal = {
        "user_id_sha256": owner_sha, "response_status": principal_evidence.get("response_status"),
        "is_active": principal_evidence.get("is_active"), "is_banned": principal_evidence.get("is_banned"),
        "trace_id": principal_evidence.get("trace_id"),
    }
    if (principal_evidence.get("user_id") != owner_id or principal["response_status"] != 200
            or principal["is_active"] is not True or principal["is_banned"] is not False
            or not isinstance(principal["trace_id"], str) or not principal["trace_id"]):
        raise ValueError("u03_owner_principal_evidence_invalid")
    for index, post in enumerate(posts):
        key, payload = post.get("business_key"), post.get("request_payload")
        if not isinstance(payload, dict):
            raise ValueError("u03_java_receipt_capture_invalid")
        if key is None:
            key = next((value for value in readbacks
                        if hashlib.sha256(value.encode()).hexdigest() == post.get("business_key_sha256")), None)
        if not isinstance(key, str):
            raise ValueError("u03_java_receipt_capture_invalid")
        successful = post.get("http_status") == 200
        readback_status, readback_vo = readbacks[key] if successful else (None, None)
        request_projection = _payload_projection(payload)
        response_vo = post.get("response_vo")
        response_projection = _vo_projection(response_vo) if isinstance(response_vo, dict) else None
        readback_projection = _vo_projection(readback_vo) if isinstance(readback_vo, dict) else None
        if successful and (
            not isinstance(response_projection, dict) or not isinstance(readback_projection, dict)
            or response_projection != {"id": response_projection.get("id"), **request_projection}
            or readback_projection != response_projection
        ):
            raise ValueError("u03_java_receipt_readback_mismatch")
        if not successful and (post.get("http_status") != 409 or post.get("http_code") != 40900):
            raise ValueError("u03_java_conflict_receipt_invalid")
        request_projection_sha = post.get("request_projection_sha256") or _projection_sha256({
            "owner_sha256": owner_sha, **request_projection,
        })
        raw = {
            "schema": "ulticode-u03-java-receipt-v1", "scenario": scenario,
            "candidate_head": head, "u02_gate_sha256": gate_sha,
            "operation": "save_learning_plan", "request_id": post["request_id"],
            "thread_id": thread_id, "run_id": run_id, "owner_sha256": owner_sha,
            "principal_evidence": principal,
            "business_key_sha256": hashlib.sha256(key.encode()).hexdigest(),
            "request_payload": request_projection,
            "request_payload_sha256": post.get("request_wire_sha256", post.get("request_payload_sha256")),
            "request_projection_sha256": request_projection_sha,
            "response_received": response_received if successful else True,
            "http_status": post["http_status"], "http_code": post["http_code"],
            "response_vo": response_projection if response_received and successful else None,
            "response_vo_sha256": _projection_sha256(response_projection) if response_projection and response_received else None,
            "response_payload_sha256": post.get("response_payload_sha256") if response_received else None,
            "readback_status": readback_status, "readback_vo": readback_projection,
            "readback_vo_sha256": _projection_sha256(readback_projection) if readback_projection else None,
            "readback_payload_sha256": readback_fingerprints.get(key) if successful else None,
            "business_rows": 1, "java_post_count": index + 1,
        }
        target = evidence_dir / f"{scenario}.receipt-{index + 1}.json"
        digest = _private_no_clobber(target, raw)
        refs.append({"path": target.relative_to(evidence_dir.parent).as_posix(), "sha256": digest})
    return refs
class _DropCommittedSaveTransport(httpx.AsyncBaseTransport):
    """Lose exactly one real Java save response after server commit."""

    def __init__(self, inner: httpx.AsyncBaseTransport | None = None, marker: Path | None = None) -> None:
        self._inner = inner or httpx.AsyncHTTPTransport(retries=0)
        self._marker = marker
        self.committed_response_dropped = False

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        response = await self._inner.handle_async_request(request)
        if (not self.committed_response_dropped and request.method == "POST"
                and request.url.path.rstrip("/") == "/learning-plans"):
            await response.aread()
            if response.status_code < 500:
                self.committed_response_dropped = True
                if self._marker is not None:
                    body = json.loads(response.content, object_pairs_hook=_json_pairs, parse_constant=_constant)
                    data = body.get("data") if isinstance(body, dict) else None
                    _private_no_clobber(self._marker, {
                        "schema": "ulticode-u03-response-loss-marker-v1",
                        "http_status": response.status_code,
                        "result_code": body.get("code") if isinstance(body, dict) else None,
                        "planId": data.get("id") if isinstance(data, dict) else None,
                        "businessKeySha256": hashlib.sha256(
                            request.headers.get("Idempotency-Key", "").encode()
                        ).hexdigest(),
                    })
                await response.aclose()
                raise httpx.ReadError("U03 isolated response-drop fault", request=request)
        return response

    async def aclose(self) -> None:
        await self._inner.aclose()

def _kill_at_fault(point: str, thread_id: str, marker: Path):
    """Build runner-only app hook; exact owned thread and point required."""
    if point not in {"before_java_save_dispatch", "after_java_save_response"}:
        raise ValueError("unsupported_u03_fault_point")
    if not marker.is_absolute():
        raise ValueError("fault_marker_must_be_absolute")

    def hook(actual_point: str, context: dict[str, object]) -> None:
        if actual_point != point or context.get("thread_id") != thread_id:
            return
        payload = {"schema": "ulticode-u03-fault-marker-v1", "point": point,
                   "thread_id": thread_id, "run_id": context.get("run_id"),
                   "plan_id": context.get("plan_id")}
        _private_no_clobber(marker, payload)
        os._exit(86)

    return hook

def _agent_child(
    state_path: str, gate_path: str, head: str, expected_base: str, agent_base: str,
    app_base: str, auth_base: str, candidate_root: str, fault_point: str | None,
    fault_thread_id: str | None, marker_path: str, drop_java_response: bool,
    clock_offset: float, period_values: tuple[str, str, str] | None,
    guard_path: str | None, guard_sha256: str | None, model_purpose: str | None,
) -> None:
    import time
    from agent_service.app import create_app
    guard = None
    expected_period = None
    if period_values is not None:
        from authorized_budget_period import POLICY_ID, PeriodIdentity
        from dav58_live_guard import IncrementalGuard
        from model_budget import authorized_model
        from agent_service.gate import current_budget_anchor

        expected_period = PeriodIdentity(
            period_id=period_values[0], identity=period_values[1], config_sha256=period_values[2],
            policy_id=POLICY_ID,
        )
        _, budget = authorized_model(expected_period)
        snapshot = budget.snapshot()
        anchor = current_budget_anchor()
        if (guard_path is None or guard_sha256 is None or model_purpose != "u03_analysis"
                or anchor.get("identity") != expected_period.identity
                or anchor.get("config_sha256") != expected_period.config_sha256
                or snapshot.get("period_identity") != expected_period.identity
                or snapshot.get("config_sha256") != expected_period.config_sha256
                or snapshot.get("attempts") != anchor.get("attempts")
                or snapshot.get("actual_micro_usd") != anchor.get("actual_micro_usd")):
            raise ValueError("u03_runtime_budget_binding_changed")
        private_guard_path = Path(guard_path)
        info = private_guard_path.lstat()
        raw = private_guard_path.read_bytes()
        if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or stat.S_IMODE(info.st_mode) != 0o600
                or hashlib.sha256(raw).hexdigest() != guard_sha256):
            raise ValueError("u03_shared_guard_changed")
        guard = IncrementalGuard(
            private_guard_path, resume_sha256=guard_sha256,
            period_identity=expected_period.identity, config_sha256=expected_period.config_sha256,
        )
        if (len(guard.state["receipts"]) != snapshot["attempts"]
                or guard.state["settled_peak_micro_usd"] != snapshot["actual_micro_usd"]
                or guard.state["pending_micro_usd"] or guard.state["halted"]):
            guard.close()
            raise ValueError("u03_shared_guard_budget_mismatch")

    def client_factory(*, access_token: str, csrf_token: str | None):
        base_marker = Path(marker_path)
        capture_path = base_marker.with_name(base_marker.stem + ".java-exchange.json")
        recorder = _JavaReceiptTransport(capture_path=capture_path)
        transport = _DropCommittedSaveTransport(inner=recorder, marker=base_marker) if drop_java_response else recorder
        return UlticodeClient.for_session(
            app_base, auth_base, access_token=access_token, csrf_token=csrf_token, transport=transport,
        )

    hook = _kill_at_fault(fault_point, fault_thread_id, Path(marker_path)) if fault_point else None
    try:
        app = create_app(
            state_path=state_path, client_factory=client_factory,
            u02_gate_path=gate_path, expected_head=head, expected_base=expected_base,
            candidate_root=candidate_root, trusted_origin=agent_base, fault_hook=hook,
            fault_thread_ids=frozenset({fault_thread_id}) if fault_thread_id else frozenset(),
            expected_period=expected_period, budget_guard=guard, model_purpose=model_purpose,
            clock=lambda: time.time() + clock_offset,
        )
        import uvicorn

        parsed = urlsplit(agent_base)
        server = uvicorn.Server(uvicorn.Config(
            app, host=parsed.hostname, port=parsed.port, log_level="critical",
            access_log=False, lifespan="on",
        ))
        asyncio.run(server.serve())
    finally:
        if guard is not None:
            guard.close()


def _agent_base() -> str:
    import socket

    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    return f"http://127.0.0.1:{port}"


def _agent_state_path() -> Path:
    home = Path(pwd.getpwuid(os.getuid()).pw_dir)
    return home / ".local/state/ulticode/u03-e2e" / str(uuid.uuid4()) / "state.sqlite3"


async def _start_agent(
    *, state_path: Path, gate_path: Path, head: str, expected_base: str,
    agent_base: str, app_base: str, auth_base: str, candidate_root: Path,
    marker_path: Path, fault_point: str | None = None, fault_thread_id: str | None = None,
    drop_java_response: bool = False, clock_offset: float = 0,
    period_values: tuple[str, str, str] | None = None, guard_path: Path | None = None,
    guard_sha256: str | None = None, model_purpose: str | None = None,
):
    import multiprocessing

    process = multiprocessing.get_context("fork").Process(
        target=_agent_child,
        args=(str(state_path), str(gate_path), head, expected_base, agent_base,
              app_base, auth_base, str(candidate_root), fault_point, fault_thread_id,
              str(marker_path), drop_java_response, clock_offset, period_values,
              str(guard_path) if guard_path else None, guard_sha256, model_purpose),
        daemon=True,
    )
    process.start()
    _ACTIVE_AGENT_PROCESSES.add(process)
    probe = httpx.AsyncClient(
        base_url=agent_base, timeout=httpx.Timeout(0.5), follow_redirects=False, trust_env=False,
    )
    deadline = asyncio.get_running_loop().time() + 30
    try:
        while asyncio.get_running_loop().time() < deadline:
            if not process.is_alive():
                raise RuntimeError("u03_agent_process_exited")
            try:
                response = await probe.get("/agent/threads/11111111-1111-4111-8111-111111111111")
                if response.status_code in {400, 401, 404}:
                    return process
            except httpx.HTTPError:
                pass
            await asyncio.sleep(0.05)
    except BaseException:
        _stop_agent(process)
        raise
    finally:
        await probe.aclose()
    _stop_agent(process)
    raise TimeoutError("u03_agent_ready_timeout")


def _shared_guard_path(identity) -> Path:
    from model_budget import _authorization_slot

    return _authorization_slot() / "accounting" / f"dav58-increment-{identity.identity}.json"


def _read_existing_guard(path: Path) -> tuple[dict, str]:
    if not path.is_absolute():
        raise ValueError("u03_guard_path_must_be_absolute")
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or stat.S_IMODE(info.st_mode) != 0o600:
        raise ValueError("u03_shared_guard_not_private")
    value, raw = _read_json(path)
    if (value.get("halted") is not False or value.get("pending_micro_usd") != 0
            or not isinstance(value.get("receipts"), list)):
        raise ValueError("u03_shared_guard_unresolved")
    return value, hashlib.sha256(raw).hexdigest()


def _u03_runtime_binding(args: argparse.Namespace):
    from authorized_budget_period import POLICY_ID, POLICY as PERIOD_POLICY, PeriodIdentity
    purposes = {"u03_analysis", "u03_citation_judge"}
    if not purposes <= set(PERIOD_POLICY["lanes"]):
        raise ValueError("model_budget_purpose_blocked")
    from model_budget import authorized_model
    from agent_service.gate import current_budget_anchor

    values = (args.period_id, args.period_identity, args.config_sha256)
    if any(not isinstance(value, str) or not value.strip() for value in values):
        raise ValueError("model_budget_binding_required")
    expected = PeriodIdentity(
        period_id=args.period_id, identity=args.period_identity, config_sha256=args.config_sha256,
        policy_id=POLICY_ID,
    )
    _, budget = authorized_model(expected)
    snapshot, anchor = budget.snapshot(), current_budget_anchor()
    if (anchor.get("identity") != expected.identity or anchor.get("period_id") != expected.period_id
            or anchor.get("config_sha256") != expected.config_sha256 or anchor.get("policy_id") != expected.policy_id
            or snapshot.get("period_identity") != expected.identity
            or snapshot.get("config_sha256") != expected.config_sha256
            or snapshot.get("attempts") != anchor.get("attempts")
            or snapshot.get("actual_micro_usd") != anchor.get("actual_micro_usd")
            or snapshot.get("state") != "active" or snapshot.get("sql_gate") != "active"
            or snapshot.get("halted") or snapshot.get("unknown_usage_attempts")
            or snapshot.get("unsettled_attempts")):
        raise ValueError("u03_budget_anchor_mismatch")
    guard_path = _shared_guard_path(expected)
    guard_data, guard_sha = _read_existing_guard(guard_path)
    if (guard_data.get("period_identity") != expected.identity
            or guard_data.get("config_sha256") != expected.config_sha256
            or len(guard_data["receipts"]) != anchor.get("attempts")
            or guard_data.get("settled_peak_micro_usd") != anchor.get("actual_micro_usd")):
        raise ValueError("u03_guard_prefix_mismatch")
    return (expected.period_id, expected.identity, expected.config_sha256), guard_path, guard_sha


async def start_u03_agent(
    *, state_path: Path, gate_path: Path, head: str, expected_base: str,
    agent_base: str, app_base: str, auth_base: str, candidate_root: Path,
    marker_path: Path, expected_period, budget_guard_path: Path, budget_guard_sha256: str,
):
    """Start isolated API using only caller-validated shared budget state."""
    if not re.fullmatch(r"[0-9a-f]{64}", budget_guard_sha256):
        raise ValueError("u03_guard_digest_invalid")
    return await _start_agent(
        state_path=state_path, gate_path=gate_path, head=head, expected_base=expected_base,
        agent_base=agent_base, app_base=app_base, auth_base=auth_base,
        candidate_root=candidate_root, marker_path=marker_path,
        period_values=(expected_period.period_id, expected_period.identity, expected_period.config_sha256),
        guard_path=budget_guard_path, guard_sha256=budget_guard_sha256, model_purpose="u03_analysis",
    )

def _stop_agent(process, *, expected_exit: int | None = None) -> int:
    if process.is_alive():
        process.terminate()
    process.join(10)
    if process.is_alive():
        raise RuntimeError("u03_agent_shutdown_timeout")
    code = process.exitcode if process.exitcode is not None else -1
    _ACTIVE_AGENT_PROCESSES.discard(process)
    if expected_exit is not None and code != expected_exit:
        raise RuntimeError("u03_fault_process_exit_mismatch")
    return code


def _stop_active_agents() -> None:
    first_error = None
    for process in tuple(_ACTIVE_AGENT_PROCESSES):
        try:
            _stop_agent(process)
        except BaseException as error:
            first_error = first_error or error
    if first_error is not None:
        raise first_error


async def _create_confirmed_thread(session: httpx.AsyncClient, source_id: str) -> dict:
    created = await session.post("/agent/threads", json={
        "sourceSubmissionId": source_id,
        "question": "Recover a confirmed learning plan after an isolated process restart.",
    })
    code, data = _envelope(created)
    thread_id = data.get("threadId")
    if created.status_code != 200 or code != 0 or not isinstance(thread_id, str):
        raise ValueError("fault_case_thread_create_failed")
    confirmed = await session.post(f"/agent/threads/{thread_id}/confirm", json={
        "draftVersion": data["draft"]["draftVersion"],
        "paramsDigest": data["paramsDigest"],
        "confirm": True,
    })
    confirm_code, confirm_data = _envelope(confirmed)
    if confirmed.status_code != 200 or confirm_code != 0:
        raise ValueError("fault_case_confirmation_failed")
    state_response = await session.get(f"/agent/threads/{thread_id}")
    state_code, state = _envelope(state_response)
    if state_response.status_code != 200 or state_code != 0 or not state.get("runId"):
        raise ValueError("fault_case_initial_state_unavailable")
    return {"thread_id": thread_id, "run_id": state["runId"],
            "version": data["draft"]["draftVersion"],
            "confirmation_id": confirm_data["confirmation"]["id"]}



def _local_workflow_record(state_path: Path, thread_id: str) -> dict[str, object]:
    import sqlite3

    info = state_path.stat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or stat.S_IMODE(info.st_mode) != 0o600:
        raise ValueError("u03_state_file_not_private")
    connection = sqlite3.connect(f"{state_path.as_uri()}?mode=ro", uri=True, timeout=5)
    try:
        check = connection.execute("PRAGMA quick_check").fetchone()
        if check != ("ok",):
            raise ValueError("u03_state_quick_check_failed")
        row = connection.execute(
            "SELECT thread_id,run_id,status,business_key,plan_id,save_attempted FROM threads WHERE thread_id=?",
            (thread_id,),
        ).fetchone()
        if row is None:
            raise ValueError("u03_state_thread_missing")
        return {"threadId": row[0], "runId": row[1], "status": row[2], "businessKey": row[3],
                "businessKeySha256": hashlib.sha256(str(row[3]).encode()).hexdigest(),
                "planId": row[4], "saveAttempted": bool(row[5]), "sqliteQuickCheck": check[0]}
    finally:
        connection.close()


async def _save_crash_scenario(
    *, scenario: str, point: str | None, drop_response: bool, state_path: Path,
    gate_path: Path, head: str, expected_base: str, candidate_root: Path,
    app_base: str, auth_base: str, access: str, csrf: str, foreign_access: str,
    foreign_csrf: str, source_id: str, evidence_dir: Path, gate_sha: str,
) -> tuple[dict[str, object], dict[str, object] | None]:
    agent_base = _agent_base()
    marker_path = evidence_dir / f"{scenario}.fault.json"
    initial = await _start_agent(
        state_path=state_path, gate_path=gate_path, head=head, expected_base=expected_base,
        agent_base=agent_base, app_base=app_base, auth_base=auth_base,
        candidate_root=candidate_root, marker_path=marker_path,
    )
    headers = {"X-CSRF-Token": csrf, "Origin": agent_base}
    async with httpx.AsyncClient(base_url=agent_base, cookies={"access_token": access, "csrf_token": csrf},
                                 headers=headers, timeout=httpx.Timeout(30, connect=5),
                                 follow_redirects=False, trust_env=False) as session:
        thread = await _create_confirmed_thread(session, source_id)
    _stop_agent(initial)
    pre = _local_workflow_record(state_path, thread["thread_id"])
    key = str(pre["businessKey"])
    key_sha = hashlib.sha256(key.encode()).hexdigest()

    agent_base = _agent_base()
    child = await _start_agent(
        state_path=state_path, gate_path=gate_path, head=head, expected_base=expected_base,
        agent_base=agent_base, app_base=app_base, auth_base=auth_base,
        candidate_root=candidate_root, marker_path=marker_path,
        fault_point=point, fault_thread_id=thread["thread_id"], drop_java_response=drop_response,
    )
    child_pid = child.pid
    headers = {"X-CSRF-Token": csrf, "Origin": agent_base}
    async with httpx.AsyncClient(base_url=agent_base, cookies={"access_token": access, "csrf_token": csrf},
                                 headers=headers, timeout=httpx.Timeout(30, connect=5),
                                 follow_redirects=False, trust_env=False) as session:
        try:
            response = await session.post(
                f"/agent/threads/{thread['thread_id']}/save",
                json={"confirmationId": thread["confirmation_id"]},
            )
            code, body = _envelope(response)
        except httpx.HTTPError:
            code, body = None, {}

    if point:
        child.join(10)
        if child.is_alive() or child.exitcode != 86 or not marker_path.exists():
            _stop_agent(child)
            raise ValueError("u03_fault_process_did_not_exit_at_expected_point")
        child_exit = child.exitcode
        _stop_agent(child, expected_exit=86)
    else:
        child_exit = 0
        expected_live_status = "unknown" if drop_response else "saved"
        if not isinstance(body, dict) or body.get("status") != expected_live_status or code != 0:
            _stop_agent(child)
            raise ValueError("u03_save_result_not_expected")
        _stop_agent(child)

    agent_base = _agent_base()
    restarted = await _start_agent(
        state_path=state_path, gate_path=gate_path, head=head, expected_base=expected_base,
        agent_base=agent_base, app_base=app_base, auth_base=auth_base,
        candidate_root=candidate_root, marker_path=marker_path,
    )
    restart_pid = restarted.pid
    headers = {"X-CSRF-Token": csrf, "Origin": agent_base}
    async with httpx.AsyncClient(base_url=agent_base, cookies={"access_token": access, "csrf_token": csrf},
                                 headers=headers, timeout=httpx.Timeout(30, connect=5),
                                 follow_redirects=False, trust_env=False) as owner_session:
        recovered = await owner_session.post(
            f"/agent/threads/{thread['thread_id']}/recover", json={"retry": False},
        )
        recover_code, recovered_data = _envelope(recovered)
        foreign_headers = {"X-CSRF-Token": foreign_csrf, "Origin": agent_base}
        async with httpx.AsyncClient(base_url=agent_base,
                                     cookies={"access_token": foreign_access, "csrf_token": foreign_csrf},
                                     headers=foreign_headers, timeout=httpx.Timeout(30, connect=5),
                                     follow_redirects=False, trust_env=False) as foreign_session:
            foreign_read = await foreign_session.get(f"/agent/threads/{thread['thread_id']}")
    if recovered.status_code != 200 or recover_code != 0:
        _stop_agent(restarted)
        raise ValueError("u03_restart_recovery_failed")
    foreign_status = foreign_read.status_code
    if foreign_status != 404:
        _stop_agent(restarted)
        raise ValueError("u03_foreign_thread_read_allowed")
    expected_status = "unknown" if point == "before_java_save_dispatch" else "saved"
    if recovered_data.get("status") != expected_status:
        _stop_agent(restarted)
        raise ValueError("u03_restart_status_mismatch")
    receipt = None
    if expected_status == "saved":
        owner_capture = _JavaReceiptTransport()
        async with UlticodeClient.for_session(
            app_base, auth_base, access_token=access, csrf_token=csrf, transport=owner_capture,
        ) as java_client:
            owner_id = await java_client.principal()
            vo = await java_client.get_learning_plan_by_key(key)
        plan_id = vo.get("id")
        if plan_id != (recovered_data.get("receipt") or {}).get("planId"):
            _stop_agent(restarted)
            raise ValueError("u03_restart_receipt_mismatch")
        exchange_path = marker_path.with_name(marker_path.stem + ".java-exchange.json")
        exchange, _ = _read_json(exchange_path)
        raw_refs = _write_java_receipts(
            scenario=scenario, posts=[exchange], owner_id=owner_id, source_id=source_id,
            thread_id=thread["thread_id"], run_id=thread["run_id"], head=head, gate_sha=gate_sha,
            evidence_dir=evidence_dir, readbacks={key: (200, vo)},
            principal_evidence=owner_capture.principal_evidence,
            readback_fingerprints=owner_capture.readback_fingerprints,
            response_received=not drop_response,
        )
        receipt = {"scenario": scenario, "planId": plan_id, "threadId": thread["thread_id"],
                   "runId": thread["run_id"], "businessKeySha256": key_sha,
                   "receiptSha256": raw_refs[0]["sha256"], "raw_refs": raw_refs}
        by_key_status = 200
    else:
        async with UlticodeClient.for_session(app_base, auth_base, access_token=access,
                                              csrf_token=csrf) as java_client:
            try:
                await java_client.get_learning_plan_by_key(key)
            except UlticodeServiceError as error:
                if error.status_code != 404 or error.code != 40400:
                    _stop_agent(restarted)
                    raise ValueError("u03_intent_pre_http_by_key_unexpected")
                by_key_status = error.status_code
            else:
                _stop_agent(restarted)
                raise ValueError("u03_intent_pre_http_found_business_write")
    marker = None
    if marker_path.exists():
        marker, _ = _read_json(marker_path)
        if point and (marker.get("schema") != "ulticode-u03-fault-marker-v1"
                      or marker.get("point") != point or marker.get("thread_id") != thread["thread_id"]):
            _stop_agent(restarted)
            raise ValueError("u03_fault_marker_mismatch")
        if drop_response and (marker.get("schema") != "ulticode-u03-response-loss-marker-v1"
                              or marker.get("businessKeySha256") != key_sha
                              or marker.get("planId") != (receipt or {}).get("planId")):
            _stop_agent(restarted)
            raise ValueError("u03_response_loss_marker_mismatch")
    post_state = _local_workflow_record(state_path, thread["thread_id"])
    exchange_path = marker_path.with_name(marker_path.stem + ".java-exchange.json")
    java_post_count, owner_sha = 0, None
    if exchange_path.exists():
        exchange, _ = _read_json(exchange_path)
        if exchange.get("schema") == "ulticode-u03-java-exchange-v1":
            java_post_count, owner_sha = 1, exchange.get("owner_sha256")
    if receipt:
        receipt_doc, _ = _read_json(evidence_dir.parent / receipt["raw_refs"][0]["path"])
        owner_sha = receipt_doc["owner_sha256"]
        if len(receipt["raw_refs"]) != java_post_count:
            _stop_agent(restarted)
            raise ValueError("u03_java_capture_count_mismatch")
    _stop_agent(restarted)
    return ({
        "fault_point": point, "child_exit": child_exit, "restart_by_key_status": by_key_status,
        "recovered_status": recovered_data.get("status"), "foreign_read_status": foreign_status,
        "owner_read_status": recovered.status_code, "business_rows": int(bool(receipt)),
        "java_post_count": java_post_count,
        "java_commit_observed": bool(drop_response and java_post_count == 1),
        "response_lost": bool(drop_response and java_post_count == 1),
        "java_vo_received": bool(point == "after_java_save_response" and marker and marker.get("plan_id")),
        "restart_pid_changed": child_pid != restart_pid,
        "sqlite_quick_check": post_state["sqliteQuickCheck"],
        "planId": (receipt or {}).get("planId"), "threadId": thread["thread_id"],
        "runId": thread["run_id"], "businessKeySha256": key_sha, "ownerSha256": owner_sha,
        "receiptSha256": (receipt or {}).get("receiptSha256"),
    }, receipt)

async def _expired_confirmation_guard(
    *, state_path: Path, gate_path: Path, head: str, expected_base: str, candidate_root: Path,
    app_base: str, auth_base: str, access: str, csrf: str, source_id: str,
    evidence_dir: Path,
) -> dict[str, object]:
    marker = evidence_dir / "expired-confirmation.unused.json"
    first_base = _agent_base()
    first = await _start_agent(
        state_path=state_path, gate_path=gate_path, head=head, expected_base=expected_base,
        agent_base=first_base, app_base=app_base, auth_base=auth_base,
        candidate_root=candidate_root, marker_path=marker,
    )
    headers = {"X-CSRF-Token": csrf, "Origin": first_base}
    async with httpx.AsyncClient(
        base_url=first_base, cookies={"access_token": access, "csrf_token": csrf}, headers=headers,
        timeout=httpx.Timeout(30, connect=5), follow_redirects=False, trust_env=False,
    ) as session:
        thread = await _create_confirmed_thread(session, source_id)
    _stop_agent(first)
    before = _local_workflow_record(state_path, thread["thread_id"])
    expired_base = _agent_base()
    expired = await _start_agent(
        state_path=state_path, gate_path=gate_path, head=head, expected_base=expected_base,
        agent_base=expired_base, app_base=app_base, auth_base=auth_base,
        candidate_root=candidate_root, marker_path=marker, clock_offset=1801,
    )
    headers = {"X-CSRF-Token": csrf, "Origin": expired_base}
    async with httpx.AsyncClient(
        base_url=expired_base, cookies={"access_token": access, "csrf_token": csrf}, headers=headers,
        timeout=httpx.Timeout(30, connect=5), follow_redirects=False, trust_env=False,
    ) as session:
        rejected = await session.post(
            f"/agent/threads/{thread['thread_id']}/save",
            json={"confirmationId": thread["confirmation_id"]},
        )
        code, _ = _envelope(rejected)
    _stop_agent(expired)
    after = _local_workflow_record(state_path, thread["thread_id"])
    async with UlticodeClient.for_session(app_base, auth_base, access_token=access, csrf_token=csrf) as java:
        try:
            await java.get_learning_plan_by_key(str(before["businessKey"]))
        except UlticodeServiceError as error:
            if error.status_code != 404 or error.code != 40400:
                raise ValueError("expired_confirmation_business_readback_unexpected")
        else:
            raise ValueError("expired_confirmation_created_java_plan")
    if (rejected.status_code != 409 or code != 40900 or before["saveAttempted"]
            or after["saveAttempted"] or after["status"] != "confirmed"):
        raise ValueError("expired_confirmation_not_blocked_before_dispatch")
    return {"expired_confirmation_tested": True, "expired_status": rejected.status_code,
            "expired_code": code, "business_posts": 0}


def _json_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate_json_key")
        result[key] = value
    return result


def _constant(_: str):
    raise ValueError("nonfinite_json_number")


def _read_json(path: Path, *, limit: int = 8 * 1024 * 1024) -> tuple[dict, bytes]:
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > limit:
            raise ValueError("evidence_file_invalid")
        chunks, size = [], 0
        while True:
            chunk = os.read(fd, min(65536, limit + 1 - size))
            if not chunk:
                break
            chunks.append(chunk)
            size += len(chunk)
            if size > limit:
                raise ValueError("evidence_file_oversized")
        raw = b"".join(chunks)
        value = json.loads(raw, object_pairs_hook=_json_pairs, parse_constant=_constant)
        if not isinstance(value, dict):
            raise ValueError("evidence_shape_invalid")
        return value, raw
    finally:
        os.close(fd)


def _private_no_clobber(path: Path, payload: dict) -> str:
    """Create private immutable JSON; never truncate, replace, or follow links."""
    if not path.is_absolute():
        raise ValueError("output_must_be_absolute")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    raw = (json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC, 0o600)
    try:
        view = memoryview(raw)
        while view:
            view = view[os.write(fd, view):]
        os.fsync(fd)
    finally:
        os.close(fd)
    directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)
    saved, saved_raw = _read_json(path)
    if saved != payload or saved_raw != raw:
        raise ValueError("artifact_readback_mismatch")
    return hashlib.sha256(raw).hexdigest()


def _relative_ref(root: Path, path: Path) -> dict[str, str]:
    if not path.is_absolute():
        raise ValueError("evidence_path_must_be_absolute")
    resolved_root = root.resolve(strict=True)
    resolved = path.resolve(strict=True)
    if resolved_root not in resolved.parents or path.is_symlink():
        raise ValueError("evidence_path_escape")
    _, raw = _read_json(resolved)
    return {"path": resolved.relative_to(resolved_root).as_posix(), "sha256": hashlib.sha256(raw).hexdigest()}


def _candidate_fingerprint(root: Path, head: str) -> dict[str, str]:
    if not _HEAD.fullmatch(head):
        raise ValueError("invalid_expected_head")
    actual = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], check=True, capture_output=True, text=True).stdout.strip()
    if actual != head:
        raise ValueError("candidate_head_mismatch")
    dirty = subprocess.run(["git", "-C", str(root), "status", "--porcelain", "--untracked-files=all"], check=True, capture_output=True, text=True).stdout
    if dirty:
        raise ValueError("candidate_worktree_dirty")
    paths = subprocess.run(["git", "-C", str(root), "ls-files", "-z"], check=True, capture_output=True).stdout.split(b"\0")
    roots = (b"services/agent/", b"services/auth/", b"services/submission/",
             b"services/app/modules/learningplan/", b"services/app/app-web/src/")
    selected = [p.decode() for p in paths if p and p.startswith(roots)]
    if not selected:
        raise ValueError("candidate_sources_missing")
    result = {}
    for relative in selected:
        path = root / relative
        if path.is_symlink() or not path.is_file():
            raise ValueError("candidate_source_invalid")
        result[relative] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def issue_u02_gate(args: argparse.Namespace) -> int:
    root = Path(args.candidate).resolve(strict=True)
    if not _HEAD.fullmatch(args.expected_head) or not _HEAD.fullmatch(args.expected_base):
        raise ValueError("invalid_candidate_binding")
    base_ok = subprocess.run(["git", "-C", str(root), "merge-base", "--is-ancestor", args.expected_base, args.expected_head]).returncode == 0
    if not base_ok:
        raise ValueError("candidate_base_not_ancestor")
    output = Path(args.output)
    if not output.is_absolute():
        raise ValueError("gate_output_must_be_absolute")
    evidence_root = output.parent
    info = evidence_root.lstat()
    if (not stat.S_ISDIR(info.st_mode) or stat.S_ISLNK(info.st_mode)
            or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o700):
        raise ValueError("gate_evidence_directory_not_private")
    payload = {
        "schema": "ulticode-u02-gate-v1",
        "candidate_head": args.expected_head,
        "candidate_base": args.expected_base,
        "issued_at": datetime.now(timezone.utc).isoformat(),
        "dav58_artifact": _relative_ref(evidence_root, Path(args.dav58_artifact)),
        "dav53_artifact": _relative_ref(evidence_root, Path(args.dav53_artifact)),
        "budget_audit": _relative_ref(evidence_root, Path(args.budget_audit)),
        "prior_five_manifest": _relative_ref(evidence_root, Path(args.prior_five_manifest)),
        "budget_anchor": current_budget_anchor(),
        "source_fingerprint": _candidate_fingerprint(root, args.expected_head),
    }
    validate_u02_gate_payload(payload, expected_head=args.expected_head, expected_base=args.expected_base,
                              candidate_root=root, evidence_root=evidence_root)
    digest = _private_no_clobber(output, payload)
    load_u02_gate(output, args.expected_head, expected_base=args.expected_base, candidate_root=root)
    print(f"OK u02_gate_issued sha256={digest}")
    return 0


def _read_tty(prompt: str, timeout: float = 170) -> str:
    import select
    import time

    print(prompt, end="", flush=True)
    deadline = time.monotonic() + max(0.0, timeout)
    fd = sys.stdin.fileno()
    content = bytearray()
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0 or not select.select([fd], [], [], remaining)[0]:
            raise TimeoutError("human_confirmation_timeout")
        char = os.read(fd, 1)
        if not char:
            raise ValueError("human_input_closed")
        if char in {b"\n", b"\r"}:
            return content.decode("utf-8")
        if char == b"\x04":
            raise ValueError("human_input_closed")
        content.extend(char)
        if len(content) > 64 * 1024:
            raise ValueError("human_input_too_long")
async def _autonomous_review(draft, deadline: float) -> dict[str, bool]:
    """Review a test-owned draft under delegated acceptance authority, without TTY."""
    if asyncio.get_running_loop().time() >= deadline:
        raise TimeoutError("autonomous_confirmation_timeout")
    if (not isinstance(draft, dict) or type(draft.get("draftVersion")) is not int
            or draft["draftVersion"] < 1
            or any(not isinstance(draft.get(key), str) or not draft[key].strip()
                   for key in ("title", "content"))):
        raise ValueError("autonomous_draft_invalid")
    return {"reviewed": True, "edit": False, "confirmed": True}


def _loopback_url(value: str) -> bool:
    parsed = urlsplit(value)
    return parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"} and parsed.port is not None


def _envelope(response: httpx.Response) -> tuple[int, dict]:
    data = response.json()
    if not isinstance(data, dict) or set(data) != {"code", "message", "data", "traceId"} or type(data["code"]) is not int or not isinstance(data["data"], dict):
        raise ValueError("result_envelope_invalid")
    return data["code"], data["data"]

async def _confirmation_guard_matrix(session: httpx.AsyncClient, source_id: str) -> dict[str, object]:
    created = await session.post("/agent/threads", json={
        "sourceSubmissionId": source_id,
        "question": "Check explicit confirmation guards.",
    })
    code, data = _envelope(created)
    thread_id = data.get("threadId")
    if created.status_code != 200 or code != 0 or not isinstance(thread_id, str):
        raise ValueError("confirmation_guard_thread_create_failed")
    version = data["draft"]["draftVersion"]
    params_digest = data["paramsDigest"]
    missing = await session.post(f"/agent/threads/{thread_id}/save", json={"confirmationId": str(uuid.uuid4())})
    missing_code, _ = _envelope(missing)
    if missing.status_code != 400 or missing_code != 40000:
        raise ValueError("unconfirmed_save_not_rejected")
    confirmed = await session.post(f"/agent/threads/{thread_id}/confirm", json={
        "draftVersion": version, "paramsDigest": params_digest, "confirm": True,
    })
    confirm_code, confirm_data = _envelope(confirmed)
    confirmation_id = (confirm_data.get("confirmation") or {}).get("id")
    if confirmed.status_code != 200 or confirm_code != 0 or not confirmation_id:
        raise ValueError("confirmation_guard_confirm_failed")
    edited = await session.put(f"/agent/threads/{thread_id}/draft", json={
        "draftVersion": version, "title": "Updated guard preview", "content": "Changed after confirmation.",
    })
    edit_code, _ = _envelope(edited)
    if edited.status_code != 200 or edit_code != 0:
        raise ValueError("confirmation_guard_edit_failed")
    stale = await session.post(f"/agent/threads/{thread_id}/save", json={"confirmationId": confirmation_id})
    stale_code, _ = _envelope(stale)
    if stale.status_code != 409 or stale_code != 40900:
        raise ValueError("stale_confirmation_not_rejected")
    cancelled = await session.post(f"/agent/threads/{thread_id}/cancel", json={})
    cancel_code, cancel_data = _envelope(cancelled)
    if cancelled.status_code != 200 or cancel_code != 0 or cancel_data.get("status") != "cancelled":
        raise ValueError("confirmation_guard_cancel_failed")
    after_cancel = await session.post(f"/agent/threads/{thread_id}/confirm", json={
        "draftVersion": version + 1, "paramsDigest": "0" * 64, "confirm": True,
    })
    cancelled_code, _ = _envelope(after_cancel)
    if after_cancel.status_code != 409 or cancelled_code != 40900:
        raise ValueError("cancelled_confirmation_not_rejected")
    return {
        "missing_status": missing.status_code, "missing_code": missing_code,
        "stale_status": stale.status_code, "stale_code": stale_code,
        "cancel_status": cancelled.status_code, "cancelled_confirm_status": after_cancel.status_code,
        "business_posts": None, "expired_confirmation_tested": False,
        "planId": None, "threadId": None, "runId": None,
        "businessKeySha256": None, "receiptSha256": None,
    }


async def _cancel_old_run_fence(
    session: httpx.AsyncClient, source_id: str,
) -> dict[str, object]:
    created = await session.post("/agent/threads", json={
        "sourceSubmissionId": source_id, "question": "Cancel this isolated analysis run.",
    })
    code, initial = _envelope(created)
    if created.status_code != 200 or code != 0:
        raise ValueError("cancel_fence_thread_create_failed")
    thread_id = initial["threadId"]
    before = initial["draft"]
    analysis_task = asyncio.create_task(session.post(f"/agent/threads/{thread_id}/analyze", json={}))
    analysis_run_id = None
    deadline = asyncio.get_running_loop().time() + 10
    while asyncio.get_running_loop().time() < deadline:
        current_response = await session.get(f"/agent/threads/{thread_id}")
        current_code, current = _envelope(current_response)
        if current_response.status_code != 200 or current_code != 0:
            analysis_task.cancel()
            raise ValueError("cancel_fence_state_unavailable")
        if current.get("status") == "analyzing":
            analysis_run_id = current.get("runId")
            if not analysis_run_id:
                analysis_task.cancel()
                raise ValueError("cancel_fence_run_id_missing")
            break
        if analysis_task.done():
            analysis_task.result()
            raise ValueError("cancel_fence_analysis_finished_before_cancel")
        await asyncio.sleep(0.05)
    else:
        analysis_task.cancel()
        raise TimeoutError("cancel_fence_analyzing_state_timeout")
    cancel_response = await session.post(f"/agent/threads/{thread_id}/cancel", json={})
    cancel_code, canceled = _envelope(cancel_response)
    if (cancel_response.status_code != 200 or cancel_code != 0
            or canceled.get("status") != "cancelled"):
        analysis_task.cancel()
        raise ValueError("cancel_fence_cancel_not_committed")
    try:
        await asyncio.wait_for(analysis_task, timeout=35)
    except (httpx.HTTPError, TimeoutError):
        pass
    current_response = await session.get(f"/agent/threads/{thread_id}")
    current_code, current = _envelope(current_response)
    draft = current.get("draft") or {}
    if (current_response.status_code != 200 or current_code != 0 or current.get("status") != "cancelled"
            or current.get("runId") != analysis_run_id
            or draft.get("draftVersion") != before.get("draftVersion")
            or draft.get("title") != before.get("title") or draft.get("content") != before.get("content")):
        raise ValueError("cancel_fence_late_result_applied")
    return {
        "cancel_committed_before_late_result": True, "old_result_applied": False,
        "current_run_unchanged": True, "cancelled_status": "cancelled",
        "planId": None, "threadId": None, "runId": None,
        "businessKeySha256": None, "receiptSha256": None,
    }


async def _java_idempotency_matrix(
    *, app_base: str, auth_base: str, owner_access: str, owner_csrf: str,
    foreign_access: str, foreign_csrf: str, source_id: str, foreign_source_id: str,
    head: str, gate_sha: str, evidence_dir: Path,
) -> tuple[dict[str, dict[str, object]], dict[str, list[dict[str, str]]], list[dict[str, object]], dict[str, str]]:
    capture, foreign_capture = _JavaReceiptTransport(), _JavaReceiptTransport()
    async with UlticodeClient.for_session(
        app_base, auth_base, access_token=owner_access, csrf_token=owner_csrf, transport=capture,
    ) as owner, UlticodeClient.for_session(
        app_base, auth_base, access_token=foreign_access, csrf_token=foreign_csrf, transport=foreign_capture,
    ) as foreign:
        owner_id, foreign_id = await owner.principal(), await foreign.principal()
        if owner_id == foreign_id:
            raise ValueError("u03_accounts_not_isolated")
        principal_evidence = capture.principal_evidence
        if (principal_evidence is None or principal_evidence.get("user_id") != owner_id
                or principal_evidence.get("response_status") != 200
                or principal_evidence.get("is_active") is not True
                or principal_evidence.get("is_banned") is not False
                or not isinstance(principal_evidence.get("trace_id"), str)
                or not principal_evidence["trace_id"]):
            raise ValueError("u03_owner_principal_evidence_invalid")
        source, other_source = await owner.get_my_submission(source_id), await foreign.get_my_submission(foreign_source_id)
        if (source.get("id", "").lower() != source_id.lower()
                or other_source.get("id", "").lower() != foreign_source_id.lower()):
            raise ValueError("u03_source_ownership_preflight_failed")
        payload = {"source_submission_id": source_id, "draft_version": 1,
                   "title": "U03 isolated idempotency check",
                   "content": "Synthetic runner-owned acceptance record."}

        async def save(client: UlticodeClient, key: str, data: dict):
            return await client.save_learning_plan(**data, idempotency_key=key)

        keys: dict[str, str] = {}
        same_key = keys["java_same_key_same_payload"] = str(uuid.uuid4())
        first, second = await save(owner, same_key, payload), await save(owner, same_key, payload)
        readback = await owner.get_learning_plan_by_key(same_key)
        if (first.get("id") != second.get("id") or readback.get("id") != first.get("id")
                or any(readback.get(k) != v for k, v in {
                    "sourceSubmissionId": source_id.lower(), "draftVersion": 1,
                    "title": payload["title"], "content": payload["content"],
                }.items())):
            raise ValueError("u03_java_idempotent_readback_mismatch")

        concurrent_key = keys["java_concurrent_same_key"] = str(uuid.uuid4())
        concurrent = await asyncio.gather(save(owner, concurrent_key, payload),
                                          save(owner, concurrent_key, payload))
        concurrent_readback = await owner.get_learning_plan_by_key(concurrent_key)
        if (concurrent[0].get("id") != concurrent[1].get("id")
                or concurrent_readback.get("id") != concurrent[0].get("id")):
            raise ValueError("u03_java_concurrent_idempotency_mismatch")

        mismatch_key = keys["java_same_key_payload_mismatch"] = str(uuid.uuid4())
        original = await save(owner, mismatch_key, payload)
        changed = {**payload, "content": payload["content"] + " altered"}
        mismatch_status, mismatch_code = 0, 0
        try:
            await save(owner, mismatch_key, changed)
        except UlticodeServiceError as error:
            mismatch_status, mismatch_code = error.status_code, error.code
        mismatch_readback = await owner.get_learning_plan_by_key(mismatch_key)
        if (mismatch_status != 409 or mismatch_code != 40900
                or mismatch_readback.get("id") != original.get("id")):
            raise ValueError("u03_java_payload_mismatch_not_rejected")
        foreign_status, foreign_code = 0, 0
        try:
            await foreign.get_learning_plan_by_key(same_key)
        except UlticodeServiceError as error:
            foreign_status, foreign_code = error.status_code, error.code
        if foreign_status not in {403, 404} or foreign_code not in {40300, 40400}:
            raise ValueError("u03_java_foreign_owner_read_allowed")
        foreign_principal = foreign_capture.principal_evidence
        foreign_exchange = foreign_capture.foreign_read
        if (not foreign_principal or not foreign_exchange
                or foreign_principal.get("user_id") != foreign_id
                or foreign_exchange.get("http_status") != foreign_status):
            raise ValueError("u03_foreign_read_capture_missing")

        reads = {same_key: (200, readback), concurrent_key: (200, concurrent_readback),
                 mismatch_key: (200, mismatch_readback)}
        raw_refs = {}
        projections = []
        for name, key in keys.items():
            count = 2
            posts = [row for row in capture.posts if row.get("business_key") == key][:count]
            if len(posts) != count:
                raise ValueError("u03_java_post_receipt_count_mismatch")
            raw_refs[name] = _write_java_receipts(
                scenario=name, posts=posts, owner_id=owner_id, source_id=source_id,
                thread_id=None, run_id=None, head=head, gate_sha=gate_sha,
                evidence_dir=evidence_dir, readbacks=reads, principal_evidence=principal_evidence,
                readback_fingerprints=capture.readback_fingerprints,
            )
            for ref in raw_refs[name]:
                raw_receipt, _ = _read_json(evidence_dir.parent / ref["path"])
                if raw_receipt["http_status"] == 200:
                    projections.append({
                        "scenario": name, "planId": raw_receipt["response_vo"]["id"],
                        "threadId": None, "runId": None,
                        "businessKeySha256": raw_receipt["business_key_sha256"],
                        "receiptSha256": ref["sha256"],
                    })
        foreign_principal_evidence = {
            "user_id_sha256": hashlib.sha256(foreign_id.encode()).hexdigest(),
            "response_status": foreign_principal["response_status"],
            "is_active": foreign_principal["is_active"], "is_banned": foreign_principal["is_banned"],
            "trace_id": foreign_principal["trace_id"],
        }
        owner_receipt = raw_refs["java_same_key_same_payload"][0]
        foreign_document = {
            "schema": "ulticode-u03-java-foreign-read-v1",
            "scenario": "java_foreign_owner_read", "candidate_head": head,
            "u02_gate_sha256": gate_sha, "owner_write_receipt": owner_receipt,
            "request_method": "GET", "request_path": foreign_exchange["request_path"],
            "request_path_business_key_sha256": foreign_exchange["request_path_business_key_sha256"],
            "foreign_principal": foreign_principal_evidence,
            "http_status": foreign_exchange["http_status"],
            "response_body_raw": foreign_exchange["response_body_raw"],
            "response_body_sha256": hashlib.sha256(
                foreign_exchange["response_body_raw"].encode("utf-8")
            ).hexdigest(),
        }
        foreign_read_receipt = {
            "path": evidence_dir.name + "/java-foreign-read.json",
            "sha256": _private_no_clobber(evidence_dir / "java-foreign-read.json", foreign_document),
        }
        no_ids = {"threadId": None, "runId": None}
        owner_sha = hashlib.sha256(owner_id.encode()).hexdigest()
        observations = {
            "java_same_key_same_payload": {
                **no_ids, "attempts": len(raw_refs["java_same_key_same_payload"]),
                "successful": sum(row.get("http_status") == 200 for row in capture.posts
                                  if row.get("business_key") == same_key),
                "distinct_plan_ids": len({row.get("id") for row in (first, second)}),
                "matching_payload": all(
                    all(row.get(field) == value for field, value in {
                        "sourceSubmissionId": source_id.lower(),
                        "draftVersion": payload["draft_version"],
                        "title": payload["title"],
                        "content": payload["content"],
                    }.items())
                    for row in (first, second, readback)
                ),
                "business_rows": 1, "planId": readback.get("id"),
                "businessKeySha256": hashlib.sha256(same_key.encode()).hexdigest(),
                "receiptSha256": raw_refs["java_same_key_same_payload"][0]["sha256"],
                "ownerSha256": owner_sha,
            },
            "java_concurrent_same_key": {
                **no_ids, "requests": len(raw_refs["java_concurrent_same_key"]),
                "successes": sum(row.get("id") == concurrent[0].get("id") for row in concurrent),
                "distinct_plan_ids": len({row.get("id") for row in concurrent}),
                "business_rows": 1, "planId": concurrent_readback.get("id"),
                "businessKeySha256": hashlib.sha256(concurrent_key.encode()).hexdigest(),
                "receiptSha256": raw_refs["java_concurrent_same_key"][0]["sha256"],
                "ownerSha256": owner_sha,
            },
            "java_same_key_payload_mismatch": {
                **no_ids, "first_status": 200, "first_code": 0, "changed_status": mismatch_status,
                "changed_code": mismatch_code, "business_rows": 1, "planId": original.get("id"),
                "businessKeySha256": hashlib.sha256(mismatch_key.encode()).hexdigest(),
                "receiptSha256": raw_refs["java_same_key_payload_mismatch"][0]["sha256"],
                "ownerSha256": owner_sha,
            },
            "java_foreign_owner_read": {
                **no_ids, "planId": None, "businessKeySha256": hashlib.sha256(same_key.encode()).hexdigest(),
                "receiptSha256": None, "owner_hash": owner_sha,
                "foreign_owner_hash": foreign_principal_evidence["user_id_sha256"],
                "foreign_status": foreign_status, "foreign_code": foreign_code, "returned_plan": False,
            },
        }
        return observations, raw_refs, projections, foreign_read_receipt

async def _run_u03(args: argparse.Namespace) -> tuple[dict, int]:
    start = datetime.now(timezone.utc).isoformat()
    expected_head, expected_base = args.expected_head, args.expected_base
    root = require_execution_candidate(args.candidate)
    gate_path = Path(args.u02_gate)
    load_u02_gate(gate_path, expected_head, expected_base=expected_base, candidate_root=root)
    _, gate_raw = _read_json(gate_path)
    gate_sha = hashlib.sha256(gate_raw).hexdigest()
    result = {
        "schema": "ulticode-u03-workflow-result-v1", "candidate_head": expected_head,
        "candidate_base": expected_base, "u02_gate_sha256": gate_sha, "status": "INCOMPLETE",
        "reason": "prerequisites_missing", "coverage": {}, "scenarios": {}, "receipts": [],
        "exit_code": 1, "deadline_seconds": 1800,
    }
    if not args.candidate_inputs:
        result["reason"] = "candidate_inputs_required"
        return result, 1
    if not args.evidence_root or not Path(args.evidence_root).is_absolute():
        result["reason"] = "private_evidence_root_required"
        return result, 1
    evidence_root = _private_evidence_root(Path(args.evidence_root))
    if (root == evidence_root or root in evidence_root.parents
            or evidence_root in root.parents):
        raise ValueError("u03_candidate_evidence_roots_overlap")
    evidence_dir = _new_evidence_dir(evidence_root)
    candidate, candidate_ref = _candidate_inputs_ref(
        root, Path(args.candidate_inputs), head=expected_head, base=expected_base, evidence_dir=evidence_dir,
    )
    result.update({"candidate_inputs_sha256": candidate_ref["sha256"], "candidate_inputs": candidate_ref,
                   "source_fingerprint": candidate["source_fingerprint"],
                   "configuration_fingerprint": candidate["configuration_fingerprint"]})
    lanes = POLICY.get("lanes", {})
    if not {"u03_analysis", "u03_citation_judge"} <= set(lanes):
        result["reason"] = "model_budget_purpose_blocked"
        result["coverage"] = {"external_calls": 0, "condition_scenarios_unexecuted": list(_U03_SCENARIOS)}
        return result, 1
    period_values, guard_path, guard_sha = _u03_runtime_binding(args)
    base = _agent_base()
    app_base, auth_base = os.environ.get("ULTICODE_APP_BASE", ""), os.environ.get("ULTICODE_AUTH_BASE", "")
    access, csrf = os.environ.get("ULTICODE_U03_ACCESS_TOKEN", ""), os.environ.get("ULTICODE_U03_CSRF_TOKEN", "")
    source_id = os.environ.get("ULTICODE_U03_SOURCE_SUBMISSION_ID", "")
    foreign_access = os.environ.get("ULTICODE_U03_FOREIGN_ACCESS_TOKEN", "")
    foreign_csrf = os.environ.get("ULTICODE_U03_FOREIGN_CSRF_TOKEN", "")
    foreign_source_id = os.environ.get("ULTICODE_U03_FOREIGN_SOURCE_SUBMISSION_ID", "")
    if not (_loopback_url(base) and _loopback_url(app_base) and _loopback_url(auth_base)
            and access and csrf and source_id and foreign_access and foreign_csrf and foreign_source_id):
        result["reason"] = "loopback_sessions_or_sources_missing"
        return result, 1
    result["coverage"]["private_evidence_root"] = True
    loop = asyncio.get_running_loop()
    batch_deadline = loop.time() + 1800
    scenarios: dict[str, dict] = result["scenarios"]
    workflow_process = None
    try:
        observations_by_name, raw_by_name, receipt_projections, foreign_read_receipt = await _java_idempotency_matrix(
            app_base=app_base, auth_base=auth_base, owner_access=access, owner_csrf=csrf,
            foreign_access=foreign_access, foreign_csrf=foreign_csrf, source_id=source_id,
            foreign_source_id=foreign_source_id, head=expected_head, gate_sha=gate_sha,
            evidence_dir=evidence_dir,
        )
        result["receipts"].extend(receipt_projections)
        for name, observations in observations_by_name.items():
            ref = _scenario_evidence(
                root, name, expected_head, gate_sha, observations, started_at=start,
                completed_at=datetime.now(timezone.utc).isoformat(), evidence_dir=evidence_dir,
                raw_receipts=raw_by_name.get(name),
                foreign_read_receipt=foreign_read_receipt if name == "java_foreign_owner_read" else None,
            )
            _record_validated_scenario(
                scenarios, ref, scenario=name, evidence_root=evidence_root,
                head=expected_head, gate_sha=gate_sha,
            )
        guard_state, guard_marker, guard_base = _agent_state_path(), evidence_dir / "guards.fault.json", _agent_base()
        guard_process = await _start_agent(
            state_path=guard_state, gate_path=gate_path, head=expected_head, expected_base=expected_base,
            agent_base=guard_base, app_base=app_base, auth_base=auth_base, candidate_root=root,
            marker_path=guard_marker,
        )
        try:
            guard_headers = {"X-CSRF-Token": csrf, "Origin": guard_base}
            async with httpx.AsyncClient(
                base_url=guard_base, cookies={"access_token": access, "csrf_token": csrf},
                headers=guard_headers, timeout=httpx.Timeout(30, connect=5),
                follow_redirects=False, trust_env=False,
            ) as guard_session:
                guard_observations = await _confirmation_guard_matrix(guard_session, source_id)
        finally:
            _stop_agent(guard_process)
        guard_exchange = guard_marker.with_name(guard_marker.stem + ".java-exchange.json")
        if guard_exchange.exists():
            raise ValueError("confirmation_guard_unexpected_java_post")
        expiry = await _expired_confirmation_guard(
            state_path=_agent_state_path(), gate_path=gate_path, head=expected_head,
            expected_base=expected_base, candidate_root=root, app_base=app_base,
            auth_base=auth_base, access=access, csrf=csrf, source_id=source_id,
            evidence_dir=evidence_dir,
        )
        expiry_exchange = evidence_dir / "expired-confirmation.unused.java-exchange.json"
        if expiry_exchange.exists():
            raise ValueError("expired_confirmation_unexpected_java_post")
        guard_observations["expired_confirmation_tested"] = expiry["expired_confirmation_tested"]
        guard_observations["business_posts"] = expiry["business_posts"]
        guard_ref = _scenario_evidence(
            root, "confirmation_guards", expected_head, gate_sha, guard_observations,
            started_at=start, completed_at=datetime.now(timezone.utc).isoformat(),
            evidence_dir=evidence_dir,
        )
        _record_validated_scenario(
            scenarios, guard_ref, scenario="confirmation_guards", evidence_root=evidence_root,
            head=expected_head, gate_sha=gate_sha,
        )
        from authorized_budget_period import POLICY_ID, PeriodIdentity
        expected_period = PeriodIdentity(
            period_id=period_values[0], identity=period_values[1], config_sha256=period_values[2],
            policy_id=POLICY_ID,
        )
        workflow_base = _agent_base()
        workflow_process = await start_u03_agent(
            state_path=_agent_state_path(), gate_path=gate_path, head=expected_head,
            expected_base=expected_base, agent_base=workflow_base, app_base=app_base,
            auth_base=auth_base, candidate_root=root, marker_path=evidence_dir / "workflow.fault.json",
            expected_period=expected_period, budget_guard_path=guard_path,
            budget_guard_sha256=guard_sha,
        )
        base = workflow_base
        headers = {"X-CSRF-Token": csrf, "Origin": base}
        async with httpx.AsyncClient(
            base_url=base, cookies={"access_token": access, "csrf_token": csrf}, headers=headers,
            timeout=httpx.Timeout(30.0, connect=5.0), follow_redirects=False, trust_env=False,
        ) as session:
            cancel_observations = await _cancel_old_run_fence(session, source_id)
            cancel_ref = _scenario_evidence(
                root, "cancel_old_run_fence", expected_head, gate_sha, cancel_observations,
                started_at=start, completed_at=datetime.now(timezone.utc).isoformat(),
                evidence_dir=evidence_dir,
            )
            _record_validated_scenario(
                scenarios, cancel_ref, scenario="cancel_old_run_fence", evidence_root=evidence_root,
                head=expected_head, gate_sha=gate_sha,
            )
            human_demo_start = asyncio.get_running_loop().time()
            def human_input(prompt: str) -> str:
                remaining = min(180.0 - (loop.time() - human_demo_start), batch_deadline - loop.time())
                if remaining <= 0:
                    raise TimeoutError("u03_human_demo_deadline_exceeded")
                return _read_tty(prompt, remaining)
            created = await session.post("/agent/threads", json={
                "sourceSubmissionId": source_id,
                "question": "Review visible submission facts and propose one verifiable learning step.",
            })
            code, data = _envelope(created)
            if created.status_code != 200 or code != 0 or not data.get("threadId"):
                raise ValueError("thread_create_unavailable")
            thread = data["threadId"]
            analyzed = await session.post(f"/agent/threads/{thread}/analyze", json={})
            code, analysis = _envelope(analyzed)
            if analyzed.status_code != 200 or code != 0:
                raise ValueError(analysis.get("reason", "analysis_incomplete"))
            state_response = await session.get(f"/agent/threads/{thread}")
            state_code, state = _envelope(state_response)
            if state_response.status_code != 200 or state_code != 0 or state.get("status") != "awaiting_confirmation":
                raise ValueError("analysis_state_not_confirmable")
            draft = state.get("draft") or {}
            result["coverage"].update({"real_http_analysis": True, "draft_persisted": True,
                                       "previewed_by_human": False})
            interactive_confirm = getattr(args, "interactive_confirm", False)
            if interactive_confirm and not sys.stdin.isatty():
                raise ValueError("interactive_human_confirmation_required")
            if interactive_confirm:
                print("Review draft preview; do not treat it as verified source diagnosis:")
                print(f"Title: {draft.get('title', '')}\n{draft.get('content', '')}")
            if interactive_confirm and human_input("Edit title? (blank keeps current): ").strip():
                new_title = human_input("New title: ")
                new_content = human_input("New content (single line): ")
                edited = await session.put(f"/agent/threads/{thread}/draft", json={
                    "draftVersion": draft["draftVersion"], "title": new_title,
                    "content": new_content,
                })
                edit_code, _ = _envelope(edited)
                if edited.status_code != 200 or edit_code != 0:
                    raise ValueError("draft_edit_rejected")
                _, state = _envelope(await session.get(f"/agent/threads/{thread}"))
                draft = state["draft"]
            if not interactive_confirm:
                await _autonomous_review(draft, min(human_demo_start + 180, batch_deadline))
            result["coverage"].update({"previewed_by_human": interactive_confirm,
                                       "draft_reviewed": True,
                                       "confirmation_actor": "human" if interactive_confirm else "autonomous"})
            if interactive_confirm and human_input("Type CONFIRM to authorize this exact Java save, anything else cancels: ") != "CONFIRM":
                result["reason"], result["status"] = "human_did_not_confirm", "CANCELLED"
                return result, 1
            confirmed = await session.post(f"/agent/threads/{thread}/confirm", json={
                "draftVersion": draft["draftVersion"], "paramsDigest": state["paramsDigest"], "confirm": True,
            })
            code, confirmed_data = _envelope(confirmed)
            if confirmed.status_code != 200 or code != 0:
                raise ValueError("confirmation_rejected")
            saved = await session.post(f"/agent/threads/{thread}/save", json={
                "confirmationId": confirmed_data.get("confirmation", {}).get("id"),
            })
            code, saved_data = _envelope(saved)
            if saved.status_code != 200 or code != 0 or saved_data.get("status") != "saved":
                raise ValueError("save_not_confirmed")
            result["plan_id"] = (saved_data.get("receipt") or {}).get("planId")
            readback = await session.get(f"/agent/threads/{thread}")
            readback_code, readback_data = _envelope(readback)
            if (readback.status_code != 200 or readback_code != 0
                    or readback_data.get("status") != "saved"
                    or (readback_data.get("receipt") or {}).get("planId") != result["plan_id"]):
                raise ValueError("saved_readback_unavailable")
            human_demo_seconds = asyncio.get_running_loop().time() - human_demo_start
            if human_demo_seconds > 180:
                raise TimeoutError("u03_human_demo_deadline_exceeded")
            result["coverage"].update({
                "java_save_readback": True, "human_demo_completed": interactive_confirm,
                "workflow_demo_completed": True,
                "human_demo_seconds": human_demo_seconds,
            })
        _stop_agent(workflow_process)
        workflow_process = None
        crash_cases = (
            ("kill_intent_pre_http", "before_java_save_dispatch", False),
            ("kill_java_commit_response_lost", None, True),
            ("kill_vo_received_local_not_committed", "after_java_save_response", False),
            ("restart_owner_disk_recovery", None, False),
        )
        for scenario, point, drop_response in crash_cases:
            observation, receipt = await _save_crash_scenario(
                scenario=scenario, point=point, drop_response=drop_response,
                state_path=_agent_state_path(), gate_path=gate_path, head=expected_head,
                expected_base=expected_base, candidate_root=root, app_base=app_base,
                auth_base=auth_base, access=access, csrf=csrf, foreign_access=foreign_access,
                foreign_csrf=foreign_csrf, source_id=source_id, evidence_dir=evidence_dir,
                gate_sha=gate_sha,
            )
            common = {
                "planId": observation["planId"] if receipt else None,
                "threadId": observation["threadId"] if receipt else None,
                "runId": observation["runId"] if receipt else None,
                "businessKeySha256": observation["businessKeySha256"] if receipt else None,
                "receiptSha256": observation["receiptSha256"] if receipt else None,
            }
            if receipt:
                common["ownerSha256"] = observation["ownerSha256"]
            if scenario == "kill_intent_pre_http":
                observations = {**common, "fault_point": point, "child_exit": observation["child_exit"],
                                "java_post_count": observation["java_post_count"],
                                "restart_by_key_status": observation["restart_by_key_status"],
                                "recovered_status": observation["recovered_status"]}
            elif scenario == "kill_java_commit_response_lost":
                observations = {**common, "java_commit_observed": observation["java_commit_observed"],
                                "response_lost": observation["response_lost"],
                                "restart_by_key_status": observation["restart_by_key_status"],
                                "recovered_status": observation["recovered_status"],
                                "business_rows": observation["business_rows"],
                                "java_post_count": observation["java_post_count"]}
            elif scenario == "kill_vo_received_local_not_committed":
                observations = {**common, "java_vo_received": observation["java_vo_received"],
                                "restart_by_key_status": observation["restart_by_key_status"],
                                "recovered_status": observation["recovered_status"],
                                "business_rows": observation["business_rows"],
                                "java_post_count": observation["java_post_count"]}
            else:
                observations = {**common, "restart_pid_changed": observation["restart_pid_changed"],
                                "owner_read_status": observation["owner_read_status"],
                                "foreign_read_status": observation["foreign_read_status"],
                                "sqlite_quick_check": observation["sqlite_quick_check"],
                                "java_post_count": observation["java_post_count"]}
            raw_refs = receipt.get("raw_refs", []) if receipt else []
            ref = _scenario_evidence(
                root, scenario, expected_head, gate_sha, observations, started_at=start,
                completed_at=datetime.now(timezone.utc).isoformat(), evidence_dir=evidence_dir,
                raw_receipts=raw_refs or None,
            )
            _record_validated_scenario(
                scenarios, ref, scenario=scenario, evidence_root=evidence_root,
                head=expected_head, gate_sha=gate_sha,
            )
            if receipt:
                result["receipts"].append({
                    key: receipt[key] for key in (
                        "scenario", "planId", "threadId", "runId", "businessKeySha256", "receiptSha256",
                    )
                })
        result["coverage"].update({
            "restart_recovery": True, "fault_matrix": True, "private_evidence_run_id": evidence_dir.name,
        })
        completed = datetime.now(timezone.utc).isoformat()
        accepted = _accepted_u03_result(
            root=root, evidence_root=evidence_root, head=expected_head, base=expected_base,
            gate_sha256=gate_sha, candidate_inputs_sha256=result["candidate_inputs_sha256"],
            candidate_inputs=candidate_ref, source_fingerprint=candidate["source_fingerprint"],
            started_at=start, completed_at=completed, scenarios=scenarios,
            receipts=result["receipts"], coverage=result["coverage"], exit_code=0,
            deadline_seconds=1800,
        )
        return accepted, 0
    except (OSError, httpx.HTTPError, ValueError, UlticodeServiceError, KeyError, TimeoutError, RuntimeError) as error:
        result["reason"] = str(error) if isinstance(error, ValueError) else type(error).__name__
        return result, 1
    finally:
        _stop_active_agents()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument("--interactive-confirm", action="store_true", help="Optional interactive review; autonomous confirmation is the default")
    parser.add_argument("--issue-u02-gate", action="store_true")
    parser.add_argument("--dav58-artifact")
    parser.add_argument("--dav53-artifact")
    parser.add_argument("--prior-five-manifest")
    parser.add_argument("--budget-audit")
    parser.add_argument("--candidate")
    parser.add_argument("--candidate-inputs")
    parser.add_argument("--evidence-root")
    parser.add_argument("--expected-head", required=True)
    parser.add_argument("--expected-base", required=True)
    parser.add_argument("--result", "--output", dest="output")
    parser.add_argument("--period-id")
    parser.add_argument("--period-identity")
    parser.add_argument("--config-sha256")
    parser.add_argument("--u02-gate")
    args = parser.parse_args(argv)
    try:
        if args.issue_u02_gate:
            missing = [name for name in ("dav58_artifact", "dav53_artifact", "prior_five_manifest", "budget_audit", "candidate", "output") if not getattr(args, name)]
            if missing:
                raise ValueError("gate_evidence_arguments_missing")
            return issue_u02_gate(args)
        if os.environ.get("ULTICODE_U03_E2E") != "1":
            print("INCOMPLETE reason=opt_in_not_set")
            return 1
        if not args.u02_gate or not args.output or not args.candidate or not args.candidate_inputs or not args.evidence_root:
            raise ValueError("u03_gate_candidate_inputs_evidence_root_and_result_required")

        async def bounded_run():
            try:
                return await asyncio.wait_for(_run_u03(args), timeout=1800)
            except TimeoutError:
                return ({"schema": "ulticode-u03-workflow-result-v1", "candidate_head": args.expected_head,
                         "candidate_base": args.expected_base, "status": "INCOMPLETE",
                         "reason": "batch_deadline_exceeded", "exit_code": 124,
                         "deadline_seconds": 1800, "scenarios": {}, "receipts": []}, 124)

        output_path = _evidence_output(Path(args.evidence_root), Path(args.output))
        artifact, code = asyncio.run(bounded_run())
        _private_no_clobber(output_path, artifact)
        if artifact.get("status") == "PASS" and code == 0:
            print("PASS")
        else:
            print(f"{artifact.get('status', 'INCOMPLETE')} reason={artifact.get('reason', 'scenario_failed')}")
        return code
    except (GateError, OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"INCOMPLETE reason={type(error).__name__}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
