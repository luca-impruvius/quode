# syntax=docker/dockerfile:1
# Backend image (M0-10). Build from the repository root, so the git metadata for Actuator /info is
# available: docker build -f backend/Dockerfile .

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY .git .git
COPY backend backend
WORKDIR /workspace/backend
# Tests run in CI, not here. The cache mount keeps Gradle's downloads between builds.
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon \
    && java -Djarmode=tools -jar build/libs/quode-*-SNAPSHOT.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre-noble
RUN useradd --system --uid 10001 --no-create-home quode
WORKDIR /app
# Layers from least to most often changed, so a code change rebuilds only the last one.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER quode
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080 8081
# The JRE image has no curl or wget: a plain HTTP/1.0 request through bash's /dev/tcp.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=4 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /api/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && head -n1 <&3 | grep -q ' 200 '"]
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
