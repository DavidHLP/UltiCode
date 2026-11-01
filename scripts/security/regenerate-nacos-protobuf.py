#!/usr/bin/env python3
"""Check/rebuild the five Nacos messages with the pinned Linux x86_64 protoc.

Normal Maven builds use committed Java sources; no generator download occurs.
The descriptor is taken from the published API jar, not the mismatching tag proto.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "services/platform/nacos-api-compat"
FAMILIES = {"Metadata", "MetadataOrBuilder", "Payload", "PayloadOrBuilder", "NacosGrpcService"}
PROTOC_SHA256 = "e34121274da8409bc86c093b868e05df198cbe1f5017202a560866390ae3d6ec"
DESCRIPTOR_SHA256 = "ea709b2a17cd7e71b1467ff5be7fbacec74d52da50f956f6e48bbe4348faea20"

def require(value, message):
    if not value:
        raise RuntimeError(message)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--protoc", type=Path, required=True)
    parser.add_argument("--write", action="store_true", help="replace generated sources after all input checks")
    args = parser.parse_args()
    protoc = args.protoc.resolve()
    descriptor = MODULE / "src/test/resources/nacos-published.desc"
    require(hashlib.sha256(protoc.read_bytes()).hexdigest() == PROTOC_SHA256, "protoc digest mismatch")
    require(hashlib.sha256(descriptor.read_bytes()).hexdigest() == DESCRIPTOR_SHA256, "descriptor digest mismatch")
    with tempfile.TemporaryDirectory(prefix="nacos-protobuf-") as directory:
        generated = Path(directory)
        subprocess.run([str(protoc), "--descriptor_set_in=" + str(descriptor), "--java_out=" + directory, "nacos_grpc_service.proto"], check=True, timeout=30)
        files = list(generated.rglob("*.java"))
        require(len(files) == 5 and {p.stem for p in files} == FAMILIES, "unexpected generated source inventory")
        for source in files:
            # protoc 3.25.8 emits trailing whitespace; canonicalize only that
            # and terminal blank lines for repository whitespace policy.
            content = ("\n".join(line.rstrip() for line in source.read_text().splitlines()).rstrip() + "\n").encode()
            target = MODULE / "src/main/java" / source.relative_to(generated)
            if args.write:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
            else:
                require(target.read_bytes() == content, "generated source drift: " + str(target.relative_to(ROOT)))
    print("Nacos generated source identity verified")

if __name__ == "__main__":
    main()
