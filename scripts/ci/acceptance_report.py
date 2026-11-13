#!/usr/bin/env python3
"""Publish fixed diagnostic vocabulary, bounded observations and coverage, never raw logs."""
import json
import os
import re
import stat
import subprocess
import sys
from pathlib import Path

from acceptance_readiness import directory_fd, read_snapshot, valid_project

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


# Match the entire generated payload. Prefixes are private logger metadata, never emitted.
COUNT_TOKEN = r"(?:0|[1-9][0-9]{0,4}|UNKNOWN|OVERFLOW|ERROR)"
DISCOVERY = re.compile(
    r"^SubmissionDiscoveryObservation outcome=(?P<outcome>ready|deadline) elapsed_ms=(?P<elapsed_ms>[0-9]{1,8}) "
    r"interface=(?P<interface>problem_facts|user_read) "
    r"state=(?P<state>MANAGER_MISSING|REFERENCE_MISSING|CONFIG_MISSING|INVOKER_MISSING|INVOKER_PRESENT|UNSUPPORTED|INSPECTION_ERROR) "
    r"invoker_kind=(?P<invoker_kind>SERVICE_DISCOVERY_MIGRATION|MIGRATION|CLUSTER|OTHER|MISSING) "
    r"directory_kind=(?P<directory_kind>SERVICE_DISCOVERY|DYNAMIC|OTHER|MISSING) "
    r"destroyed=(?P<destroyed>TRUE|FALSE|UNKNOWN|ERROR) "
    + " ".join(rf"{name}=(?P<{name}>{COUNT_TOKEN})" for name in ("all", "valid", "reconnect", "disabled"))
    + r"$"
)

APP_DISCOVERY = re.compile(
    r"^AppDiscoveryObservation interface=(?P<interface>submission_user_query|auth_account_query) "
    r"state=(?P<state>MANAGER_MISSING|REFERENCE_MISSING|CONFIG_MISSING|INVOKER_MISSING|INVOKER_PRESENT|UNSUPPORTED|INSPECTION_ERROR) "
    r"invoker_kind=(?P<invoker_kind>SERVICE_DISCOVERY_MIGRATION|MIGRATION|CLUSTER|OTHER|MISSING) "
    r"directory_kind=(?P<directory_kind>SERVICE_DISCOVERY|DYNAMIC|OTHER|MISSING) "
    r"destroyed=(?P<destroyed>TRUE|FALSE|UNKNOWN|ERROR) "
    + " ".join(rf"{name}=(?P<{name}>{COUNT_TOKEN})" for name in ("all", "valid", "reconnect", "disabled"))
    + r"$"
)


APP_APPLICATION_DISCOVERY = re.compile(
    APP_DISCOVERY.pattern.removesuffix(r"$").replace("^AppDiscoveryObservation ", "^AppApplicationDiscoveryObservation ")
    + r" listener_state=(?P<listener_state>MISSING|PRESENT|UNSUPPORTED|ERROR) "
    + " ".join(rf"{name}=(?P<{name}>{COUNT_TOKEN})" for name in ("mapped_apps", "observed_apps", "instances"))
    + r"$"
)
ADMIN_FILE = "private-admin-readiness.json"
ADMIN_LIMIT = 65536
POST_LAUNCH_PHASES = {"launch", "auth-environment", "fixtures", "browser-base", "browser-business", "complete"}


def admin_context(phase, project):
    return (os.environ.get("ACCEPTANCE_DISPOSABLE") == "true"
            and phase in POST_LAUNCH_PHASES and valid_project(project))


def capture_admin_readiness(root, phase, project):
    """One credentials-free, loopback-only observation, never a readiness gate."""
    if not admin_context(phase, project):
        return
    # The launcher already records PM2 evidence. Do not probe an endpoint when
    # launch failed before creating Admin, or start/query any process ourselves.
    admin = read_snapshot(root, project)["services"].get("ulticode-admin", {})
    if admin.get("pm2_collection") != "ok":
        return
    directory = directory_fd(root)
    temporary = f".admin-readiness-{os.getpid()}.tmp"
    created = False
    try:
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     0o600, dir_fd=directory)
        created = True
        with os.fdopen(fd, "wb") as stream:
            # -q first disables curlrc; no redirects, proxies, netrc, auth or retries.
            result = subprocess.run(
                ["curl", "-q", "--silent", "--noproxy", "*", "--proxy", "",
                 "--proto", "=http", "--max-time", "3", "--connect-timeout", "3",
                 "--max-filesize", str(ADMIN_LIMIT),
                 "http://127.0.0.1:9102/api/v1/admin/health/ready"],
                stdout=stream, stderr=subprocess.DEVNULL, timeout=4, check=False)
        # curl without --fail preserves HTTP 503 bodies as well as 200 bodies.
        if result.returncode == 0:
            os.replace(temporary, ADMIN_FILE, src_dir_fd=directory, dst_dir_fd=directory)
    finally:
        if created:
            try:
                os.unlink(temporary, dir_fd=directory)
            except FileNotFoundError:
                pass
        os.close(directory)


def admin_readiness_observation(root, phase, project):
    result = {"status": "UNKNOWN", "components": {name: "UNKNOWN" for name in
              ("db", "redis", "storage", "problemOwnerRpc")}}
    if not admin_context(phase, project):
        return result
    try:
        directory = directory_fd(root)
        try:
            fd = os.open(ADMIN_FILE, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        finally:
            os.close(directory)
        with os.fdopen(fd, "rb") as stream:
            info = os.fstat(stream.fileno())
            if (not stat.S_ISREG(info.st_mode) or info.st_size > ADMIN_LIMIT
                    or info.st_mtime < int(project.split("-")[2])):
                return result
            raw = stream.read(ADMIN_LIMIT + 1)
        if len(raw) > ADMIN_LIMIT:
            return result
        value = json.loads(raw)
        if not isinstance(value, dict):
            return result
        if value.get("status") in ("UP", "DOWN"):
            result["status"] = value["status"]
        components = value.get("components")
        if isinstance(components, dict):
            for name in result["components"]:
                if components.get(name) in ("UP", "DOWN"):
                    result["components"][name] = components[name]
    except (OSError, ValueError, TypeError, RecursionError):
        pass
    return result


def app_application_discovery_observations(root):
    return _discovery_observations(root / "logs/backend-app.out.log",
                                   "AppApplicationDiscoveryObservation ", APP_APPLICATION_DISCOVERY)


def discovery_observations(root):
    return _discovery_observations(root / "logs/backend-submission.out.log",
                                   "SubmissionDiscoveryObservation ", DISCOVERY)


def app_discovery_observations(root):
    return _discovery_observations(root / "logs/backend-app.out.log",
                                   "AppDiscoveryObservation ", APP_DISCOVERY)


def _discovery_observations(path, marker, pattern):
    # Historical bounded log tail, not a same-process timeline or root-cause claim.
    try:
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            fd = os.open(path.name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        finally:
            os.close(directory)
        with os.fdopen(fd, "rb") as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode):
                return []
            offset = max(0, info.st_size - LIMIT)
            stream.seek(offset)
            text = stream.read(LIMIT).decode("utf-8", errors="replace")
            if offset:
                text = text.partition("\n")[2]  # discard a potentially truncated record
    except OSError:
        return []
    observations = []
    for line in text.splitlines():
        # Logger prefixes are discarded, not trusted. Require one complete fixed payload.
        if line.count(marker) != 1:
            continue
        payload = marker + line.partition(marker)[2]
        match = pattern.fullmatch(payload)
        if not match:
            continue
        item = match.groupdict()
        if "elapsed_ms" in item:
            item["elapsed_ms"] = int(item["elapsed_ms"])
            if item["elapsed_ms"] > 86_400_000:
                continue
        counts = [name for name in ("all", "valid", "reconnect", "disabled", "mapped_apps", "observed_apps", "instances") if name in item]
        for name in counts:
            if item[name].isdigit():
                item[name] = int(item[name])
        if any(isinstance(item[name], int) and item[name] > 10_000
               for name in counts):
            continue
        observations.append(item)
    return observations[-16:]


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
        "auth_readiness_failed": r"ulticode-auth did not become healthy; cannot run dev-admin bootstrap\.|backend-auth did not become healthy; not starting App or its deferred consumers\.",
        "app_readiness_failed": r"backend-app did not become healthy; (?:starting ulticode-judge anyway|not starting deferred Submission/Judge services\.)",
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
        "service_diagnostics_scope": "historical-log-observations-not-root-cause",
        "launch_readiness": read_snapshot(root, project),
        "admin_post_launch_readiness": admin_readiness_observation(root, phase, project),
        "admin_post_launch_readiness_scope": "post-launch-observation-not-same-timestamp-or-root-cause",
        "submission_discovery_observations": discovery_observations(root),
        "submission_discovery_scope": "historical-bounded-tail-not-process-correlated",
        "app_discovery_observations": app_discovery_observations(root),
        "app_discovery_scope": "historical-bounded-tail-not-process-correlated",
        "app_application_discovery_observations": app_application_discovery_observations(root),
        "app_application_discovery_scope": "historical-bounded-tail-not-process-correlated",
    }



def main():
    try:
        root = Path.cwd()
        if sys.argv[1:] == ["--capture-admin-readiness"]:
            capture_admin_readiness(root, os.environ.get("ACCEPTANCE_PHASE", "unknown"),
                                   os.environ.get("COMPOSE_PROJECT_NAME", ""))
            return 0
        report = build_report(root, os.environ.get("ACCEPTANCE_PHASE", "unknown"),
                              int(os.environ.get("ACCEPTANCE_EXIT", "1")),
                              os.environ.get("COMPOSE_PROJECT_NAME", ""))
        output = root / ".local/acceptance/installation.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n")
        if not report["passed"]:
            print(json.dumps({"acceptanceFailureDiagnostics": report}, separators=(",", ":")))
    except (OSError, ValueError, subprocess.SubprocessError):
        if sys.argv[1:] == ["--capture-admin-readiness"]:
            return 0  # Optional diagnostics cannot change the acceptance outcome.
        print("Acceptance report could not be written; private logs were not printed")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
