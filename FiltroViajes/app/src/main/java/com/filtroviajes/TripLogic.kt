package com.filtroviajes

data class Trip(
    val fare: Double?,
    val pickupKm: Double?,
    val pickupMin: Double?,
    val tripKm: Double?,
    val tripMin: Double?,
    val rating: Double?
)

data class Verdict(
    val ok: Boolean,
    val perKm: Double?,
    val perHour: Double?,
    val reasons: List<String>
)

object TripParser {
    private val moneyRe = Regex("""(?:COP|COL\$|\$)\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)
    private val moneyAfterRe = Regex("""([0-9][0-9.,]*)\s*(?:COP|pesos)""", RegexOption.IGNORE_CASE)

    // Formato tipo Uber: "4 min (1.2 km) away" y "12 min (5.3 km) trip"
    private val minKmRe = Regex("""(\d+)\s*min[^\d]{0,6}(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val distRe = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val minRe = Regex("""(\d+)\s*min\b""", RegexOption.IGNORE_CASE)
    private val ratingRe = Regex("""(?<![\d.,$])([3-5][.,]\d{1,2})(?!\d|\s*(?:km|m|min)\b)""", RegexOption.IGNORE_CASE)

    private fun toMoney(s: String): Double? {
        var x = s.trim().trimEnd('.', ',')
        x = x.replace(Regex("[.,]\\d{1,2}$"), "")
        x = x.replace(".", "").replace(",", "")
        return x.toDoubleOrNull()
    }

    private fun toKm(v: String, unit: String): Double {
        val n = v.replace(',', '.').toDoubleOrNull() ?: 0.0
        return if (unit.equals("m", ignoreCase = true)) n / 1000.0 else n
    }

    fun parse(texts: List<String>): Trip {
        val all = texts.joinToString(" | ")

        val fare = (moneyRe.find(all) ?: moneyAfterRe.find(all))
            ?.let { toMoney(it.groupValues[1]) }
            ?.takeIf { it >= 500 }

        var pickupKm: Double? = null
        var pickupMin: Double? = null
        var tripKm: Double? = null
        var tripMin: Double? = null

        val pairs = minKmRe.findAll(all).toList()
        if (pairs.size >= 2) {
            pickupMin = pairs[0].groupValues[1].toDouble()
            pickupKm = toKm(pairs[0].groupValues[2], pairs[0].groupValues[3])
            tripMin = pairs[1].groupValues[1].toDouble()
            tripKm = toKm(pairs[1].groupValues[2], pairs[1].groupValues[3])
        } else {
            val dists = distRe.findAll(all).map { toKm(it.groupValues[1], it.groupValues[2]) }.toList()
            val mins = minRe.findAll(all).map { it.groupValues[1].toDouble() }.toList()
            when {
                dists.size >= 2 -> { pickupKm = dists[0]; tripKm = dists[1] }
                dists.size == 1 -> tripKm = dists[0]
            }
            when {
                mins.size >= 2 -> { pickupMin = mins[0]; tripMin = mins[1] }
                mins.size == 1 -> tripMin = mins[0]
            }
        }

        val rating = ratingRe.find(all)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

        return Trip(fare, pickupKm, pickupMin, tripKm, tripMin, rating)
    }
}

object TripFilter {
    fun eval(t: Trip, p: Prefs): Verdict {
        val reasons = ArrayList<String>()
        val fare = t.fare
        val km = (t.pickupKm ?: 0.0) + (t.tripKm ?: 0.0)
        val min = (t.pickupMin ?: 0.0) + (t.tripMin ?: 0.0)
        val perKm = if (fare != null && km > 0) fare / km else null
        val perHour = if (fare != null && min > 0) fare / (min / 60.0) else null

        val minFare = p.num("minFare")
        val minPerKm = p.num("minPerKm")
        val minPerHour = p.num("minPerHour")
        val maxPickupKm = p.num("maxPickupKm")
        val maxPickupMin = p.num("maxPickupMin")
        val maxTripKm = p.num("maxTripKm")
        val minRating = p.num("minRating")

        if (fare != null && minFare > 0 && fare < minFare) reasons.add("Tarifa baja")
        if (perKm != null && minPerKm > 0 && perKm < minPerKm) reasons.add("Paga poco por km")
        if (perHour != null && minPerHour > 0 && perHour < minPerHour) reasons.add("Paga poco por hora")
        if (t.pickupKm != null && maxPickupKm > 0 && t.pickupKm > maxPickupKm) reasons.add("Recogida lejos")
        if (t.pickupMin != null && maxPickupMin > 0 && t.pickupMin > maxPickupMin) reasons.add("Recogida demorada")
        if (t.tripKm != null && maxTripKm > 0 && t.tripKm > maxTripKm) reasons.add("Viaje muy largo")
        if (t.rating != null && minRating > 0 && t.rating < minRating) reasons.add("Pasajero mal calificado")

        return Verdict(reasons.isEmpty(), perKm, perHour, reasons)
    }
}
