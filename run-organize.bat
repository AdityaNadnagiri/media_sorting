@echo off
REM Run the media organization job using settings from application.properties
mvn spring-boot:run -Dspring-boot.run.arguments=--job=organize
