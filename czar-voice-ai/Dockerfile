FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

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

RUN mvn -pl czar-voice-ai -am dependency:go-offline -B -q

COPY czar-common/src   czar-common/src
COPY czar-voice-ai/src czar-voice-ai/src

RUN mvn -pl czar-voice-ai -am clean package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S czar && adduser -S czar -G czar
USER czar
COPY --from=builder /workspace/czar-voice-ai/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
