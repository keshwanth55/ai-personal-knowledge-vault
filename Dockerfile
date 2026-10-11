FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml dependency:go-offline

COPY backend/src backend/src
RUN mvn -B -f backend/pom.xml verify

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/data /app/uploads \
    && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/backend/target/knowledge-vault-api-1.0.0.jar /app/app.jar

USER app
EXPOSE 8081
ENV VAULT_STORAGE_DIR=/app/uploads
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
