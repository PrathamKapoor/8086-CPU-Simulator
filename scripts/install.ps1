# ------------------------------------------------------------------
# 8086 CPU Simulator — PowerShell Installation Script (Windows)
# Version: 3.0.0
# ------------------------------------------------------------------
# Usage (from Administrator or standard PowerShell 5.1+ / 7+):
#   .\scripts\install.ps1 [-RunTests] [-SkipBuild]
# ------------------------------------------------------------------

[CmdletBinding()]
param(
    [switch]$RunTests,
    [switch]$SkipBuild,
    [switch]$Force
)

# ------------------------------------------------------------------
# Configuration
# ------------------------------------------------------------------
$MIN_JAVA_VERSION = 21
$MIN_MAVEN_VERSION = [System.Version]"3.8.0"
$SIMULATOR_VERSION = "3.0.0"
$REPO_URL = "https://github.com/cpu-simulator/8086"

# ------------------------------------------------------------------
# Helper Functions
# ------------------------------------------------------------------
function Write-LogInfo  { param($Msg) Write-Host "[INFO]  $Msg" -ForegroundColor Cyan }
function Write-LogOk    { param($Msg) Write-Host "[OK]    $Msg" -ForegroundColor Green }
function Write-LogWarn  { param($Msg) Write-Host "[WARN]  $Msg" -ForegroundColor Yellow }
function Write-LogError { param($Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

function Exit-Error {
    param([string]$Message)
    Write-LogError $Message
    exit 1
}

# ------------------------------------------------------------------
# Dependency Checks
# ------------------------------------------------------------------
function Test-JavaPrerequisite {
    Write-LogInfo "Checking Java installation ..."

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        Exit-Error "Java not found in PATH. Please install OpenJDK 21 or later (https://adoptium.net/)."
    }

    $versionString = (java -version 2>&1 | Select-String -Pattern '"([0-9_]+)"' -AllMatches).Matches.Value -replace '"', ''
    if ([string]::IsNullOrWhiteSpace($versionString)) {
        $versionString = (java -version 2>&1 | Select-String -Pattern 'version').Line.Split('"')[1]
    }

    # Extract major version (e.g., "21.0.2" -> 21)
    $major = [int]($versionString.Split('.')[0])
    if ($major -lt $MIN_JAVA_VERSION) {
        Exit-Error "Java version $versionString is too old (minimum: $MIN_JAVA_VERSION)."
    }

    Write-LogOk "Java $versionString detected (>= $MIN_JAVA_VERSION)"
}

function Test-MavenPrerequisite {
    Write-LogInfo "Checking Maven installation ..."

    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvnCmd) {
        Exit-Error "Maven not found in PATH. Please install Maven 3.8+ (https://maven.apache.org/download.cgi)."
    }

    $mvnVersionString = (mvn -v | Select-String "Apache Maven").Line.Split()[-1]
    $mvnVersion = [System.Version]$mvnVersionString
    if ($mvnVersion -lt $MIN_MAVEN_VERSION) {
        Exit-Error "Maven $mvnVersionString is too old (minimum: $MIN_MAVEN_VERSION)."
    }

    Write-LogOk "Maven $mvnVersionString detected (>= $MIN_MAVEN_VERSION)"
}

function Test-JavaFXPrerequisite {
    Write-LogInfo "Checking JavaFX dependency declaration ..."

    if (Test-Path "pom.xml") {
        $pomContent = Get-Content "pom.xml" -Raw
        if ($pomContent -match "javafx") {
            Write-LogOk "JavaFX dependency declared in pom.xml"
        } else {
            Write-LogWarn "No JavaFX dependency found in pom.xml"
        }
    } else {
        Write-LogWarn "pom.xml not found in current directory"
    }
}

# ------------------------------------------------------------------
# Build Operations
# ------------------------------------------------------------------
function Invoke-Build {
    Write-LogInfo "Building 8086 CPU Simulator v$SIMULATOR_VERSION ..."
    $output = mvn clean package -B -DskipTests 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-LogError "Maven build failed."
        Write-Host $output -ForegroundColor Red
        Exit-Error "Build failed. See Maven output above."
    }
    Write-LogOk "Build completed successfully."
    Write-LogInfo "Artifact: $(Resolve-Path '.\target\cpu-simulator-' + $SIMULATOR_VERSION + '.jar')"
}

function Invoke-Tests {
    Write-LogInfo "Running JUnit test suite ..."
    $output = mvn test -B 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-LogError "Tests failed."
        Write-Host $output -ForegroundColor Red
        Exit-Error "Tests failed. See Maven output above."
    }
    Write-LogOk "All tests passed."
}

function Install-SystemLink {
    Write-LogInfo "Creating optional convenience links ..."
    $jarPath = Resolve-Path ".\target\cpu-simulator-$SIMULATOR_VERSION.jar" -ErrorAction SilentlyContinue
    if (-not $jarPath) {
        Write-LogWarn "Built JAR not found at target/cpu-simulator-$SIMULATOR_VERSION.jar"
        return
    }

    # Create a desktop shortcut (optional)
    $desktop = [Environment]::GetFolderPath("Desktop")
    $shortcutPath = Join-Path $desktop "8086 CPU Simulator.lnk"
    $wshShell = New-Object -ComObject WScript.Shell
    $shortcut = $wshShell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = "javaw.exe"
    $shortcut.Arguments = "-jar `"$jarPath`""
    $shortcut.WorkingDirectory = $PSScriptRoot
    $shortcut.Description = "8086 CPU Simulator v$SIMULATOR_VERSION"
    $shortcut.Save()
    Write-LogOk "Desktop shortcut created: $shortcutPath"
}

# ------------------------------------------------------------------
# Main
# ------------------------------------------------------------------
function Main {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

    Write-Host "============================================================" -ForegroundColor Blue
    Write-Host "  8086 CPU Simulator — Installation Script v$SIMULATOR_VERSION" -ForegroundColor Blue
    Write-Host "  Repository: $REPO_URL" -ForegroundColor Blue
    Write-Host "============================================================" -ForegroundColor Blue
    Write-Host ""

    # Move to project root (parent of scripts folder)
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
    $projectRoot = Split-Path -Parent $scriptDir
    Set-Location $projectRoot

    # Dependency checks
    Test-JavaPrerequisite
    Test-MavenPrerequisite
    Test-JavaFXPrerequisite

    Write-Host ""

    if (-not $SkipBuild) {
        Invoke-Build
    } else {
        Write-LogInfo "Skipping build (requested via -SkipBuild)."
    }

    if ($RunTests -or (-not $SkipBuild -and -not $Force)) {
        Invoke-Tests
    } else {
        Write-LogWarn "Skipping tests (use -RunTests to enforce)."
    }

    Install-SystemLink

    Write-Host ""
    Write-LogOk "Installation complete!"
    Write-LogInfo "Run the simulator: java -jar target\cpu-simulator-$SIMULATOR_VERSION.jar"
    Write-LogInfo "Or via Maven:     mvn javafx:run"
}

Main
