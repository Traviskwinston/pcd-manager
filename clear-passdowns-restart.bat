@echo off
echo ====================================
echo Restarting server to clear passdowns
echo ====================================
echo.
echo The server uses an in-memory database in dev mode.
echo Restarting will clear all passdowns automatically.
echo.
echo Please:
echo 1. Stop the current server (Ctrl+C in the server terminal)
echo 2. Run: mvn spring-boot:run
echo.
echo Or, if you want to keep the passdowns and just test the many-to-many:
echo 1. Just refresh your browser - the fix is already deployed via DevTools
echo.
pause

