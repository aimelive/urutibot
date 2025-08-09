# syntax=docker/dockerfile:1.7

# Multi-stage build for UrutiBot (Spring Boot, Java 21)
# - Builder uses Maven + Temurin JDK 21
# - JRE stage creates a minimal custom runtime via jlink
# - Runtime uses debian:12-slim + minimal JRE + libgomp1 for ONNX runtime
# - Includes urutihub.txt on filesystem and sets default env to point to it

ARG MAVEN_VERSION=3.9.9

FROM maven:${MAVEN_VERSION}-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Pre-fetch dependencies for better Docker layer caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests package

# Runtime image (Temurin JRE 21 on Jammy, multi-arch and stable under QEMU)
FROM eclipse-temurin:21-jre-jammy AS runtime

ENV APP_HOME=/app
WORKDIR ${APP_HOME}

# Install minimal native deps for onnxruntime
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy application
COPY --from=builder /workspace/target/*-SNAPSHOT.jar ${APP_HOME}/app.jar
COPY src/main/resources/urutihub.txt ${APP_HOME}/urutihub.txt

# Create non-root user
RUN useradd -r -u 1001 -g root spring \
    && chown -R spring:root ${APP_HOME}
USER spring

# Sensible JVM defaults for containers; can be overridden via JAVA_OPTS
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

# Default app envs (override at runtime)
ENV APP_ABOUT_COMPANY_FILE=file:/app/urutihub.txt

EXPOSE 8080

# Run the Spring Boot app
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]