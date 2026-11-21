package com.filtroviajes

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var histBox: LinearLayout
    private val fields = LinkedHashMap<String, EditText>()
    private var density = 1f

    private fun dp(x: Int) = (x * density).toInt()

    private val labels = mapOf(
        "minFare" to "Tarifa mínima ($)",
        "minPerKm" to "Mínimo por km ($)",
        "minPerHour" to "Mínimo por hora ($)",
        "maxPickupKm" to "Recogida máxima (km)",
        "maxPickupMin" to "Recogida máxima (min)",
        "maxTripKm" to "Viaje máximo (km)",
        "minRating" to "Calificación mínima del pasajero"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(40))
        }

        root.addView(text("Filtro Viajes", 26f, true))
        root.addView(text("Te muestra si un viaje de Uber, DiDi o inDrive te conviene, antes de aceptarlo.", 14f, false))

        // Estado del permiso
        status = text("", 15f, true)
        status.setPadding(0, dp(16), 0, dp(4))
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Activar permiso de accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(text(
            "Pasos: entra a Filtro Viajes > actívalo. Si Android lo bloquea: Ajustes > Aplicaciones > Filtro Viajes > menú de tres puntos > Permitir ajustes restringidos.",
            12f, false
        ))

        // Interruptores
        root.addView(section("General"))
        root.addView(switchRow("Filtro activo", prefs.enabled) { prefs.enabled = it })
        root.addView(switchRow("Aceptar automáticamente los buenos viajes", prefs.autoAccept) { prefs.autoAccept = it })
        root.addView(switchRow("Rechazar automáticamente los malos viajes", prefs.autoDecline) { prefs.autoDecline = it })

        root.addView(section("Aplicaciones"))
        for (a in App.values()) {
            root.addView(switchRow(a.label, prefs.appOn(a)) { prefs.setAppOn(a, it) })
        }

        // Filtros numéricos (0 = apagado)
        root.addView(section("Filtros (0 = apagado)"))
        for (k in prefs.numericKeys) {
            val v = prefs.num(k)
            val shown = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
            root.addView(field(labels[k] ?: k, k, shown))
        }
        root.addView(Button(this).apply {
            text = "Guardar filtros"
            setOnClickListener {
                for ((k, e) in fields) {
                    val n = e.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                    prefs.setNum(k, n)
                }
                Toast.makeText(this@MainActivity, "Filtros guardados", Toast.LENGTH_SHORT).show()
            }
        })

        // Historial
        root.addView(section("Últimas solicitudes vistas"))
        histBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(histBox)
        root.addView(Button(this).apply {
            text = "Borrar historial"
            setOnClickListener {
                prefs.clearHistory()
                refreshHistory()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = serviceEnabled()
        status.text = if (on) "● Permiso activo, la app está lista" else "● Permiso de accesibilidad apagado"
        status.setTextColor(if (on) Color.rgb(27, 143, 58) else Color.rgb(198, 40, 40))
        refreshHistory()
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.startsWith("$packageName/") && it.contains("OfferService") }
    }

    private fun refreshHistory() {
        histBox.removeAllViews()
        val items = prefs.history()
        if (items.isEmpty()) {
            histBox.addView(text("Todavía no hay solicitudes.", 13f, false))
            return
        }
        val nf = NumberFormat.getIntegerInstance(Locale("es", "CO"))
        val df = SimpleDateFormat("dd/MM HH:mm", Locale("es", "CO"))
        for (o in items) {
            val ok = o.optBoolean("ok")
            val perKm = if (o.isNull("perKm")) "—" else "$" + nf.format(o.getDouble("perKm").roundToLong())
            val perHour = if (o.isNull("perHour")) "—" else "$" + nf.format(o.getDouble("perHour").roundToLong())
            val why = o.optString("why")
            val line = (if (ok) "✔ " else "✘ ") + o.optString("app") + " · $" + nf.format(o.optDouble("fare").roundToLong()) +
                "\n" + df.format(Date(o.optLong("t"))) + " · " + perKm + "/km · " + perHour + "/h" +
                (if (why.isNotEmpty()) "\n" + why else "")
            histBox.addView(text(line, 13f, false).apply {
                setTextColor(if (ok) Color.rgb(27, 143, 58) else Color.rgb(198, 40, 40))
                setPadding(0, dp(6), 0, dp(6))
            })
        }
    }

    // ---------- helpers de interfaz ----------
    private fun text(t: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(Color.rgb(30, 30, 30))
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun section(t: String) = text(t, 17f, true).apply { setPadding(0, dp(22), 0, dp(4)) }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View =
        Switch(this).apply {
            text = label
            isChecked = checked
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }

    private fun field(label: String, key: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.rgb(30, 30, 30))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val e = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(value)
            minimumWidth = dp(100)
        }
        fields[key] = e
        row.addView(e)
        return row
    }
}
