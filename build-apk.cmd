@echo off
rem Builds a signed Thor Tools APK into dist\
rem   build-apk.cmd          lint + unit tests + release APK
rem   build-apk.cmd clean    same, from a clean build directory
rem   build-apk.cmd fast     release APK only (skips lint and tests)
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "MODE=%~1"
set "INTERACTIVE="
echo %CMDCMDLINE% | find /i "%~nx0" >nul && set "INTERACTIVE=1"
if defined THORTOOLS_NOPAUSE set "INTERACTIVE="

call :find_jdk || goto :fail
call :find_sdk || goto :fail
call :ensure_keystore || goto :fail

set "TASKS=ktlintCheck testDebugUnitTest assembleRelease"
if /i "%MODE%"=="fast" set "TASKS=assembleRelease"
if /i "%MODE%"=="clean" set "TASKS=clean %TASKS%"

echo.
echo [*] JDK : %JAVA_HOME%
echo [*] SDK : %ANDROID_HOME%
echo [*] Run : gradlew %TASKS%
echo.
call "%~dp0gradlew.bat" %TASKS%
if errorlevel 1 (
    echo.
    echo [x] Gradle build failed. Scroll up for the first error.
    goto :fail
)

for /f tokens^=2^ delims^=^" %%V in ('findstr /c:"versionName = " app\build.gradle.kts') do set "VERSION=%%V"
set "APK_IN=app\build\outputs\apk\release\app-release.apk"
set "APK_OUT=dist\ThorTools-%VERSION%.apk"
if not exist "%APK_IN%" (
    echo [x] Build finished but %APK_IN% is missing.
    goto :fail
)
if not exist dist mkdir dist
copy /y "%APK_IN%" "%APK_OUT%" >nul || goto :fail

echo.
echo [ok] %CD%\%APK_OUT%
for /f "tokens=* delims=" %%H in ('certutil -hashfile "%APK_OUT%" SHA256 ^| findstr /v ":"') do (
    echo [ok] SHA-256 %%H
)
echo      Copy it to the Thor and open it to install or update.
if defined INTERACTIVE pause
exit /b 0

:fail
if defined INTERACTIVE pause
exit /b 1

rem ---------------------------------------------------------------------------

:find_jdk
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" exit /b 0
set "JAVA_HOME="
for /d %%D in ("%ProgramFiles%\Java\jdk-*" "%ProgramFiles%\Eclipse Adoptium\jdk-*" "%ProgramFiles%\Microsoft\jdk-*" "%ProgramFiles%\Zulu\zulu-*") do (
    if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
)
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
)
if defined JAVA_HOME exit /b 0
echo [x] No JDK found. Install a JDK 17 or newer (https://adoptium.net) or set JAVA_HOME.
exit /b 1

:find_sdk
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not exist "%ANDROID_HOME%\platforms" (
    echo [x] Android SDK not found at "%ANDROID_HOME%". Install Android Studio or set ANDROID_HOME.
    exit /b 1
)
set "SDK_ESCAPED=%ANDROID_HOME:\=\\%"
> local.properties echo sdk.dir=!SDK_ESCAPED!
exit /b 0

:ensure_keystore
set "SIGN_DIR=%USERPROFILE%\.thortools"
if exist "%SIGN_DIR%\signing.properties" exit /b 0
echo [*] First run: creating your release signing key in %SIGN_DIR%
echo     Back this folder up. Updates must be signed with the same key.
if not exist "%SIGN_DIR%" mkdir "%SIGN_DIR%"
for /f %%P in ('powershell -NoProfile -Command "[guid]::NewGuid().ToString('N')"') do set "PW=%%P"
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -noprompt -storetype PKCS12 ^
    -keystore "%SIGN_DIR%\release.jks" -alias thortools -keyalg RSA -keysize 4096 -validity 36500 ^
    -storepass !PW! -keypass !PW! -dname "CN=Thor Tools" >nul
if errorlevel 1 (
    echo [x] keytool failed to create the keystore.
    exit /b 1
)
set "KS_PATH=%SIGN_DIR:\=/%/release.jks"
(
    echo storeFile=!KS_PATH!
    echo storePassword=!PW!
    echo keyAlias=thortools
    echo keyPassword=!PW!
) > "%SIGN_DIR%\signing.properties"
exit /b 0
