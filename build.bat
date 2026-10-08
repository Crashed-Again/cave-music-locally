@echo off
setlocal
cd /d "%~dp0"
title Building Cave

where dotnet >nul 2>nul
if errorlevel 1 (
    echo.
    echo  The .NET SDK was not found.
    echo  Install the .NET 8 SDK from https://dotnet.microsoft.com/download/dotnet/8.0
    echo  then run this again.
    echo.
    if /i not "%~1"=="nopause" pause
    exit /b 1
)

echo.
echo  === Building Cave ===
echo.

if exist dist\Cave rmdir /s /q dist\Cave

dotnet publish Cave.csproj -c Release -r win-x64 --self-contained true ^
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:DebugType=none ^
    "-flp:logfile=build.log;verbosity=minimal" ^
    -o dist\Cave

if errorlevel 1 (
    echo.
    echo  BUILD FAILED.
    echo  The errors are above and in build.log next to this file.
    echo  Send me the contents of build.log and I will fix it.
    echo.
    if /i not "%~1"=="nopause" pause
    exit /b 1
)

echo.
echo  Done:  dist\Cave\Cave.exe
echo.
if /i not "%~1"=="nopause" pause
exit /b 0
