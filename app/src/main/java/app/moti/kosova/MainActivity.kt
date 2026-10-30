package app.moti.kosova

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import app.moti.kosova.Fonts.Role
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
    private lateinit var tabToday: TextView
    private lateinit var tabTomorrow: TextView
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
        tabToday = findViewById(R.id.tab_today)
        tabTomorrow = findViewById(R.id.tab_tomorrow)
        cardBattery = findViewById(R.id.card_battery)

        findViewById<View>(R.id.row_city).setOnClickListener {
            startActivity(Intent(this, CityActivity::class.java))
        }
        findViewById<View>(R.id.btn_refresh).setOnClickListener { refresh(15_000L, true) }
        findViewById<View>(R.id.btn_add).setOnClickListener { addWidget() }
        findViewById<View>(R.id.btn_battery).setOnClickListener { askBackground() }
        tabToday.setOnClickListener { Prefs.setTab(this, "app", false); render() }
        tabTomorrow.setOnClickListener { Prefs.setTab(this, "app", true); render() }

        setupIntervals()
        setupLook()
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
        applyLookToCard()

        val w = Repo.load(this, city.id)
        val tomorrow = Prefs.showTomorrow(this, "app")
        styleTab(tabToday, !tomorrow)
        styleTab(tabTomorrow, tomorrow)
        val m = Present.model(this, city, w, System.currentTimeMillis(), false, tomorrow)
        val font = Prefs.font(this)
        val emojis = Prefs.emojis(this)

        tvCity.text = m.title
        tvTemp.text = if (m.hasData) "${m.temp}${if (m.tomorrow) m.unit else "°"}" else "--"
        tvIcon.text = m.icon
        tvIcon.visibility = if (Prefs.showIcon(this)) View.VISIBLE else View.GONE
        tvCond.text = m.condition
        tvHeadline.text = if (m.hasData) (m.all.firstOrNull()?.text ?: m.headline) else m.headline
        listMsgs.removeAllViews()
        for (msg in Insights.pickTop(m.all, 10).drop(1)) addMsgRow(msg, font, emojis)
        if (!busy) tvUpdated.text = if (w != null) getString(R.string.updated_at, Present.time(w.fetchedAt)) else ""
        renderHours(w, m.tomorrow)
    }

    private fun scale() = Prefs.textScale(this) / 115f

    private fun applyLookToCard() {
        val font = Prefs.font(this)
        val s = scale()
        tvCity.typeface = Fonts.typeface(font, Role.STRONG)
        tvCity.textSize = 18f * s
        tvCond.typeface = Fonts.typeface(font, Role.BODY)
        tvCond.textSize = 14f * s
        tvTemp.typeface = Fonts.typeface(font, Role.DISPLAY)
        tvTemp.textSize = 50f * s
        tvIcon.textSize = 40f * s
        tvHeadline.typeface = Fonts.typeface(font, Role.HEAD)
        tvHeadline.textSize = 26f * s
        cardWeather.setBackgroundResource(WidgetUpdater.glassRes(Prefs.corner(this)))
        cardWeather.background?.mutate()?.alpha = if (Prefs.background(this)) Prefs.glassAlpha(this) else 0
    }

    private fun styleTab(tv: TextView, on: Boolean) {
        tv.setBackgroundResource(if (on) R.drawable.tab_on else R.drawable.tab_off)
        tv.setTextColor(getColor(if (on) R.color.tab_ink else R.color.text))
    }

    private fun addMsgRow(m: Msg, font: Int, emojis: Boolean) {
        val s = scale()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(9), 0, 0)
        if (emojis) {
            val e = TextView(this)
            e.text = m.emoji.ifEmpty { "•" }
            e.textSize = 17f * s
            e.setTextColor(Color.WHITE)
            e.minWidth = dp(34)
            row.addView(e, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val t = TextView(this)
        t.text = if (emojis) m.text else "•  ${m.text}"
        t.textSize = 16f * s
        t.typeface = Fonts.typeface(font, Role.BODY)
        t.setTextColor(getColor(R.color.text2))
        t.setLineSpacing(dp(2).toFloat(), 1f)
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        listMsgs.addView(row)
    }

    private fun renderHours(w: Weather?, tomorrow: Boolean) {
        rowHours.removeAllViews()
        if (w == null) {
            cardHourly.visibility = View.GONE
            return
        }
        val f = w.forecast
        val h = f.hourly
        val off = f.utcOffset.toLong()
        val nowMs = System.currentTimeMillis()
        val now = nowMs / 1000
        val today = Math.floorDiv(now + off, 86400L)
        val c = Insights.effectiveCurrent(w, nowMs)
        val ci = Insights.hourIndex(h, now)
        val idx = ArrayList<Int>()
        if (tomorrow) {
            for (i in 0 until h.size - 1) {
                val hr = Math.floorMod(h.time[i] + off, 86400L) / 3600L
                if (Math.floorDiv(h.time[i] + off, 86400L) == today + 1 && hr >= 6) idx += i
            }
        } else if (ci >= 0) {
            for (k in 0 until 13) if (ci + k + 1 < h.size) idx += ci + k
        }
        if (idx.isEmpty()) {
            cardHourly.visibility = View.GONE
            return
        }
        cardHourly.visibility = View.VISIBLE
        val icons = Prefs.showIcon(this)
        for ((n, i) in idx.withIndex()) {
            val first = !tomorrow && n == 0
            val label = if (first) getString(R.string.now)
            else String.format(Locale.ROOT, "%02d", Math.floorMod(h.time[i] + off, 86400L) / 3600L)
            val code = if (first) c.code else h.code.at(i + 1)
            val isDay = if (first) c.isDay else h.isDay.at(i) == 1
            val temp = if (first) c.temp else h.temp.at(i)
            val pop = h.pop.at(i + 1)
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(dp(9), dp(2), dp(9), dp(2))
            col.addView(cell(label, 13f, R.color.text3, 0))
            if (icons) col.addView(cell(Wx.emoji(code, isDay), 22f, R.color.text, 6))
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

    /** A row of selectable chips in a grid. */
    private fun chips(gridId: Int, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val grid = findViewById<GridLayout>(gridId)
        grid.removeAllViews()
        labels.forEachIndexed { i, label ->
            val tv = TextView(this)
            tv.text = label
            tv.tag = i
            tv.gravity = Gravity.CENTER
            tv.textSize = 15f
            tv.setTextColor(Color.WHITE)
            tv.setPadding(dp(4), dp(11), dp(4), dp(11))
            tv.setBackgroundResource(R.drawable.chip_bg)
            tv.isSelected = i == selected
            tv.setOnClickListener {
                for (k in 0 until grid.childCount) grid.getChildAt(k).isSelected = k == i
                onPick(i)
            }
            val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f))
            lp.width = 0
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            grid.addView(tv, lp)
        }
    }

    private fun setupIntervals() {
        val labels = intervals.map { if (it < 60) "$it min" else "${it / 60} orë" }
        chips(R.id.grid_interval, labels, intervals.indexOf(Prefs.interval(this))) { i ->
            Prefs.setInterval(this, intervals[i])
            Scheduler.ensurePeriodic(this, force = true)
        }
    }

    private fun lookChanged() {
        render()
        WidgetUpdater.updateAll(this)
    }

    private fun setupLook() {
        // Text size 80–160 %
        val sbSize = findViewById<SeekBar>(R.id.sb_size)
        val tvSize = findViewById<TextView>(R.id.tv_size)
        sbSize.max = 80
        sbSize.progress = Prefs.textScale(this) - 80
        tvSize.text = "${Prefs.textScale(this)}%"
        sbSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                tvSize.text = "${p + 80}%"
                if (fromUser) { Prefs.putInt(this@MainActivity, "scale", p + 80); render() }
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { WidgetUpdater.updateAll(this@MainActivity) }
        })

        // Font style
        chips(R.id.grid_font, Fonts.names, Prefs.font(this)) { i ->
            Prefs.putInt(this, "font", i)
            lookChanged()
        }
        val grid = findViewById<GridLayout>(R.id.grid_font)
        for (k in 0 until grid.childCount) (grid.getChildAt(k) as TextView).typeface = Fonts.typeface(k, Role.BODY)

        // Background: glass or none
        val glassOpts = findViewById<View>(R.id.glass_opts)
        glassOpts.visibility = if (Prefs.background(this)) View.VISIBLE else View.GONE
        chips(R.id.grid_bg, listOf("Xham", "Pa sfond"), if (Prefs.background(this)) 0 else 1) { i ->
            Prefs.putBool(this, "bg", i == 0)
            glassOpts.visibility = if (i == 0) View.VISIBLE else View.GONE
            lookChanged()
        }

        // Corners
        chips(R.id.grid_corner, listOf("Të vogla", "Mesatare", "Të mëdha"), Prefs.corner(this)) { i ->
            Prefs.putInt(this, "corner", i)
            lookChanged()
        }

        // Transparency
        val sb = findViewById<SeekBar>(R.id.sb_alpha)
        val tvAlpha = findViewById<TextView>(R.id.tv_alpha)
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
            override fun onStopTrackingTouch(s: SeekBar) { WidgetUpdater.updateAll(this@MainActivity) }
        })

        // Switches
        val box = findViewById<LinearLayout>(R.id.switches)
        addSwitch(box, R.string.show_emojis, Prefs.emojis(this)) { Prefs.putBool(this, "emoji", it); lookChanged() }
        addSwitch(box, R.string.show_icon, Prefs.showIcon(this)) { Prefs.putBool(this, "icon", it); lookChanged() }
        addSwitch(box, R.string.auto_tomorrow, Prefs.autoTomorrow(this)) { Prefs.putBool(this, "autoTomorrow", it); lookChanged() }
    }

    private fun addSwitch(box: LinearLayout, label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(this)
        sw.setText(label)
        sw.textSize = 15f
        sw.setTextColor(getColor(R.color.text2))
        sw.isChecked = checked
        sw.setPadding(0, dp(10), 0, dp(10))
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(getColor(R.color.accent), Color.parseColor("#CCFFFFFF")))
        sw.trackTintList = ColorStateList(states, intArrayOf(Color.parseColor("#809CC4FF"), Color.parseColor("#40FFFFFF")))
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        box.addView(sw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (ignored: Exception) {
            }
        }
    }

    private fun dp(v: Int) = Glass.dp(this, v)
}
