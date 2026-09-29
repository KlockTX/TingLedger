@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
set "BT=C:\Users\KlockTX\AppData\Local\Android\Sdk\build-tools\37.0.0"
cd /d "%~dp0"
set "APK=app\build\outputs\apk\release\app-release.apk"

echo ===== apksigner verify =====
call "%BT%\apksigner.bat" verify --verbose --print-certs "%APK%"

echo ===== zipalign check =====
call "%BT%\zipalign.exe" -c -v 4 "%APK%" | findstr /C:"Verification"
endlocal
