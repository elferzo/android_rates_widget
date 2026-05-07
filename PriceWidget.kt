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
import java.net.URL
import java.text.DecimalFormat
import java.util.concurrent.Executors

class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
        scheduleNext(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, PriceWidget::class.java)
            )
            for (id in ids) {
                updateWidget(context, mgr, id)
            }
            scheduleNext(context)
        }
    }

    companion object {
        const val ACTION_UPDATE = "com.pricewidget.ACTION_UPDATE"

        // Colors
        const val COLOR_GREEN = 0xFF4CAF50.toInt()
        const val COLOR_RED = 0xFFE53935.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val COLOR_GRAY = 0xFFB0B0B0.toInt()

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    val prices = fetchPrices()
                    Handler(Looper.getMainLooper()).post {
                        val views = RemoteViews(context.packageName, R.layout.widget_layout)
                        buildRows(context, views, prices)
                        mgr.updateAppWidget(widgetId, views)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        data class AssetPrice(
            val symbol: String,
            val price: String,
            val change: Double
        )

        fun fetchPrices(): List<AssetPrice> {
            val result = mutableListOf<AssetPrice>()

            // CoinGecko — BTC, ETH, SOL
            try {
                val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana&vs_currencies=usd&include_24hr_change=true"
                val json = JSONObject(URL(url).readText())

                val btcPrice = json.getJSONObject("bitcoin").getDouble("usd")
                val btcChange = json.getJSONObject("bitcoin").getDouble("usd_24h_change")
                result.add(AssetPrice("BTC", formatCrypto(btcPrice), btcChange))

                val ethPrice = json.getJSONObject("ethereum").getDouble("usd")
                val ethChange = json.getJSONObject("ethereum").getDouble("usd_24h_change")
                result.add(AssetPrice("ETH", formatCrypto(ethPrice), ethChange))

                val solPrice = json.getJSONObject("solana").getDouble("usd")
                val solChange = json.getJSONObject("solana").getDouble("usd_24h_change")
                result.add(AssetPrice("SOL", formatCrypto(solPrice), solChange))
            } catch (e: Exception) {
                result.add(AssetPrice("BTC", "—", 0.0))
                result.add(AssetPrice("ETH", "—", 0.0))
                result.add(AssetPrice("SOL", "—", 0.0))
            }

            // Gold XAU/USD via frankfurter (XAU not always available, fallback to metals-api free)
            try {
                val url = "https://api.frankfurter.app/latest?from=XAU&to=USD"
                val json = JSONObject(URL(url).readText())
                val xauUsd = json.getJSONObject("rates").getDouble("USD")
                result.add(AssetPrice("XAU", "$${formatPrice(xauUsd)}", 0.0))
            } catch (e: Exception) {
                // Fallback: use open metals API
                try {
                    val url = "https://api.metals.live/v1/spot/gold"
                    val json = JSONObject(URL(url).readText())
                    val price = json.getDouble("price")
                    result.add(AssetPrice("XAU", "$${formatPrice(price)}", 0.0))
                } catch (e2: Exception) {
                    result.add(AssetPrice("XAU", "—", 0.0))
                }
            }

            // Brent via open API (EIA or oilpriceapi fallback)
            try {
                val url = "https://api.api-ninjas.com/v1/commodityprice?name=crude_oil_wti"
                // Note: api-ninjas requires free API key, using alternative below
                throw Exception("use alternative")
            } catch (e: Exception) {
                try {
                    // Use CoinGecko commodity workaround via exchangerate
                    val url = "https://query1.finance.yahoo.com/v8/finance/chart/BZ%3DF?interval=1d&range=2d"
                    val json = JSONObject(URL(url).readText())
                    val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                    val price = meta.getDouble("regularMarketPrice")
                    val prevClose = meta.getDouble("chartPreviousClose")
                    val change = ((price - prevClose) / prevClose) * 100
                    result.add(AssetPrice("Brent", "$${formatPrice(price)}", change))
                } catch (e2: Exception) {
                    result.add(AssetPrice("Brent", "—", 0.0))
                }
            }

            // USD/RUB via frankfurter
            try {
                val url = "https://api.frankfurter.app/latest?from=USD&to=RUB"
                val json = JSONObject(URL(url).readText())
                val usdRub = json.getJSONObject("rates").getDouble("RUB")
                result.add(AssetPrice("USD/RUB", formatPrice(usdRub), 0.0))
            } catch (e: Exception) {
                result.add(AssetPrice("USD/RUB", "—", 0.0))
            }

            return result
        }

        fun buildRows(context: Context, views: RemoteViews, prices: List<AssetPrice>) {
            val rowIds = listOf(
                R.id.row1, R.id.row2, R.id.row3,
                R.id.row4, R.id.row5, R.id.row6
            )
            val changeIds = listOf(
                R.id.change1, R.id.change2, R.id.change3,
                R.id.change4, R.id.change5, R.id.change6
            )
            val priceIds = listOf(
                R.id.price1, R.id.price2, R.id.price3,
                R.id.price4, R.id.price5, R.id.price6
            )

            for (i in prices.indices) {
                val asset = prices[i]
                val df = DecimalFormat("+0.00;-0.00")

                // Symbol label
                views.setTextViewText(rowIds[i], asset.symbol)
                views.setTextColor(rowIds[i], COLOR_GRAY)

                // Price
                views.setTextViewText(priceIds[i], asset.price)
                views.setTextColor(priceIds[i], COLOR_WHITE)

                // Change %
                if (asset.change != 0.0) {
                    val changeStr = df.format(asset.change) + "%"
                    views.setTextViewText(changeIds[i], changeStr)
                    views.setTextColor(
                        changeIds[i],
                        if (asset.change >= 0) COLOR_GREEN else COLOR_RED
                    )
                } else {
                    views.setTextViewText(changeIds[i], "")
                }
            }
        }

        fun scheduleNext(context: Context) {
            val intent = Intent(context, PriceWidget::class.java).apply {
                action = ACTION_UPDATE
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextTime = System.currentTimeMillis() + 5 * 60 * 1000L // every 5 min

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, nextTime, pi)
            }
        }

        private fun formatCrypto(price: Double): String {
            return when {
                price >= 10000 -> "$${DecimalFormat("#,##0").format(price)}"
                price >= 100 -> "$${DecimalFormat("#,##0.00").format(price)}"
                else -> "$${DecimalFormat("#,##0.00").format(price)}"
            }
        }

        private fun formatPrice(price: Double): String {
            return DecimalFormat("#,##0.00").format(price)
        }
    }
}
