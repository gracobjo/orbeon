@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"

set "JAR=orbeon-form-editor-1.0.0-SNAPSHOT.jar"
set "JAVA=java"

if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"

if not exist "%JAR%" (
    if exist "target\%JAR%" (
        set "JAR=target\%JAR%"
    ) else (
        echo [ERROR] No se encuentra %JAR%
        echo.
        echo Compile antes con: .tools\apache-maven-3.9.16\bin\mvn.cmd package -DskipTests
        echo O copie el JAR junto a este script en un equipo destino.
        pause
        exit /b 1
    )
)

netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul 2>&1
if %ERRORLEVEL%==0 (
    echo [AVISO] El puerto 8080 ya esta en uso.
    echo.
    pause
)

echo ========================================
echo  Orbeon Form Editor (JAR)
echo  http://localhost:8080
echo ========================================
echo.

"%JAVA%" -jar "%JAR%"
set "EXITCODE=%ERRORLEVEL%"

if not "%EXITCODE%"=="0" (
    echo.
    echo Java termino con codigo %EXITCODE%
    pause
)

exit /b %EXITCODE%
