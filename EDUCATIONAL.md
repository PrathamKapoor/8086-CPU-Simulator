# 8086 CPU Simulator — Educational Tool Guide

## For Educators

This simulator is designed for nationwide deployment in computer architecture courses. It provides:

- **Full 8086 ISA**: Every instruction students encounter in textbooks
- **Step-by-step execution**: See exactly how each instruction affects registers, flags, memory
- **Micro-operation visibility**: Understand the RTL-level pipeline
- **Segment addressing**: Real physical address calculation (`segment × 16 + offset`)
- **Interactive quizzes**: Built-in assessment with explanation feedback
- **Tutorial mode**: Guided predictions with immediate feedback
- **Performance profiling**: See cycle counts and pipeline overlap

---

## For Students

### How to Use This Simulator

1. **Open the GUI**: Run `run.bat` (Windows) or `run.sh` (Linux/macOS)
2. **Load a demo**: Click "Load Program" or select from the Program Workspace
3. **Step through**: Click "Step" to execute one instruction at a time
4. **Watch flags**: The Flags panel updates after each arithmetic/logic instruction
5. **Check memory**: The Memory Table shows non-zero memory cells with mapped instructions
6. **Read explanations**: Click the "ISA Reference" tab for complete instruction documentation
7. **Take quizzes**: Click "Tutorial Mode" for guided practice with predictions

---

## Study Guide

### Beginner (Week 1)
- Learn MOV, ADD, SUB, CMP
- Understand registers AX, BX, CX, DX
- Practice with demo `01_basic_data_transfer.asm`

### Intermediate (Week 2-3)
- Learn arithmetic: INC, DEC, MUL, DIV
- Learn logic: AND, OR, XOR, NOT
- Practice with demo `02_arithmetic.asm` and `10_full_alu_test.asm`
- Understand flags: CF, ZF, SF, OF

### Advanced (Week 4-6)
- Learn control flow: JMP, JZ, JNZ, LOOP, CALL, RET
- Practice with demo `04_control_flow.asm` and `06_stack_procedures.asm`
- Understand segment addressing (`07_segment_addressing.asm`)
- Study pipeline overlap and CPI (`RESEARCH.md`)

---

## Assessment Rubric

| Skill Level | Criteria | Assessment |
|-------------|----------|------------|
| Excellent | Writes multi-instruction programs with loops, stacks, segments | Can complete all 10 demos independently |
| Proficient | Understands flag behavior for all arithmetic operations | Passes Flag Quiz (80%+ score) |
| Developing | Can write basic MOV/ADD/SUB sequences | Completes demo 01 and 02 |
| Beginning | Understands register names and basic data transfer | Completes MOV exercises in Tutorial Mode |

---

## Integration Notes

### Classroom Deployment

The simulator requires:
- Java 21+ (free download from OpenJDK)
- 50 MB disk space
- No installation required (portable JAR)

### National Deployment Scale

This package includes:
- Docker container (`Dockerfile` + `docker-compose.yml`)
- CI/CD pipeline (`.github/workflows/`)
- Cross-platform install scripts (`run.bat`, `run.sh`, `scripts/install.sh`)
- Professional documentation (`README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`)
- Test framework (`mvn test` with JUnit 5)

---

## Key Features for Education

- **RTL Visibility**: Every instruction shows its micro-operation sequence
- **Real-time Updates**: Registers, memory, flags, and buses update instantly
- **Error Reporting**: Invalid instructions (e.g., MOV CS, AX) produce clear error messages
- **Progress Tracking**: Tutorial mode tracks predictions and provides feedback
- **Reference Material**: Complete ISA documentation with syntax, flags, and RTL descriptions
- **Performance Metrics**: See CPI, pipeline overlap, and cycle counts for each instruction
