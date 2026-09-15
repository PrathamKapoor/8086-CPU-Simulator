# 8086 CPU Simulator

A JavaFX-based 8086 CPU simulator with a graphical interface, designed for education and experimentation with x86 assembly language.

## Features

- **Full 8086 ISA Support** — MOV, ADD, SUB, AND, OR, XOR, NOT, SHL, SHR, SAR, ROL, ROR, MUL, IMUL, DIV, IDIV, PUSH, POP, CALL, RET, JMP, and all conditional jumps
- **Segment Addressing** — CS, DS, SS, ES segment registers with physical address calculation (segment × 16 + offset)
- **GUI Interface** — JavaFX-based register view, memory viewer, and assembly editor
- **Step-by-Step Execution** — Execute one instruction at a time with full state inspection
- **Flag Visualization** — Real-time display of CF, PF, AF, ZF, SF, TF, IF, DF, OF
- **Assembly Editor** — Syntax-highlighted editor with error reporting
- **Educational Tools** — Hover tooltips, instruction reference, and visual data flow

## Prerequisites

- **Java 21** or later (JDK 21+ to build; JRE 21+ with JavaFX to run the GUI)
- **Maven 3.8+** (to build `target/cpu-simulator.jar` and run tests)
- **Docker** (optional, for the containerized CLI)

## Quick start

Windows:

```bat
.\run.bat examples\demos\04_control_flow.asm
```

Linux/macOS:

```bash
./run.sh examples/demos/04_control_flow.asm
```

With Docker (headless CLI):

```bash
docker compose up --build
```

No argument starts the GUI (`run.bat` / `run.sh` without arguments).
The CLI can also be run directly against compiled classes or the fat JAR:

```bash
java -cp target/cpu-simulator.jar simulator.MainSimulator examples/demos/04_control_flow.asm
```

## Building

```bash
mvn clean package
```

## Phase 3 microarchitecture experiments

The simulator provides a deterministic educational BIU/EU timing model. It is
not a physical 8086 cycle-accuracy claim. Source execution retains the Phase 3
source-token queue model. Phase 4 machine-code execution (for the implemented
codec subset) instead loads actual instruction bytes into `Memory`; the BIU
fetches those bytes and the decoder reconstructs the existing semantic
`Instruction` consumed by the existing ControlUnit. No second execution engine
is used.

`FUNCTIONAL` preserves architectural execution. `SIMPLIFIED_8086` and the
explicitly experimental `EXPERIMENTAL` mode use one deterministic simulation
clock, a single shared bus (`BIU_FETCH` or `EU_MEMORY`), queue starvation,
internal-operation overlap, and post-retirement queue flushing only when the
architectural PC actually changes stream. Typed cycle snapshots drive the
profiler, CLI trace, and JavaFX Microarchitecture Timeline; the GUI keeps no
independent timing state.

```bash
java -cp target/cpu-simulator.jar simulator.MainSimulator examples/sample.asm --timing=simplified-8086 --trace --profile --json
java -cp target/cpu-simulator.jar simulator.MainSimulator --benchmark --json
```

## Phase 4 machine-code tooling

The machine-code layer currently supports a tested, growing 8086 subset:
canonical `MOV` forms, register/immediate arithmetic and logic (`ADD`, `ADC`,
`SUB`, `SBB`, `AND`, `OR`, `XOR`, `CMP`), relative `JMP`, `CALL`, conditional
jumps and loop forms, plus the tested fixed-opcode, flag, adjust, interrupt and
string forms. Unsupported forms fail explicitly; they are never emitted as
invented opcodes or decoded as `NOP`.

```bash
java -cp target/cpu-simulator.jar simulator.MainSimulator --encode "MOV AX, BX" --json
java -cp target/cpu-simulator.jar simulator.MainSimulator --decode "89 D8" --json
java -cp target/cpu-simulator.jar simulator.MainSimulator --disassemble "8B 40 FE" --json
```

All byte-vector provenance and the executable initial corpus are documented in
[`docs/verification/phase-4-machine-code-vectors.md`](docs/verification/phase-4-machine-code-vectors.md).
The codec has explicit stream offsets, exact decoded lengths and typed errors
for unknown/truncated forms. It is an educational 8086 real-mode model and does
not claim compatibility with later x86 extensions.

The fixed benchmark catalog is `compute-heavy`, `memory-heavy`,
`branch-heavy`, `loop-heavy`, `queue-friendly-sequential`,
`queue-hostile-control-flow`, and `mixed-workload`. Canonical source files are
in `benchmark/`; each defines an expected architectural result and is replayed
without wall-clock measurement. Profiler JSON reports only trace-derived
metrics: cycles, retirements/CPI, source tokens fetched/consumed, queue state,
BIU/EU activity, overlap/stalls, memory events, and control-transfer flushes.

## Running

```bash
java -jar target/cpu-simulator.jar
```

Or use the Maven JavaFX plugin:

```bash
mvn javafx:run
```

## Demo Programs

| # | File | Description |
|---|------|-------------|
| 01 | `examples/demos/01_basic_data_transfer.asm` | MOV, LOAD, STORE, XCHG, PUSH/POP |
| 02 | `examples/demos/02_arithmetic.asm` | ADD, SUB, ADC, SBB, INC, DEC, NEG, CMP |
| 03 | `examples/demos/03_logic_shifts.asm` | AND, OR, XOR, NOT, TEST, SHL, SHR, SAR, ROL, ROR |
| 04 | `examples/demos/04_control_flow.asm` | JMP, Jcc, CALL/RET, LOOP |
| 05 | `examples/demos/05_multiply_divide.asm` | MUL, IMUL, DIV, IDIV, CBW, CWD |
| 06 | `examples/demos/06_stack_procedures.asm` | PUSH/POP, CALL/RET, PUSHF/POPF |
| 07 | `examples/demos/07_segment_addressing.asm` | DS, SS, ES segment registers |
| 08 | `examples/demos/08_flags_comprehensive.asm` | All 9 flags behavior |
| 09 | `examples/demos/09_string_operations.asm` | MOVSB, LODSB, STOSB, CMPSB, SCASB |
| 10 | `examples/demos/10_full_alu_test.asm` | Comprehensive ALU test |
| 11 | `examples/demos/11_complex_program.asm` | Sum of array using loops |
| 12 | `examples/demos/12_fibonacci.asm` | Fibonacci sequence computation |
| — | `examples/sample.asm` | Collection of standalone sample programs |

## Architecture

```
┌─────────────────────────────────────────────┐
│                  GUI (JavaFX)               │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ Registers│ │ Memory   │ │ Assembly   │  │
│  │  View    │ │  Viewer  │ │  Editor    │  │
│  └──────────┘ └──────────┘ └────────────┘  │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│                   CPU                       │
│  ┌──────────────────────────────────────┐   │
│  │           Control Unit               │   │
│  │  (instruction fetch & decode)        │   │
│  └──────────────┬───────────────────────┘   │
│                 │                           │
│  ┌──────────────▼───────────────────────┐   │
│  │      MicroOperation Executor        │   │
│  │  (execute decoded instructions)      │   │
│  └──────────────┬───────────────────────┘   │
│                 │                           │
│  ┌──────────────▼───────────────────────┐   │
│  │              ALU                      │   │
│  │  (arithmetic, logic, shifts)         │   │
│  └──────────────────────────────────────┘   │
│                                             │
│  ┌──────────────────────────────────────┐   │
│  │             Memory                   │   │
│  │  (1MB address space, byte-addressed) │   │
│  └──────────────────────────────────────┘   │
│                                             │
│  ┌──────────┐  ┌──────────────────────┐     │
│  │  Flags   │  │  Segment Registers   │     │
│  │ Register │  │  CS DS SS ES         │     │
│  └──────────┘  └──────────────────────┘     │
└─────────────────────────────────────────────┘
```

## Supported Instructions

### Data Transfer
`MOV` · `MOV reg,mem` · `MOV mem,reg` · `MOV reg,imm` · `LOAD` · `STORE` · `XCHG` · `PUSH` · `POP` · `PUSHF` · `POPF` · `LEA` · `LDS` · `LES`

### Arithmetic
`ADD` · `ADC` · `SUB` · `SBB` · `INC` · `DEC` · `NEG` · `CMP` · `MUL` · `IMUL` · `DIV` · `IDIV` · `CBW` · `CWD` · `DAA` · `DAS` · `AAA` · `AAS` · `AAM` · `AAD`

### Bit Manipulation
`AND` · `OR` · `XOR` · `NOT` · `TEST` · `SHL`/`SAL` · `SHR` · `SAR` · `ROL` · `ROR` · `RCL` · `RCR`

### Control Flow
`JMP` · `JE`/`JZ` · `JNE`/`JNZ` · `JA`/`JNBE` · `JAE`/`JNB` · `JB`/`JNAE` · `JBE`/`JNA` · `JG`/`JNLE` · `JGE`/`JNL` · `JL`· `JNGE` · `JLE`/`JNG` · `JC` · `JNC` · `JO` · `JNO` · `JS` · `JNS` · `JP` · `JNP` · `LOOP` · `LOOPE` · `LOOPNE` · `CALL` · `RET` · `INT` · `INTO` · `IRET`

### I/O
`IN AL/AX, imm8/DX` · `OUT imm8/DX, AL/AX` (simulated ports backed by memory-mapped I/O region)

### Modeling status (authoritative source: `isa.ISA.statusOf`)
Every instruction the parser accepts has a documented execution status.
`SUPPORTED` = full 8086 architectural effect. `PARTIAL` = parses and retires,
but the effect is scoped by the single-CPU model — the execution trace always
states the scope, so nothing fails silently.

| Instruction | Status | Note |
|---|---|---|
| All of the above except below | SUPPORTED | Includes `DAA`/`DAS` (full Intel flag semantics) and `INTO` (traps to IVT type 4 iff OF=1) |
| `WAIT` | PARTIAL | No x87 coprocessor modeled; retires with no effect |
| `LOCK` | PARTIAL | Single CPU, no external bus master; prefix retires with no effect |
| `ESC` | PARTIAL | No external coprocessor modeled; retires with no effect |
| `REP`/`REPE`/`REPNE` standalone | PARTIAL | Only meaningful as a prefix on a string instruction (`REP MOVSB` has full effect) |

### String Operations
`MOVSB` · `MOVSW` · `CMPSB` · `CMPSW` · `SCASB` · `SCASW` · `LODSB` · `LODSW` · `STOSB` · `STOSW`

### Flag Operations
`CLC` · `STC` · `CMC` · `CLD` · `STD` · `CLI` · `STI`

### Segment
`MOV seg,reg` · `MOV reg,seg` · `PUSH seg` · `POP seg`

## File Structure

```
cpu-simulator/
├── src/
│   ├── bus/            # AddressBus, DataBus, ControlBus, ControlSignal
│   ├── clock/          # Clock (cycle counter)
│   ├── cpu/            # CPU, ControlUnit, ALU, InstructionDecoder
│   │   └── registers/  # AX..DI, CS/DS/SS/ES, PC/IR/MAR/MDR, FLAGS, Register
│   ├── gui/            # MainGUI (JavaFX dashboard), CpuArchitectureView, Launcher
│   ├── instruction/    # Instruction, InstructionParser, InstructionFormat, Opcode
│   ├── isa/            # ISA catalog + SupportStatus (authoritative impl. status)
│   ├── memory/         # Memory (1MB), MemoryCell
│   ├── microoperation/ # MicroOperation, MicroOperationType, MicroOperationExecutor
│   ├── simulator/      # MainSimulator (headless CLI entry point)
│   ├── utils/          # BinaryConverter, HexConverter, Logger
│   └── test/java/      # ALUTest, CPUTest, InstructionParserTest, BCDTest (JUnit 5)
├── examples/
│   ├── sample.asm
│   └── demos/          # 01..12 demo programs (+09_string_operations, +10_full_alu_test)
├── scripts/            # install.sh, install.ps1
├── .github/workflows/  # ci.yml, release.yml
├── pom.xml             # Maven build (fat JAR: target/cpu-simulator.jar)
├── Dockerfile          # Multi-stage build, headless CLI default
├── docker-compose.yml
├── Makefile
├── run.bat / run.sh    # GUI (no arg) or CLI (with program.asm)
└── README.md
```

## License

MIT License. See [LICENSE](LICENSE) for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -am 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow the existing code style and naming conventions
- Add unit tests for new instructions or ALU operations
- Update this README if adding new features or instructions
- Test all demo programs before merging
