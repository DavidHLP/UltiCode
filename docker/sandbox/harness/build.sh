#!/usr/bin/env bash
# =============================================================================
# harness/build.sh — pre-compile all 4 D-form harnesses for the sandbox image.
#
# Output: docker/sandbox/harness-staging/{java,python,c,cpp}/ populated with
#         the artifacts that the multi-stage Dockerfile COPYs into the
#         runtime image at /opt/harness/{language}/.
#
# Why host-side?  The base image (ulticode-sandbox:latest) already carries
# JDK 17, Python 3, gcc, g++ on Debian bookworm. Doing the compile in a
# multi-stage build would force pulling maven:*, gcc:*, g++:*, python:*
# base images (slow + flaky on this host's mirror). Pre-compiling locally
# also keeps the Docker build context small and predictable.
#
# Toolchain requirements:
#   - JDK 17 or newer (mvn)         for Java harness
#   - Python 3.10+                  for py_compile on Python harness
#   - gcc 12+                       for C harness
#   - g++ 12+                       for C++ harness (C++17)
#
# Usage:
#   ./harness/build.sh                  # build all 4 + docker build + tag :latest
#   ./harness/build.sh java python      # build only the listed ones
#   ./harness/build.sh --clean          # nuke staging/ before building
#   ./harness/build.sh --no-docker      # skip docker build (CI matrix stage)
#
# Default end-to-end behavior (per ADR-002 §6.5):
#   1. mvn / py_compile / gcc / g++ to populate harness-staging/
#   2. docker build -t ulticode-sandbox-dform:phase2 .
#   3. docker tag  ulticode-sandbox-dform:phase2  ulticode-sandbox:latest
# So a fresh `./harness/build.sh` makes SANDBOX_IMAGE=ulticode-sandbox:latest
# (the project default) actually point at the dform harness.
# =============================================================================

set -euo pipefail

# Resolve to repo root regardless of cwd.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
SANDBOX_DIR="$(cd -- "${SCRIPT_DIR}/.." &> /dev/null && pwd)"
STAGING="${SANDBOX_DIR}/harness-staging"

ALL_LANGS=(java python c cpp)

# ── arg parsing ──────────────────────────────────────────────────────────────
CLEAN=0
NO_DOCKER=0
LANGS=()
for arg in "$@"; do
    case "${arg}" in
        --clean) CLEAN=1 ;;
        --no-docker) NO_DOCKER=1 ;;
        --help|-h)
            sed -n '2,32p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        -*) echo "unknown flag: ${arg}" >&2; exit 2 ;;
        *)  LANGS+=("${arg}") ;;
    esac
done
[ "${#LANGS[@]}" -eq 0 ] && LANGS=("${ALL_LANGS[@]}")

for lang in "${LANGS[@]}"; do
    case "${lang}" in
        java|python|c|cpp) ;;
        *) echo "unknown language: ${lang} (valid: ${ALL_LANGS[*]})" >&2; exit 2 ;;
    esac
done

# ── clean ────────────────────────────────────────────────────────────────────
if [ "${CLEAN}" = "1" ]; then
    rm -rf "${STAGING}"
    echo "[build.sh] removed ${STAGING}"
fi

# ── per-language build ───────────────────────────────────────────────────────
build_java() {
    echo "[build.sh] java: mvn compile -> ${STAGING}/java/"
    mkdir -p "${STAGING}/java"
    (
        cd "${SCRIPT_DIR}/java"
        mvn -B -q -DskipTests test-compile
        cp -r target/classes/. "${STAGING}/java/"
    )
    echo "[build.sh] java: $(ls "${STAGING}/java" | wc -l) class files"
}

build_python() {
    echo "[build.sh] python: py_compile -> ${STAGING}/python/"
    mkdir -p "${STAGING}/python"
    (
        cd "${SCRIPT_DIR}/python"
        # compileall with -b writes .pyc next to source; clean stale __pycache__ first.
        rm -rf __pycache__
        python3 -m compileall -b -q .
        cp harness.py main.py oj_types.py _case_runner.py "${STAGING}/python/"
        # .pyc for the four harness modules only — drop test_*.pyc to keep the
        # image free of testing artifacts. _case_runner.py is the per-case
        # subprocess worker spawned by main.py; it MUST ship or every case
        # resolves to a Runtime Error (case runner exits non-zero).
        for m in harness main oj_types _case_runner; do
            [ -f "${m}.pyc" ] && cp "${m}.pyc" "${STAGING}/python/"
        done
    )
    echo "[build.sh] python: $(ls "${STAGING}/python/")"
}

build_c() {
    echo "[build.sh] c: gcc -O2 -> ${STAGING}/c/c-sandbox"
    mkdir -p "${STAGING}/c"
    (
        cd "${SCRIPT_DIR}/c"
        gcc -O2 -Wall -Wextra -o "${STAGING}/c/c-sandbox" main.c
    )
    ls -la "${STAGING}/c/"
}

build_cpp() {
    echo "[build.sh] cpp: g++ -std=c++17 -O2 -> ${STAGING}/cpp/cpp-sandbox"
    mkdir -p "${STAGING}/cpp"
    (
        cd "${SCRIPT_DIR}/cpp"
        # cpp-sandbox is the orchestrator: it reads /job/input.json +
        # /job/solution.cpp, statically extracts the Solution signature,
        # generates a typed runner, g++-compiles it, and emits the D-form
        # envelope. Link it against the harness library sources it shares
        # with the runtime runner.
        # Fully static: the orchestrator is compiled on the host (newer glibc
        # + libstdc++ than the Debian-bookworm base image). Static linking
        # makes cpp-sandbox self-contained so it runs against any container
        # glibc. (The generated runner is compiled INSIDE the container by
        # the image's own g++, so it has no such mismatch.)
        g++ -std=c++17 -O2 -Wall -Wextra -static \
            -o "${STAGING}/cpp/cpp-sandbox" \
            main.cpp json.cpp serializer.cpp solution_parser.cpp
        # Ship the harness sources too: the orchestrator g++-compiles the
        # generated runner against them at runtime (see main.cpp
        # generateRunner — it links /tmp/runner.cpp + json.cpp + serializer.cpp).
        cp oj_types.hpp json.hpp json.cpp serializer.hpp serializer.cpp \
           solution_parser.hpp solution_parser.cpp "${STAGING}/cpp/"
    )
    ls -la "${STAGING}/cpp/"
}

# ── dispatch ────────────────────────────────────────────────────────────────
for lang in "${LANGS[@]}"; do
    "build_${lang}"
done

# ── post-build: fail-fast on missing base image, then build + retag ───────
# The Dockerfile FROM-clause references ulticode-sandbox:base-17. If that
# tag is missing locally, `docker build` will fail with "manifest not
# found" — fail fast here so the message is clear, instead of inside the
# docker build step where it surfaces with noisy context.
if ! docker image inspect ulticode-sandbox:base-17 >/dev/null 2>&1; then
    echo "[build.sh] ERROR: ulticode-sandbox:base-17 not found." >&2
    echo "[build.sh]   First-time setup:" >&2
    echo "[build.sh]     docker tag <existing-sandbox-image> ulticode-sandbox:base-17" >&2
    echo "[build.sh]   or rebuild the base from apt:" >&2
    echo "[build.sh]     docker build -t ulticode-sandbox:base-17 -f Dockerfile.base ." >&2
    exit 2
fi

# ── Build the D-form sandbox image + retag :latest ─────────────────────────
# ADR-002 §6.5 hardening: previously the dform image was built with a
# pinned tag (ulticode-sandbox-dform:phase2) but :latest kept pointing at
# the pre-dform Form-A image. A default SANDBOX_IMAGE=ulticode-sandbox:latest
# then silently launched the broken image and produced "Runtime Error"
# for every submission. We now (a) build the dform image, (b) tag it as
# :latest so the default SANDBOX_IMAGE works out-of-the-box, and (c) keep
# the phase2 tag alive so the version-pin path
# (SANDBOX_IMAGE=...:phase2-pinned) still works.
#
# Skip with --no-docker if you only want the harness binaries (e.g. CI
# matrix job that builds images in a separate stage, or local dev that
# only edits harness sources).
DFORM_TAG="ulticode-sandbox-dform:phase2"
DFORM_BUILD_FLAGS=()
if [ "${CLEAN}" = "1" ]; then
    DFORM_BUILD_FLAGS+=(--no-cache)
fi

if [ "${NO_DOCKER:-0}" != "1" ]; then
    echo "[build.sh] docker build -t ${DFORM_TAG} ${DFORM_BUILD_FLAGS[*]} ${SANDBOX_DIR}"
    docker build -t "${DFORM_TAG}" "${DFORM_BUILD_FLAGS[@]}" "${SANDBOX_DIR}"
    echo "[build.sh] docker tag ${DFORM_TAG} ulticode-sandbox:latest"
    docker tag "${DFORM_TAG}" ulticode-sandbox:latest
    echo "[build.sh] built + tagged ${DFORM_TAG} and ulticode-sandbox:latest"
else
    echo "[build.sh] --no-docker: skipping docker build/tag (you must run it manually)"
fi

echo "[build.sh] done. Override with SANDBOX_IMAGE in .env if you want a different tag."
