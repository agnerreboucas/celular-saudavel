#!/usr/bin/env bash
# Instala o app, libera permissões, abre, faz toques aleatórios e procura falhas.
set -x
PKG=br.com.celularsaudavel
APK=app/build/outputs/apk/full/release/app-full-release.apk
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
adb install -r "$APK"
for p in READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE READ_MEDIA_IMAGES READ_MEDIA_VIDEO READ_MEDIA_AUDIO POST_NOTIFICATIONS; do
  adb shell pm grant $PKG android.permission.$p 2>/dev/null || true
done
adb shell appops set $PKG GET_USAGE_STATS allow || true
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow || true
# algumas fotos de exemplo, com duplicadas
adb shell 'mkdir -p /sdcard/DCIM/Camera'
for i in 1 2 3; do adb shell "screencap -p /sdcard/DCIM/Camera/teste_$i.png"; done
adb shell 'cp /sdcard/DCIM/Camera/teste_1.png /sdcard/DCIM/Camera/copia.png'
adb shell 'am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/Camera/teste_1.png' || true
adb logcat -c
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 15
# abre a análise tocando no botão principal por texto (uiautomator) e depois toques aleatórios
adb shell monkey -p $PKG --pct-syskeys 0 --pct-appswitch 0 --throttle 300 -s 42 -v 600 || true
sleep 5
adb logcat -d > logcat.txt
{
  echo "API $API"
  if grep -q "FATAL EXCEPTION" logcat.txt; then
    echo "FALHA ENCONTRADA:"
    grep -A 40 "FATAL EXCEPTION" logcat.txt | head -120
  else
    echo "SEM FALHAS"
  fi
  echo "--- processo:"
  adb shell pidof $PKG || echo "app nao esta rodando"
} > smoke-result.txt
cat smoke-result.txt
exit 0
