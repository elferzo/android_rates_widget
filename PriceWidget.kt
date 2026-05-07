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

        val PRICE_IDS = intArrayOf(
            R.id.price1, R.id.price2, R.id.price3, R.id.price4,
            R.id.price5, R.id.price6, R.id.price7
        )
        val CHANGE_IDS = intArrayOf(
            R.id.change1, R.id.change2, R.id.change3, R.id.change4,
            R.id.change5, R.id.change6, R.id.change7
        )

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.header_updated, "Загрузка...")
            mgr.updateAppWidget(widgetId, views)

            Executors.newSingleThreadExecutor().execute {
                val prices = fetchPrices()
                Handler(Looper.getMainLooper()).post {
                    val v = RemoteViews(context.packageName, R.layout.widget_layout)
                    v.setTextViewText(R.id.header_updated, "Избранное")
                    buildRows(v, prices)
                    mgr.updateAppWidget(widgetId, v)
                }
            }
        }

        data class AssetPrice(val price: String, val change: Double)

        private fun get(urlStr: String): String {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            return conn.inputStream.bufferedReader().readText()
        }

        fun fetchPrices(): List<AssetPrice> {
            val result = mutableListOf<AssetPrice>()

            // BTC, ETH, SOL, GMT — CoinGecko одним запросом
            try {
                val json = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana,green-metaverse-token&vs_currencies=usd&include_24hr_change=true"))
                val btc = json.getJSONObject("bitcoin")
                val eth = json.getJSONObject("ethereum")
                val sol = json.getJSONObject("solana")
                val gmt = json.getJSONObject("green-metaverse-token")
                result.add(AssetPrice("\$" + fmt(btc.getDouble("usd"), true), btc.getDouble("usd_24h_change")))
                result.add(AssetPrice("\$" + fmt(eth.getDouble("usd"), true), eth.getDouble("usd_24h_change")))
                result.add(AssetPrice("\$" + fmt(sol.getDouble("usd"), false), sol.getDouble("usd_24h_change")))
                result.add(AssetPrice("\$" + fmt(gmt.getDouble("usd"), false), gmt.getDouble("usd_24h_change")))
            } catch (e: Exception) {
                result.add(AssetPrice("—", 0.0))
                result.add(AssetPrice("—", 0.0))
                result.add(AssetPrice("—", 0.0))
                result.add(AssetPrice("—", 0.0))
            }

            // XAU
            try {
                val text = get("https://api.metals.live/v1/spot/gold")
                val price = JSONObject(text.trim().removePrefix("[").removeSuffix("]")).getDouble("gold")
                result.add(AssetPrice("\$" + fmt(price, false), 0.0))
            } catch (e: Exception) {
                result.add(AssetPrice("—", 0.0))
            }

            // Brent
            try {
                val json = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d"))
                val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                val price = meta.getDouble("regularMarketPrice")
                val prev = meta.getDouble("chartPreviousClose")
                result.add(AssetPrice("\$" + fmt(price, false), if (prev > 0) (price - prev) / prev * 100 else 0.0))
            } catch (e: Exception) {
                result.add(AssetPrice("—", 0.0))
            }

            // USD/RUB
            try {
                val json = JSONObject(get("https://open.er-api.com/v6/latest/USD"))
                val rub = json.getJSONObject("rates").getDouble("RUB")
                result.add(AssetPrice(fmt(rub, false), 0.0))
            } catch (e: Exception) {
                try {
                    val json = JSONObject(get("https://api.frankfurter.app/latest?from=USD&to=RUB"))
                    val rub = json.getJSONObject("rates").getDouble("RUB")
                    result.add(AssetPrice(fmt(rub, false), 0.0))
                } catch (e2: Exception) {
                    result.add(AssetPrice("—", 0.0))
                }
            }

            return result
        }

        fun buildRows(views: RemoteViews, prices: List<AssetPrice>) {
            val df = DecimalFormat("+0.00;-0.00")
            for (i in prices.indices) {
                views.setTextViewText(PRICE_IDS[i], prices[i].price)
                views.setTextColor(PRICE_IDS[i], 0xFFFFFFFF.toInt())
                val ch = prices[i].change
                if (ch != 0.0) {
                    views.setTextViewText(CHANGE_IDS[i], df.format(ch) + "%")
                    views.setTextColor(CHANGE_IDS[i], if (ch >= 0) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
                } else {
                    views.setTextViewText(CHANGE_IDS[i], "")
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
