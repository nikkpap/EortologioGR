package gr.eortologio.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Επαναπρογραμματίζει την ημερήσια ειδοποίηση μετά από επανεκκίνηση, ενημέρωση εφαρμογής ή αλλαγή ώρας/ζώνης. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                T.init(app)
                EventStore.get(app).invalidate() // αλλαγή ώρας/ζώνης → νέο «σήμερα»
                ReminderReceiver.schedule(app)
                TodayWidget.updateAll(app)
            } catch (_: Exception) {
            } finally {
                pr.finish()
            }
        }.start()
    }
}
