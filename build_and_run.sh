#!/bin/bash

# Build the project
echo "Starting build..."
mvn clean package -DskipTests

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "Build successful! Starting application..."
    # Run the application
    java -jar target/cadp-web-converter-0.0.1-SNAPSHOT.jar
else
    echo "Build failed!"
    exit 1
fi
