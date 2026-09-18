#!/usr/bin/env bash
# Packages the macOS receiver into CodeBridge.app with an icon and an
# ad-hoc signature. Run from anywhere; output lands in apps/macos/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MACOS_DIR="$ROOT/apps/macos"
APP="$MACOS_DIR/CodeBridge.app"
VERSION="${1:-0.1.0}"

echo "==> Building release binary"
cd "$MACOS_DIR"
swift build -c release

echo "==> Assembling bundle"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp .build/release/CodeBridgeMac "$APP/Contents/MacOS/CodeBridgeMac"

echo "==> Generating app icon"
ICON_SRC="$ROOT/docs/assets/icon.png"
ICONSET="$(mktemp -d)/AppIcon.iconset"
mkdir -p "$ICONSET"
for size in 16 32 64 128 256 512; do
  sips -z $size $size "$ICON_SRC" --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  sips -z $double $double "$ICON_SRC" --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/AppIcon.icns"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>CodeBridge</string>
    <key>CFBundleDisplayName</key>
    <string>CodeBridge</string>
    <key>CFBundleIdentifier</key>
    <string>dev.codebridge.mac</string>
    <key>CFBundleExecutable</key>
    <string>CodeBridgeMac</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>$VERSION</string>
    <key>CFBundleVersion</key>
    <string>$VERSION</string>
    <key>LSMinimumSystemVersion</key>
    <string>14.0</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
</dict>
</plist>
PLIST

echo "==> Ad-hoc signing (unnotarized build)"
codesign --force --sign - "$APP"

echo "==> Done: $APP"
