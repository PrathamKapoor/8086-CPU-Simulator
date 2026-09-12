# 8086 CPU Simulator — Multi-Stage Docker Build
# ------------------------------------------------------------------
# Stage 1: Build / dependency resolution
# Stage 2: Runtime with OpenJDK 21 JRE (slim)
# ------------------------------------------------------------------

# ------------------------------------------------------------------
# STAGE 1 — Builder
# ------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

# Metadata
LABEL org.opencontainers.image.title="8086 CPU Simulator"
LABEL org.opencontainers.image.version="3.0.0"
LABEL org.opencontainers.image.description="RTL-level 8086 CPU simulator with JavaFX GUI"
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.source="https://github.com/cpu-simulator/8086"

WORKDIR /build

# Copy dependency descriptors first (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline dependency:resolve -B

# Copy source and build fat JAR
COPY src ./src
RUN mvn clean package -B -DskipTests

# ------------------------------------------------------------------
# STAGE 2 — Runtime
# ------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S simulator && adduser -S simulator -G simulator -D -H /home/simulator

# Metadata (repeated for runtime layer)
LABEL org.opencontainers.image.title="8086 CPU Simulator"
LABEL org.opencontainers.image.version="3.0.0"

# Install minimal runtime dependencies for headless JavaFX / GUI support
RUN apk add --no-cache --update \
    libx11 \
    libxext \
    libxrender \
    libxtst \
    libxi \
    fontconfig \
    ttf-dejavu \
    && rm -rf /var/cache/apk/*

WORKDIR /app

# Copy only the built artifact from builder
COPY --from=builder /build/target/cpu-simulator.jar /app/cpu-simulator.jar

# Optional: expose port for future web interface / remote management
# The port is declared but not actively listened unless a web layer is added.
EXPOSE 8080

# Health check — verifies JAR integrity and JVM readiness
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD java -cp /app/cpu-simulator.jar -version || exit 1

# Environment variables
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication -Dfile.encoding=UTF-8"
ENV DISPLAY=""
ENV JAVA_TOOL_OPTIONS="${JAVA_OPTS}"

# Switch to non-root user
USER simulator

# Default entrypoint: run CLI simulator (headless, avoids JavaFX X11 crash in containers)
ENTRYPOINT ["sh", "-c"]
CMD ["java ${JAVA_OPTS} -cp /app/cpu-simulator.jar simulator.MainSimulator"]
