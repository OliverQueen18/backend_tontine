#!/bin/sh
set -e

UPLOAD_DIR="${UPLOAD_DIR:-/app/uploads}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

mkdir -p "$UPLOAD_DIR"

# Les volumes Docker sont souvent montés en root : on corrige le propriétaire
# avant de basculer sur l'utilisateur non-privilégié "spring".
if [ "$(id -u)" = "0" ]; then
  chown -R spring:spring "$UPLOAD_DIR" || true
  # shellcheck disable=SC2086
  exec su-exec spring java $JAVA_OPTS -jar /app/app.jar
fi

# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar /app/app.jar
