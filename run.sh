#!/bin/bash
echo "Starting Media Sorting Spring Boot Application..."
echo

echo "Building the application..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo
echo "Starting the application..."
java -jar target/media-sorting-1.0.0-SNAPSHOT.jar