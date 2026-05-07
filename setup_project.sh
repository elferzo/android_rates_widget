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
  cp "$f" "$KT/" && echo "  copied $f"
done

# Layout
cp widget_layout.xml "$RES/layout/"

# AndroidManifest
cp AndroidManifest.xml "$BASE/"

# strings.xml
cat > "$RES/values/strings.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Price Widget</string>
</resources>
XML

# widget_info.xml — generated inline, no cp needed
cat > "$RES/xml/widget_info.xml" << 'XML'
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
XML

# widget_bg.xml — generated inline
cat > "$RES/drawable/widget_bg.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC1A1A2E" />
    <corners android:radius="16dp" />
</shape>
XML

echo "Project structure ready!"
