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
import android.text.style.StyleSpan
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.util.concurrent.Executors

class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id); scheduleNext(context)
    }
    override fun onEnabled(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, PriceWidget::class.java))
        for (id in ids) updateWidget(context, mgr, id); scheduleNext(context)
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, PriceWidget::class.java))
            for (id in ids) updateWidget(context, mgr, id); scheduleNext(context)
        }
    }

    companion object {
        const val ACTION_UPDATE = "com.pricewidget.ACTION_UPDATE"

        val ROW_IDS    = intArrayOf(R.id.row1,R.id.row2,R.id.row3,R.id.row4,R.id.row5,R.id.row6,R.id.row7,R.id.row8,R.id.row9)
        val PRICE_IDS  = intArrayOf(R.id.price1,R.id.price2,R.id.price3,R.id.price4,R.id.price5,R.id.price6,R.id.price7,R.id.price8,R.id.price9)
        val CHANGE_IDS = intArrayOf(R.id.change1,R.id.change2,R.id.change3,R.id.change4,R.id.change5,R.id.change6,R.id.change7,R.id.change8,R.id.change9)

        val COLORS = intArrayOf(
            0xFFF7931A.toInt(), 0xFF627EEA.toInt(), 0xFF9945FF.toInt(),
            0xFFC2A633.toInt(), 0xFFBFBBBB.toInt(), 0xFF1DB954.toInt(),
            0xFFFFD700.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DB6AC.toInt()
        )
        val LABELS = arrayOf("BTC","ETH","SOL","DOGE","LTC","GMT","XAU","Brent","USD/RUB")

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

        private fun get(url: String): String {
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 12)")
            c.setRequestProperty("Accept","application/json")
            c.connectTimeout = 10000; c.readTimeout = 10000
            return c.inputStream.bufferedReader().readText()
        }

        fun fetchPrices(): List<AssetPrice> {
            val r = mutableListOf<AssetPrice>()
            r.add(fetchBinance("BTCUSDT",  2))
            r.add(fetchBinance("ETHUSDT",  2))
            r.add(fetchBinance("SOLUSDT",  2))
            r.add(fetchBinance("DOGEUSDT", 4))
            r.add(fetchBinance("LTCUSDT",  2))
            r.add(fetchBinance("GMTUSDT",  4))
            // XAU
            try {
                val arr = JSONArray(get("https://freegoldapi.com/data/latest.json"))
                r.add(AssetPrice("\$${fmt(arr.getJSONObject(arr.length()-1).getDouble("price"),false)}", 0.0))
            } catch(e:Exception) {
                try {
                    val meta = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/GC%3DF?interval=1d&range=2d"))
                        .getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                    val p=meta.getDouble("regularMarketPrice"); val pv=meta.getDouble("chartPreviousClose")
                    r.add(AssetPrice("\$${fmt(p,false)}", if(pv>0)(p-pv)/pv*100 else 0.0))
                } catch(e2:Exception){ r.add(AssetPrice("—",0.0)) }
            }
            // Brent
            try {
                val meta = JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d"))
                    .getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                val p=meta.getDouble("regularMarketPrice"); val pv=meta.getDouble("chartPreviousClose")
                r.add(AssetPrice("\$${fmt(p,false)}", if(pv>0)(p-pv)/pv*100 else 0.0))
            } catch(e:Exception){ r.add(AssetPrice("—",0.0)) }
            // USD/RUB
            try {
                r.add(AssetPrice(fmt(JSONObject(get("https://open.er-api.com/v6/latest/USD")).getJSONObject("rates").getDouble("RUB"),false),0.0))
            } catch(e:Exception) {
                try { r.add(AssetPrice(fmt(JSONObject(get("https://api.frankfurter.app/latest?from=USD&to=RUB")).getJSONObject("rates").getDouble("RUB"),false),0.0)) }
                catch(e2:Exception){ r.add(AssetPrice("—",0.0)) }
            }
            return r
        }

        private fun fetchBinance(symbol: String, decimals: Int=2): AssetPrice {
            return try {
                val j = JSONObject(get("https://api.binance.com/api/v3/ticker/24hr?symbol=$symbol"))
                val p = j.getDouble("lastPrice")
                AssetPrice("\$${fmtD(p,p>=1000,decimals)}", j.getDouble("priceChangePercent"))
            } catch(e:Exception){ AssetPrice("—",0.0) }
        }

        fun buildRows(views: RemoteViews, prices: List<AssetPrice>) {
            val df = DecimalFormat("+0.00;-0.00")
            for (i in prices.indices) {
                // Колонка 1: цветной буллет + тикер
                val label = LABELS[i]
                val ss = SpannableString("● $label")
                ss.setSpan(ForegroundColorSpan(COLORS[i]), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(StyleSpan(Typeface.BOLD), 2, ss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ss.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), 2, ss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(ROW_IDS[i], ss)

                // Колонка 2: цена
                views.setTextViewText(PRICE_IDS[i], prices[i].price)
                views.setTextColor(PRICE_IDS[i], 0xFFFFFFFF.toInt())

                // Колонка 3: %
                val ch = prices[i].change
                if (ch != 0.0) {
                    views.setTextViewText(CHANGE_IDS[i], df.format(ch)+"%")
                    views.setTextColor(CHANGE_IDS[i], if(ch>=0) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
                } else {
                    views.setTextViewText(CHANGE_IDS[i], "")
                }
            }
        }

        fun scheduleNext(context: Context) {
            val i = Intent(context, PriceWidget::class.java).apply { action = ACTION_UPDATE }
            val pi = PendingIntent.getBroadcast(context,0,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = System.currentTimeMillis() + 5*60*1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pi)
            else am.setExact(AlarmManager.RTC_WAKEUP,next,pi)
        }

        private fun fmt(v: Double, large: Boolean) =
            if(large) DecimalFormat("#,##0").format(v) else DecimalFormat("#,##0.00").format(v)
        private fun fmtD(v: Double, large: Boolean, d: Int) =
            if(large) DecimalFormat("#,##0").format(v) else DecimalFormat("#,##0."+"0".repeat(d)).format(v)
    }
}
