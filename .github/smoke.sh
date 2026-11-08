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
adb shell am start -W -n gr.eortologio.plus/.MainActivity --es page today > /dev/null
sleep 3
adb shell uiautomator dump /sdcard/u.xml > /dev/null 2>&1
adb shell cat /sdcard/u.xml | grep -q "Χρόνια πολλά" && echo "LANG_EL_OK" || { echo "LANG_EL_MISSING"; FAIL=1; }
adb shell input keyevent KEYCODE_HOME
adb shell pm grant gr.eortologio.plus android.permission.SEND_SMS || true
adb shell am force-stop gr.eortologio.plus
printf '<?xml version="1.0" encoding="utf-8"?>\n<map><boolean name="autoSms" value="true" /><string name="autoSmsMode">both</string><boolean name="guideDone" value="true" /><boolean name="askedNotif" value="true" /></map>\n' > /tmp/settings.xml
adb push /tmp/settings.xml /data/local/tmp/settings.xml
adb shell chmod 644 /data/local/tmp/settings.xml
adb shell run-as gr.eortologio.plus mkdir -p shared_prefs || true
adb shell run-as gr.eortologio.plus cp /data/local/tmp/settings.xml shared_prefs/settings.xml || true
adb shell run-as gr.eortologio.plus cat shared_prefs/settings.xml || true
adb shell am broadcast -n gr.eortologio.plus/.ReminderReceiver -a gr.eortologio.plus.DAILY || true
sleep 4
adb shell dumpsys notification --noredact | grep -q "pkg=gr.eortologio.plus.*id=43" && echo "CONFIRM_NOTIF_OK" || echo "CONFIRM_NOTIF_MISSING"
adb logcat -d | grep -q "EortologioAutoSms.*sent to" && echo "SENT_TOO_EARLY" || echo "NOT_SENT_BEFORE_CONFIRM_OK"
adb shell am broadcast -n gr.eortologio.plus/.ReminderReceiver -a gr.eortologio.plus.AUTOSMS_SEND || true
sleep 4
adb shell dumpsys notification --noredact | grep -A3 "pkg=gr.eortologio.plus" | head -20 || true
adb shell am broadcast -n gr.eortologio.plus/.ReminderReceiver -a gr.eortologio.plus.SNOOZE --ei minutes 1 || true
sleep 3
adb shell dumpsys alarm | grep -A2 "gr.eortologio.plus" | head -12 || true
adb logcat -d | grep -E "FATAL|AndroidRuntime: |at gr\.eortologio|EortologioAutoSms" | head -60 || true
adb logcat -d | grep -q "EortologioAutoSms.*sent to" && echo "AUTOSMS_OK" || echo "AUTOSMS_NOT_SENT"
# ---- Αγγλικά: όλες οι σελίδες + κείμενο
adb shell am force-stop gr.eortologio.plus
printf '<?xml version="1.0" encoding="utf-8"?>\n<map><string name="lang">en</string><boolean name="guideDone" value="true" /><boolean name="askedNotif" value="true" /></map>\n' > /tmp/settings_en.xml
adb push /tmp/settings_en.xml /data/local/tmp/settings.xml
adb shell run-as gr.eortologio.plus cp /data/local/tmp/settings.xml shared_prefs/settings.xml || true
for P in today calendar contacts personal settings; do
  adb shell am start -W -n gr.eortologio.plus/.MainActivity --es page $P > /dev/null
  sleep 3
  if adb shell pidof gr.eortologio.plus > /dev/null; then echo "EN_PAGE_OK $P"; else echo "EN_PAGE_CRASH $P"; FAIL=1; fi
done
adb shell am start -W -n gr.eortologio.plus/.MainActivity --es page today > /dev/null
sleep 3
adb shell uiautomator dump /sdcard/u.xml > /dev/null 2>&1
adb shell cat /sdcard/u.xml | grep -q "Happy name day" && echo "LANG_EN_OK" || { echo "LANG_EN_MISSING"; FAIL=1; }
adb shell cat /sdcard/u.xml | grep -o 'text="[^"]*"' | head -25
if adb logcat -d | grep -q "FATAL EXCEPTION"; then echo "SMOKE_FAILED"; exit 1; fi
[ $FAIL = 0 ] || { echo "SMOKE_FAILED"; exit 1; }
[ $FAIL = 0 ] && echo "SMOKE_PASSED"
