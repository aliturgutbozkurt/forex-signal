#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -d "/Users/aliturgutbozkurt/Library/Java/JavaVirtualMachines/jdk-25.0.4.1+1/Contents/Home" ]; then
  export JAVA_HOME="/Users/aliturgutbozkurt/Library/Java/JavaVirtualMachines/jdk-25.0.4.1+1/Contents/Home"
fi
export MAVEN_OPTS="--enable-native-access=ALL-UNNAMED"
exec "$DIR/tools/apache-maven-3.9.9/bin/mvn" "$@"
