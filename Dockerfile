# syntax=docker/dockerfile:1.7

ARG MAVEN_VERSION=3.9.9
ARG BUSYBOX_VERSION=1.37.0

# ---------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM maven:${MAVEN_VERSION}-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Pre-fetch dependencies for better Docker layer caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests package

RUN java -Djarmode=tools -jar target/*-SNAPSHOT.jar extract --layers --launcher --destination /extracted

# Work out which JDK modules the app actually needs (feeds jlink below)
COPY docker/jdeps-modules.sh /docker/jdeps-modules.sh
RUN sh /docker/jdeps-modules.sh /extracted /modules.txt

# ---------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM maven:${MAVEN_VERSION}-eclipse-temurin-21 AS prune
ARG TARGETARCH
COPY --from=builder /extracted /extracted
COPY docker/prune-natives.sh /docker/prune-natives.sh
RUN sh /docker/prune-natives.sh /extracted "$TARGETARCH"

# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS jre
COPY --from=builder /modules.txt /modules.txt
RUN jlink \
      --add-modules "$(cat /modules.txt)" \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress zip-0 \
      --output /jre

# ---------------------------------------------------------------------------
FROM busybox:${BUSYBOX_VERSION}-musl AS busybox

FROM gcr.io/distroless/cc-debian12:nonroot AS runtime

COPY --from=busybox /bin/busybox /bin/busybox

ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:/usr/local/bin:/usr/bin:/bin \
    LANG=C.UTF-8 \
    APP_HOME=/app
COPY --from=jre /jre ${JAVA_HOME}
# Build-time sanity check: the jlinked runtime must start on this base
RUN ["/opt/java/bin/java", "-version"]

WORKDIR ${APP_HOME}
# Least-changing layers first so day-to-day app changes only rebuild the last one.
# Files stay root-owned (read-only for the app user); the app writes only to /tmp and $HOME.
COPY --from=prune /extracted/dependencies/ ./
COPY --from=prune /extracted/spring-boot-loader/ ./
COPY --from=prune /extracted/snapshot-dependencies/ ./
COPY --from=prune /extracted/application/ ./
COPY src/main/resources/urutihub.txt ./urutihub.txt

# distroless :nonroot = uid 65532, HOME=/home/nonroot (used by the DJL tokenizer cache)
USER nonroot

# Sensible JVM defaults for containers; can be overridden via JAVA_OPTS
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

# Default app envs (override at runtime)
ENV APP_ABOUT_COMPANY_FILE=file:/app/urutihub.txt

EXPOSE 8080

# Liveness probe (permitted anonymously in SecurityConfig). busybox wget exits
# non-zero on any HTTP error, so a 503 from a DOWN liveness state fails the check.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD ["/bin/busybox", "wget", "-q", "-T", "4", "-O", "/dev/null", "http://127.0.0.1:8080/actuator/health/liveness"]

# busybox sh expands JAVA_OPTS, then exec so the JVM is PID 1 and receives SIGTERM
ENTRYPOINT ["/bin/busybox", "sh", "-c", "exec /opt/java/bin/java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
