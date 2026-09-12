# Handoff: 8086 CPU Simulator — Subphase Completion

## 1. Current Phase

- **Phase**: Phase 4 (Implementation & Hardening) → now entering user-directed **Phase 1 of the 10/10 roadmap: Correctness closure** (stub elimination).
- **Subphase**: Correctness closure, part 1 — Silent-stub elimination (roadmap items 1–6, 18, 30–32 scoped).
- **Objective**: No advertised instruction may silently do nothing. Implement DAA/DAS/INTO; scope LOCK/WAIT/ESC explicitly; make ISA status authoritative; fix repo honesty defects (Dockerfile jar name, README tree, junk file).
- **Status**: COMPLETED — Zero silent NOPs remain for any parsed opcode. DAA/DAS verified over 2048 exhaustive cases + known answers. INTO verified end-to-end (trap + fall-through). 19/19 programs run clean. Build has 0 errors (non-GUI + GUI).
- **Previous Subphase**: 4.3 — Full build verification (javac clean, 14/14 demos, PUSH/IN-OUT fixes).
- **Next**: Correctness closure part 2 (property-based ALU/flag testing, golden vectors, CI enforcement) OR roadmap Phase 4 (BIU/prefetch) — user's roadmap order says verification infrastructure next (items 11–14, 19–20).
- **Overall Project Phase**: Phase 4 of 6 per old roadmap; superseded by the 39-item 10/10 roadmap (see context below).

---

## 2. Work Completed (All Executed & Verified)

### A. DAA/DAS implemented with true Intel 8086 semantics [EXECUTED, EXHAUSTIVE]
- **Files**: `src/microoperation/MicroOperationExecutor.java` (new `daa()`, `das()`), `src/cpu/ControlUnit.java` (`case DAA/DAS` now wired; old `-> nop()` removed).
- **Semantics**: two-stage adjust; AF from stage 1, CF from stage 2 (sticky: entering CF=1 forces `±60H` and keeps CF=1); SF/ZF/PF recomputed; OF preserved (undefined per Intel — documented in code).
- **Verification**: scratch harness `DaaDasExhaustive.java` (temp dir, NOT in repo): **1024 DAA + 1024 DAS cases** (all AL × AF × CF) vs independent oracle + AH-preservation guard + OF-preservation guard → ALL PASS; 7 published known-answer vectors (e.g., 58+27→85, 59+42→101 w/ carry, 43−29→14, 30−41→89 w/ borrow) → ALL OK.
- **E2E**: temp `bcd_into.asm` confirms DAA/DAS through parser+CPU (`[0x0400]=0x0014` after SUB+DAS+store).

### B. INTO implemented as OF-gated INT 4 [EXECUTED, E2E]
- **Files**: executor `into()` (delegates to existing `int_(4)` iff `flags.isOverflow()`), ControlUnit `case INTO` (removed from the `WAIT, LOCK, ESC, INTO -> nop()` group).
- **E2E** (`bcd_into.asm`, self-contained IVT plant + overflow + handler): OF=0 → falls through (CX marker set, IP advances); OF=1 → pushes FLAGS/CS/IP, IP loaded from `mem[16]`, CS from `mem[18]`, handler runs (DX=`0xBEEF`), HLT. Finals: DX=`0xBEEF`, CX=`0x1111` (trap path skipped overwrite), SP=`0xFFF8` (3 pushes, no IRET — correct).
- **Test-design lesson**: first attempt planted IVT IP=14 while the plant code lived at index 14 → self-clobbering loop (mem[16] overwritten with AX=`0x8000` on re-pass, IP followed). Simulator behaved deterministically; fixed by planting the true handler index (22). If a future INTO test loops, suspect the test's IVT layout first.

### C. WAIT/LOCK/ESC explicitly scoped — no longer silent [EXECUTED]
- **Files**: executor `wait_()/lock_()/esc_()` (NOP_OP with honest RTL text stating scope + `(PARTIAL)` tag), ControlUnit wiring.
- **E2E** (temp `scoped.asm`): traces show `WAIT ; no x87 coprocessor modeled - no effect (PARTIAL)` etc.; clean retire, `Simulation complete.`
- Naming: `esc_()`/`wait_()` trailing underscore follows `int_()` convention (`wait` would clash with `Object.wait`).

### D. Byte-register bug class fixed in AAA/AAS/AAM/AAD/XLAT [EXECUTED, VERIFIED]
- **Discovery**: `reg("AL").output()/.load()` operate on full AX (Register has no byte identities) — old code read AH bits into AL computations and clobbered AH (e.g., AAA on AX=`0x020B` gave `0x0001` instead of `0x0301`).
- **Fix**: converted all 24 occurrences (lines ~1364–1452) to `reg("AX").lowByte()/highByte()/loadLow()/loadHigh()`, matching the convention already used by MOV/string/I-O paths. Verified no other `reg("[A-D][LH]")` uses exist anywhere.
- **Verified values** (scratch `ByteGuard`): AAA `020B→0301` CF/AF=1; AAM `FF19→0205` (AH garbage no longer leaks); AAD `0205→0019`; AAS `07FF→0609` (spec-correct: `(FF−6) AND 0FH = 09`).

### E. ISA is now the authoritative status source (roadmap 5–6, scoped) [EXECUTED]
- **File**: `src/isa/ISA.java` — new `SupportStatus {SUPPORTED, PARTIAL}`, `PARTIAL_NOTES` map, `statusOf()/statusNote()/partialOpcodes()`. Default SUPPORTED; PARTIAL = WAIT, LOCK, ESC, REP, REPE, REPNE (standalone). No third state by design: parser-accepted ⇒ status documented.
- **Surfaced**: GUI ISA Reference tab gained a MODELING STATUS section; README gained the status table; runtime smoke test of `statusOf` prints correct values.
- **NOT done** (deferred): full item-5 refactor (operand-pattern legality gate, timing model, micro-op generator in ISA) — parser still validates locally. Next agent: do NOT claim ISA gates legality; it gates *status*.

### F. Repo honesty fixes (roadmap 18, 30–32) [EXECUTED]
- `Dockerfile:55`: `cpu-simulator-3.0.0.jar` → `cpu-simulator.jar` (matches `pom.xml` `<finalName>`; old name would break the Docker build).
- Deleted `compile_errors.txt` (stale garbage from a broken per-package script).
- `README.md`: real file tree (old tree described nonexistent `src/main/java/com/cpusimulator/...`), Quick start (run.bat/run.sh/docker), truthful prerequisites, I/O section, Modeling status table, INTO added to control-flow list.
- GUI ISA tab status section (see E).

### G. Permanent tests added (for CI; roadmap 11–13 seed)
- **File**: `src/test/java/cpu/BCDTest.java` (JUnit 5): exhaustive DAA/DAS (2048 cases), 6 known vectors, `aaa_preserves_ah`, `aam_uses_al_only`. Uses only APIs verified present. **NOT compilable in this env** (no Jupiter jars, no Maven binary) — logic mirrored 1:1 in the executed scratch harnesses (ByteGuard confirms the two non-exhaustive asserts' expected values, including catching my own `0x09`-vs-`0x05` slip before it entered the repo file — corrected to `0x05`).
- Prior suites (`ALUTest`, `CPUTest`, `InstructionParserTest`) untouched.

---

## 3. Testing and Verification (Actual Commands Executed)

Env: JDK 25.0.2 via full path; no Maven binary; JavaFX 21.0.1 `-win.jar` classifiers for GUI compile; `build/sources.txt` must stay ASCII.

1. Full rebuild (48 non-GUI + 3 GUI): **0 errors** (only pre-existing `unchecked`/deprecation notes).
2. **19/19 programs** print `Simulation complete.` with zero exceptions/parse errors: 14 demos + `sample.asm` + `verify_string_io.asm` + temp `edge.asm` + temp `bcd_into.asm` + temp `scoped.asm`. No regressions from 4.3 fixes.
3. `DaaDasExhaustive`: 2048/2048 + 7/7 known answers PASS.
4. `ByteGuard`: AAA/AAM/AAD/AAS values correct.
5. `IsaStatusSmoke`: status API returns expected values for 7 opcodes.
6. `BCDTest.java`: written, NOT executed here (no JUnit5). Must pass in CI before claiming verification closure.

### NOT executed:
- `mvn clean test/package`, `java -jar`, Docker build/up, GUI launch (same env limits as 4.3).
- Roadmap items 7–17, 19–29 (prefetch/BIU, timing models, vectors infra, property tests, encoding/disassembler, debugger, experiments, web) — explicitly future phases.

---

## 4. Files Modified / Created

### Modified:
- `src/microoperation/MicroOperationExecutor.java` — byte-access fix (24 sites), new `daa()/das()/into()/wait_()/lock_()/esc_()`.
- `src/cpu/ControlUnit.java` — DAA/DAS/INTO/WAIT/LOCK/ESC wiring; REP comment; old NOP group removed.
- `src/isa/ISA.java` — SupportStatus + PARTIAL_NOTES + accessors.
- `src/gui/MainGUI.java` — ISA Reference MODELING STATUS section appended.
- `Dockerfile` — jar name fix.
- `README.md` — tree, quickstart, I/O, status table, INTO.

### Created:
- `src/test/java/cpu/BCDTest.java` — permanent JUnit5 tests (unexecuted here).
- `handoff.md` — this file.

### Deleted:
- `compile_errors.txt`.

### Deliberately NOT in repo (scratch, temp dir):
- `DaaDasExhaustive.java`, `ByteGuard.java`, `IsaStatusSmoke.java`, `bcd_into.asm`, `scoped.asm`, `edge.asm` (+ compiled classes). Recreate from §2 descriptions if needed. Consider promoting `bcd_into.asm` content into `examples/demos/` next phase.

---

## 5. Remaining Silent-NOP Audit (for next agent — verify, don't trust)

After this session, every `nop()` in `ControlUnit.java` is accounted for:
- `case NOP` (line ~329) — legitimate.
- String-op `default` (line ~383) — unreachable via parser (all string opcodes enumerated); defensive.
- IN/OUT `else` branches (lines ~474/487) — unreachable via parser (all 4 FIXED_* forms emitted); defensive.
- Standalone `REP/REPE/REPNE` (line ~510) — documented PARTIAL, comment explains prefix handling.
- `wait_/lock_/esc_` — explicit PARTIAL micro-ops, not `nop()` calls (same NOP_OP type, honest descriptions).

---

## 6. Decisions Made

1. **OF preserved (not cleared) by DAA/DAS**: Intel marks OF undefined; real silicon preserves it; tests assert preservation. Documented in code.
2. **INTO delegates to `int_(4).execute()`** rather than duplicating interrupt logic — one IVT/stack implementation, zero drift.
3. **PARTIAL as first-class executed behavior** (honest trace text) instead of parse-time rejection — keeps the assembler model total while making scope visible where students look (the trace).
4. **ISA gates status, not legality (yet)**: minimal honest slice of roadmap item 5; full semantic-validation refactor explicitly deferred.
5. **JUnit tests added but flagged unexecuted**: CI (with Maven+Jupiter) is the backstop; scratch harnesses executed here mirror them.

---

## 7. Requirements / Constraints (Future Agents Must Preserve)

- [REQUIRED] DAA/DAS two-stage Intel semantics incl. CF stickiness; SF/ZF/PF recomputed; OF preserved; byte access via `lowByte/loadLow` (never `reg("AL").output()/.load()` — that pattern is always a bug in this codebase).
- [REQUIRED] INTO = `int_(4)` iff OF; WAIT/LOCK/ESC retire with PARTIAL trace text; ISA `statusOf` stays the single status source; GUI tab + README table must agree with it.
- [REQUIRED] `build/sources.txt` ASCII; GUI needs `-win.jar` JavaFX classpath; JDK path `C:\Program Files\Java\jdk-25.0.2\bin`.
- [REQUIRED] Dockerfile jar name `cpu-simulator.jar` must match `pom.xml` `<finalName>`.
- [FORBIDDEN] Reintroducing silent NOPs for parsed opcodes; adding opcodes without a status entry; claiming ISA validates legality (it doesn't yet).
- [REQUIRED PRESERVATION] All 4.3 requirements (memory 1MB, OPCODE_MAP, labels, SP init, PUSH/IN-OUT formats) still hold — 19/19 suite proves it.

### Key commands:
- Rebuild: `& "C:\Program Files\Java\jdk-25.0.2\bin\javac.exe" -encoding UTF-8 -d "build\classes" -sourcepath "src" "@build\sources.txt"`
- All-demos loop + extras (see §3 item 2 pattern); DAA/DAS exhaustive via recreated scratch harness or `mvn test` (BCDTest) when Maven exists.

---

## 8. Verification Status

| Component | Status |
|---|---|
| DAA/DAS semantics | VERIFIED (2048 exhaustive + 7 known answers, executed) |
| INTO trap + fall-through | VERIFIED (E2E, executed) |
| WAIT/LOCK/ESC explicit scope | VERIFIED (E2E trace, executed) |
| AAA/AAS/AAM/AAD byte correctness | VERIFIED (executed values) |
| ISA status API | VERIFIED (runtime smoke) |
| 19/19 programs | VERIFIED (executed, 0 failures) |
| Full javac build (51 files) | VERIFIED (0 errors) |
| BCDTest in CI | UNKNOWN (written, needs Maven+Jupiter) |
| mvn/Docker/GUI-launch | UNKNOWN (env limits) |
| Roadmap items 7–29 | NOT STARTED (user's Phase 2+ per roadmap order: verification infra first) |

---

## 9. Next Agent: Concrete Instructions

Per the user's roadmap order (§38: Phase 1 correctness → Phase 2 ISA authority → Phase 3 verification infra), this session finished Phase 1's core. Recommended next:
1. Get Maven running (install binary or use Docker `maven:3.9.6-eclipse-temurin-21` image) → `mvn clean test` must pass incl. new `BCDTest`; `mvn package` → smoke `java -jar`.
2. Then roadmap items 11–14: golden ISA vectors (start JSON or Java), property-based ALU/flag tests, headless `verify` command (item 20 is cheap: wrap the 19-program loop + JUnit + BCD vectors into one report).
3. Then item 19: CI that enforces all of the above (install JDK+Maven in Actions, fail on any demo failure).
4. Do NOT start prefetch/BIU (items 7–10) before verification infra exists — roadmap order matters.
5. Read the user's 39-item message as the standing product spec; this handoff + prior (4.3) handoffs are the state record.
