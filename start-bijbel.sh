#!/bin/bash
# Start de emulator (als hij nog niet draait) en opent StatenBijbel erin.
set -e
SDK="$HOME/Android/Sdk"
ADB="$SDK/platform-tools/adb"
AVD="bijbel"
POORT=5556
SERIAL="emulator-$POORT"

if ! "$ADB" devices | grep -q "^$SERIAL[[:space:]]*device"; then
    echo "Emulator starten ($AVD) ..."
    nohup "$SDK/emulator/emulator" -avd "$AVD" -port "$POORT" \
        -no-snapshot-save -no-boot-anim >/tmp/bijbel-emulator.log 2>&1 &
    "$ADB" -s "$SERIAL" wait-for-device
    echo -n "Wachten tot Android klaar is"
    while [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        echo -n "."; sleep 2
    done
    echo
fi

APK="$(dirname "$0")/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ] && ! "$ADB" -s "$SERIAL" shell pm list packages | grep -q nl.statenbijbel.app; then
    echo "App installeren ..."
    "$ADB" -s "$SERIAL" install -r "$APK"
fi

"$ADB" -s "$SERIAL" shell am start -n nl.statenbijbel.app/nl.statenbijbel.app.MainActivity >/dev/null
echo "StatenBijbel draait op $SERIAL."
