@echo off
echo Starting continuous compilation watch...
echo Press Ctrl+C to stop watching
echo.

:loop
echo Compiling at %time%...
mvn compile -q
if %errorlevel% neq 0 (
    echo Compilation failed!
) else (
    echo Compilation successful - server should restart automatically
)
echo Waiting 3 seconds before next check...
timeout /t 3 /nobreak >nul
goto loop 