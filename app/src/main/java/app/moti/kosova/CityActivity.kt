package app.moti.kosova

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView

/** Pick a Kosovo city — for the app, or for one specific widget (also used as the widget's setup screen). */
class CityActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        Glass.setup(this)
        setContentView(R.layout.activity_city)
        Glass.applyInsets(findViewById(R.id.city_root))

        val forWidget = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        val current = if (forWidget) Prefs.cityForWidget(this, widgetId) else Prefs.defaultCity(this)
        findViewById<TextView>(R.id.tv_title).setText(if (forWidget) R.string.pick_city_widget else R.string.pick_city_title)

        val adapter = CityAdapter(this, current.id)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> choose(adapter.getItem(pos)) }

        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
        })
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        Glass.updateBlur(this)
    }

    private fun choose(city: City) {
        val ids = WidgetUpdater.widgetIds(this)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Prefs.setWidgetCity(this, widgetId, city.id)
            // With a single widget, keep the app on the same city.
            if (ids.size <= 1) Prefs.setDefaultCity(this, city.id)
        } else {
            Prefs.setDefaultCity(this, city.id)
            // With a single widget, the widget follows the app's city.
            if (ids.size == 1) Prefs.clearWidgetCity(this, ids[0])
        }
        WidgetUpdater.updateAll(this)
        Scheduler.ensurePeriodic(this)
        Scheduler.runNow(this)
        Refresher.refreshAsync(this, 10 * 60_000L)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private class CityAdapter(private val ctx: Context, private val selectedId: String) : BaseAdapter() {
        private var items: List<City> = Cities.all

        fun filter(q: String) {
            val n = Cities.norm(q.trim())
            items = if (n.isEmpty()) Cities.all else Cities.all.filter { Cities.norm(it.name).contains(n) }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): City = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.item_city, parent, false)
            val c = items[position]
            v.findViewById<TextView>(R.id.tv_name).text = c.name
            v.findViewById<TextView>(R.id.tv_check).visibility =
                if (c.id == selectedId) View.VISIBLE else View.INVISIBLE
            return v
        }
    }
}
