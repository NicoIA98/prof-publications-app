@echo off
setlocal

title Professor Publications App

echo ==========================================
echo Professor Publications App
echo ==========================================
echo.

set "APP_DIR=%~dp0"
set "APP_JAR=%APP_DIR%prof-publications-app.jar"
set "LIB_DIR=%APP_DIR%lib"
set "JAVA_VERSION_FILE=%TEMP%\prof-publications-java-version.txt"

if not exist "%APP_JAR%" (
    echo ERRORE: JAR applicazione non trovato:
    echo %APP_JAR%
    echo.
    pause
    exit /b 1
)

if not exist "%LIB_DIR%" (
    echo ERRORE: cartella lib non trovata:
    echo %LIB_DIR%
    echo.
    pause
    exit /b 1
)

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

if not defined JAVA_CMD (
    where java >nul 2>nul
    if errorlevel 1 (
        echo ERRORE: Java non trovato nel PATH.
        echo Installa Java 21 oppure configura JAVA_HOME/PATH.
        echo.
        pause
        exit /b 1
    )

    set "JAVA_CMD=java"
)

"%JAVA_CMD%" -version 2> "%JAVA_VERSION_FILE%"

for /f "tokens=3" %%v in ('findstr /i "version" "%JAVA_VERSION_FILE%"') do (
    set "JAVA_VERSION_RAW=%%v"
)

set "JAVA_VERSION_RAW=%JAVA_VERSION_RAW:"=%"

for /f "tokens=1 delims=." %%m in ("%JAVA_VERSION_RAW%") do (
    set "JAVA_MAJOR=%%m"
)

if "%JAVA_MAJOR%"=="" (
    echo ERRORE: impossibile rilevare la versione Java.
    echo Java usato:
    echo %JAVA_CMD%
    echo.
    echo Output java -version:
    type "%JAVA_VERSION_FILE%"
    echo.
    pause
    exit /b 1
)

if %JAVA_MAJOR% LSS 21 (
    echo ERRORE: versione Java non compatibile.
    echo Versione rilevata: %JAVA_VERSION_RAW%
    echo Versione richiesta: Java 21 o superiore.
    echo.
    echo Java usato:
    echo %JAVA_CMD%
    echo.
    echo Soluzione:
    echo - installa Java 21;
    echo - oppure configura JAVA_HOME/PATH verso un JDK 21.
    echo.
    pause
    exit /b 1
)

echo Java rilevato: %JAVA_VERSION_RAW%
echo Java usato:
echo %JAVA_CMD%
echo.
echo Avvio applicazione...
echo.

"%JAVA_CMD%" ^
  --module-path "%LIB_DIR%" ^
  --add-modules javafx.controls,javafx.fxml ^
  -cp "%APP_JAR%;%LIB_DIR%\*" ^
  -Dfile.encoding=UTF-8 ^
  com.iadanza.profpublicationsapp.Launcher

echo.
echo Applicazione terminata.
pause