FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    nodejs \
    python3 \
    openjdk-17-jdk-headless \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -u 1000 sandbox
USER sandbox
WORKDIR /home/sandbox

CMD ["node", "--version"]
