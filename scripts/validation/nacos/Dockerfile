FROM debian@sha256:a3bba7dc7dbcc1f37c2856c51fc9a7fac96608f50cb87b1b4e4d8b8c85ae988e
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-17-jdk-headless python3 iproute2 coreutils && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
WORKDIR /work/nacos
CMD ["python3", "/work/nacos/run_six.py"]
