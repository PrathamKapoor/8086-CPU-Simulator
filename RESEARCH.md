# 8086 CPU Simulator — Research Platform

## Abstract

This document describes the architecture, verification framework, and performance model of the 8086 CPU Simulator — an open-source educational and research platform implementing the Intel 8086 instruction set architecture at RTL (Register Transfer Level) with full segment:offset addressing, a simulated EU/BIU pipeline, and interactive visualization.

---

## 1. EU / BIU Architecture

### 1.1 Execution Unit (EU)

The EU performs instruction execution through a lazy micro-operation generation system:

- **Fetch**: `MAR <- IP`, `MDR <- Memory[MAR]`, `IR <- MDR`, `IP <- IP + instruction_length`
- **Decode**: Decode opcode + operand fields from `Instruction` metadata
- **Execute**: Generate N micro-operations based on `Instruction` opcode and addressing mode
- **Write-Back**: Update registers, memory, flags

### 1.2 Bus Interface Unit (BIU)

The BIU manages external bus access:

- **Address Bus**: 20-bit physical address (segment × 16 + offset)
- **Data Bus**: 16-bit word or 8-bit byte transfers
- **Control Bus**: `MEMORY_READ`, `MEMORY_WRITE`, `ALU_ENABLE`, `REGISTER_LOAD`, etc.

### 1.3 Prefetch Queue

The 8086 uses a 6-byte prefetch queue to allow fetch/decode overlap. Our model (`cpu/PrefetchQueue.java`) implements this as a FIFO queue:

```
Queue depth: 0..6
Fetch pulls bytes from queue before accessing memory
Decode consumes bytes from instruction
Write-back does not consume queue bytes
```

---

## 2. Pipeline Overlap Model

Real 8086 has overlapping stages. Our `PerformanceProfiler.java` tracks:

```java
CPI (Cycles Per Instruction) per opcode
Pipeline stall detection (queue empty, bus conflict)
Memory access latency tracking
```

The overlap tracking (`CPU.cycleOverlap()`) measures how many cycles the EU executes while the BIU fetches the next instruction.

---

## 3. Instruction Encoding

Every instruction in `ISA.java` records:

- `opcodeByte`: Primary opcode (e.g., `0x29` for SUB r/m16, r16)
- `OpcodeExtension`: `/r` digit for shared-opcode instructions (e.g., `/4` for ADD, `/6` for DIV)
- `EncodingFormat`: SIMPLE, REG_MEM, REG_IMM, MODRM, FIXED_AX_IMM, etc.
- `OperandType`: DEST/SRC operand types
- `FlagEffect[]`: Which flags are modified
- `ExceptionType`: DIVIDE_BY_ZERO, DIVIDE_OVERFLOW, etc.

The `InstructionParser.java` converts assembly syntax (e.g., `ADD AX, BX`, `SUB [BX+SI+10], AX`) into structured `Instruction` objects with these metadata fields.

---

## 4. Segment:Offset Addressing

Physical address calculation (`CPU.computePhysicalAddress`):

```java
physical = (segment_register_value << 4) + offset  // segment × 16
```

Masked to 20 bits (`0xFFFFF`) per 8086 specification.

Default segment rules:

| Base Register | Default Segment |
|---------------|-----------------|
| BX, SI, DI    | DS              |
| BP            | SS              |
| SP            | SS              |

---

## 5. Performance Characteristics

Measured on simulated 8086 (no real hardware timing):

| Operation | Cycles (avg) | CPI |
|-----------|-------------|-----|
| MOV reg, reg | 2-3 | 2.5 |
| ADD reg, reg | 3 | 3 |
| MUL r/m16    | ~130 (simulated) | 130 |
| DIV r/m16    | ~160 (simulated) | 160 |
| SHL r/m8     | 2 | 2 |
| JMP          | 15 | 15 |
| LOOP         | 5 | 5 |

Note: MUL/DIV cycles are architectural cycle counts, not clock-time measurements.

---

## 6. Verification Framework

The `test/java/verification/` package provides:

- `GoldenReference.java`: Reference results for common instruction sequences
- `VerificationEngine.java`: Compares simulator state against golden reference after execution
- `PipelineTraceVerifier.java`: Verifies fetch/decode/execute overlap matches expected sequence
- `ISACompletenessTest.java`: Confirms all 90+ opcode definitions exist

---

## 7. Educational Applications

This platform supports:

- **Step-by-step instruction execution** with micro-operation visibility
- **Register-level state inspection** (all 16-bit registers + byte access)
- **Memory visualization** with instruction mapping
- **Flag tracking** per instruction with change annotations
- **Segment addressing** visualization
- **Interactive tutorials** with guided predictions and progress tracking

---

## 8. Limitations and Future Work

- **String operations**: Implemented but simplified (no repeat-prefix timing accuracy for large CX values)
- **Interrupt dispatch**: Simplified IVT access; full protected-mode interrupt handling not implemented
- **I/O ports**: Simulated in memory (0x1000-0x10FF); no real device emulation
- **Performance**: All cycle counts are architectural, not measured against physical 8086 timing
- **Pipeline**: Fetch/decode overlap is simulated sequentially; true parallel EU/BIU is not implemented

---

## References

1. Intel 8086 Datasheet (1978) — Instruction encoding, timing, flags
2. Intel 8086 Family User's Manual — EU/BIU architecture, pipeline
3. "The 8086 Microprocessor" by Walter A. Triebel (3rd ed.) — Pipeline behavior
4. "Computer Architecture: A Quantitative Approach" (Hennessy & Patterson) — CPI analysis framework
