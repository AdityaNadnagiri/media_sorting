@echo off
REM Run the cleanup job using settings from application.properties
mvn spring-boot:run -Dspring-boot.run.arguments=--job=cleanup
