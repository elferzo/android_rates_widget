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
import org.json.JSONArray
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

        val ROW_IDS    = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6, R.id.row7, R.id.row8, R.id.row9)
        val CHANGE_IDS = intArrayOf(R.id.change1, R.id.change2, R.id.change3, R.id.change4, R.id.change5, R.id.change6, R.id.change7, R.id.change8, R.id.change9)

        val COLORS = intArrayOf(
            0xFFF7931A.toInt(),  // BTC
            0xFF627EEA.toInt(),  // ETH
            0xFF9945FF.toInt(),  // SOL
            0xFFC2A633.toInt(),  // DOGE
            0xFFBFBBBB.toInt(),  // LTC
            0xFF1DB954.toInt(),  // GMT
            0xFFFFD700.toInt(),  // XAU
            0xFF4FC3F7.toInt(),  // Brent
            0xFF4DB6AC.toInt()   // USD/RUB
        )
        val LABELS = arrayOf("BTC", "ETH", "SOL", "DOGE", "LTC", "GMT", "XAU", "Brent", "USD/RUB")

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            Executors.newSingleThreadExecutor().execute {
                val prices = fetchPrices()
                Handler(Looper.getMainLooper()).post {
                    val v = RemoteViews(context.packageName, R.layout.widget_layout)
                    v.setTextViewText(R.id.header_updated, "")
                    buildRows(v, prices)
                    mgr.updateAppWidget(widgetId, v)
                }
            }
        }

        data class AssetPrice(val price: String, val change: Double)

        private fun get(urlStr: String): String {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            return conn.inputStream.bufferedReader().readText()
        }

        fun fetchPrices(): List<AssetPrice> {
            val result = mutableListOf<AssetPrice>()
            result.add(fetchBinance("BTCUSDT", 2))
            result.add(fetchBinance("ETHUSDT", 2))
            result.add(fetchBinance("SOLUSDT", 2))
            result.add(fetchBinance("DOGEUSDT", 4))
            result.add(fetchBinance("LTCUSDT", 2))
            result.add(fetchBinance("GMTUSDT", 4))

            // XAU — freegoldapi.com, fallback Yahoo GC=F
            try {
                val arr = JSONArray(get("https://freegoldapi.com/data/latest.json"))
                val price = arr.getJSONObject(arr.length() - 1).getDouble("price")
                result.add(AssetPrice("\$${fmt(price, false)}", 0.0))
            } catch (e: Exception) {
                try {
                    val meta = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/GC%3DF?interval=1d&range=2d"))
                        .getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                    val price = meta.getDouble("regularMarketPrice")
                    val prev  = meta.getDouble("chartPreviousClose")
                    result.add(AssetPrice("\$${fmt(price, false)}", if (prev > 0) (price - prev) / prev * 100 else 0.0))
                } catch (e2: Exception) { result.add(AssetPrice("—", 0.0)) }
            }

            // Brent — Yahoo BZ=F
            try {
                val meta = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d"))
                    .getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                val price = meta.getDouble("regularMarketPrice")
                val prev  = meta.getDouble("chartPreviousClose")
                result.add(AssetPrice("\$${fmt(price, false)}", if (prev > 0) (price - prev) / prev * 100 else 0.0))
            } catch (e: Exception) { result.add(AssetPrice("—", 0.0)) }

            // USD/RUB
            try {
                val rub = JSONObject(get("https://open.er-api.com/v6/latest/USD")).getJSONObject("rates").getDouble("RUB")
                result.add(AssetPrice(fmt(rub, false), 0.0))
            } catch (e: Exception) {
                try {
                    val rub = JSONObject(get("https://api.frankfurter.app/latest?from=USD&to=RUB")).getJSONObject("rates").getDouble("RUB")
                    result.add(AssetPrice(fmt(rub, false), 0.0))
                } catch (e2: Exception) { result.add(AssetPrice("—", 0.0)) }
            }

            return result
        }

        private fun fetchBinance(symbol: String, decimals: Int = 2): AssetPrice {
            return try {
                val json   = JSONObject(get("https://api.binance.com/api/v3/ticker/24hr?symbol=$symbol"))
                val price  = json.getDouble("lastPrice")
                val change = json.getDouble("priceChangePercent")
                AssetPrice("\$${fmtD(price, price >= 1000, decimals)}", change)
            } catch (e: Exception) { AssetPrice("—", 0.0) }
        }

        fun buildRows(views: RemoteViews, prices: List<AssetPrice>) {
            val df = DecimalFormat("+0.00;-0.00")
            for (i in prices.indices) {
                val label = LABELS[i]
                val price = prices[i].price
                // "● LABEL        $price" — пробелы создают отступ цены
                val gap   = "        "
                val full  = "● $label$gap$price"
                val ss    = SpannableString(full)

                // цветной буллет
                ss.setSpan(ForegroundColorSpan(COLORS[i]), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                // label белый bold
                ss.setSpan(StyleSpan(Typeface.BOLD), 2, 2 + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), 2, 2 + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                // цена чуть крупнее
                val ps = full.length - price.length
                ss.setSpan(StyleSpan(Typeface.BOLD), ps, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), ps, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(RelativeSizeSpan(1.1f), ps, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                views.setTextViewText(ROW_IDS[i], ss)

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

        private fun fmt(v: Double, large: Boolean) =
            if (large) DecimalFormat("#,##0").format(v) else DecimalFormat("#,##0.00").format(v)

        private fun fmtD(v: Double, large: Boolean, d: Int): String {
            if (large) return DecimalFormat("#,##0").format(v)
            return DecimalFormat("#,##0." + "0".repeat(d)).format(v)
        }
    }
}
