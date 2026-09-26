#!/usr/bin/env bash
# Führt die Messung aus. Jede Zeile wird sofort in die CSV geschrieben, darum kann das Skript
# nach einem Abbruch einfach neu starten: fertige Läufe werden übersprungen.
set -u
BENCH_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$BENCH_DIR/../.." && pwd)"
CSV="${1:-/mnt/hdd/Starlos/Analysen/daten/foto-messung-2026-09-26.csv}"
DISHES="${2:-$BENCH_DIR/dishes.json}"
MODEL="${MODEL:-$HOME/.local/share/kalorien-bench/gemma-4-E2B-it.litertlm}"
IMAGES="${IMAGES:-$HOME/.local/share/kalorien-bench/images}"

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21.0.12.1+1}"
mkdir -p "$(dirname "$CSV")"

for attempt in $(seq 1 60); do
    "$REPO/gradlew" -p "$BENCH_DIR" --console=plain --quiet run \
        --args="--model $MODEL --images $IMAGES --dishes $DISHES --csv $CSV"
    status=$?
    if [ $status -eq 0 ]; then
        echo "Messung abgeschlossen nach Versuch $attempt."
        exit 0
    fi
    echo "Lauf abgebrochen (Status $status), Versuch $attempt. Neustart."
done
echo "Zu viele Abbrüche."
exit 1
