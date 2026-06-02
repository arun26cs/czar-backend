# Multi-stage Docker build — build context is the REPO ROOT
# Usage from repo root: docker build -f czar-gateway/Dockerfile -t czar-gateway .

FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copy all POMs first — maximises Docker layer cache when only source changes
COPY pom.xml .
COPY czar-common/pom.xml     czar-common/pom.xml
COPY czar-gateway/pom.xml    czar-gateway/pom.xml
COPY czar-auth/pom.xml       czar-auth/pom.xml
COPY czar-user/pom.xml       czar-user/pom.xml
COPY czar-planner/pom.xml    czar-planner/pom.xml
COPY czar-notes/pom.xml      czar-notes/pom.xml
COPY czar-voice-ai/pom.xml   czar-voice-ai/pom.xml
COPY czar-conflict/pom.xml   czar-conflict/pom.xml
COPY czar-notification/pom.xml czar-notification/pom.xml

# Download dependencies (cached layer — only re-runs when any POM changes)
RUN mvn -pl czar-gateway -am dependency:go-offline -B -q

# Copy source for czar-common (required to build czar-gateway)
COPY czar-common/src czar-common/src
COPY czar-gateway/src czar-gateway/src

RUN mvn -pl czar-gateway -am clean package -DskipTests -B -q

# ─── Runtime image ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S czar && adduser -S czar -G czar
USER czar

COPY --from=builder /workspace/czar-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
