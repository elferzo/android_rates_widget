package com.pricewidget.widget

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Samsung One UI fix: request exact alarm permission (same pattern as DualClock v13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
        
        // Trigger first data load immediately
        val mgr = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(
            android.content.ComponentName(this, PriceWidget::class.java)
        )
        for (id in ids) {
            PriceWidget.updateWidget(this, mgr, id)
        }
        
        finish()
    }
}
