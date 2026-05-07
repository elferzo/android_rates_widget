#!/bin/bash
# Setup script for PriceWidget Android project
# Run from the root of the repo after cloning

set -e

BASE="app/src/main"
KT="$BASE/kotlin/com/pricewidget/widget"
RES="$BASE/res"

echo "Creating project structure..."

mkdir -p "$KT"
mkdir -p "$RES/layout"
mkdir -p "$RES/xml"
mkdir -p "$RES/drawable"
mkdir -p "$RES/values"

# Move Kotlin sources
cp PriceWidget.kt   "$KT/"
cp MainActivity.kt  "$KT/"
cp BootReceiver.kt  "$KT/"

# Move resources
cp widget_layout.xml "$RES/layout/"
cp widget_info.xml   "$RES/xml/"
cp widget_bg.xml     "$RES/drawable/"

# Move manifest
cp AndroidManifest.xml "$BASE/"

# Create strings.xml if missing
cat > "$RES/values/strings.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Price Widget</string>
</resources>
EOF

echo "✅ Project structure ready!"
echo ""
echo "Next steps:"
echo "  1. Open in Android Studio OR use GitHub Actions to build APK"
echo "  2. Install APK on phone"
echo "  3. Open 'Price Widget' app → grant exact alarm permission"
echo "  4. Long-press home screen → Widgets → Price Widget (4×5)"
