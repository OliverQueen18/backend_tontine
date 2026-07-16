#!/bin/sh
set -e

UPLOAD_DIR="${UPLOAD_DIR:-/app/uploads}"
mkdir -p "$UPLOAD_DIR"

# Les volumes Docker sont souvent montés en root : on corrige le propriétaire
# avant de basculer sur l'utilisateur non-privilégié "spring".
if [ "$(id -u)" = "0" ]; then
  chown -R spring:spring "$UPLOAD_DIR" || true
  exec su-exec spring java -jar /app/app.jar
fi

exec java -jar /app/app.jar
