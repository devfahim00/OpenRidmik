package com.devfahim00.openridmik

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases (latest, non-prerelease) for a newer version. Silent: failures are ignored. */
object Updater {
    private const val REPO = "devfahim00/OpenRidmik"
    private const val MIN_INTERVAL_MS = 6L * 60 * 60 * 1000

    class Info(val version: String, val apkUrl: String, val pageUrl: String)

    sealed class Result {
        class Available(val info: Info) : Result()
        object UpToDate : Result()
        object Failed : Result()
    }

    private val main = Handler(Looper.getMainLooper())

    fun currentVersion(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
            .map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Update found by the last check (null when none, or when this build is already current). */
    fun available(c: Context): Info? {
        val v = Prefs.getString(c, "upd_version")
        if (v.isEmpty() || !isNewer(v, currentVersion(c))) return null
        return Info(v, Prefs.getString(c, "upd_apk"), Prefs.getString(c, "upd_page"))
    }

    /** Runs in the background; [onDone] is called on the main thread. Throttled unless [force]. */
    fun check(c: Context, force: Boolean, onDone: ((Result) -> Unit)? = null) {
        val app = c.applicationContext
        val last = Prefs.getString(app, "upd_checked").toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - last < MIN_INTERVAL_MS) {
            onDone?.let { cb -> main.post { cb(available(app)?.let { Result.Available(it) } ?: Result.UpToDate) } }
            return
        }
        Thread {
            val res = try {
                fetch(app)
            } catch (e: Exception) {
                Result.Failed
            }
            main.post { onDone?.invoke(res) }
        }.start()
    }

    private fun fetch(c: Context): Result {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "OpenRidmik-updater")
        try {
            if (conn.responseCode == 404) {
                Prefs.setString(c, "upd_checked", System.currentTimeMillis().toString())
                Prefs.setString(c, "upd_version", "")
                return Result.UpToDate
            }
            if (conn.responseCode != 200) return Result.Failed
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            if (tag.isEmpty()) return Result.Failed
            var apk = ""
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apk = a.optString("browser_download_url")
                        break
                    }
                }
            }
            Prefs.setString(c, "upd_checked", System.currentTimeMillis().toString())
            Prefs.setString(c, "upd_version", tag.removePrefix("v"))
            Prefs.setString(c, "upd_apk", apk)
            Prefs.setString(c, "upd_page", o.optString("html_url"))
            return available(c)?.let { Result.Available(it) } ?: Result.UpToDate
        } finally {
            conn.disconnect()
        }
    }
}
