package gr.eortologio.plus

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager

/** Ρυθμίσεις εφαρμογής (αρχείο "settings" — ίδια κλειδιά με τις παλιές εκδόσεις, ώστε να μη χάνεται τίποτα). */
object Prefs {
    fun sp(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun hour(c: Context) = sp(c).getInt("hour", 9)
    fun minute(c: Context) = sp(c).getInt("minute", 0)
    fun timeText(c: Context) = String.format(Names.EL, "%02d:%02d", hour(c), minute(c))

    fun canNotify(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryOk(c: Context): Boolean {
        val pm = c.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(c.packageName)
    }
}
