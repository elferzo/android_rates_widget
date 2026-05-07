#!/bin/bash
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

# Kotlin sources
for f in PriceWidget.kt MainActivity.kt BootReceiver.kt; do
  if [ -f "$f" ]; then
    cp "$f" "$KT/"
    echo "  copied $f"
  else
    echo "ERROR: $f not found in repo root"; exit 1
  fi
done

# Layout
if [ -f "widget_layout.xml" ]; then
  cp widget_layout.xml "$RES/layout/"
else
  echo "ERROR: widget_layout.xml not found"; exit 1
fi

# widget_info.xml → res/xml/
if [ -f "widget_info.xml" ]; then
  cp widget_info.xml "$RES/xml/"
else
  echo "  widget_info.xml not found — generating..."
  cat > "$RES/xml/widget_info.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="280dp"
    android:targetCellWidth="4"
    android:targetCellHeight="5"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_layout"
    android:previewLayout="@layout/widget_layout"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
EOF
fi

# widget_bg.xml → res/drawable/
if [ -f "widget_bg.xml" ]; then
  cp widget_bg.xml "$RES/drawable/"
else
  echo "  widget_bg.xml not found — generating..."
  cat > "$RES/drawable/widget_bg.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC1A1A2E" />
    <corners android:radius="16dp" />
</shape>
EOF
fi

# AndroidManifest
if [ -f "AndroidManifest.xml" ]; then
  cp AndroidManifest.xml "$BASE/"
else
  echo "ERROR: AndroidManifest.xml not found"; exit 1
fi

# strings.xml
cat > "$RES/values/strings.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Price Widget</string>
</resources>
EOF

echo "✅ Project structure ready!"
