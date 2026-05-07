#!/bin/bash
set -e

BASE="app/src/main"
KT="$BASE/kotlin/com/pricewidget/widget"
RES="$BASE/res"

echo "Creating project structure..."
mkdir -p "$KT" "$RES/layout" "$RES/xml" "$RES/drawable" "$RES/values"

cp PriceWidget.kt    "$KT/"
cp MainActivity.kt   "$KT/"
cp BootReceiver.kt   "$KT/"
cp widget_layout.xml "$RES/layout/"
cp AndroidManifest.xml "$BASE/"
cp gradle.properties .

# Иконки из корня репо в drawable
cp ic_btc.xml    "$RES/drawable/"
cp ic_eth.xml    "$RES/drawable/"
cp ic_sol.xml    "$RES/drawable/"
cp ic_gmt.xml    "$RES/drawable/"
cp ic_xau.xml    "$RES/drawable/"
cp ic_brent.xml  "$RES/drawable/"
cp ic_usdrub.xml "$RES/drawable/"

cat > "$RES/values/strings.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Price Widget</string>
</resources>
XML

cat > "$RES/xml/widget_info.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="300dp"
    android:targetCellWidth="4"
    android:targetCellHeight="6"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_layout"
    android:previewLayout="@layout/widget_layout"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
XML

cat > "$RES/drawable/widget_bg.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#00000000" />
</shape>
XML

echo "Done!"
