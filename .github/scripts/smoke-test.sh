#!/usr/bin/env bash
# Emülatörde uygulamayı açar ve temel kurye akışını dokunarak dener:
# teslim et → ertele → iade → ana turu bitir → ertelenenler rotası → gün sonu.
set -uo pipefail

PKG=com.rotaasistani.demo
APK=$(ls apk/*.apk | head -1)
OUT=smoke
FAILED=0
mkdir -p "$OUT"

dump() {
  for _ in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; then
      adb shell cat /sdcard/ui.xml > "$OUT/$1.xml"
      adb exec-out screencap -p > "$OUT/$1.png"
      echo "--- [$1] ekrandaki metinler:"
      grep -o 'text="[^"]*"' "$OUT/$1.xml" | sed 's/^text="//; s/"$//' | grep -v '^$' | head -40
      return 0
    fi
    sleep 3
  done
  echo "HATA [$1] ekran dökümü alınamadı"
  FAILED=1
}

expect() {
  if grep -q "$2" "$OUT/$1.xml" 2> /dev/null; then
    echo "OK   [$1] '$2'"
  else
    echo "HATA [$1] '$2' bulunamadı"
    FAILED=1
  fi
}

# $1 döküm adı, $2 öznitelik (text | content-desc), $3 değer
tap() {
  local node
  node=$(grep -o "$2=\"$3\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$OUT/$1.xml" | head -1)
  if [ -z "$node" ]; then
    echo "HATA [$1] dokunulacak öğe yok: $3"
    FAILED=1
    return 1
  fi
  read -r x1 y1 x2 y2 <<< "$(echo "$node" | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')"
  adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

adb install -r "$APK"
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb logcat -c
adb shell am start -W -n $PKG/com.rotaasistani.MainActivity
sleep 30

dump 01-acilis
expect 01-acilis "SIRADAKİ DURAK · kalan 65"
expect 01-acilis "Plan "

echo "--- rota teşhisi"
adb shell run-as $PKG cat shared_prefs/rota_asistani_state.xml > "$OUT/state.xml"
python3 .github/scripts/route-diagnostics.py "$OUT/state.xml" || echo "teşhis betiği çalışmadı"

tap 01-acilis text "Teslim edildi" && sleep 4
dump 02-teslim
expect 02-teslim "kalan 64"

tap 02-teslim text "Sonra dön" && sleep 2
dump 03-ertele-dialog
tap 03-ertele-dialog text "Kaydet" && sleep 4
dump 04-ertelendi
expect 04-ertelendi "Ertelenen 1"
expect 04-ertelendi "kalan 63"

tap 04-ertelendi text "Teslim edilemedi" && sleep 2
dump 05-iade-dialog
tap 05-iade-dialog text "Kaydet" && sleep 4
dump 06-iade
expect 06-iade "İade 1"

tap 06-iade content-desc "Menü" && sleep 2
dump 07-menu
tap 07-menu text "Demo: ana turu hızlıca bitir" && sleep 4
dump 08-ana-tur-bitti
expect 08-ana-tur-bitti "Ertelenenler için rota oluştur"

tap 08-ana-tur-bitti text "Ertelenenler için rota oluştur" && sleep 20
dump 09-ikinci-tur
expect 09-ikinci-tur "ERTELENEN PAKET · kalan 1"

tap 09-ikinci-tur text "Teslim edildi" && sleep 4
dump 10-gun-bitti
expect 10-gun-bitti "Tüm paketler işlendi"
tap 10-gun-bitti text "Gün sonu özeti" && sleep 2
dump 11-ozet
expect 11-ozet "Şubeye iade: 1"

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "HATA uygulama çöktü:"
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  FAILED=1
fi
if ! adb shell pidof $PKG > /dev/null; then
  echo "HATA uygulama süreci çalışmıyor"
  FAILED=1
fi

if [ "$FAILED" -eq 0 ]; then echo "DUMAN TESTİ BAŞARILI"; else echo "DUMAN TESTİ BAŞARISIZ"; fi
exit $FAILED
