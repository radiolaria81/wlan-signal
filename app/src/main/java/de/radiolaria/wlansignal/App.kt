package de.radiolaria.wlansignal

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Schreibt unbehandelte Ausnahmen nach files/crash.txt, damit MainActivity sie beim nächsten Start zeigen kann. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val head = "WLAN-Signal ${BuildConfigInfo.version(this)} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n\n"
                crashFile(this).writeText(head + sw.toString())
            }
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        fun crashFile(app: android.content.Context) = File(app.filesDir, "crash.txt")
    }
}

object BuildConfigInfo {
    fun version(ctx: android.content.Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
}
