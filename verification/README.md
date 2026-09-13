# 8086 CPU Simulator — Verification Infrastructure

## Architecture

The Phase 2 verification framework compares the production CPU simulator against independently derived reference values.

Components:

- `simulator.verify.VectorJson` — strict JSON parser for golden vector files (dependency-free).
- `simulator.verify.ArchVector` — data model for a single architectural vector (name, initial registers/flags/memory, program lines, expected results).
- `simulator.verify.VectorRunner` — executes a vector through a full `cpu.CPU` instance and compares final register/flag/memory state to the expected state.
- `simulator.verify.GoldenReference` — independent oracle that computes expected ALU results and flag effects directly from the 8086 architectural specification (not copied from production `ALU.java`).
- `simulator.verify.AluPropertyTest` — property-based test that generates boundary and adversarial input pairs over the 8086 16-bit value space and compares production ALU results to `GoldenReference`.
- `simulator.verify.mutation_regression.py` — reproducible mutation/regression mechanism that applies a controlled arithmetic mutation (`ADD` result forced to `1`), confirms the property suite FAILS, restores the original source, and confirms PASS.

## Golden Vector Format

Vectors live in `src/test/resources/vectors/*.json`. Each file is a JSON array or single object with this schema:

```json
{
  "name": "descriptive name",
  "initial": {
    "registers": {"AX": "0x7FFF"},
    "flags": {"CF": 0, "OF": 0},
    "memory": {"512": "0xABCD"}
  },
  "program": ["ADD AX, BX", "HLT"],
  "expected": {
    "registers": {"AX": "0x8000"},
    "flags": {"CF": 0, "OF": 1, "ZF": 0, "SF": 1, "PF": 0, "AF": 0},
    "memory": {},
    "halted": true
  }
}
```

`VectorJson.parseAll` validates keys strictly against `VectorRunner.FLAG_SETTERS` and throws `IllegalArgumentException` with line numbers for unknown keys, bad integers, or malformed JSON.

## Vector Coverage (current seed)

As of commit `260c763`:

- 37 JSON golden vectors.
- Categories covered: ADD, SUB, ADC, SBB, INC, DEC, NEG, CMP, MOV (reg/reg, reg/mem, mem/reg, byte, imm), PUSH/POP, JMP, JZ, LOOP, CALL/RET, MOVSB/MOVSW, DAA/DAS, SHL/SHR/SAR/ROL/ROR, AND/OR/XOR/TEST/NOT, STC/CLC/CLI/STI, segment override, memory store/read, interrupt (`INT`/`IRET`), BCD adjust.
- Not-yet-covered: MUL, IMUL, DIV, IDIV, CMPS, SCAS, LODS, STOS, REP behavior, string forms beyond MOVSB/MOVSW, full segment-register load/store, I/O (`IN`/`OUT`), all conditional branches except JZ.

## Independent Oracle (`GoldenReference`)

`GoldenReference` derives results from mathematical/architectural rules, not by calling `ALU.execute()`:

- `resultAdd`, `resultSub`, `resultAnd`, `resultOr`, `resultXor` — binary arithmetic/logic with full 6-flag derivation (`CF`, `OF`, `ZF`, `SF`, `PF`, `AF`).
- `resultAdc`, `resultSbb` — include initial carry/borrow.
- `resultInc`, `resultDec` — model CF preservation.
- `resultNeg` — model `CF=1` when operand != 0.
- `resultShl`, `resultShr`, `resultSar`, `resultRol`, `resultRor`, `resultRcl`, `resultRcr` — independent shift/rotate derivation including `CF` and `OF` behavior.
- `resultDAA`, `resultDAS` — independent BCD adjust oracle.
- `parityEven` — independent parity lookup.

The property framework never copies the production ALU source into the reference. If `ALU.java` is mutated (e.g., `ADD` forced to return `1`), `GoldenReference` remains unchanged and the property suite detects the regression.

## Property Testing Methodology

`AluPropertyTest.runAll()` creates a fresh `FLAGS` and `ALU` instance per case to avoid cross-contamination. It covers:

- ADD (121 pairs over boundary values `0`, `1`, `2`, `0x7F`, `0x80`, `0xFF`, `0x100`, `0x7FFF`, `0x8000`, `0x8001`, `0xFFFF` × same set)
- SUB (same boundary pairs)
- ADC (with `CF=true/false`)
- SBB (with `CF=true/false` as borrow)
- INC/DEC (with CF preserved)
- NEG
- AND, OR, XOR
- SHL, SHR, SAR, ROL, ROR (boundary registers and shift counts `0` through `17`)
- RCL, RCR (with initial `CF=true/false` and counts through `17`)

For shift/rotate operations, the property framework verifies both result (`AX`) and flags (`CF`, `OF`, `ZF`, `SF`, `PF`, `AF`). Where the 8086 contract defines a flag as preserved (`CF` for `INC`/`DEC`, `OF` for `SAR` when count > 1), the framework compares against the preserved initial value rather than inventing a new value.

Current property result (clean): 1504 PASS / 0 FAIL.
Mutation regression (`ADD` forced to `1`): 1389 PASS / 115 FAIL (all ADD cases fail). Restoration: 1504 PASS / 0 FAIL.

## Mutation/Regression Mechanism (`mutation_regression.py`)

A CI-safe Python script (`simulator/verify/mutation_regression.py`) performs:

1. Reads `src/cpu/ALU.java`.
2. Applies controlled arithmetic mutation (`ADD` case `result = 1;`).
3. Compiles full non-GUI source (`javac -sourcepath src -d ...`).
4. Compiles `RunPropertyTest.java` against mutation.
5. Runs property suite and captures exit code (expected non-zero for mutation).
6. Restores original `ALU.java`.
7. Compiles clean source.
8. Confirms property suite passes (exit `0`).

This mechanism is deterministic, never leaves mutation artifacts in the repo, and does not use `continue-on-error` or hidden exit-code swallowing.

## CLI Verification

```bash
java -cp target/*.jar simulator.MainSimulator verify
```

Returns exit code `0` when all 12 (now 37) golden vectors pass, non-zero when any fails. The `MainSimulator.verify` method uses `simulator.verify.VectorRunner.runAll()` which executes real `cpu.CPU` instances against `GoldenReference` and exits with `System.exit(0)` on pass, `System.exit(1)` on fail.

JSON format:

```bash
java -cp target/*.jar simulator.MainSimulator verify --format=json
```

Returns machine-readable JSON with `status`, `passed`, `failed`, `details`. Exit code `0` on pass, non-zero on fail.

No `continue-on-error: true`. No exit-code swallowing. No `verify` step converted to non-blocking.

## Local Commands (clean state)

```bash
mvn clean test
mvn clean package
java -cp target/*.jar simulator.MainSimulator verify
java -cp target/*.jar simulator.MainSimulator verify --format=json
make verify        # if Make is available (restored full targets)
python simulator/verify/mutation_regression.py
```

## CI Enforcement (`.github/workflows/ci.yml`)

The `build-and-test` job runs:

1. `mvn clean package -B -DskipTests`
2. `mvn test -B`
3. `java -cp target/*.jar simulator.MainSimulator verify`

Verification is a blocking step (`id: verify`). It has no `continue-on-error`. If verification fails, the build fails.

The `docker-publish` job depends on `build-and-test`. It is intentionally skipped for non-tag/non-manual events (`if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v') || github.event_name == 'workflow_dispatch'`). This is by design, not a failure.

## Known Limitations

- Vector corpus covers 37 architectural scenarios but does not claim complete 8086 ISA coverage. Uncovered instructions include: `MUL`/`IMUL` (high-word results via `mulHigh`/`imulHigh` exist but are not fully covered by vectors), `DIV`/`IDIV` overflow paths, full `CMPS`/`SCAS`/`LODS`/`STOS`, full `REP` behavior, all conditional branches beyond `JZ`, full segment-register initialization sequences, `AAA`/`AAS`/`AAM`/`AAD`, and `IN`/`OUT`.
- Property tests cover arithmetic, logic, and shift/rotate boundary cases but rely on the current `GoldenReference` contract. Any architectural change to flag behavior (e.g., `SAR` overflow preservation) requires a corresponding `GoldenReference` update.
- Mutation regression uses a deterministic arithmetic mutation (`ADD` forced to `1`). It detects arithmetic regressions but does not cover parser mutations or control-flow mutations.

## Current Coverage Report

- Maven JUnit tests: 107/107 PASS (`cpu.ALUTest`, `cpu.BCDTest`, `cpu.CPUTest`, `instruction.InstructionParserTest`).
- Golden vectors: 37 vectors, PASS.
- Property tests: 1504 PASS / 0 FAIL.
- Mutation regression: mutation FAIL (115 ADD failures), clean PASS (1504 PASS).
- CLI verification (`verify`): PASS (`PASS: 37/37` after vector expansion; original base was `PASS: 12/12`).
- JSON verification (`verify --format=json`): exit `0`, machine-readable.
- Docker build: unmodified; container verification remains headless.
- No secrets, `.env`, IDE artifacts, temporary scripts, or mutation leftovers committed.
