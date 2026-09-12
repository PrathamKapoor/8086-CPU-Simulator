@echo off
REM 8086 CPU Simulator - Launch Script (Windows)
REM Usage: run.bat [program.asm]

setlocal

set JAVA=java
set JAR=target\cpu-simulator.jar
set MAIN_GUI=gui.MainGUI
set MAIN_CLI=simulator.MainSimulator

if not exist "%JAR%" (
    echo Building project first...
    call mvn clean package -q -DskipTests
    if errorlevel 1 (
        echo Build failed. Please install Maven and Java 21+.
        exit /b 1
    )
)

if "%~1"=="" (
    echo Starting GUI mode...
    %JAVA% -cp "%JAR%" %MAIN_GUI%
) else (
    echo Running CLI mode with %~1...
    %JAVA% -cp "%JAR%" %MAIN_CLI% "%~1"
)

endlocal
