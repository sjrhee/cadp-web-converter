#!/bin/bash

# Build the project
echo "Starting build..."
mvn clean package -DskipTests

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "Build successful! Starting application..."
    # Run the application
    echo "Running in background. Logs: server.log"
    nohup java -jar target/cadp-web-converter-0.0.1-SNAPSHOT.jar > server.log 2>&1 &
    
    # Save PID
    echo $! > app.pid
    echo "Process started with PID $(cat app.pid)"
else
    echo "Build failed!"
    exit 1
fi
