@echo off
setlocal
cd /d "%~dp0"
title Building Cave installer

rem ---- 1. build the exe first
call build.bat nopause
if errorlevel 1 (
    pause
    exit /b 1
)

rem ---- 2. make sure the WiX tool is installed (one time)
set "PATH=%PATH%;%USERPROFILE%\.dotnet\tools"
where wix >nul 2>nul
if errorlevel 1 (
    echo.
    echo  === Installing the WiX tool ^(one time^) ===
    dotnet tool install --global wix --version 5.0.2
    if errorlevel 1 (
        echo.
        echo  Could not install WiX. Check your internet connection and try again.
        pause
        exit /b 1
    )
)

rem ---- 3. build the MSI
echo.
echo  === Building Cave.msi ===
echo.
wix build installer\Package.wxs -arch x64 -d "PublishDir=%CD%\dist\Cave" -d "ProjectDir=%CD%" -o dist\Cave-1.0.0.msi
if errorlevel 1 (
    echo.
    echo  MSI BUILD FAILED. The errors are above.
    pause
    exit /b 1
)

echo.
echo  Done:  dist\Cave-1.0.0.msi
echo.
pause
exit /b 0
