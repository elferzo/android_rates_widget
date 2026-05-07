package com.pricewidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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

        // row IDs: ticker+price in one TextView, change in second
        val ROW_IDS = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6, R.id.row7)
        val CHANGE_IDS = intArrayOf(R.id.change1, R.id.change2, R.id.change3, R.id.change4, R.id.change5, R.id.change6, R.id.change7)

        // Bullet colors per asset
        val COLORS = intArrayOf(
            0xFFF7931A.toInt(), // BTC orange
            0xFF627EEA.toInt(), // ETH blue
            0xFF9945FF.toInt(), // SOL purple
            0xFF1DB954.toInt(), // GMT green
            0xFFFFD700.toInt(), // XAU gold
            0xFF4FC3F7.toInt(), // Brent light blue
            0xFF4DB6AC.toInt()  // USD/RUB teal
        )

        val LABELS = arrayOf("BTC", "ETH", "SOL", "GMT", "XAU", "Brent", "USD/RUB")

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            return conn.inputStream.bufferedReader().readText()
        }

        fun fetchPrices(): List<AssetPrice> {
            val result = mutableListOf<AssetPrice>()

            // BTC — отдельный запрос
            result.add(fetchCrypto("bitcoin"))
            // ETH
            result.add(fetchCrypto("ethereum"))
            // SOL
            result.add(fetchCrypto("solana"))
            // GMT
            result.add(fetchCrypto("green-metaverse-token"))

            // XAU
            try {
                val text = get("https://api.metals.live/v1/spot/gold")
                val clean = text.trim().let {
                    if (it.startsWith("[")) it.removePrefix("[").removeSuffix("]") else it
                }
                val price = JSONObject(clean).getDouble("gold")
                result.add(AssetPrice("\$${fmt(price, false)}", 0.0))
            } catch (e: Exception) {
                result.add(AssetPrice("—", 0.0))
            }

            // Brent
            try {
                val json = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d"))
                val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                val price = meta.getDouble("regularMarketPrice")
                val prev = meta.getDouble("chartPreviousClose")
                result.add(AssetPrice("\$${fmt(price, false)}", if (prev > 0) (price - prev) / prev * 100 else 0.0))
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

        private fun fetchCrypto(id: String): AssetPrice {
            return try {
                Thread.sleep(300) // avoid rate limit
                val json = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=$id&vs_currencies=usd&include_24hr_change=true"))
                val obj = json.getJSONObject(id)
                val price = obj.getDouble("usd")
                val change = obj.getDouble("usd_24h_change")
                AssetPrice("\$${fmt(price, price >= 1000)}", change)
            } catch (e: Exception) {
                AssetPrice("—", 0.0)
            }
        }

        fun buildRows(views: RemoteViews, prices: List<AssetPrice>) {
            val df = DecimalFormat("+0.00;-0.00")
            for (i in prices.indices) {
                // Build "● LABEL   $price" as SpannableString
                val label = LABELS[i]
                val price = prices[i].price
                val full = "● $label   $price"
                val ss = SpannableString(full)

                // Bullet colored
                ss.setSpan(ForegroundColorSpan(COLORS[i]), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                // Label bold white
                ss.setSpan(StyleSpan(Typeface.BOLD), 2, 2 + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), 2, 2 + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                // Price larger bold white
                val priceStart = full.length - price.length
                ss.setSpan(StyleSpan(Typeface.BOLD), priceStart, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), priceStart, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(RelativeSizeSpan(1.15f), priceStart, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                views.setTextViewText(ROW_IDS[i], ss)

                // Change %
                val ch = prices[i].change
                if (ch != 0.0) {
                    views.setTextViewText(CHANGE_IDS[i], "   " + df.format(ch) + "%")
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
            if (large) DecimalFormat("#,##0").format(v)
            else DecimalFormat("#,##0.00").format(v)
    }
}
