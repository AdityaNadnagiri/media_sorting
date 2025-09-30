@echo off
echo Starting Media Sorting Spring Boot Application...
echo.
echo Building the application...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Starting the application...
java -jar target\media-sorting-1.0.0-SNAPSHOT.jar

pause