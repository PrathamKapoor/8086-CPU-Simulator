# Contributing to the 8086 CPU Simulator

First, thank you for considering a contribution. This project is designed to serve both educational and research purposes — every improvement to accuracy, clarity, or usability directly benefits students, educators, and researchers studying the 8086 architecture.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [How to Contribute](#how-to-contribute)
3. [Development Environment](#development-environment)
4. [Coding Standards](#coding-standards)
5. [Branch Naming Convention](#branch-naming-convention)
6. [Testing Requirements](#testing-requirements)
7. [Pull Request Process](#pull-request-process)
8. [Release Cycle](#release-cycle)
9. [Questions & Support](#questions--support)

---

## Code of Conduct

We expect all contributors to uphold a professional and respectful environment. By participating, you agree to:

- Treat all participants with respect and courtesy.
- Provide constructive, evidence-based feedback on code and design.
- Accept decisions made by maintainers when consensus cannot be reached.
- Focus criticism on code, architecture, or outcomes — never on individuals.
- Report inappropriate behavior to the maintainers via the repository issue tracker or email (if available).

Violations of this code may result in removal of contributions without further explanation.

---

## How to Contribute

### Types of Contributions

- **Bug Fixes** — Incorrect ALU results, instruction decoding errors, memory addressing bugs, or GUI rendering issues.
- **Feature Additions** — New instructions, enhanced pipeline visualization, export formats (JSON/CSV), or web dashboard integration.
- **Documentation** — Corrections or expansions to `README.md`, `CHANGELOG.md`, inline code comments, or architectural diagrams.
- **Testing** — New JUnit 5 test cases covering edge cases, integration tests, or performance benchmarks.
- **Infrastructure** — Improvements to Docker builds, CI/CD pipelines (`.github/workflows`), installation scripts, or packaging.

### Before Starting

1. Check the [open issues](https://github.com/cpu-simulator/8086/issues) to avoid duplicate work.
2. For significant changes (new instructions, architectural modifications), open a discussion issue first.
3. Confirm your development environment meets prerequisites (see below).

---

## Development Environment

### Prerequisites

| Component | Minimum Version | Notes |
|-----------|----------------|-------|
| Java Development Kit | 21 (LTS) | `temurin` or `openjdk` recommended |
| Apache Maven | 3.8.0 | Build automation |
| JavaFX SDK / Dependency | 21.0.2 | Declared in `pom.xml`; resolved automatically |
| Git | 2.30+ | Source control |
| Docker (optional) | 24.0+ | Multi-stage build testing |

### Quick Setup (Linux / macOS)

```bash
# Clone and verify prerequisites
chmod +x scripts/install.sh
./scripts/install.sh

# Or manually:
git clone https://github.com/cpu-simulator/8086.git
cd 8086
git checkout -b feature/your-feature-name
```

### Quick Setup (Windows — PowerShell)

```powershell
# Clone and verify prerequisites
.\scripts\install.ps1 -RunTests

# Or manually:
git clone https://github.com/cpu-simulator/8086.git
cd 8086
git checkout -b feature/your-feature-name
```

---

## Coding Standards

### General Principles

1. **Accuracy First** — This is an architecture simulator. All instruction behaviors must match the Intel 8086 reference manual. When in doubt, verify against primary sources.
2. **Clarity Over Cleverness** — Code should be readable by students. Avoid unnecessary abstraction layers or obfuscated algorithms.
3. **Consistency** — Follow the existing patterns in `CPU.java`, `ALU.java`, `InstructionParser.java`, and the GUI classes (`CpuArchitectureView.java`, `MainGUI.java`).
4. **Self-Documenting Names** — Variable and method names should describe their purpose without requiring comments. Comments should explain *why*, not *what*.

### Java-Specific Rules

- **Source Encoding:** UTF-8 (`project.build.sourceEncoding` in `pom.xml`).
- **Line Length:** 120 characters maximum.
- **Indentation:** 4 spaces (no tabs).
- **Braces:** Always use braces (`{}`) even for single-line blocks.
- **Imports:** Explicit imports only (`import java.util.*` is forbidden). Organize imports alphabetically.
- **Access Modifiers:** Explicit (`public`, `private`, `protected`) — never rely on package-default visibility.

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Class | PascalCase | `InstructionDecoder`, `MicroOperationExecutor` |
| Method | camelCase | `executeMicroOperation()`, `decodeInstruction()` |
| Variable | camelCase | `instructionPointer`, `segmentRegister` |
| Constant | UPPER_SNAKE_CASE | `MAX_MEMORY_SIZE`, `DEFAULT_JAVA_OPTS` |
| Package | lowercase, dot-separated | `gui`, `cpu.registers`, `instruction` |

### Error Handling

- Use checked exceptions (`Exception`) only when the caller must respond differently based on the error type.
- Use unchecked exceptions (`RuntimeException`) for programming errors (e.g., invalid state, null arguments that violate preconditions).
- Log at appropriate levels using the project's `Logger.java` utility (`DEBUG` for detailed execution traces, `INFO` for state changes, `ERROR` for unrecoverable conditions).

---

## Branch Naming Convention

Branches must follow this pattern:

```
<category>/<short-description>
```

Categories:

- `feature/` — New functionality, instructions, or GUI enhancements.
- `bugfix/` — Corrections to existing behavior.
- `refactor/` — Internal restructuring with no external behavior change.
- `docs/` — Documentation updates only.
- `infrastructure/` — CI/CD, Docker, build scripts, or packaging.
- `research/` — Experimental or analytical features.
- `test/` — New or expanded test coverage.

Examples:

```
feature/add-cbw-instruction
bugfix/fix-parity-flag-computation
docs/update-install-instructions
infrastructure/update-ci-pipeline
```

---

## Testing Requirements

Every pull request that introduces or modifies behavior must include tests.

### Unit Tests (`src/test/java/`)

- Use JUnit 5 (`org.junit.jupiter`).
- Test class names must end with `Test` (`ALUTest.java`, `InstructionParserTest.java`).
- Each public method in core classes (`ALU`, `CPU`, `InstructionParser`, `Memory`, `MicroOperationExecutor`) should have at least one positive and one negative test case.
- Mock external dependencies only when necessary; prefer integration-style tests for architecture accuracy.

### Integration Tests

- Test full instruction cycles: parse → decode → execute → verify state change.
- Cover edge cases: maximum/minimum register values (`0xFFFF`, `0x0000`), overflow conditions, segment boundary crossings (`segment:offset = 0xFFFF:0xFFFF` → physical `0xFFFFF`), and flag transitions.

### Running Tests

```bash
# Full suite
make test
# Or directly
mvn test -B

# Specific test class
mvn test -Dtest=ALUTest

# Verbose output
mvn test -B -DtrimStackTrace=false
```

### Coverage Expectations

- New code: minimum 80% line coverage.
- Critical paths (`ALU.java`, `InstructionParser.java`, `MicroOperationExecutor.java`): minimum 90% line coverage.

---

## Pull Request Process

1. **Fork and Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Develop and Test**
   - Make focused commits with clear messages.
   - Ensure `make test` passes locally.
   - Verify `make package` produces a valid fat JAR (`target/cpu-simulator-3.0.0.jar`).

3. **Update Documentation**
   - Update `CHANGELOG.md` under the `[Unreleased]` section.
   - Update `README.md` if new instructions, features, or usage patterns are added.
   - Update inline documentation for public APIs.

4. **Run Local Validation**
   ```bash
   make clean
   make build
   make test
   make package
   make release-check
   ```

5. **Submit Pull Request**
   - Fill out the PR template (if available) or include:
     - **Summary:** What changed and why.
     - **Testing:** How the change was verified.
     - **References:** Related issue numbers (`Fixes #42`, `Closes #55`).
   - Keep the PR focused. One logical change per PR.
   - Respond promptly to review feedback.

### Review Criteria

Maintainers will evaluate:

- **Correctness:** Does the code match 8086 architectural behavior?
- **Clarity:** Can a student understand this in under 5 minutes?
- **Testing:** Are positive, negative, and edge cases covered?
- **Style:** Does the code follow the conventions above?
- **Performance:** Does the change significantly degrade simulation speed?
- **Security:** No secrets, no unsafe file operations, no hardcoded credentials.

---

## Release Cycle

Releases follow [Semantic Versioning](https://semver.org/):

- **MAJOR (`X.0.0`)** — Breaking architectural changes, new full ISA releases, or major GUI redesigns.
- **MINOR (`x.Y.0`)** — New instructions, features, or significant documentation updates (backward compatible).
- **PATCH (`x.y.Z`)** — Bug fixes, performance improvements, or documentation corrections.

Release artifacts include:

- Tagged GitHub Release (`v3.0.0`)
- Fat JAR (`cpu-simulator-3.0.0.jar`)
- Docker image (`cpu-simulator:3.0.0`)
- Updated `VERSION`, `CHANGELOG.md`, and `MANIFEST.in`

---

## Questions & Support

- For usage questions: open an issue labeled `question`.
- For bugs: open an issue labeled `bug` with a minimal reproduction (assembly file or JUnit test case).
- For feature proposals: open an issue labeled `enhancement` with a clear description of the expected behavior and use case.

Before opening a new issue, please search existing open and closed issues.

---

## License

By contributing, you agree that your contributions will be licensed under the same MIT License that covers the project (`LICENSE`).

---

Thank you for contributing to the 8086 CPU Simulator. Your work helps make computer architecture education more accessible and accurate.
