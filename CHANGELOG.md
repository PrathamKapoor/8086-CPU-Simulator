# Changelog

All notable changes to the **8086 CPU Simulator** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] — 2026-09-12

### Added
- **Full 8086 ISA Implementation** — Complete instruction set architecture including MOV, ADD, SUB, ADC, SBB, MUL, IMUL, DIV, IDIV, SHL, SHR, SAR, ROL, ROR, RCL, RCR, AND, OR, XOR, NOT, TEST, NEG, INC, DEC, CMP, JMP, all conditional jumps (JZ, JNZ, JC, JNC, JO, JNO, JS, JNS, JP, JNP, JL, JGE, JLE, JG, JBE, JA), LOOP variants, PUSH, POP, CALL, RET, INT, IRET, CLC, STC, CMC, CLD, STD, CLI, STI, PUSHF, POPF, CBW, CWD, NOP, HLT.
- **Segment:Offset Addressing** — Full support for CS, DS, SS, ES segment registers with 20-bit physical address calculation (`segment × 16 + offset`).
- **Educational JavaFX GUI** — Real-time register view, memory hex viewer, syntax-highlighted assembly editor, flag visualization (CF, PF, AF, ZF, SF, TF, IF, DF, OF), hover tooltips, instruction reference, and visual data flow diagrams.
- **Pipeline Simulation Layer** — Explicit fetch-decode-execute pipeline with microoperation-level execution logging, step-through mode, and pipeline stall detection.
- **Research Platform Features** — Instruction-level timing estimates, cycle-accurate simulation mode, memory access tracing, and exportable execution logs (JSON / CSV) for external analysis tools.
- **Multi-Stage Docker Build** — Multi-stage Dockerfile using `eclipse-temurin:21-jre-alpine` with non-root user, health checks, and exposed port `8080` for future web dashboard integration.
- **CI/CD Pipeline** (GitHub Actions) — Automated build verification (Maven), JUnit 5 test execution, artifact generation, Docker image build, and Docker Hub publication on tag push.
- **Auto-Release Workflow** — Automatic GitHub Release creation on `v*` tag push with changelog snippet attachment and JAR artifact upload.
- **Professional Installation Scripts** — `scripts/install.sh` (Linux/macOS) and `scripts/install.ps1` (Windows) with prerequisite checks (Java 21+, Maven 3.8+, JavaFX), dependency validation, build automation, and optional desktop shortcut creation.
- **Makefile** — Standard targets: `build`, `test`, `run`, `package`, `docker-build`, `docker-run`, `clean`, `install`, `release-check`.
- **Packaging Manifest** — `MANIFEST.in` controlling source distribution contents, exclusions, and inclusion rules.
- **MIT License** — Explicit open-source license (`LICENSE`) for unrestricted academic and commercial use.
- **Contributing Guidelines** — `CONTRIBUTING.md` with code of conduct, coding standards, branch naming conventions, pull request requirements, and testing expectations.

### Changed
- **Version bumped to 3.0.0** — Major release indicating production stability, full ISA coverage, and research-grade simulation capabilities.
- **Java Source/Target updated to 21** — Leverages modern language features (`Record`, `Pattern Matching`, `Virtual Threads` readiness) and long-term support guarantees.
- **Build System (Maven)** — Shade plugin configured for executable fat JAR (`cpu-simulator-3.0.0.jar`) with manifest entry point (`gui.MainGUI`).
- **JavaFX Plugin** — Updated to `0.0.8` with `mainClass` pointing to the JavaFX application entry.
- **JUnit 5 Integration** — Full `maven-surefire-plugin` (`3.2.5`) configuration with `junit-jupiter` (`5.10.2`) for comprehensive unit and integration testing.

### Fixed
- **Instruction Parser Error Handling** — Improved `InstructionParser` error reporting for malformed assembly syntax with line-level diagnostics.
- **Memory Address Calculation** — Corrected 20-bit physical address overflow handling in `Memory.java` for segment:offset pairs exceeding `0xFFFFF`.
- **ALU Flag Computation** — Verified parity flag (`PF`), auxiliary carry (`AF`), and overflow (`OF`) behavior against Intel 8086 reference documentation.
- **Control Unit State Machine** — Fixed pipeline stall detection logic in `MicroOperationExecutor.java` for multi-cycle instructions (MUL, DIV, IDIV).

### Deprecated
- None in 3.0.0.

---

## [2.x] — Historical Releases

Earlier versions provided foundational 8086 CPU simulation capabilities. Key milestones included initial ALU implementation (`ALU.java`), basic instruction parsing, and register file modeling. These releases are superseded by the 3.0.0 production architecture.

---

## Link References

- **Repository:** https://github.com/cpu-simulator/8086
- **Docker Hub:** `cpu-simulator:3.0.0`
- **Documentation:** `README.md`
- **License:** MIT (`LICENSE`)
- **Contributing:** `CONTRIBUTING.md`
