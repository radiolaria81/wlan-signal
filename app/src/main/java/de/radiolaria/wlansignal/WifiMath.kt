package de.radiolaria.wlansignal

/** Reine Hilfsfunktionen ohne Android-Abhängigkeit (unit-testbar). */
object WifiMath {

    /** Android meldet -127 dBm, wenn kein gültiger RSSI vorliegt. */
    const val INVALID_RSSI = -127

    /** Darstellungsbereich für Balken und Verlauf. */
    const val RSSI_MIN = -100
    const val RSSI_MAX = -30

    enum class Band(val label: String) { GHZ_2_4("2,4 GHz"), GHZ_5("5 GHz"), GHZ_6("6 GHz"), GHZ_60("60 GHz"), UNKNOWN("?") }

    fun band(freqMhz: Int): Band = when (freqMhz) {
        in 2400..2500 -> Band.GHZ_2_4
        in 4900..5899 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        in 57000..71000 -> Band.GHZ_60
        else -> Band.UNKNOWN
    }

    /** Kanalnummer aus der Mittenfrequenz (IEEE 802.11), null wenn nicht zuordenbar. */
    fun channel(freqMhz: Int): Int? = when {
        freqMhz == 2484 -> 14
        freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
        freqMhz in 5150..5895 -> (freqMhz - 5000) / 5
        freqMhz == 5935 -> 2
        freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
        else -> null
    }

    /**
     * Grobe Einstufung. Die Schwellen sind verbreitete Faustwerte (z. B. -67 dBm als übliche
     * Mindestanforderung für VoIP/Video), kein Normwert.
     */
    enum class Rating(val label: String) {
        EXCELLENT("Ausgezeichnet"), GOOD("Gut"), FAIR("Ordentlich"), WEAK("Schwach"), POOR("Sehr schwach")
    }

    fun rating(rssi: Int): Rating = when {
        rssi >= -50 -> Rating.EXCELLENT
        rssi >= -60 -> Rating.GOOD
        rssi >= -67 -> Rating.FAIR
        rssi >= -75 -> Rating.WEAK
        else -> Rating.POOR
    }

    /** Anteil 0..1 für den Balken (linear zwischen RSSI_MIN und RSSI_MAX). */
    fun fraction(rssi: Int): Float =
        ((rssi - RSSI_MIN).toFloat() / (RSSI_MAX - RSSI_MIN)).coerceIn(0f, 1f)

    /** SSID von Anführungszeichen befreien; null wenn von Android verborgen. */
    fun cleanSsid(raw: String?): String? {
        if (raw == null) return null
        val s = raw.removeSurrounding("\"")
        return s.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    }

    /** BSSID; null wenn von Android verborgen (02:00:00:00:00:00) oder leer. */
    fun cleanBssid(raw: String?): String? =
        raw?.lowercase()?.takeIf { it.isNotEmpty() && it != "02:00:00:00:00:00" }
}
