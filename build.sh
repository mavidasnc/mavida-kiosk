#!/usr/bin/env bash
# build.sh — incrementa la versione, compila la APK di debug e stampa
# le istruzioni di installazione.
#
# Uso:
#   ./build.sh [major|minor|patch]   (default: patch)
#
# Il versionCode viene SEMPRE incrementato; il versionName segue semver.

set -euo pipefail
cd "$(dirname "$0")"

# grep -P richiede una locale UTF-8/unibyte: forziamo C per portabilita' (Git Bash su Windows).
export LC_ALL=C

BUMP="${1:-patch}"
GRADLE_FILE="app/build.gradle.kts"

# --- Leggi versione corrente dal build file ---
# grep -P non e' affidabile su Git Bash/Windows: estrazione con sed (portabile).
current_code=$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE_FILE" | head -1)
current_name=$(sed -n 's/.*versionName = "\([0-9.]*\)".*/\1/p' "$GRADLE_FILE" | head -1)

new_code=$((current_code + 1))
IFS='.' read -r major minor patch <<< "$current_name"
case "$BUMP" in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
    *) echo "Parametro non valido: $BUMP (usa major|minor|patch)"; exit 1 ;;
esac
new_name="$major.$minor.$patch"

# --- Aggiorna il build file ---
sed -i "s/versionCode = $current_code/versionCode = $new_code/" "$GRADLE_FILE"
sed -i "s/versionName = \"$current_name\"/versionName = \"$new_name\"/" "$GRADLE_FILE"
echo "Versione: $current_name ($current_code) -> $new_name ($new_code)"

# --- Compila la APK di debug ---
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
echo
echo "=============================================="
echo " APK pronta: $APK_PATH"
echo "=============================================="
echo
echo "Installazione via ADB (USB o Wi-Fi):"
echo "   adb install -r $APK_PATH"
echo
echo "Per l'ADB wireless: vedi la sezione 'Installazione' del README.md"
echo
echo "NOTA: aggiorna CHANGELOG.md con le novita' di questa versione."
