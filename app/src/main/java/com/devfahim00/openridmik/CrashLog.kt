package com.devfahim00.openridmik

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash / swallowed exception so the Settings screen can show *why* the keyboard stopped.
 * When the keyboard process dies Android may fall back to another keyboard; this makes the cause visible.
 */
object CrashLog {
    private const val KEY = "last_crash"
    private var installed = false

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun record(c: Context, where: String, e: Throwable) {
        try {
            val trace = e.stackTraceToString().lines().take(8).joinToString("\n")
            Prefs.setString(c.applicationContext, KEY, "${stamp()}  [$where]\n$trace")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun install(app: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val trace = e.stackTraceToString().lines().take(8).joinToString("\n")
                // commit (not apply): the process is about to die
                app.getSharedPreferences("openridmik", Context.MODE_PRIVATE).edit()
                    .putString(KEY, "${stamp()}  [FATAL ${t.name}]\n$trace").commit()
            } catch (_: Exception) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun last(c: Context): String = Prefs.getString(c, KEY)

    fun clear(c: Context) = Prefs.setString(c, KEY, "")
}
