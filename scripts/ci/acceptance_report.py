#!/usr/bin/env python3
"""Publish fixed diagnostic vocabulary and bounded coverage, never raw log strings."""
import json
import os
import re
from pathlib import Path

LIMIT = 1_000_000
KINDS = re.compile(r"\b(?:org|com|java|javax|jakarta|io)\.(?:[A-Za-z_$][A-Za-z0-9_$]*\.)*[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error)\b")
FRAMES = re.compile(r"\bat (com\.ulticode\.[A-Za-z0-9_.$]+)\(([A-Za-z0-9_$]+\.java:[0-9]+)\)")
# Regexes detect candidate tokens only; output is intersected with this fixed
# vocabulary. A Java-looking secret or forged source frame is never serialized.
ALLOWED_KINDS = frozenset({
    "java.io.IOException", "java.io.InterruptedIOException",
    "java.lang.IllegalStateException", "java.lang.OutOfMemoryError",
    "java.net.ConnectException", "java.net.SocketTimeoutException",
    "java.util.concurrent.TimeoutException", "javax.net.ssl.SSLHandshakeException",
    "jakarta.validation.NoProviderFoundException",
    "org.apache.dubbo.remoting.RemotingException", "org.apache.dubbo.rpc.RpcException",
    "org.springframework.beans.factory.BeanCreationException",
})
ALLOWED_FRAMES = frozenset({
    ("com.ulticode.submission.config.SubmissionDiscoveryStartupGate.await", "SubmissionDiscoveryStartupGate.java:40"),
    ("com.ulticode.submission.config.SubmissionDiscoveryStartupGate.run", "SubmissionDiscoveryStartupGate.java:32"),
    ("com.ulticode.submission.BackendSubmissionApplication.main", "BackendSubmissionApplication.java:38"),
})
OWNERS = ("app", "submission", "admin", "auth", "judge", "notification", "search")
PRIVATE_LOGS = ("environment", "sandbox", "prepare", "cutover", "launch")
PHASES = {"environment", "sandbox", "fresh-migrations", "backfill-and-cutover", "launch",
          "auth-environment", "fixtures", "browser-base", "browser-business", "complete"}


FINAL_APPS = tuple("ulticode-" + owner for owner in OWNERS) + ("ulticode-core", "ulticode-9002", "ulticode-9003", "ulticode-9004")
HTTP_DETAILS = ("http_200", "http_401", "http_403", "http_404", "http_500", "http_502", "http_503", "http_504", "http_unreachable", "http_other")
PM2_DETAILS = ("pm2_not_online", "pm2_online_no_banner_required", "pm2_log_missing", "pm2_banner_missing", "pm2_banner_observed", "unknown_probe_kind")
HEALTH_COMPONENTS = {
    "ulticode-auth": ("db", "redis"),
    "ulticode-admin": ("db", "redis", "problemOwnerRpc", "storage"),
    "ulticode-app": ("db", "redis", "storage"),
    "ulticode-notification": ("db", "redis"),
}


def final_probes(text):
    """Last explicit final-cycle markers only; all serialized values are fixed."""
    details = set(HTTP_DETAILS) | set(PM2_DETAILS) | {a + "/" + b for a in HTTP_DETAILS for b in HTTP_DETAILS}
    readiness = {app: {"observed": False, "state": "unknown", "detail": "unknown"} for app in FINAL_APPS}
    dependencies = {app: {"observed": False, "probe": "unknown", "components": dict.fromkeys(keys, "unknown")}
                    for app, keys in HEALTH_COMPONENTS.items()}
    for line in text.splitlines():
        match = re.fullmatch(r"ULTICODE_FINAL_READINESS app=([^ ]+) state=(ready|failed) detail=([^ ]+)", line)
        if match:
            app, state, detail = match.groups()
            if app in readiness and detail in details:
                readiness[app] = {"observed": True, "state": state, "detail": detail}
        match = re.fullmatch(r"ULTICODE_FINAL_HEALTH app=([^ ]+) state=(http_200|http_503|unreachable|http_other|invalid_response|oversized_response)", line)
        if match:
            app, state = match.groups()
            if app in dependencies:
                dependencies[app]["observed"] = True
                dependencies[app]["probe"] = state
        match = re.fullmatch(r"ULTICODE_FINAL_COMPONENT app=([^ ]+) component=([^ ]+) state=(up|down|unknown)", line)
        if match:
            app, key, state = match.groups()
            if app in dependencies and key in dependencies[app]["components"]:
                dependencies[app]["components"][key] = state
    return {"final_readiness": readiness, "final_dependency_probes": dependencies}


def summary(paths, include_final_probes=False):
    paths = tuple(paths)
    kinds, frames, messages, observed = set(), set(), [], []
    coverage = {"files_expected": len(paths), "files_seen": 0, "files_missing": 0,
                "files_excluded": 0, "read_errors": 0, "tail_truncated": False}
    unknown_kinds = unknown_frames = False
    for path in paths:
        try:
            if path.is_symlink():
                coverage["files_excluded"] += 1
                continue
            if not path.exists():
                coverage["files_missing"] += 1
                continue
            if not path.is_file():
                coverage["files_excluded"] += 1
                continue
            with path.open("rb") as stream:
                size = os.fstat(stream.fileno()).st_size
                coverage["tail_truncated"] |= size > LIMIT
                stream.seek(max(0, size - LIMIT))
                text = stream.read(LIMIT).decode("utf-8", errors="replace")
            coverage["files_seen"] += 1
        except OSError:
            coverage["read_errors"] += 1
            continue
        observed.append(text)
        lines = text.splitlines()
        for index, line in enumerate(lines):
            if any(marker in line for marker in ("RpcException", "Caused by:", "Last error", "last error", "Exception:", "Error:")):
                messages.extend(lines[index:index + 3])
        candidates = set(KINDS.findall(text))
        kinds.update(candidates & ALLOWED_KINDS)
        unknown_kinds |= bool(candidates - ALLOWED_KINDS)
        candidates = set(FRAMES.findall(text))
        frames.update(candidates & ALLOWED_FRAMES)
        unknown_frames |= bool(candidates - ALLOWED_FRAMES)
    context = "\n".join(messages)
    patterns = {
        "timeout": r"\b(?:timeout waiting|timed out|deadline exceeded|TimeoutException)\b",
        "no_rpc_provider": r"no providers? available|not found exported service|no valid provider",
        "connection": r"\b(?:connection refused|connection reset|failed to connect|broken pipe)\b",
        "serialization": r"(?:failed|unable|cannot|error)[^\n]{0,80}(?:serialize|deserialize|serialization)|(?:serialize|deserialize|serialization)[^\n]{0,60}(?:failed|error|reject|not allow)|not serializable|unsupported[^\n]{0,40}record",
        "authorization": r"\b(?:unauthorized|forbidden|access denied|permission denied)\b",
        "tls_identity": r"\b(?:SSLHandshakeException|certificate_unknown|untrusted peer|peer identity mismatch|tls handshake)\b",
        "validation_provider": r"\bNoProviderFoundException\b",
    }
    # Only booleans under fixed keys are emitted; never include matched text,
    # exception messages, endpoints, process environment or arbitrary timestamps.
    startup_patterns = {
        "spring_context_started": r"\bStarted (?:Backend(?:App|Submission|Admin|Auth|Judge|Notification)Application|SearchWorkerApplication) in ",
        "application_failed": r"APPLICATION FAILED TO START",
        "submission_projection_providers_discovered": r"Submission projection providers discovered",
        "submission_projection_discovery_deadline": r"Submission projection providers were not discovered before readiness deadline",
        "nacos_client_starting": r"Client not connected, current status:STARTING|Client not connected, current status: STARTING",
        "nacos_client_unhealthy": r"Client not connected, current status:UNHEALTHY|Client not connected, current status: UNHEALTHY",
        "connection_refused": r"\bConnection refused\b",
        "reactor_build_failed": r"Error: reactor install failed; fix compilation errors before starting the stack\.",
        "auth_readiness_failed": r"ulticode-auth did not become healthy; cannot run dev-admin bootstrap\.",
        "app_readiness_failed": r"backend-app did not become healthy; starting ulticode-judge anyway",
        "stack_readiness_timed_out": r"Application readiness check timed out for:",
    }
    observed_text = "\n".join(observed)
    coverage["complete"] = (bool(paths) and coverage["files_seen"] == len(paths)
                            and not coverage["tail_truncated"])
    # False signals mean "not observed in the inspected tails", not proof of
    # absence. Coverage makes missing/unreadable/truncated evidence explicit.
    result = {"coverage": coverage,
            "unlisted_exception_type_observed": unknown_kinds,
            "unlisted_source_frame_observed": unknown_frames,
            "startup_signals": {name: bool(re.search(pattern, observed_text)) for name, pattern in startup_patterns.items()},
            "exception_classes": sorted(kinds)[:20], "public_source_frames": [{"symbol": symbol, "location": location} for symbol, location in sorted(frames)],
            "rpc_failure_categories": {name: bool(re.search(pattern, context, re.I)) for name, pattern in patterns.items()}}
    if include_final_probes:
        result.update(final_probes(observed_text))
    return result


def build_report(root, phase, exit_code, project):
    private = summary((root / ".local/acceptance" / f"private-{stage}.log" for stage in PRIVATE_LOGS), include_final_probes=True)
    services = {}
    for owner in OWNERS:
        item = summary([root / "logs" / f"backend-{owner}.{kind}.log" for kind in ("out", "err")])
        # Keep every selected owner visible even when both log files are absent.
        services[owner] = item
    return {
        "evidence": "disposable-real-services",
        "phase": phase if phase in PHASES else "unknown",
        "passed": exit_code == 0,
        "project": "disposable" if re.fullmatch(r"ulticode-acceptance-[0-9]{1,20}-[0-9]{1,20}", project) else "unknown",
        "raw_logs_published": False,
        "signals_semantics": "observed_in_bounded_log_tails_not_current_health",
        "exception_classes": private["exception_classes"],
        "launcher_signals": private["startup_signals"],
        "launcher_coverage": private["coverage"],
        "final_readiness": private["final_readiness"],
        "final_dependency_probes": private["final_dependency_probes"],
        "service_diagnostics": services,
    }


def main():
    try:
        root = Path.cwd()
        report = build_report(root, os.environ.get("ACCEPTANCE_PHASE", "unknown"),
                              int(os.environ.get("ACCEPTANCE_EXIT", "1")),
                              os.environ.get("COMPOSE_PROJECT_NAME", ""))
        output = root / ".local/acceptance/installation.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n")
        if not report["passed"]:
            print(json.dumps({"acceptanceFailureDiagnostics": report}, separators=(",", ":")))
    except (OSError, ValueError):
        print("Acceptance report could not be written; private logs were not printed")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
