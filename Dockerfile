# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
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
EXPOSE 6000

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:6000/api/public/content || exit 1

# Démarrage en root pour chown du volume, puis drop vers spring (voir entrypoint).
ENTRYPOINT ["/app/docker-entrypoint.sh"]
