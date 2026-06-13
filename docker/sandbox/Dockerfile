# =============================================================================
# UltiCode sandbox — D-form (LeetCode/HackerRank harness) image.
#
# Base image: ulticode-sandbox:latest (the project's pre-existing sandbox
# image, which already has openjdk-17-jdk-headless + python3 + gcc + g++ on
# Debian bookworm, plus a `sandbox` uid=1000 user). Building FROM scratch
# with apt-get installs is slow on this host's mirror and adds nothing
# beyond what ulticode-sandbox already provides.
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
#
# Build:  ./harness/build.sh  (or `docker build` after staging exists)
# =============================================================================

FROM ulticode-sandbox:latest AS runtime

# Pull pre-compiled harnesses from the host-side staging dir produced by
# harness/build.sh. The .dockerignore keeps `target/`, `__pycache__/`, `*.pyc`,
# and other source-tree noise OUT of the build context.
COPY --chown=sandbox:sandbox harness-staging/java/   /opt/harness/java/
COPY --chown=sandbox:sandbox harness-staging/python/ /opt/harness/python/
COPY --chown=sandbox:sandbox harness-staging/c/      /opt/harness/c/
COPY --chown=sandbox:sandbox harness-staging/cpp/    /opt/harness/cpp/

USER sandbox
WORKDIR /home/sandbox
