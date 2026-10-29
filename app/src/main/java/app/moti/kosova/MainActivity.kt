package app.moti.kosova

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var tvCity: TextView
    private lateinit var tvCond: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvIcon: TextView
    private lateinit var tvHeadline: TextView
    private lateinit var listMsgs: LinearLayout
    private lateinit var tvUpdated: TextView
    private lateinit var cardWeather: View
    private lateinit var cardHourly: View
    private lateinit var rowHours: LinearLayout
    private lateinit var tvCityValue: TextView
    private lateinit var grid: GridLayout
    private lateinit var tvAlpha: TextView
    private lateinit var cardBattery: View
    private var busy = false

    private val intervals = intArrayOf(15, 30, 60, 120, 180, 360)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Glass.setup(this)
        Glass.applyInsets(findViewById(R.id.content))

        tvCity = findViewById(R.id.tv_city)
        tvCond = findViewById(R.id.tv_cond)
        tvTemp = findViewById(R.id.tv_temp)
        tvIcon = findViewById(R.id.tv_icon)
        tvHeadline = findViewById(R.id.tv_headline)
        listMsgs = findViewById(R.id.list_msgs)
        tvUpdated = findViewById(R.id.tv_updated)
        cardWeather = findViewById(R.id.card_weather)
        cardHourly = findViewById(R.id.card_hourly)
        rowHours = findViewById(R.id.row_hours)
        tvCityValue = findViewById(R.id.tv_city_value)
        grid = findViewById(R.id.grid_interval)
        tvAlpha = findViewById(R.id.tv_alpha)
        cardBattery = findViewById(R.id.card_battery)

        findViewById<View>(R.id.row_city).setOnClickListener {
            startActivity(Intent(this, CityActivity::class.java))
        }
        findViewById<View>(R.id.btn_refresh).setOnClickListener { refresh(15_000L, true) }
        findViewById<View>(R.id.btn_add).setOnClickListener { addWidget() }
        findViewById<View>(R.id.btn_battery).setOnClickListener { askBackground() }

        setupIntervals()
        setupTransparency()
    }

    override fun onResume() {
        super.onResume()
        Glass.updateBlur(this)
        render()
        cardBattery.visibility = if (needsBatteryHint()) View.VISIBLE else View.GONE
        Scheduler.ensurePeriodic(this)
        if (Repo.age(this, Prefs.defaultCity(this).id) > 10 * 60_000L) refresh(10 * 60_000L, false)
    }

    // ---------------- weather ----------------

    private fun refresh(maxAge: Long, userAsked: Boolean) {
        if (busy) return
        busy = true
        tvUpdated.setText(R.string.refreshing)
        Refresher.refreshAsync(this, maxAge) { ok ->
            runOnUiThread {
                busy = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                render()
                if (!ok && userAsked) Toast.makeText(this, R.string.fetch_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render() {
        val city = Prefs.defaultCity(this)
        tvCityValue.text = city.name
        tvCity.text = getString(R.string.city_country, city.name)
        cardWeather.background?.mutate()?.alpha = Prefs.glassAlpha(this)

        val w = Repo.load(this, city.id)
        if (w == null) {
            tvTemp.text = "--"
            tvIcon.text = "⏳"
            tvCond.text = ""
            tvHeadline.setText(if (Prefs.lastFailed(this) && !busy) R.string.no_internet else R.string.loading)
            listMsgs.removeAllViews()
            cardHourly.visibility = View.GONE
            if (!busy) tvUpdated.text = ""
            return
        }
        val now = System.currentTimeMillis()
        val c = Insights.effectiveCurrent(w, now)
        tvTemp.text = "${Wx.deg(c.temp)}°"
        tvIcon.text = Wx.emoji(c.code, c.isDay)
        tvCond.text = Present.condition(c)

        val msgs = Insights.pickTop(Insights.build(w, now), 9)
        tvHeadline.text = msgs.firstOrNull()?.text ?: ""
        listMsgs.removeAllViews()
        for (m in msgs.drop(1)) addMsgRow(m)
        if (!busy) tvUpdated.text = getString(R.string.updated_at, Present.time(w.fetchedAt))
        renderHours(w, c, now)
    }

    private fun addMsgRow(m: Msg) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(9), 0, 0)
        val e = TextView(this)
        e.text = m.emoji.ifEmpty { "•" }
        e.textSize = 17f
        e.setTextColor(Color.WHITE)
        e.minWidth = dp(32)
        val t = TextView(this)
        t.text = m.text
        t.textSize = 16f
        t.setTextColor(getColor(R.color.text2))
        t.setLineSpacing(dp(2).toFloat(), 1f)
        row.addView(e, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        listMsgs.addView(row)
    }

    private fun renderHours(w: Weather, c: Current, nowMs: Long) {
        rowHours.removeAllViews()
        val h = w.forecast.hourly
        val ci = Insights.hourIndex(h, nowMs / 1000)
        if (ci < 0 || ci + 1 >= h.size) {
            cardHourly.visibility = View.GONE
            return
        }
        cardHourly.visibility = View.VISIBLE
        val off = w.forecast.utcOffset.toLong()
        for (k in 0 until 13) {
            val i = ci + k
            if (i + 1 >= h.size) break
            val label = if (k == 0) getString(R.string.now)
            else String.format(Locale.ROOT, "%02d", Math.floorMod(h.time[i] + off, 86400L) / 3600L)
            val code = if (k == 0) c.code else h.code.at(i + 1)
            val isDay = if (k == 0) c.isDay else h.isDay.at(i) == 1
            val temp = if (k == 0) c.temp else h.temp.at(i)
            val pop = h.pop.at(i + 1)

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(dp(9), dp(2), dp(9), dp(2))
            col.addView(cell(label, 13f, R.color.text3, 0))
            col.addView(cell(Wx.emoji(code, isDay), 22f, R.color.text, 6))
            col.addView(cell("${Wx.deg(temp)}°", 16f, R.color.text, 6))
            col.addView(cell(if (!pop.isNaN() && pop >= 20) "💧${pop.toInt()}%" else " ", 11f, R.color.accent, 4))
            rowHours.addView(col)
        }
    }

    private fun cell(text: String, size: Float, color: Int, top: Int): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = size
        tv.setTextColor(getColor(color))
        tv.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top)
        tv.layoutParams = lp
        return tv
    }

    // ---------------- settings ----------------

    private fun setupIntervals() {
        val current = Prefs.interval(this)
        for (m in intervals) {
            val tv = TextView(this)
            tv.text = if (m < 60) "$m min" else "${m / 60} orë"
            tv.tag = m
            tv.gravity = Gravity.CENTER
            tv.textSize = 15f
            tv.setTextColor(Color.WHITE)
            tv.setPadding(0, dp(11), 0, dp(11))
            tv.setBackgroundResource(R.drawable.chip_bg)
            tv.isSelected = m == current
            tv.setOnClickListener { selectInterval(m) }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            )
            lp.width = 0
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            grid.addView(tv, lp)
        }
    }

    private fun selectInterval(m: Int) {
        Prefs.setInterval(this, m)
        for (i in 0 until grid.childCount) {
            val v = grid.getChildAt(i)
            v.isSelected = v.tag == m
        }
        Scheduler.ensurePeriodic(this, force = true)
    }

    private fun setupTransparency() {
        val sb = findViewById<SeekBar>(R.id.sb_alpha)
        sb.max = 90
        sb.progress = Prefs.transparency(this)
        tvAlpha.text = "${sb.progress}%"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                tvAlpha.text = "$p%"
                if (fromUser) {
                    Prefs.setTransparency(this@MainActivity, p)
                    cardWeather.background?.mutate()?.alpha = Prefs.glassAlpha(this@MainActivity)
                }
            }

            override fun onStartTrackingTouch(s: SeekBar) {}

            override fun onStopTrackingTouch(s: SeekBar) {
                WidgetUpdater.updateAll(this@MainActivity)
            }
        })
    }

    private fun addWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, WeatherWidget::class.java), null, null)
        } else {
            Toast.makeText(this, R.string.add_widget_manual, Toast.LENGTH_LONG).show()
        }
    }

    private fun needsBatteryHint(): Boolean {
        if (WidgetUpdater.widgetIds(this).isEmpty()) return false
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun askBackground() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (ignored: Exception) {
            }
        }
    }

    private fun dp(v: Int) = Glass.dp(this, v)
}
