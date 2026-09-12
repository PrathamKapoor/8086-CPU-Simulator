# ------------------------------------------------------------------
# 8086 CPU Simulator — Production Makefile
# Version: 3.0.0
# ------------------------------------------------------------------
# Usage:
#   make build          — Compile with Maven (skip tests for speed)
#   make test           — Run full JUnit 5 test suite
#   make run            — Launch JavaFX GUI (mvn javafx:run)
#   make package        — Build fat JAR (shade plugin)
#   make docker-build   — Build multi-stage Docker image
#   make docker-run     — Run container with compose
#   make clean          — Remove build artifacts
#   make install        — Run dependency checks + build + install symlink
# ------------------------------------------------------------------

# ------------------------------------------------------------------
# Variables
# ------------------------------------------------------------------
PROJECT   := cpu-simulator
VERSION   := 3.0.0
JAR_FILE  := target/$(PROJECT)-$(VERSION).jar
DOCKER_IMG := cpu-simulator:$(VERSION)

# Maven settings
MVN       := mvn
MVN_OPTS  := -B

# Java settings
JAVA      := java
JAVA_OPTS := -Xms256m -Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication -Dfile.encoding=UTF-8

# ------------------------------------------------------------------
# Default target
# ------------------------------------------------------------------
.PHONY: all
all: build

# ------------------------------------------------------------------
# Build targets
# ------------------------------------------------------------------
.PHONY: build
build:
	@echo "========================================"
	@echo "  Building $(PROJECT) v$(VERSION)"
	@echo "========================================"
	$(MVN) clean compile $(MVN_OPTS)

.PHONY: package
package: build
	@echo "========================================"
	@echo "  Packaging fat JAR (shade plugin)"
	@echo "========================================"
	$(MVN) package $(MVN_OPTS) -DskipTests
	@echo "Artifact: $(JAR_FILE)"

# ------------------------------------------------------------------
# Test targets
# ------------------------------------------------------------------
.PHONY: test
# Run full JUnit 5 suite with Surefire

test:
	@echo "========================================"
	@echo "  Running JUnit 5 Tests"
	@echo "========================================"
	$(MVN) test $(MVN_OPTS)

.PHONY: test-verbose
test-verbose:
	$(MVN) test $(MVN_OPTS) -DtrimStackTrace=false -Dmaven.test.failure.ignore=false

# ------------------------------------------------------------------
# Run targets
# ------------------------------------------------------------------
.PHONY: run
run:
	@echo "========================================"
	@echo "  Launching JavaFX GUI"
	@echo "========================================"
	$(MVN) javafx:run $(MVN_OPTS)

.PHONY: run-jar
run-jar: package
	@echo "========================================"
	@echo "  Running packaged JAR: $(JAR_FILE)"
	@echo "========================================"
	$(JAVA) $(JAVA_OPTS) -jar $(JAR_FILE)

# ------------------------------------------------------------------
# Docker targets
# ------------------------------------------------------------------
.PHONY: docker-build
docker-build:
	@echo "========================================"
	@echo "  Building multi-stage Docker image"
	@echo "========================================"
	docker build -t $(DOCKER_IMG) --target runtime .

.PHONY: docker-run
docker-run:
	@echo "========================================"
	@echo "  Starting Docker Compose stack"
	@echo "========================================"
	docker-compose up --build --detach

.PHONY: docker-stop
docker-stop:
	docker-compose down

# ------------------------------------------------------------------
# Install / deployment targets
# ------------------------------------------------------------------
.PHONY: install
install:
	@echo "========================================"
	@echo "  Running installation script"
	@echo "========================================"
	bash scripts/install.sh

.PHONY: install-windows
install-windows:
	@echo "========================================"
	@echo "  Running Windows PowerShell install"
	@echo "========================================"
	powershell -ExecutionPolicy Bypass -File scripts/install.ps1

# ------------------------------------------------------------------
# Clean targets
# ------------------------------------------------------------------
.PHONY: clean
clean:
	@echo "========================================"
	@echo "  Cleaning build artifacts"
	@echo "========================================"
	$(MVN) clean $(MVN_OPTS)
	rm -rf out/
	rm -f $(JAR_FILE)

.PHONY: clean-all
clean-all: clean
	docker-compose down --rmi all --volumes --remove-orphans || true

# ------------------------------------------------------------------
# Release / packaging targets
# ------------------------------------------------------------------
.PHONY: release-check
release-check: package test
	@echo "========================================"
	@echo "  Release verification: v$(VERSION)"
	@echo "========================================"
	@test -f VERSION || (echo "VERSION file missing"; exit 1)
	@test -f LICENSE || (echo "LICENSE file missing"; exit 1)
	@echo "All release artifacts verified."

.PHONY: version
version:
	@cat VERSION

# ------------------------------------------------------------------
# Help
# ------------------------------------------------------------------
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  build          — Compile sources"
	@echo "  package        — Build fat JAR"
	@echo "  test           — Run JUnit 5 tests"
	@echo "  run            — Launch JavaFX GUI (mvn javafx:run)"
	@echo "  run-jar        — Launch packaged JAR directly"
	@echo "  docker-build   — Build Docker image"
	@echo "  docker-run     — Start compose stack"
	@echo "  docker-stop    — Stop compose stack"
	@echo "  install        — Run Linux/macOS install script"
	@echo "  install-windows — Run Windows install script"
	@echo "  clean          — Remove build artifacts"
	@echo "  release-check  — Verify release artifacts"
