@echo off
setlocal EnableExtensions
chcp 65001 >nul

rem Ir al directorio del proyecto (donde está este script)
cd /d "%~dp0"

set "MVN=%~dp0.tools\apache-maven-3.9.16\bin\mvn.cmd"

if not exist "%MVN%" (
    echo [ERROR] No se encuentra Maven en:
    echo   %MVN%
    echo.
    echo Comprueba que existe .tools\apache-maven-3.9.16
    pause
    exit /b 1
)

echo ========================================
echo  Orbeon Form Editor
echo  http://localhost:8080
echo ========================================
echo.

rem Avisar si el puerto 8080 ya esta en uso (instancia antigua)
netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul 2>&1
if %ERRORLEVEL%==0 (
    echo [AVISO] El puerto 8080 ya esta en uso.
    echo         Cierra la ventana anterior de arrancar.cmd o deten el proceso
    echo         antes de continuar, o seguira corriendo una version antigua.
    echo.
    pause
)

echo Compilando y arrancando...
echo.

"%MVN%" spring-boot:run
set "EXITCODE=%ERRORLEVEL%"

if not "%EXITCODE%"=="0" (
    echo.
    echo Maven termino con codigo %EXITCODE%
    pause
)

exit /b %EXITCODE%
