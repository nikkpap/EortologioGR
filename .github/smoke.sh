#!/bin/bash
# Smoke test σε emulator: ανοίγει όλες τις οθόνες, στέλνει ειδοποίηση, ελέγχει για crash.
adb root || true
sleep 3
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant gr.eortologio.plus android.permission.POST_NOTIFICATIONS || true
adb shell pm grant gr.eortologio.plus android.permission.READ_CONTACTS || true
adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n: || true
adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:1 --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'Μιχάλης Παπάς' --bind data2:s:'Μιχάλης'" || true
adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:1 --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:6971234567 --bind data2:i:2" || true
adb logcat -c
FAIL=0
for P in today calendar contacts personal settings today; do
  adb shell am start -W -n gr.eortologio.plus/.MainActivity --es page $P > /dev/null
  sleep 4
  if adb shell pidof gr.eortologio.plus > /dev/null; then echo "PAGE_OK $P"; else echo "PAGE_CRASH $P"; FAIL=1; fi
done
adb shell input keyevent KEYCODE_HOME
adb shell am broadcast -n gr.eortologio.plus/.ReminderReceiver -a gr.eortologio.plus.DAILY || true
sleep 4
adb shell dumpsys notification --noredact | grep -A3 "pkg=gr.eortologio.plus" | head -20 || true
adb shell am broadcast -n gr.eortologio.plus/.ReminderReceiver -a gr.eortologio.plus.SNOOZE --ei minutes 1 || true
sleep 3
adb shell dumpsys alarm | grep -A2 "gr.eortologio.plus" | head -12 || true
adb logcat -d | grep -E "FATAL|AndroidRuntime: |Caused by|at gr\.eortologio" | head -60 || true
if adb logcat -d | grep -q "FATAL EXCEPTION"; then echo "SMOKE_FAILED"; exit 1; fi
[ $FAIL = 0 ] && echo "SMOKE_PASSED"
