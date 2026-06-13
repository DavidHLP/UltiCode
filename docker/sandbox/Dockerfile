# =============================================================================
# UltiCode sandbox — D-form (LeetCode/HackerRank harness) image.
#
# Base image: ulticode-sandbox:base-17 — a separately tagged, JDK-17-pinned
# base that provides openjdk-17-jdk-headless + python3 + gcc + g++ on Debian
# bookworm, plus a `sandbox` uid=1000 user. We do NOT FROM ulticode-sandbox
# `:latest` because that's the project's rolling sandbox tag (currently
# pointing at the Form A image); an unversioned dependency on `:latest` makes
# builds silently inherit whatever happens to be on the host and the output
# tag of this Dockerfile would also be `:latest`, which is a self-dependency
# cycle.
#
# Build the base (once per host, rarely changes):
#   docker build -t ulticode-sandbox:base-17 -f Dockerfile.base .
# or, on first-time setup, point an apt-cached existing image:
#   docker tag <existing-sandbox-image> ulticode-sandbox:base-17
#
# Then build this Dockerfile:
#   ./harness/build.sh
#   docker build -t ulticode-sandbox-dform:phase2 .
#
# Strategy
# --------
# Harness sources are pre-compiled on the HOST (build-time, not inside the
# image) by `harness/build.sh` (see that file for the per-language recipes).
# The pre-compiled artifacts land in a local `harness-staging/` directory and
# are COPYed into the runtime image. This sidesteps the need to pull heavy
# build toolchain images (maven:*, gcc:*, g++:*, python:*) at build time and
# keeps the runtime image minimal.
#
# Why host-side pre-compile?
#   1. The host already has the toolchain (JDK 17/21, Python 3, gcc, g++).
#   2. The Maven `dependency:go-offline` step needs network anyway, so doing
#      it on the host is no worse than in a multi-stage build.
#   3. Avoids bloating image build context with `target/`, `__pycache__/`, etc.
#
# Runtime contract (see harness/README.md):
#   - User code:    /job/Solution.{java,py,c,cpp}
#   - Input:        /job/input.json
#   - Dispatch (backend's job):
#       Java:    javac -cp /opt/harness/java -d /job /job/Solution.java
#                && java -cp /opt/harness/java:/job Main /job/input.json
#       Python:  python3 /opt/harness/python/main.py /job/input.json
#       C:       gcc -O2 -o /job/Solution /job/Solution.c
#                && /job/Solution /job/input.json
#       C++:     g++ -O2 -o /job/Solution /job/Solution.cpp
#                && /job/Solution /job/input.json
#   - stdout = single JSON envelope, exit 0 = OK, exit 2 = harness panic
#   - stderr = harness stack trace on panic (never user output)
# =============================================================================

FROM ulticode-sandbox:base-17 AS runtime

# Pull pre-compiled harnesses from the host-side staging dir produced by
# harness/build.sh. The .dockerignore keeps `target/`, `__pycache__/`, `*.pyc`,
# and other source-tree noise OUT of the build context.
COPY --chown=sandbox:sandbox harness-staging/java/   /opt/harness/java/
COPY --chown=sandbox:sandbox harness-staging/python/ /opt/harness/python/
COPY --chown=sandbox:sandbox harness-staging/c/      /opt/harness/c/
COPY --chown=sandbox:sandbox harness-staging/cpp/    /opt/harness/cpp/

USER sandbox
WORKDIR /home/sandbox
