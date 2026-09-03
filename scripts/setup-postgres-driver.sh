#!/bin/sh
set -eu
VERSION=42.7.13
mkdir -p lib
TARGET="lib/postgresql-$VERSION.jar"
if [ -f "$TARGET" ]; then
  printf '%s\n' "PostgreSQL JDBC driver already exists: $TARGET"
  exit 0
fi
curl --fail --location --output "$TARGET" "https://jdbc.postgresql.org/download/postgresql-$VERSION.jar"
printf '%s\n' "Downloaded $TARGET"
