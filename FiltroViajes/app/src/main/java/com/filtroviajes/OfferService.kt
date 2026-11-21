package com.filtroviajes

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Servicio de accesibilidad: lee la pantalla de Uber / DiDi / inDrive conductor,
 * detecta una solicitud de viaje, calcula $/km y $/hora y muestra el veredicto.
 */
class OfferService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayView
    private val handler = Handler(Looper.getMainLooper())

    private var lastScan = 0L
    private var shownKey: String? = null
    private val hideTask = Runnable {
        overlay.hide()
        shownKey = null
    }

    private val acceptRe = Regex("(?i)(aceptar|accept|match|confirmar|acepto).*")
    private val declineRe = Regex("(?i)(rechazar|omitir|saltar|decline|skip|ignorar|descartar|no,? gracias)")

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        overlay = OverlayView(this)
    }

    override fun onInterrupt() {
        overlay.hide()
    }

    override fun onDestroy() {
        overlay.hide()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val app = App.byPackage(pkg) ?: return
        if (!prefs.enabled || !prefs.appOn(app)) return

        val now = SystemClock.uptimeMillis()
        if (now - lastScan < 250) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val texts = ArrayList<String>()
        val accept = ArrayList<AccessibilityNodeInfo>()
        val decline = ArrayList<AccessibilityNodeInfo>()
        collect(root, texts, accept, decline, 0)

        val trip = TripParser.parse(texts)
        val fare = trip.fare
        if (fare == null || accept.isEmpty()) {
            if (shownKey != null) hideTask.run()
            return
        }

        val verdict = TripFilter.eval(trip, prefs)
        val key = "${app.name}|$fare|${trip.pickupKm}|${trip.tripKm}"

        if (key != shownKey) {
            shownKey = key
            overlay.show(verdict.ok, buildLines(app, trip, verdict))
            prefs.addHistory(app, fare, verdict.ok, verdict.perKm, verdict.perHour, verdict.reasons.joinToString(", "))

            if (verdict.ok && prefs.autoAccept) {
                click(accept[0])
            } else if (!verdict.ok && prefs.autoDecline && decline.isNotEmpty()) {
                click(decline[0])
            }
        }

        handler.removeCallbacks(hideTask)
        handler.postDelayed(hideTask, 5000)
    }

    private fun collect(
        n: AccessibilityNodeInfo?,
        texts: MutableList<String>,
        accept: MutableList<AccessibilityNodeInfo>,
        decline: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (n == null || depth > 40) return
        val t = (n.text ?: n.contentDescription)?.toString()?.trim()
        if (!t.isNullOrEmpty()) {
            texts.add(t)
            if (t.length <= 40) {
                if (acceptRe.matches(t)) accept.add(n)
                else if (declineRe.matches(t)) decline.add(n)
            }
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), texts, accept, decline, depth + 1)
    }

    private fun click(node: AccessibilityNodeInfo) {
        var c: AccessibilityNodeInfo? = node
        var steps = 0
        while (c != null && steps < 6) {
            if (c.isClickable) {
                c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            c = c.parent
            steps++
        }
    }

    private fun cop(v: Double): String =
        "$" + NumberFormat.getIntegerInstance(Locale("es", "CO")).format(v.roundToLong())

    private fun buildLines(app: App, t: Trip, v: Verdict): List<String> {
        val lines = ArrayList<String>()
        lines.add(if (v.ok) "✔ TE CONVIENE · ${app.label}" else "✘ NO TE CONVIENE · ${app.label}")
        val fare = t.fare ?: 0.0
        lines.add("Tarifa: ${cop(fare)}")
        lines.add(
            "Por km: ${v.perKm?.let { cop(it) } ?: "—"}   ·   Por hora: ${v.perHour?.let { cop(it) } ?: "—"}"
        )
        if (t.pickupKm != null || t.pickupMin != null) {
            lines.add(
                "Recogida: ${t.pickupKm?.let { "%.1f km".format(Locale.US, it) } ?: "—"} · " +
                    "${t.pickupMin?.let { "${it.toInt()} min" } ?: "—"}"
            )
        }
        if (t.tripKm != null || t.tripMin != null) {
            lines.add(
                "Viaje: ${t.tripKm?.let { "%.1f km".format(Locale.US, it) } ?: "—"} · " +
                    "${t.tripMin?.let { "${it.toInt()} min" } ?: "—"}"
            )
        }
        if (v.reasons.isNotEmpty()) lines.add(v.reasons.joinToString(" · "))
        return lines
    }
}
