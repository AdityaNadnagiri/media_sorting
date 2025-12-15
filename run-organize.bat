@echo off
REM Media Sorting - Quick Run Script
REM Usage: run-organize.bat "D:\Your\Folder"

if "%~1"=="" (
    echo Error: Please provide a source folder
    echo Usage: run-organize.bat "D:\Your\Folder"
    exit /b 1
)

echo.
echo ========================================
echo  Media Sorting - Organize Job
echo ========================================
echo Source Folder: %~1
echo.

java -jar target\media-sorting-1.0.0-SNAPSHOT.jar --job=organize --sourceFolder="%~1"

echo.
echo ========================================
echo  Job Complete!
echo ========================================
pause
