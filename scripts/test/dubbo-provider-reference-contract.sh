#!/usr/bin/env bash
set -euo pipefail

# ARCH-DUBBO-001: every source-level provider must have a real repository
# consumer; compatibility exceptions must be explicit and reviewable.
# ponytail: source-level inventory cannot see generated registrations; replace
# with bytecode/registry inspection if provider generation is introduced.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
from collections import defaultdict
import os
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
java_files = sorted(
    path
    for path in root.glob("services/**/src/main/java/**/*.java")
    if path.is_file()
)


def owner(path: Path) -> str:
    relative = path.relative_to(root)
    return relative.parts[1]


def annotation(lines: list[str], start: int, marker: str) -> tuple[str, int]:
    text = lines[start].strip()
    end = start
    while ")" not in text and end + 1 < len(lines):
        end += 1
        text += " " + lines[end].strip()
    if ")" not in text:
        raise SystemExit(f"{marker} annotation is unterminated at line {start + 1}")
    return text, end


def annotation_value(text: str, key: str) -> str | None:
    match = re.search(rf"\b{key}\s*=\s*(\"[^\"]*\"|[A-Za-z0-9_.-]+)", text)
    if not match:
        return None
    value = match.group(1).strip()
    return value[1:-1] if value.startswith('"') and value.endswith('"') else value


def resolve_yaml_scalar(value: str) -> str | None:
    value = value.split("#", 1)[0].strip()
    if len(value) >= 2 and value[0] == value[-1] == '"':
        value = value[1:-1]
    placeholder = re.fullmatch(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\}", value)
    if placeholder:
        name, default = placeholder.groups()
        value = os.environ.get(name, default)
        if value is None:
            return None
        value = value.strip()
    return value or None


runtime_version_cache: dict[Path, str | None] = {}


def runtime_consumer_version(source: str) -> str | None:
    source_path = root / source
    config_path = next(
        (parent / "src/main/resources/application.yml"
         for parent in source_path.parents
         if (parent / "src/main/resources/application.yml").is_file()),
        None,
    )
    if config_path is None:
        raise SystemExit(f"{source} has no runtime application.yml for <config> version resolution")
    if config_path in runtime_version_cache:
        return runtime_version_cache[config_path]

    in_dubbo = False
    in_consumer = False
    dubbo_indent = -1
    consumer_indent = -1
    resolved = None
    for raw_line in config_path.read_text(encoding="utf-8").splitlines():
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip())
        if not in_dubbo:
            if stripped == "dubbo:":
                in_dubbo = True
                dubbo_indent = indent
            continue
        if indent <= dubbo_indent:
            in_dubbo = stripped == "dubbo:"
            dubbo_indent = indent if in_dubbo else -1
            in_consumer = False
            if in_dubbo:
                continue
        if not in_consumer:
            if indent == dubbo_indent + 2 and stripped == "consumer:":
                in_consumer = True
                consumer_indent = indent
            continue
        if indent <= consumer_indent:
            in_consumer = False
            if indent == dubbo_indent + 2 and stripped == "consumer:":
                in_consumer = True
                consumer_indent = indent
            continue
        if indent == consumer_indent + 2 and stripped.startswith("version:"):
            resolved = resolve_yaml_scalar(stripped.split(":", 1)[1])
            break
    if resolved is None:
        raise SystemExit(
            f"{source} uses <config> but application.yml has no resolvable "
            "dubbo.consumer.version"
        )
    runtime_version_cache[config_path] = resolved
    return resolved


providers: list[dict[str, str]] = []
references: list[dict[str, str]] = []

for path in java_files:
    lines = path.read_text(encoding="utf-8").splitlines()
    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("@DubboService"):
            text, end = annotation(lines, index, "@DubboService")
            class_match = None
            for candidate in lines[end + 1 : end + 9]:
                class_match = re.search(
                    r"\bclass\s+(\w+)\s+implements\s+([A-Za-z_$][\w$]*)", candidate
                )
                if class_match:
                    break
            if not class_match:
                raise SystemExit(
                    f"{path}:{index + 1} @DubboService has no directly implemented contract"
                )
            implementation_text = " ".join(lines[end + 1 : end + 9])
            implemented_match = re.search(
                r"\bimplements\s+(.+?)(?:\{|$)", implementation_text
            )
            implemented_interfaces = [class_match.group(2)]
            if implemented_match:
                implemented_interfaces = [
                    re.sub(r"<.*>", "", item).strip()
                    for item in implemented_match.group(1).split(",")
                    if re.sub(r"<.*>", "", item).strip()
                ]
            group = annotation_value(text, "group")
            version = annotation_value(text, "version")
            if not group or not version:
                raise SystemExit(
                    f"{path}:{index + 1} provider must declare group and version"
                )
            providers.append(
                {
                    "path": str(path.relative_to(root)),
                    "owner": owner(path),
                    "class": class_match.group(1),
                    "interface": class_match.group(2),
                    "group": group,
                    "version": version,
                    "implemented_interfaces": implemented_interfaces,
                    "source": "\n".join(lines),
                }
            )
        elif stripped.startswith("@DubboReference"):
            text, end = annotation(lines, index, "@DubboReference")
            field_match = None
            for candidate in lines[end + 1 : end + 9]:
                field_match = re.search(
                    r"\b(?:private|protected|public)\s+(?:final\s+)?"
                    r"([A-Za-z_$][\w$]*)\s+(\w+)\s*;",
                    candidate,
                )
                if field_match:
                    break
            if not field_match:
                raise SystemExit(
                    f"{path}:{index + 1} @DubboReference has no directly declared field"
                )
            class_name = "<unknown>"
            for candidate in reversed(lines[:index]):
                class_match = re.search(r"\bclass\s+(\w+)\b", candidate)
                if class_match:
                    class_name = class_match.group(1)
                    break
            group = annotation_value(text, "group")
            if not group:
                raise SystemExit(f"{path}:{index + 1} reference must declare group")
            references.append(
                {
                    "path": str(path.relative_to(root)),
                    "owner": owner(path),
                    "class": class_name,
                    "interface": field_match.group(1),
                    "field": field_match.group(2),
                    "group": group,
                    "version": annotation_value(text, "version") or "<config>",
                    "runtime_version": runtime_consumer_version(str(path))
                    if not annotation_value(text, "version")
                    else None,
                }
            )

providers_by_identity: dict[tuple[str, str, str], list[dict[str, str]]] = defaultdict(list)
for provider in providers:
    providers_by_identity[
        (provider["interface"], provider["group"], provider["version"])
    ].append(provider)
for identity, provider_matches in sorted(providers_by_identity.items()):
    if len(provider_matches) > 1:
        files = ", ".join(match["path"] for match in provider_matches)
        raise SystemExit(f"duplicate Dubbo providers for {identity}: {files}")

def matches(provider: dict[str, str], reference: dict[str, str]) -> bool:
    # `<config>` means the version is supplied by the consumer's runtime
    # configuration. runtime_consumer_version() fails closed when the owner
    # configuration does not provide a concrete version, so a config-backed
    # reference must match the provider version exactly.
    reference_version = (
        reference["runtime_version"]
        if reference["version"] == "<config>"
        else reference["version"]
    )
    return (
        reference["interface"] == provider["interface"]
        and reference["group"] == provider["group"]
        and reference_version == provider["version"]
    )


def matching_consumers(provider: dict[str, str]) -> list[dict[str, str]]:
    return [
        reference
        for reference in references
        if reference["path"] != provider["path"] and matches(provider, reference)
    ]


for provider in providers:
    consumers = matching_consumers(provider)
    if not consumers:
        raise SystemExit(
            f"{provider['path']}:{provider['class']} exposes "
            f"({provider['interface']}, {provider['group']}, {provider['version']}) "
            "with no repository @DubboReference consumer"
        )

print(
    f"Dubbo inventory: providers={len(providers)}, references={len(references)}, "
    f"interfaces={len({provider['interface'] for provider in providers})}"
)
for provider in sorted(providers, key=lambda item: item["path"]):
    consumers = sorted(
        f"{reference['owner']}:{reference['class']}.{reference['field']}"
        for reference in matching_consumers(provider)
    )
    print(
        "PROVIDER "
        f"owner={provider['owner']} "
        f"class={provider['class']} "
        f"interface={provider['interface']} "
        f"group={provider['group']} "
        f"version={provider['version']} "
        f"consumers={','.join(consumers) or 'N-1-compatibility'} "
        f"file={provider['path']}"
    )
for reference in sorted(references, key=lambda item: item["path"]):
    print(
        "REFERENCE "
        f"owner={reference['owner']} "
        f"consumer={reference['class']}.{reference['field']} "
        f"interface={reference['interface']} "
        f"group={reference['group']} "
        f"version={reference['version']} "
        f"resolved_version={reference['runtime_version'] or '<explicit>'} "
        f"file={reference['path']}"
    )
print("dubbo-provider-reference-contract: PASS")
PY
