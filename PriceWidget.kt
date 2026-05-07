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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.util.concurrent.Executors

class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
        scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
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
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.header_updated, "Загрузка...")
            mgr.updateAppWidget(widgetId, views)

            Executors.newSingleThreadExecutor().execute {
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

        private fun get(urlStr: String): String {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            return conn.inputStream.bufferedReader().readText()
        }

        fun fetchPrices(): List<AssetPrice> {
            val result = mutableListOf<AssetPrice>()

            // BTC, ETH, SOL — CoinGecko
            try {
                val json = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana&vs_currencies=usd&include_24hr_change=true"))
                result.add(AssetPrice("BTC", fmt(json.getJSONObject("bitcoin").getDouble("usd"), true), json.getJSONObject("bitcoin").getDouble("usd_24h_change")))
                result.add(AssetPrice("ETH", fmt(json.getJSONObject("ethereum").getDouble("usd"), true), json.getJSONObject("ethereum").getDouble("usd_24h_change")))
                result.add(AssetPrice("SOL", fmt(json.getJSONObject("solana").getDouble("usd"), false), json.getJSONObject("solana").getDouble("usd_24h_change")))
            } catch (e: Exception) {
                result.add(AssetPrice("BTC", "—", 0.0))
                result.add(AssetPrice("ETH", "—", 0.0))
                result.add(AssetPrice("SOL", "—", 0.0))
            }

            // XAU — gold-api.com (бесплатный, без ключа)
            try {
                val json = JSONObject(get("https://www.goldapi.io/api/XAU/USD"))
                val price = json.getDouble("price")
                val change = json.optDouble("ch", 0.0)
                val changePct = json.optDouble("chp", 0.0)
                result.add(AssetPrice("XAU", "$" + fmt(price, false), changePct))
            } catch (e: Exception) {
                // Fallback: metals-live
                try {
                    val text = get("https://api.metals.live/v1/spot/gold")
                    // returns [{"gold": 3300.5}]
                    val price = JSONObject(text.trim().removePrefix("[").removeSuffix("]")).getDouble("gold")
                    result.add(AssetPrice("XAU", "$" + fmt(price, false), 0.0))
                } catch (e2: Exception) {
                    result.add(AssetPrice("XAU", "—", 0.0))
                }
            }

            // Brent — Yahoo Finance
            try {
                val json = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d"))
                val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                val price = meta.getDouble("regularMarketPrice")
                val prev = meta.getDouble("chartPreviousClose")
                val chg = if (prev > 0) (price - prev) / prev * 100 else 0.0
                result.add(AssetPrice("Brent", "$" + fmt(price, false), chg))
            } catch (e: Exception) {
                // Fallback: Yahoo v7
                try {
                    val json = JSONObject(get("https://query2.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d"))
                    val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                    val price = meta.getDouble("regularMarketPrice")
                    val prev = meta.getDouble("chartPreviousClose")
                    result.add(AssetPrice("Brent", "$" + fmt(price, false), (price - prev) / prev * 100))
                } catch (e2: Exception) {
                    result.add(AssetPrice("Brent", "—", 0.0))
                }
            }

            // USD/RUB — exchangerate-api (бесплатный, без ключа)
            try {
                val json = JSONObject(get("https://open.er-api.com/v6/latest/USD"))
                val rub = json.getJSONObject("rates").getDouble("RUB")
                result.add(AssetPrice("USD/RUB", fmt(rub, false), 0.0))
            } catch (e: Exception) {
                // Fallback: frankfurter
                try {
                    val json = JSONObject(get("https://api.frankfurter.app/latest?from=USD&to=RUB"))
                    val rub = json.getJSONObject("rates").getDouble("RUB")
                    result.add(AssetPrice("USD/RUB", fmt(rub, false), 0.0))
                } catch (e2: Exception) {
                    result.add(AssetPrice("USD/RUB", "—", 0.0))
                }
            }

            return result
        }

        fun buildRows(views: RemoteViews, prices: List<AssetPrice>) {
            val priceIds = listOf(R.id.price1, R.id.price2, R.id.price3, R.id.price4, R.id.price5, R.id.price6)
            val changeIds = listOf(R.id.change1, R.id.change2, R.id.change3, R.id.change4, R.id.change5, R.id.change6)
            val df = DecimalFormat("+0.00;-0.00")

            for (i in prices.indices) {
                val a = prices[i]
                views.setTextViewText(priceIds[i], a.price)
                views.setTextColor(priceIds[i], 0xFFFFFFFF.toInt())
                if (a.change != 0.0) {
                    views.setTextViewText(changeIds[i], df.format(a.change) + "%")
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

        private fun fmt(v: Double, large: Boolean): String =
            if (large && v >= 1000) DecimalFormat("#,##0").format(v)
            else DecimalFormat("#,##0.00").format(v)
    }
}
