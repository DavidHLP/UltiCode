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
#   ./harness/build.sh              # build all 4
#   ./harness/build.sh java python  # build only the listed ones
#   ./harness/build.sh --clean      # nuke staging/ before building
#
# CI: this script is what `./docker build` of the sandbox image assumes has
# already run.  Wire it ahead of `docker build` in your pipeline.
# =============================================================================

set -euo pipefail

# Resolve to repo root regardless of cwd.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
SANDBOX_DIR="$(cd -- "${SCRIPT_DIR}/.." &> /dev/null && pwd)"
STAGING="${SANDBOX_DIR}/harness-staging"

ALL_LANGS=(java python c cpp)

# ── arg parsing ──────────────────────────────────────────────────────────────
CLEAN=0
LANGS=()
for arg in "$@"; do
    case "${arg}" in
        --clean) CLEAN=1 ;;
        --help|-h)
            sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
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
        cp harness.py main.py oj_types.py "${STAGING}/python/"
        # .pyc for the three harness modules only — drop test_*.pyc to keep the
        # image free of testing artifacts.
        for m in harness main oj_types; do
            [ -f "${m}.pyc" ] && cp "${m}.pyc" "${STAGING}/python/"
        done
    )
    echo "[build.sh] python: $(ls "${STAGING}/python}")"
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
        g++ -std=c++17 -O2 -Wall -Wextra -o "${STAGING}/cpp/cpp-sandbox" main.cpp
    )
    ls -la "${STAGING}/cpp/"
}

# ── dispatch ────────────────────────────────────────────────────────────────
for lang in "${LANGS[@]}"; do
    "build_${lang}"
done

echo "[build.sh] done. Next: docker build -t ulticode-sandbox-dform ."
