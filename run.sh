#!/bin/bash
# 8086 CPU Simulator - Launch Script (Linux/macOS)
# Usage: ./run.sh [program.asm]

set -e

JAVA="java"
JAR="target/cpu-simulator.jar"
MAIN_GUI="gui.MainGUI"
MAIN_CLI="simulator.MainSimulator"

if [ ! -f "$JAR" ]; then
    echo "Building project first..."
    mvn clean package -q -DskipTests
    if [ $? -ne 0 ]; then
        echo "Build failed. Please install Maven and Java 21+."
        exit 1
    fi
fi

if [ -z "$1" ]; then
    echo "Starting GUI mode..."
    $JAVA -cp "$JAR" $MAIN_GUI
else
    echo "Running CLI mode with $1..."
    $JAVA -cp "$JAR" $MAIN_CLI "$1"
fi
