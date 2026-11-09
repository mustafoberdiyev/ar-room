#!/usr/bin/env bash
# Скачивает 10 моделей (.glb) и миниатюры из Khronos glTF-Sample-Assets
# в app/src/main/assets. Запускается в CI перед сборкой (или вручную).
set -euo pipefail

BASE="https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS="$ROOT/app/src/main/assets/models"
THUMBS="$ROOT/app/src/main/assets/thumbs"
mkdir -p "$MODELS" "$THUMBS"

NAMES=(SheenChair GlamVelvetSofa ChairDamaskPurplegold Lantern WaterBottle \
       Avocado BoomBox Duck ToyCar DamagedHelmet)

for n in "${NAMES[@]}"; do
  if [ ! -s "$MODELS/$n.glb" ]; then
    echo "model: $n"
    curl -fsSL --retry 3 -o "$MODELS/$n.glb" "$BASE/$n/glTF-Binary/$n.glb"
  fi
  if [ ! -s "$THUMBS/$n.jpg" ]; then
    # миниатюра необязательна: если её нет, приложение покажет букву
    curl -fsSL --retry 3 -o "$THUMBS/$n.jpg" "$BASE/$n/screenshot/screenshot.jpg" \
      || echo "no thumbnail for $n"
  fi
done

echo "Done: $(ls "$MODELS" | wc -l) models"
