package com.filtroviajes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class App(val label: String, val packages: Set<String>) {
    UBER("Uber", setOf("com.ubercab.driver")),
    DIDI("DiDi", setOf("com.didiglobal.driver")),
    INDRIVE("inDrive", setOf("sinet.startup.inDriver"));

    companion object {
        fun byPackage(pkg: String): App? = values().firstOrNull { pkg in it.packages }
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("filtro", Context.MODE_PRIVATE)

    // Valores por defecto pensados para pesos colombianos. 0 = filtro apagado.
    private val defaults = mapOf(
        "minFare" to 6000f,
        "minPerKm" to 1500f,
        "minPerHour" to 20000f,
        "maxPickupKm" to 3f,
        "maxPickupMin" to 8f,
        "maxTripKm" to 0f,
        "minRating" to 0f
    )

    fun num(key: String): Double = sp.getFloat(key, defaults[key] ?: 0f).toDouble()
    fun setNum(key: String, value: Float) = sp.edit().putFloat(key, value).apply()

    fun flag(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun setFlag(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()

    var enabled: Boolean
        get() = flag("enabled", true)
        set(v) = setFlag("enabled", v)

    var autoAccept: Boolean
        get() = flag("autoAccept", false)
        set(v) = setFlag("autoAccept", v)

    var autoDecline: Boolean
        get() = flag("autoDecline", false)
        set(v) = setFlag("autoDecline", v)

    fun appOn(a: App): Boolean = flag("app_" + a.name, true)
    fun setAppOn(a: App, v: Boolean) = setFlag("app_" + a.name, v)

    val numericKeys: List<String> get() = defaults.keys.toList()

    // ---------- Historial ----------
    fun addHistory(app: App, fare: Double, ok: Boolean, perKm: Double?, perHour: Double?, reasons: String) {
        val arr = JSONArray(sp.getString("hist", "[]"))
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        o.put("app", app.label)
        o.put("fare", fare)
        o.put("ok", ok)
        o.put("perKm", perKm ?: JSONObject.NULL)
        o.put("perHour", perHour ?: JSONObject.NULL)
        o.put("why", reasons)
        val out = JSONArray()
        out.put(o)
        for (i in 0 until minOf(arr.length(), 79)) out.put(arr.get(i))
        sp.edit().putString("hist", out.toString()).apply()
    }

    fun history(): List<JSONObject> {
        val arr = JSONArray(sp.getString("hist", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun clearHistory() = sp.edit().remove("hist").apply()
}
