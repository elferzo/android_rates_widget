package com.pricewidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.DecimalFormat
import java.util.concurrent.Executors

class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
        scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
        // Вызывается при добавлении первого виджета
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, PriceWidget::class.java))
        for (id in ids) updateWidget(context, mgr, id)
        scheduleNext(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, PriceWidget::class.java))
            for (id in ids) updateWidget(context, mgr, id)
            scheduleNext(context)
        }
    }

    companion object {
        const val ACTION_UPDATE = "com.pricewidget.ACTION_UPDATE"

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            // Показываем "загрузка" сразу
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.header_updated, "Загрузка...")
            mgr.updateAppWidget(widgetId, views)

            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                val prices = fetchPrices()
                Handler(Looper.getMainLooper()).post {
                    val v = RemoteViews(context.packageName, R.layout.widget_layout)
                    v.setTextViewText(R.id.header_updated, "Обновлено только что")
                    buildRows(v, prices)
                    mgr.updateAppWidget(widgetId, v)
                }
            }
        }

        data class AssetPrice(val symbol: String, val price: String, val change: Double)

        fun fetchPrices(): List<AssetPrice> {
            val result = mutableListOf<AssetPrice>()

            // BTC, ETH, SOL — CoinGecko
            try {
                val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana&vs_currencies=usd&include_24hr_change=true"
                val json = JSONObject(URL(url).readText())
                result.add(AssetPrice("BTC", fmt(json.getJSONObject("bitcoin").getDouble("usd"), true), json.getJSONObject("bitcoin").getDouble("usd_24h_change")))
                result.add(AssetPrice("ETH", fmt(json.getJSONObject("ethereum").getDouble("usd"), true), json.getJSONObject("ethereum").getDouble("usd_24h_change")))
                result.add(AssetPrice("SOL", fmt(json.getJSONObject("solana").getDouble("usd"), true), json.getJSONObject("solana").getDouble("usd_24h_change")))
            } catch (e: Exception) {
                result.add(AssetPrice("BTC", "err", 0.0))
                result.add(AssetPrice("ETH", "err", 0.0))
                result.add(AssetPrice("SOL", "err", 0.0))
            }

            // XAU — frankfurter
            try {
                val json = JSONObject(URL("https://api.frankfurter.app/latest?from=XAU&to=USD").readText())
                result.add(AssetPrice("XAU", "$" + fmt(json.getJSONObject("rates").getDouble("USD"), false), 0.0))
            } catch (e: Exception) {
                result.add(AssetPrice("XAU", "err", 0.0))
            }

            // Brent — Yahoo Finance
            try {
                val json = JSONObject(URL("https://query1.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d").readText())
                val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                val price = meta.getDouble("regularMarketPrice")
                val prev = meta.getDouble("chartPreviousClose")
                result.add(AssetPrice("Brent", "$" + fmt(price, false), (price - prev) / prev * 100))
            } catch (e: Exception) {
                result.add(AssetPrice("Brent", "err", 0.0))
            }

            // USD/RUB — frankfurter
            try {
                val json = JSONObject(URL("https://api.frankfurter.app/latest?from=USD&to=RUB").readText())
                result.add(AssetPrice("USD/RUB", fmt(json.getJSONObject("rates").getDouble("RUB"), false), 0.0))
            } catch (e: Exception) {
                result.add(AssetPrice("USD/RUB", "err", 0.0))
            }

            return result
        }

        fun buildRows(views: RemoteViews, prices: List<AssetPrice>) {
            val rowIds = listOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6)
            val priceIds = listOf(R.id.price1, R.id.price2, R.id.price3, R.id.price4, R.id.price5, R.id.price6)
            val changeIds = listOf(R.id.change1, R.id.change2, R.id.change3, R.id.change4, R.id.change5, R.id.change6)

            for (i in prices.indices) {
                val a = prices[i]
                views.setTextViewText(rowIds[i], a.symbol)
                views.setTextColor(rowIds[i], 0xFFB0B0B0.toInt())
                views.setTextViewText(priceIds[i], a.price)
                views.setTextColor(priceIds[i], 0xFFFFFFFF.toInt())
                if (a.change != 0.0) {
                    views.setTextViewText(changeIds[i], DecimalFormat("+0.00;-0.00").format(a.change) + "%")
                    views.setTextColor(changeIds[i], if (a.change >= 0) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
                } else {
                    views.setTextViewText(changeIds[i], "")
                }
            }
        }

        fun scheduleNext(context: Context) {
            val intent = Intent(context, PriceWidget::class.java).apply { action = ACTION_UPDATE }
            val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = System.currentTimeMillis() + 5 * 60 * 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else
                am.setExact(AlarmManager.RTC_WAKEUP, next, pi)
        }

        private fun fmt(price: Double, crypto: Boolean): String {
            return when {
                crypto && price >= 1000 -> DecimalFormat("#,##0").format(price)
                else -> DecimalFormat("#,##0.00").format(price)
            }
        }
    }
}
