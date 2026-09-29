@echo off
rem ============================================================
rem build.cmd - Compila el APK del Sprint 2 (CPP)
rem JDK: JBR 21 de Android Studio. Si recibe el ^<hash^> de la
rem carpeta dist de Gradle 8.13, regenera antes el wrapper.
rem ============================================================
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\CPP\android

if not "%1"=="" (
    echo === Generando Gradle wrapper ===
    call "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.13-bin\%1\gradle-8.13\bin\gradle.bat" wrapper --gradle-version 8.13 --distribution-type bin --console=plain
    if errorlevel 1 goto :error
)

echo === Compilando APK (assembleDebug) ===
call .\gradlew.bat :app:assembleDebug --console=plain
if errorlevel 1 goto :error

echo === BUILD_OK ===
echo APK: C:\CPP\android\app\build\outputs\apk\debug\app-debug.apk
exit /b 0

:error
echo === BUILD_ERROR ===
exit /b 1
