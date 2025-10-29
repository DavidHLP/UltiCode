FROM eclipse-temurin:17-jdk-jammy
LABEL maintainer="sentinel-user"
LABEL version="1.8.8"
LABEL description="Sentinel Dashboard 1.8.8 on JDK 17"
WORKDIR /app
RUN wget -O sentinel-dashboard.jar https://github.com/alibaba/Sentinel/releases/download/1.8.8/sentinel-dashboard-1.8.8.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-Dserver.port=8080", "-Dcsp.sentinel.dashboard.server=localhost:8080", "-Dproject.name=sentinel-dashboard", "-jar", "sentinel-dashboard.jar" ]