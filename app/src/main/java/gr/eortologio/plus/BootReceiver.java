package gr.eortologio.plus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Επαναπρογραμματίζει την ημερήσια ειδοποίηση μετά από επανεκκίνηση, ενημέρωση εφαρμογής ή αλλαγή ώρας/ζώνης. */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    EventStore.invalidate(); // αλλαγή ώρας/ζώνης → νέο «σήμερα»
    ReminderReceiver.schedule(context);
  }
}
