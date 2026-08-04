# Stage 1: Build (empreinte mémoire limitée pour petits serveurs CI)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Heap Maven plafonné — un build non limité satura facilement un VPS 2–4 Go
ARG MAVEN_OPTS=-Xmx1024m -XX:+UseSerialGC -Djava.awt.headless=true
ENV MAVEN_OPTS=${MAVEN_OPTS}

COPY pom.xml .
# Résolution deps seule (pas de compile) — couche cache Docker
RUN mvn -B -q dependency:go-offline -DskipTests

COPY src ./src
# 1 thread Maven, pas de tests en image runtime
RUN mvn -B -T 1C package -DskipTests -Dmaven.test.skip=true \
    && rm -rf /root/.m2/repository/*/resolver-status.properties \
    && find /root/.m2 -name "*lastUpdated" -delete 2>/dev/null || true

# Stage 2: Runtime léger
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget su-exec \
    && addgroup -S spring \
    && adduser -S spring -G spring \
    && mkdir -p /app/uploads \
    && chown -R spring:spring /app

COPY --from=build /app/target/backend-tontine-*.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh \
    && chown spring:spring /app/app.jar /app/docker-entrypoint.sh

ENV SERVER_PORT=6000
ENV UPLOAD_DIR=/app/uploads
# Runtime aussi plafonné (évite qu'une instance mange tout le VPS)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

EXPOSE 6000

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:6000/api/public/content || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
