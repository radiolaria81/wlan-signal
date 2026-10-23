package de.radiolaria.wlansignal

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WifiManager
    private lateinit var cm: ConnectivityManager

    // Letzte WifiInfo aus dem NetworkCallback (liefert ab Android 12 SSID/BSSID nur so)
    @Volatile private var cbInfo: WifiInfo? = null
    @Volatile private var wifiConnected = false
    private var callback: ConnectivityManager.NetworkCallback? = null

    // Statistik seit Start / Reset
    private var count = 0
    private var sum = 0L
    private var minRssi = Int.MAX_VALUE
    private var maxRssi = Int.MIN_VALUE

    // Farben
    private var cBg = 0; private var cCard = 0; private var cText = 0; private var cSub = 0
    private var cPrimary = 0; private var cTrack = 0; private var cDivider = 0

    // Ansichten
    private lateinit var content: LinearLayout
    private lateinit var tvSsid: TextView
    private lateinit var tvRssi: TextView
    private lateinit var tvRating: TextView
    private lateinit var bar: ProgressBar
    private lateinit var hintBox: LinearLayout
    private lateinit var tvHint: TextView
    private lateinit var btnHint: Button
    private lateinit var graph: SignalGraph
    private lateinit var tvMin: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvMax: TextView
    private val details = LinkedHashMap<String, TextView>()

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wm = applicationContext.getSystemService(WifiManager::class.java)
        cm = getSystemService(ConnectivityManager::class.java)
        loadColors()
        setupWindow()
        buildUi()
        // Beim Herumlaufen und Messen soll das Display anbleiben
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!hasLocationPermission()) askPermission()
    }

    override fun onStart() {
        super.onStart()
        registerCallback()
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        unregisterCallback()
        super.onStop()
    }

    // ---------- Daten ----------

    private fun registerCallback() {
        if (callback != null) return
        val cb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    cbInfo = caps.transportInfo as? WifiInfo
                    wifiConnected = true
                }
                override fun onLost(network: Network) {
                    cbInfo = null
                    wifiConnected = false
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { wifiConnected = true }
                override fun onLost(network: Network) { wifiConnected = false }
            }
        }
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // auch WLAN ohne Internet
            .build()
        runCatching { cm.registerNetworkCallback(req, cb, handler) }.onSuccess { callback = cb }
    }

    private fun unregisterCallback() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        cbInfo = null
        wifiConnected = false
    }

    @Suppress("DEPRECATION")
    private fun update() {
        if (!wm.isWifiEnabled) {
            showNoSignal("WLAN ist ausgeschaltet")
            showHint("WLAN ist aus. Schalte es ein, um die Signalstärke zu sehen.", "WLAN-Einstellungen") {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            return
        }

        // connectionInfo liefert RSSI/Frequenz/Linkrate laufend aktuell (vom System ca. alle 3 s aufgefrischt)
        val polled: WifiInfo? = wm.connectionInfo
        val rssi = polled?.rssi?.takeIf { it != WifiMath.INVALID_RSSI && it < 0 }
            ?: cbInfo?.rssi?.takeIf { it != WifiMath.INVALID_RSSI && it < 0 }

        if (!wifiConnected || rssi == null) {
            showNoSignal("Nicht mit einem WLAN verbunden")
            updatePermissionHint()
            return
        }

        val info = cbInfo ?: polled
        val ssid = WifiMath.cleanSsid(info?.ssid) ?: WifiMath.cleanSsid(polled?.ssid)
        val bssid = WifiMath.cleanBssid(info?.bssid) ?: WifiMath.cleanBssid(polled?.bssid)

        // Statistik + Verlauf
        count++; sum += rssi
        if (rssi < minRssi) minRssi = rssi
        if (rssi > maxRssi) maxRssi = rssi
        graph.add(rssi)

        val rating = WifiMath.rating(rssi)
        val rc = ratingColor(rating)
        tvSsid.text = ssid ?: "Verbunden (Name verborgen)"
        tvRssi.text = "$rssi dBm"
        tvRssi.setTextColor(cText)
        tvRating.text = rating.label
        tvRating.setTextColor(rc)
        bar.progress = (WifiMath.fraction(rssi) * 1000).roundToInt()
        bar.progressTintList = ColorStateList.valueOf(rc)

        tvMin.text = "$minRssi"
        tvMax.text = "$maxRssi"
        tvAvg.text = (sum.toDouble() / count).roundToInt().toString()

        val freq = (polled?.frequency ?: 0).takeIf { it > 0 } ?: (info?.frequency ?: 0)
        val band = WifiMath.band(freq)
        val ch = WifiMath.channel(freq)
        setDetail("BSSID", bssid ?: "verborgen")
        setDetail("Band / Kanal", if (freq > 0) "${band.label} · Kanal ${ch ?: "?"}" else "–")
        setDetail("Frequenz", if (freq > 0) "$freq MHz" else "–")
        val src = polled ?: info
        val tx = src?.txLinkSpeedMbps ?: -1
        val rx = src?.rxLinkSpeedMbps ?: -1
        setDetail("Linkrate Senden / Empfangen", "${mbps(tx)} / ${mbps(rx)}")
        setDetail("WLAN-Standard", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) standard(src?.wifiStandard ?: 0) else "ab Android 11")
        setDetail("Android-Signalstufe", signalLevel(rssi))

        updatePermissionHint()
    }

    private fun showNoSignal(title: String) {
        tvSsid.text = title
        tvRssi.text = "– dBm"
        tvRssi.setTextColor(cSub)
        tvRating.text = ""
        bar.progress = 0
        graph.add(null)
        for (k in details.keys) setDetail(k, "–")
    }

    private fun updatePermissionHint() {
        when {
            !hasLocationPermission() -> showHint(
                "Für den Netzwerknamen (SSID) und die BSSID verlangt Android die Standortberechtigung. " +
                    "Die Signalstärke selbst funktioniert auch ohne.",
                "Berechtigung erteilen"
            ) { askPermission() }
            !isLocationOn() -> showHint(
                "Standort ist ausgeschaltet – Android verbirgt dann SSID und BSSID.",
                "Standort-Einstellungen"
            ) { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            else -> hintBox.visibility = View.GONE
        }
    }

    private fun showHint(text: String, button: String, action: () -> Unit) {
        tvHint.text = text
        btnHint.text = button
        btnHint.setOnClickListener { action() }
        hintBox.visibility = View.VISIBLE
    }

    private fun resetStats() {
        count = 0; sum = 0; minRssi = Int.MAX_VALUE; maxRssi = Int.MIN_VALUE
        tvMin.text = "–"; tvAvg.text = "–"; tvMax.text = "–"
        graph.clear()
    }

    private fun mbps(v: Int) = if (v > 0) "$v Mbit/s" else "–"

    private fun standard(s: Int) = when (s) {
        1 -> "802.11a/b/g"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> "Wi-Fi 6 (802.11ax)"
        7 -> "WiGig (802.11ad)"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> "unbekannt"
    }

    @Suppress("DEPRECATION")
    private fun signalLevel(rssi: Int): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${wm.calculateSignalLevel(rssi)} von ${wm.maxSignalLevel}"
        } else {
            "${WifiManager.calculateSignalLevel(rssi, 5)} von 4"
        }

    private fun ratingColor(r: WifiMath.Rating) = getColor(
        when (r) {
            WifiMath.Rating.EXCELLENT, WifiMath.Rating.GOOD -> R.color.good
            WifiMath.Rating.FAIR -> R.color.ok
            WifiMath.Rating.WEAK -> R.color.warn
            WifiMath.Rating.POOR -> R.color.bad
        }
    )

    // ---------- Berechtigungen ----------

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isLocationOn(): Boolean {
        val lm = getSystemService(LocationManager::class.java) ?: return false
        return lm.isLocationEnabled
    }

    private fun askPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQ_PERM
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            // Callback neu registrieren, damit SSID/BSSID mit der neuen Berechtigung geliefert werden
            unregisterCallback()
            registerCallback()
            update()
        }
    }

    // ---------- Oberfläche ----------

    private fun loadColors() {
        cBg = getColor(R.color.bg); cCard = getColor(R.color.card); cText = getColor(R.color.text)
        cSub = getColor(R.color.text_sub); cPrimary = getColor(R.color.primary)
        cTrack = getColor(R.color.track); cDivider = getColor(R.color.divider)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(cBg) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(text("WLAN-Signal", 24f, cText, bold = true).apply {
            setPadding(dp(4), dp(8), 0, dp(16))
        })

        // Hauptkarte
        val main = card()
        tvSsid = text("", 17f, cText, bold = true)
        tvRssi = text("– dBm", 56f, cText, bold = true).apply { setPadding(0, dp(6), 0, 0) }
        tvRating = text("", 18f, cSub, bold = true).apply { setPadding(0, 0, 0, dp(14)) }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            isIndeterminate = false
            progressBackgroundTintList = ColorStateList.valueOf(cTrack)
            progressTintList = ColorStateList.valueOf(cPrimary)
            scaleY = 2.5f
        }
        main.addView(tvSsid)
        main.addView(tvRssi)
        main.addView(tvRating)
        main.addView(bar, lp(bottom = 6))
        main.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text("${WifiMath.RSSI_MIN} dBm", 11f, cSub), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("${WifiMath.RSSI_MAX} dBm", 11f, cSub).apply { gravity = Gravity.END },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        content.addView(main, lp(bottom = 12))

        // Hinweis (Berechtigung / Standort / WLAN aus)
        hintBox = card().apply { visibility = View.GONE }
        tvHint = text("", 14f, cText)
        btnHint = Button(this).apply {
            isAllCaps = false
            setTextColor(cPrimary)
            background = null
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        hintBox.addView(tvHint)
        hintBox.addView(btnHint, lp(w = ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(hintBox, lp(bottom = 12))

        // Verlauf
        val graphCard = card()
        graphCard.addView(text("Verlauf (letzte 2 Minuten)", 13f, cSub).apply { setPadding(0, 0, 0, dp(8)) })
        graph = SignalGraph(this)
        graphCard.addView(graph, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)))
        content.addView(graphCard, lp(bottom = 12))

        // Statistik
        val stats = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvMin = statCell(row, "Min")
        tvAvg = statCell(row, "Mittel")
        tvMax = statCell(row, "Max")
        stats.addView(text("Seit Start (dBm)", 13f, cSub).apply { setPadding(0, 0, 0, dp(6)) })
        stats.addView(row)
        stats.addView(Button(this).apply {
            text = "Zurücksetzen"
            isAllCaps = false
            setTextColor(cPrimary)
            background = null
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            setOnClickListener { resetStats() }
        }, lp(w = ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(stats, lp(bottom = 12))

        // Details
        val det = card()
        for (k in listOf("BSSID", "Band / Kanal", "Frequenz", "Linkrate Senden / Empfangen", "WLAN-Standard", "Android-Signalstufe")) {
            det.addView(text(k, 12f, cSub).apply { setPadding(0, dp(6), 0, 0) })
            val v = text("–", 16f, cText).apply { setPadding(0, 0, 0, dp(6)) }
            det.addView(v)
            details[k] = v
        }
        content.addView(det, lp(bottom = 12))

        content.addView(text(
            "Android frischt den RSSI-Wert etwa alle 3 Sekunden auf. Die Einstufung " +
                "(≥ −50 ausgezeichnet, ≥ −60 gut, ≥ −67 ordentlich, ≥ −75 schwach) ist ein Faustwert.",
            12f, cSub
        ).apply { setPadding(dp(4), dp(4), dp(4), dp(8)) })

        setContentView(scroll)
        applyInsets(scroll)
    }

    private fun setDetail(key: String, value: String) {
        details[key]?.text = value
    }

    private fun statCell(row: LinearLayout, label: String): TextView {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val v = text("–", 26f, cText, bold = true)
        col.addView(v)
        col.addView(text(label, 12f, cSub))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return v
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(cCard)
            cornerRadius = dp(16).toFloat()
        }
        setPadding(dp(20), dp(18), dp(20), dp(18))
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun lp(top: Int = 0, bottom: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        }

    // ---------- Fenster / Ränder ----------

    private fun isNight() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        window.setBackgroundDrawable(ColorDrawable(cBg))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (isNight()) 0 else mask, mask)
        } else {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (!isNight()) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }
    }

    @Suppress("DEPRECATION")
    private fun applyInsets(scroll: View) {
        val pad = dp(16)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val l: Int; val t: Int; val r: Int; val b: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                l = i.left; t = i.top; r = i.right; b = i.bottom
            } else {
                l = insets.systemWindowInsetLeft; t = insets.systemWindowInsetTop
                r = insets.systemWindowInsetRight; b = insets.systemWindowInsetBottom
            }
            content.setPadding(pad + l, dp(12) + t, pad + r, pad + b)
            insets
        }
        scroll.requestApplyInsets()
    }

    companion object {
        private const val REQ_PERM = 1
    }
}
