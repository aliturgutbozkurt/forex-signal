#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -d "/Users/aliturgutbozkurt/Library/Java/JavaVirtualMachines/jdk-25.0.4.1+1/Contents/Home" ]; then
  export JAVA_HOME="/Users/aliturgutbozkurt/Library/Java/JavaVirtualMachines/jdk-25.0.4.1+1/Contents/Home"
fi
export MAVEN_OPTS="--enable-native-access=ALL-UNNAMED"
if [ -x "$DIR/tools/apache-maven-3.9.9/bin/mvn" ]; then
  exec "$DIR/tools/apache-maven-3.9.9/bin/mvn" "$@"
elif command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
else
  mkdir -p "$DIR/tools"
  curl -sSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz | tar -xz -C "$DIR/tools"
  exec "$DIR/tools/apache-maven-3.9.9/bin/mvn" "$@"
fi
