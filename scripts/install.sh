#!/usr/bin/env bash
# ------------------------------------------------------------------
# 8086 CPU Simulator — One-Click Linux / macOS Installation Script
# Version: 3.0.0
# ------------------------------------------------------------------
# Usage:
#   chmod +x scripts/install.sh
#   ./scripts/install.sh
# ------------------------------------------------------------------

set -euo pipefail

# ------------------------------------------------------------------
# Configuration
# ------------------------------------------------------------------
MIN_JAVA_VERSION=21
MIN_MAVEN_VERSION="3.8.0"
SIMULATOR_VERSION="3.0.0"
REPO_URL="https://github.com/cpu-simulator/8086"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ------------------------------------------------------------------
# Helper Functions
# ------------------------------------------------------------------
log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

fail() {
    log_error "$*"
    exit 1
}

# ------------------------------------------------------------------
# Dependency Checks
# ------------------------------------------------------------------
check_java() {
    if ! command -v java &>/dev/null; then
        fail "Java not found in PATH. Please install OpenJDK 21 or later."
    fi

    local java_version
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    local major_version
    major_version=$(echo "$java_version" | cut -d. -f1)

    # Handle versions like "21.0.2" or "17"
    if [[ "$major_version" =~ ^[0-9]+$ ]]; then
        if [[ "$major_version" -lt "$MIN_JAVA_VERSION" ]]; then
            fail "Java version $java_version is too old (minimum: $MIN_JAVA_VERSION)."
        fi
    else
        # Try numeric comparison for non-standard strings
        log_warn "Unrecognized Java version string: $java_version. Proceeding anyway."
    fi

    log_ok "Java $java_version detected (>= $MIN_JAVA_VERSION)"
}

check_maven() {
    if ! command -v mvn &>/dev/null; then
        fail "Maven not found in PATH. Please install Maven 3.8+ (https://maven.apache.org/download.cgi)."
    fi

    local mvn_version
    mvn_version=$(mvn -v | grep "Apache Maven" | awk '{print $3}')
    local major minor
    major=$(echo "$mvn_version" | cut -d. -f1)
    minor=$(echo "$mvn_version" | cut -d. -f2)

    # Simple version comparison (3.8.0 -> 3 >= 3, 8 >= 8)
    if [[ "$major" -lt 3 ]]; then
        fail "Maven $mvn_version is too old (minimum: $MIN_MAVEN_VERSION)."
    elif [[ "$major" -eq 3 && "${minor:-0}" -lt 8 ]]; then
        fail "Maven $mvn_version is too old (minimum: $MIN_MAVEN_VERSION)."
    fi

    log_ok "Maven $mvn_version detected (>= $MIN_MAVEN_VERSION)"
}

check_javafx() {
    # JavaFX is bundled via Maven dependencies in this project.
    # We verify that the build can resolve javafx-controls.
    if [[ -f "pom.xml" ]]; then
        if grep -q "javafx" pom.xml; then
            log_ok "JavaFX dependency declared in pom.xml"
        else
            log_warn "No JavaFX dependency found in pom.xml"
        fi
    else
        log_warn "pom.xml not found in current directory"
    fi
}

check_os() {
    case "$(uname -s)" in
        Linux*)     log_ok "OS: Linux";;
        Darwin*)    log_ok "OS: macOS";;
        CYGWIN*|MINGW*|MSYS*)
            log_warn "Detected Windows-compatible shell. Ensure prerequisites are available."
            ;;
        *)          log_warn "Unknown OS: $(uname -s)";;
    esac
}

# ------------------------------------------------------------------
# Build & Install
# ------------------------------------------------------------------
build_project() {
    log_info "Building 8086 CPU Simulator v$SIMULATOR_VERSION ..."
    mvn clean package -B -DskipTests || fail "Maven build failed. See output above."
    log_ok "Build completed successfully."
    log_info "Artifact: $(pwd)/target/cpu-simulator-${SIMULATOR_VERSION}.jar"
}

run_tests() {
    log_info "Running JUnit test suite ..."
    mvn test -B || fail "Tests failed. See output above."
    log_ok "All tests passed."
}

install_system() {
    log_info "Creating system symlink (optional) ..."
    local target_jar
    target_jar="target/cpu-simulator-${SIMULATOR_VERSION}.jar"

    if [[ -f "$target_jar" ]]; then
        # Create a convenience script in /usr/local/bin if writable
        if [[ -w "/usr/local/bin" ]]; then
            ln -sf "$(pwd)/$target_jar" /usr/local/bin/cpu-simulator.jar || true
            log_ok "Symlink created: /usr/local/bin/cpu-simulator.jar"
        else
            log_warn "Cannot write to /usr/local/bin (no sudo). Skipping system symlink."
        fi
    else
        log_warn "Built JAR not found at $target_jar"
    fi
}

# ------------------------------------------------------------------
# Main
# ------------------------------------------------------------------
main() {
    echo "============================================================"
    echo "  8086 CPU Simulator — Installation Script v${SIMULATOR_VERSION}"
    echo "  Repository: ${REPO_URL}"
    echo "============================================================"
    echo ""

    # Change to script's parent directory (project root)
    cd "$(dirname "$0")/.."

    check_os
    check_java
    check_maven
    check_javafx

    echo ""
    read -rp "Run full build with tests? [Y/n]: " choice
    case "${choice:-Y}" in
        [Yy]* )
            check_java
            build_project
            run_tests
            ;;
        [Nn]* )
            log_info "Skipping tests. Running build only."
            build_project
            ;;
        * )
            log_info "Defaulting to build + tests."
            build_project
            run_tests
            ;;
    esac

    install_system

    echo ""
    log_ok "Installation complete!"
    log_info "Run the simulator:  java -jar target/cpu-simulator-${SIMULATOR_VERSION}.jar"
    log_info "Or via Maven:     mvn javafx:run"
}

main "$@"
