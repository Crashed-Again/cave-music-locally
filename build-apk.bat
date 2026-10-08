@echo off
setlocal
cd /d "%~dp0"
title Building Cave for Android

rem ---- Java (JDK 17). Android Studio ships one, so use that if Java isn't set up.
where java >nul 2>nul
if errorlevel 1 (
    if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
)
where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo  Java was not found. Install Android Studio ^(it includes Java^) or a JDK 17,
    echo  then run this again.
    echo.
    pause
    exit /b 1
)

rem ---- Android SDK
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME (
    echo.
    echo  The Android SDK was not found.
    echo  Install Android Studio once ^(https://developer.android.com/studio^), open it,
    echo  let it download the SDK, then run this again.
    echo.
    pause
    exit /b 1
)
set "SDKPATH=%ANDROID_HOME:\=/%"
> local.properties echo sdk.dir=%SDKPATH%

rem ---- Gradle wrapper (made once with the gradle command if it is missing)
if not exist gradlew.bat (
    where gradle >nul 2>nul
    if errorlevel 1 (
        echo.
        echo  No Gradle found. Easiest fix: open this folder in Android Studio and use
        echo  Build ^> Build APK^(s^). Or install Gradle:  winget install Gradle.Gradle
        echo  then run this again.
        echo.
        pause
        exit /b 1
    )
    echo  Creating the Gradle wrapper...
    call gradle wrapper --gradle-version 8.7
    if errorlevel 1 (
        echo  Could not create the Gradle wrapper.
        pause
        exit /b 1
    )
)

echo.
echo  === Building Cave.apk ===
echo.
call gradlew.bat assembleDebug --console=plain
if errorlevel 1 (
    echo.
    echo  BUILD FAILED. The errors are above. Send me a screenshot or copy of them.
    echo.
    pause
    exit /b 1
)

if not exist dist mkdir dist
copy /y app\build\outputs\apk\debug\app-debug.apk dist\Cave.apk >nul
echo.
echo  Done:  dist\Cave.apk
echo  Copy it to your phone and open it ^(allow "install unknown apps" when asked^).
echo.
pause
exit /b 0
