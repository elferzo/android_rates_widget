package com.pricewidget.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PriceWidget::class.java))
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    PriceWidget.updateWidget(context, mgr, id)
                }
                PriceWidget.scheduleNext(context)
            }
        }
    }
}
