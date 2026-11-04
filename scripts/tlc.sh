#!/usr/bin/env bash
# Tüm TLA+ modellerini TLC ile doğrular. Kullanım: ./scripts/tlc.sh [ModelAdı]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/tools/tla2tools.jar"
[[ -f "$JAR" ]] || { mkdir -p "$ROOT/tools"; curl -sL -o "$JAR" https://github.com/tlaplus/tlaplus/releases/latest/download/tla2tools.jar; }
cd "$ROOT/docs/tla"
MODELS=("${@:-SignalLifecycle AlertDelivery BarIngestion}")
for m in ${MODELS[@]}; do
  echo "=== TLC: $m ==="
  java -XX:+UseParallelGC -cp "$JAR" tlc2.TLC -workers auto -cleanup -config "$m.cfg" "$m.tla" | tail -n 8
done
