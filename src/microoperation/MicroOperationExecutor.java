package microoperation;

import bus.AddressBus;
import bus.ControlBus;
import bus.ControlSignal;
import bus.DataBus;
import clock.Clock;
import cpu.ALU;
import cpu.registers.*;
import isa.ISA;
import memory.Memory;

import java.util.Map;

/**
 * MicroOperationExecutor — factory that constructs MicroOperation lambdas.
 * Supports full 8086 ISA: MOV, ADD, SUB, ADC, SBB, MUL, IMUL, DIV, IDIV,
 * SHL, SHR, SAR, ROL, ROR, RCL, RCR, AND, OR, XOR, NOT, INC, DEC, NEG,
 * CMP, TEST, JMP, JZ, JNZ, JC, JNC, JO, LOOP, PUSH, POP, CALL, RET,
 * INT, IRET, NOP, HLT, CLC, STC, CMC, CLD, STD, CLI, STI.
 */
public class MicroOperationExecutor {

    private final Map<String, Register> regs;
    private final PC         pc;
    private final IR         ir;
    private final MAR        mar;
    private final MDR        mdr;
    private final FLAGS      flags;
    private final ALU        alu;
    private final Memory     memory;
    private final AddressBus addressBus;
    private final DataBus    dataBus;
    private final ControlBus controlBus;
    private final Clock      clock;

    public MicroOperationExecutor(Map<String, Register> regs,
                                  PC pc, IR ir, MAR mar, MDR mdr, FLAGS flags,
                                  ALU alu, Memory memory,
                                  AddressBus addressBus, DataBus dataBus,
                                  ControlBus controlBus, Clock clock) {
        this.regs       = regs;
        this.pc         = pc;
        this.ir         = ir;
        this.mar        = mar;
        this.mdr        = mdr;
        this.flags      = flags;
        this.alu        = alu;
        this.memory     = memory;
        this.addressBus = addressBus;
        this.dataBus    = dataBus;
        this.controlBus = controlBus;
        this.clock      = clock;
    }

    // ============================================================
    //  FETCH PHASE (3 micro-ops)
    // ============================================================

    public MicroOperation fetchStep1_MAR_from_PC() {
        return new MicroOperation(
            MicroOperationType.MAR_LOAD_PC,
            "MAR <- PC",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(pc.output());
                addressBus.drive(mar.output());
            },
            "MAR", "IP", MicroOperation.BusActivity.ADDRESS_BUS
        );
    }

    public MicroOperation fetchStep2_MDR_from_Memory() {
        return new MicroOperation(
            MicroOperationType.MDR_LOAD_MEMORY,
            "MDR <- Memory[MAR]",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                addressBus.drive(mar.output());
                memory.busRead();
                mdr.load(dataBus.read());
            },
            "MDR", "Memory[MAR]", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation fetchStep3_IR_from_MDR_and_PC_inc() {
        return new MicroOperation(
            MicroOperationType.IR_LOAD_MDR,
            "IR <- MDR  ;  PC <- PC+1",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.IR_LOAD);
                controlBus.assert_(ControlSignal.PC_INCREMENT);
                ir.load(mdr.output());
                pc.increment();
                dataBus.clear();
                addressBus.clear();
            },
            "IR", "MDR", MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  DECODE PHASE (1 micro-op)
    // ============================================================

    public MicroOperation decode(String instrText) {
        return new MicroOperation(
            MicroOperationType.DECODE,
            "DECODE: " + instrText,
            () -> { controlBus.clearAll(); alu.setActive(false); },
            "IR", "---", MicroOperation.BusActivity.NONE
        );
    }

    // ============================================================
    //  EXECUTE — MOV reg, reg / reg, imm / reg, mem
    // ============================================================

    public MicroOperation mov_reg_reg(String dst, String src) {
        boolean isDstByte = isByteReg(dst);
        boolean isSrcByte = isByteReg(src);
        if (isDstByte || isSrcByte) {
            return new MicroOperation(
                MicroOperationType.REG_LOAD_REG,
                dst + " <- " + src,
                () -> {
                    controlBus.clearAll();
                    controlBus.assert_(ControlSignal.REGISTER_OUTPUT_ENABLE);
                    controlBus.assert_(ControlSignal.REGISTER_LOAD);
                    if (isDstByte && isSrcByte) {
                        reg(dst).loadLow(reg(src).lowByte());
                    } else if (isDstByte) {
                        reg(dst).loadLow(reg(src).output());
                    } else {
                        reg(dst).load(reg(src).output());
                    }
                    dataBus.drive(reg(src).output());
                },
                dst, src, MicroOperation.BusActivity.DATA_BUS
            );
        }
        return new MicroOperation(
            MicroOperationType.REG_LOAD_REG,
            dst + " <- " + src,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.REGISTER_OUTPUT_ENABLE);
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                dataBus.drive(reg(src).output());
                reg(dst).load(dataBus.read());
            },
            dst, src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation mov_reg_imm(String dst, int imm) {
        boolean isByte = isByteReg(dst);
        return new MicroOperation(
            MicroOperationType.REG_LOAD_IMM,
            dst + " <- " + imm,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                if (isByte) {
                    reg(dst).loadLow(imm);
                } else {
                    reg(dst).load(imm);
                }
                dataBus.drive(imm);
            },
            dst, "#" + imm, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — MOV reg, [mem] / MOV [mem], reg / MOV [mem], imm
    //  Uses effective address from Instruction
    // ============================================================

    public MicroOperation load_effective_addr(int effectiveAddr) {
        return new MicroOperation(
            MicroOperationType.MAR_LOAD_ADDR,
            "MAR <- " + formatAddr(effectiveAddr),
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(effectiveAddr);
                addressBus.drive(effectiveAddr);
            },
            "MAR", "#" + effectiveAddr, MicroOperation.BusActivity.ADDRESS_BUS
        );
    }

    /**
     * Compute effective address with default segment rules.
     * [BP...] -> SS segment, everything else -> DS segment.
     */
    public int computeSegmentedAddr(String baseReg, String indexReg, int displacement) {
        int offset = displacement;
        if (baseReg != null) {
            String upper = baseReg.toUpperCase();
            offset += reg(upper).output();
        }
        if (indexReg != null) {
            offset += reg(indexReg.toUpperCase()).output();
        }
        offset &= 0xFFFF;

        // Default segment: BP uses SS, everything else uses DS
        String segName = (baseReg != null && baseReg.toUpperCase().equals("BP")) ? "SS" : "DS";
        int segment = reg(segName).output();
        return ((segment * 16) + offset) & 0xFFFFF;
    }

    public MicroOperation load_step1_MAR(int addr) {
        return new MicroOperation(
            MicroOperationType.MAR_LOAD_ADDR,
            "MAR <- " + addr,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(addr);
                addressBus.drive(addr);
            },
            "MAR", "#" + addr, MicroOperation.BusActivity.ADDRESS_BUS
        );
    }

    public MicroOperation load_step2_MDR() {
        return new MicroOperation(
            MicroOperationType.MDR_LOAD_MEMORY,
            "MDR <- Memory[MAR]",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                addressBus.drive(mar.output());
                memory.busRead();
                mdr.load(dataBus.read());
            },
            "MDR", "Memory[MAR]", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation load_step3_reg_from_MDR(String dst) {
        return new MicroOperation(
            MicroOperationType.REG_LOAD_MDR,
            dst + " <- MDR",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                dataBus.drive(mdr.output());
                reg(dst).load(mdr.output());
            },
            dst, "MDR", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation load_indirect_step1_MAR(String srcReg) {
        return new MicroOperation(
            MicroOperationType.MAR_LOAD_ADDR,
            "MAR <- [" + srcReg + "]",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(reg(srcReg).output());
                addressBus.drive(reg(srcReg).output());
            },
            "MAR", srcReg, MicroOperation.BusActivity.ADDRESS_BUS
        );
    }

    // ============================================================
    //  EXECUTE — STORE [mem], reg
    // ============================================================

    public MicroOperation store_step1_MAR(int addr) {
        return load_step1_MAR(addr);
    }

    public MicroOperation store_step2_MDR(String src) {
        return new MicroOperation(
            MicroOperationType.MDR_LOAD_REG,
            "MDR <- " + src,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.REGISTER_OUTPUT_ENABLE);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                dataBus.drive(reg(src).output());
                mdr.load(dataBus.read());
            },
            "MDR", src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation store_step3_write() {
        return new MicroOperation(
            MicroOperationType.MEMORY_WRITE_MDR,
            "Memory[MAR] <- MDR",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                addressBus.drive(mar.output());
                dataBus.drive(mdr.output());
                memory.busWrite();
            },
            "Memory[MAR]", "MDR", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    // ============================================================
    //  EXECUTE — ALU binary (ADD SUB ADC SBB AND OR XOR CMP TEST)
    // ============================================================

    public MicroOperation alu_binary(MicroOperationType type, ALU.Operation op,
                                     String dst, String src) {
        String sym = switch (op) {
            case ADD -> "+"; case SUB -> "-"; case ADC -> "+CF";
            case SBB -> "-CF"; case AND -> "&"; case OR -> "|";
            case XOR -> "^"; default -> "?";
        };
        return new MicroOperation(
            type,
            dst + " <- " + dst + " " + sym + " " + src,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                alu.setActive(true);
                int result = alu.execute(op, reg(dst).output(), reg(src).output());
                if (op != ALU.Operation.CMP && op != ALU.Operation.TEST) {
                    reg(dst).load(result);
                }
                dataBus.drive(result);
            },
            dst, src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation alu_imm(MicroOperationType type, ALU.Operation op,
                                  String dst, int imm) {
        String sym = switch (op) {
            case ADD -> "+"; case SUB -> "-"; case ADC -> "+CF";
            case SBB -> "-CF"; case AND -> "&"; case OR -> "|";
            case XOR -> "^"; default -> "?";
        };
        return new MicroOperation(
            type,
            dst + " <- " + dst + " " + sym + " " + imm,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                alu.setActive(true);
                int result = alu.execute(op, reg(dst).output(), imm);
                if (op != ALU.Operation.CMP && op != ALU.Operation.TEST) {
                    reg(dst).load(result);
                }
                dataBus.drive(result);
            },
            dst, "#" + imm, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — ALU unary (INC DEC NEG NOT)
    // ============================================================

    public MicroOperation alu_unary(MicroOperationType type, ALU.Operation op, String dst) {
        String sym = switch (op) {
            case INC -> "++"; case DEC -> "--"; case NEG -> "NEG"; case NOT -> "~";
            default -> "?";
        };
        return new MicroOperation(
            type,
            dst + " <- " + sym + " " + dst,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                alu.setActive(true);
                int result = alu.execute(op, reg(dst).output(), 0);
                reg(dst).load(result);
                dataBus.drive(result);
            },
            dst, dst, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — ALU binary with one memory-resident operand (MDR)
    //  Mirrors alu_binary/alu_imm exactly; the memory side is staged
    //  into MDR by a prior load_effective_addr()+load_step2_MDR() pair,
    //  and (for the RMW dest-is-memory forms) written back by a
    //  subsequent store_step3_write().
    // ============================================================

    public MicroOperation alu_reg_mdr(MicroOperationType type, ALU.Operation op, String dst) {
        return new MicroOperation(
            type,
            dst + " <- " + dst + " op MDR",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                alu.setActive(true);
                int result = alu.execute(op, reg(dst).output(), mdr.output());
                reg(dst).load(result);
                dataBus.drive(result);
            },
            dst, "MDR", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation alu_mdr_reg(MicroOperationType type, ALU.Operation op, String src) {
        return new MicroOperation(
            type,
            "MDR <- MDR op " + src,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                int result = alu.execute(op, mdr.output(), reg(src).output());
                mdr.load(result);
                dataBus.drive(result);
            },
            "MDR", src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation alu_mdr_imm(MicroOperationType type, ALU.Operation op, int imm) {
        return new MicroOperation(
            type,
            "MDR <- MDR op " + imm,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                int result = alu.execute(op, mdr.output(), imm);
                mdr.load(result);
                dataBus.drive(result);
            },
            "MDR", "#" + imm, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation alu_unary_mdr(MicroOperationType type, ALU.Operation op) {
        String sym = switch (op) {
            case INC -> "++"; case DEC -> "--"; case NEG -> "NEG"; case NOT -> "~";
            default -> "?";
        };
        return new MicroOperation(
            type,
            "MDR <- " + sym + " MDR",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                int result = alu.execute(op, mdr.output(), 0);
                mdr.load(result);
                dataBus.drive(result);
            },
            "MDR", "MDR", MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — CMP / TEST
    // ============================================================

    public MicroOperation alu_cmp(String r1, String r2) {
        return new MicroOperation(
            MicroOperationType.ALU_CMP,
            "FLAGS <- CMP(" + r1 + ", " + r2 + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.CMP, reg(r1).output(), reg(r2).output());
                dataBus.clear();
            },
            "FLAGS", r1 + "/" + r2, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_cmp_imm(String r1, int imm) {
        return new MicroOperation(
            MicroOperationType.ALU_CMP,
            "FLAGS <- CMP(" + r1 + ", " + imm + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.CMP, reg(r1).output(), imm);
                dataBus.clear();
            },
            "FLAGS", r1 + "/#" + imm, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_test(String r1, String r2) {
        return new MicroOperation(
            MicroOperationType.ALU_TEST,
            "FLAGS <- TEST(" + r1 + ", " + r2 + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.TEST, reg(r1).output(), reg(r2).output());
                dataBus.clear();
            },
            "FLAGS", r1 + "/" + r2, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_test_imm(String r1, int imm) {
        return new MicroOperation(
            MicroOperationType.ALU_TEST,
            "FLAGS <- TEST(" + r1 + ", " + imm + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.TEST, reg(r1).output(), imm);
                dataBus.clear();
            },
            "FLAGS", r1 + "/#" + imm, MicroOperation.BusActivity.NONE
        );
    }

    // ---- CMP/TEST variants with one memory-resident operand (MDR) ----

    public MicroOperation alu_cmp_reg_mdr(String r1) {
        return new MicroOperation(
            MicroOperationType.ALU_CMP,
            "FLAGS <- CMP(" + r1 + ", MDR)",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.CMP, reg(r1).output(), mdr.output());
                dataBus.clear();
            },
            "FLAGS", r1 + "/MDR", MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_cmp_mdr_reg(String r2) {
        return new MicroOperation(
            MicroOperationType.ALU_CMP,
            "FLAGS <- CMP(MDR, " + r2 + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.CMP, mdr.output(), reg(r2).output());
                dataBus.clear();
            },
            "FLAGS", "MDR/" + r2, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_cmp_mdr_imm(int imm) {
        return new MicroOperation(
            MicroOperationType.ALU_CMP,
            "FLAGS <- CMP(MDR, " + imm + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.CMP, mdr.output(), imm);
                dataBus.clear();
            },
            "FLAGS", "MDR/#" + imm, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_test_reg_mdr(String r1) {
        return new MicroOperation(
            MicroOperationType.ALU_TEST,
            "FLAGS <- TEST(" + r1 + ", MDR)",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.TEST, reg(r1).output(), mdr.output());
                dataBus.clear();
            },
            "FLAGS", r1 + "/MDR", MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_test_mdr_reg(String r2) {
        return new MicroOperation(
            MicroOperationType.ALU_TEST,
            "FLAGS <- TEST(MDR, " + r2 + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.TEST, mdr.output(), reg(r2).output());
                dataBus.clear();
            },
            "FLAGS", "MDR/" + r2, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation alu_test_mdr_imm(int imm) {
        return new MicroOperation(
            MicroOperationType.ALU_TEST,
            "FLAGS <- TEST(MDR, " + imm + ")",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                alu.execute(ALU.Operation.TEST, mdr.output(), imm);
                dataBus.clear();
            },
            "FLAGS", "MDR/#" + imm, MicroOperation.BusActivity.NONE
        );
    }

    // ============================================================
    //  EXECUTE — MUL (AX *= src, result in DX:AX)
    // ============================================================

    public MicroOperation mul_reg(String src) {
        return new MicroOperation(
            MicroOperationType.ALU_MUL,
            "DX:AX <- AX * " + src,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                int axVal = reg("AX").output();
                int srcVal = reg(src).output();
                long fullResult = (long) (axVal & 0xFFFF) * (long) (srcVal & 0xFFFF);
                int lo = (int) (fullResult & 0xFFFF);
                int hi = (int) ((fullResult >> 16) & 0xFFFF);
                reg("AX").load(lo);
                reg("DX").load(hi);
                alu.execute(ALU.Operation.MUL, axVal, srcVal);
                dataBus.drive(lo);
            },
            "DX:AX", "AX*" + src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — IMUL (signed AX *= src, result in DX:AX)
    // ============================================================

    public MicroOperation imul_reg(String src) {
        return new MicroOperation(
            MicroOperationType.ALU_IMUL,
            "DX:AX <- signed AX * " + src,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                int axVal = reg("AX").output();
                int srcVal = reg(src).output();
                long fullResult = (long) (short) axVal * (long) (short) srcVal;
                int lo = (int) (fullResult & 0xFFFF);
                int hi = (int) ((fullResult >> 16) & 0xFFFF);
                reg("AX").load(lo);
                reg("DX").load(hi);
                alu.execute(ALU.Operation.IMUL, axVal, srcVal);
                dataBus.drive(lo);
            },
            "DX:AX", "AX*" + src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — DIV (DX:AX / src, quotient AX, remainder DX)
    // ============================================================

    public MicroOperation div_reg(String src) {
        return new MicroOperation(
            MicroOperationType.ALU_DIV,
            "AX <- DX:AX / " + src + ", DX <- remainder",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                int dxVal = reg("DX").output();
                int axVal = reg("AX").output();
                int divisor = reg(src).output();
                if (divisor == 0) throw new ArithmeticException("Division by zero");
                long dividend = ((long) dxVal << 16) | (axVal & 0xFFFF);
                int quotient = (int) (dividend / (divisor & 0xFFFF));
                int remainder = (int) (dividend % (divisor & 0xFFFF));
                if (quotient > 0xFFFF) {
                    throw new ArithmeticException("Divide overflow");
                }
                reg("AX").load(quotient & 0xFFFF);
                reg("DX").load(remainder & 0xFFFF);
                dataBus.drive(quotient);
            },
            "AX", "DX:AX/" + src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — IDIV (signed DX:AX / src, quotient AX, remainder DX)
    // ============================================================

    public MicroOperation idiv_reg(String src) {
        return new MicroOperation(
            MicroOperationType.ALU_IDIV,
            "AX <- signed DX:AX / " + src + ", DX <- remainder",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                alu.setActive(true);
                int dxVal = (short) reg("DX").output();
                int axVal = (short) reg("AX").output();
                int divisor = (short) reg(src).output();
                if (divisor == 0) throw new ArithmeticException("Division by zero");
                long dividend = ((long) dxVal << 16) | (axVal & 0xFFFF);
                int quotient = (int) (dividend / divisor);
                int remainder = (int) (dividend % divisor);
                if (quotient > 32767 || quotient < -32768) {
                    throw new ArithmeticException("Divide overflow");
                }
                reg("AX").load(quotient & 0xFFFF);
                reg("DX").load(remainder & 0xFFFF);
                dataBus.drive(quotient & 0xFFFF);
            },
            "AX", "DX:AX/" + src, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — SHL/SHR/SAR/ROL/ROR/RCL/RCR
    // ============================================================

    public MicroOperation shift_reg(MicroOperationType type, ALU.Operation op,
                                    String dst, int count) {
        String name = switch (op) {
            case SHL -> "SHL"; case SHR -> "SHR"; case SAR -> "SAR";
            case ROL -> "ROL"; case ROR -> "ROR";
            case RCL -> "RCL"; case RCR -> "RCR";
            default -> "?";
        };
        return new MicroOperation(
            type,
            dst + " <- " + name + " " + dst + ", " + count,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.ALU_ENABLE);
                controlBus.assert_(ControlSignal.REGISTER_LOAD);
                alu.setActive(true);
                int result = alu.execute(op, reg(dst).output(), count);
                reg(dst).load(result);
                dataBus.drive(result);
            },
            dst, "#" + count, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — XCHG with one memory-resident operand (MDR)
    // ============================================================

    public MicroOperation xchg_reg_mdr(String dst) {
        return new MicroOperation(
            MicroOperationType.REG_LOAD_REG,
            dst + " <-> MDR",
            () -> {
                controlBus.clearAll();
                int tmp = reg(dst).output();
                reg(dst).load(mdr.output());
                mdr.load(tmp);
                dataBus.drive(tmp);
            },
            dst, "MDR", MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  EXECUTE — PUSH (SP-=2, Memory[SS:SP] <- reg)
    // ============================================================

    public MicroOperation push_reg(String src) {
        return new MicroOperation(
            MicroOperationType.SP_DECREMENT,
            "SP <- SP-2 ; Mem[SS:SP] <- " + src,
            () -> {
                controlBus.clearAll();
                reg("SP").load(reg("SP").output() - 2);
                int physAddr = computePhysical("SS", reg("SP").output());
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(reg(src).output());
                memory.busWrite();
            },
            "Mem[SS:SP]", src, MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation push_imm(int imm) {
        return new MicroOperation(
            MicroOperationType.SP_DECREMENT,
            "SP <- SP-2 ; Mem[SS:SP] <- " + imm,
            () -> {
                controlBus.clearAll();
                reg("SP").load(reg("SP").output() - 2);
                int physAddr = computePhysical("SS", reg("SP").output());
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(imm);
                memory.busWrite();
            },
            "Mem[SS:SP]", "#" + imm, MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    /** PUSH of a memory operand: the value must already be staged into MDR. */
    public MicroOperation push_mdr() {
        return new MicroOperation(
            MicroOperationType.SP_DECREMENT,
            "SP <- SP-2 ; Mem[SS:SP] <- MDR",
            () -> {
                controlBus.clearAll();
                reg("SP").load(reg("SP").output() - 2);
                int physAddr = computePhysical("SS", reg("SP").output());
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(mdr.output());
                memory.busWrite();
            },
            "Mem[SS:SP]", "MDR", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    // ============================================================
    //  EXECUTE — POP (reg <- Memory[SS:SP], SP+=2)
    // ============================================================

    public MicroOperation pop_reg(String dst) {
        return new MicroOperation(
            MicroOperationType.SP_INCREMENT,
            dst + " <- Mem[SS:SP] ; SP <- SP+2",
            () -> {
                controlBus.clearAll();
                int physAddr = computePhysical("SS", reg("SP").output());
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead();
                mdr.load(dataBus.read());
                reg(dst).load(mdr.output());
                reg("SP").load(reg("SP").output() + 2);
            },
            dst, "Mem[SS:SP]", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    /** POP into MDR (destination is memory): the caller stores MDR afterward. */
    public MicroOperation pop_to_mdr() {
        return new MicroOperation(
            MicroOperationType.SP_INCREMENT,
            "MDR <- Mem[SS:SP] ; SP <- SP+2",
            () -> {
                controlBus.clearAll();
                int physAddr = computePhysical("SS", reg("SP").output());
                controlBus.assert_(ControlSignal.MAR_LOAD);
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead();
                mdr.load(dataBus.read());
                reg("SP").load(reg("SP").output() + 2);
            },
            "MDR", "Mem[SS:SP]", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    // ============================================================
    //  EXECUTE — JMP / conditional jumps
    // ============================================================

    public MicroOperation jmp(int instrIndex) {
        return new MicroOperation(
            MicroOperationType.PC_LOAD_ADDR,
            "PC <- " + instrIndex,
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.PC_LOAD);
                dataBus.drive(instrIndex);
                pc.load(instrIndex);
                addressBus.clear();
                alu.setActive(false);
            },
            "IP", "#" + instrIndex, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation jcc(boolean condition, int instrIndex, String condStr) {
        return new MicroOperation(
            MicroOperationType.PC_LOAD_ADDR,
            "IF " + condStr + ": PC <- " + instrIndex,
            () -> {
                controlBus.clearAll();
                if (condition) {
                    controlBus.assert_(ControlSignal.PC_LOAD);
                    dataBus.drive(instrIndex);
                    pc.load(instrIndex);
                }
                alu.setActive(false);
            },
            "IP", condStr + "?" + instrIndex, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation loop_(int instrIndex) {
        return new MicroOperation(
            MicroOperationType.PC_LOAD_ADDR,
            "CX <- CX-1 ; IF CX!=0: PC <- " + instrIndex,
            () -> {
                controlBus.clearAll();
                reg("CX").decrement();
                if (reg("CX").output() != 0) {
                    controlBus.assert_(ControlSignal.PC_LOAD);
                    dataBus.drive(instrIndex);
                    pc.load(instrIndex);
                }
                alu.setActive(false);
            },
            "IP", "CX?" + instrIndex, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation loopz(int instrIndex) {
        return new MicroOperation(
            MicroOperationType.PC_LOAD_ADDR,
            "CX <- CX-1 ; IF CX!=0 AND ZF=1: PC <- " + instrIndex,
            () -> {
                controlBus.clearAll();
                reg("CX").decrement();
                if (reg("CX").output() != 0 && flags.isZero()) {
                    controlBus.assert_(ControlSignal.PC_LOAD);
                    dataBus.drive(instrIndex);
                    pc.load(instrIndex);
                }
                alu.setActive(false);
            },
            "IP", "CX&ZF?" + instrIndex, MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation loopnz(int instrIndex) {
        return new MicroOperation(
            MicroOperationType.PC_LOAD_ADDR,
            "CX <- CX-1 ; IF CX!=0 AND ZF=0: PC <- " + instrIndex,
            () -> {
                controlBus.clearAll();
                reg("CX").decrement();
                if (reg("CX").output() != 0 && !flags.isZero()) {
                    controlBus.assert_(ControlSignal.PC_LOAD);
                    dataBus.drive(instrIndex);
                    pc.load(instrIndex);
                }
                alu.setActive(false);
            },
            "IP", "CX&!ZF?" + instrIndex, MicroOperation.BusActivity.NONE
        );
    }

    // ============================================================
    //  EXECUTE — CALL / RET
    // ============================================================

    public MicroOperation call(int instrIndex) {
        return new MicroOperation(
            MicroOperationType.SP_DECREMENT,
            "PUSH IP ; PC <- " + instrIndex,
            () -> {
                controlBus.clearAll();
                reg("SP").load(reg("SP").output() - 2);
                int physAddr = computePhysical("SS", reg("SP").output());
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(pc.output());
                memory.busWrite();
                pc.load(instrIndex);
            },
            "IP", "#" + instrIndex, MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation ret() {
        return new MicroOperation(
            MicroOperationType.SP_INCREMENT,
            "POP IP ; SP <- SP+2",
            () -> {
                controlBus.clearAll();
                int physAddr = computePhysical("SS", reg("SP").output());
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead();
                mdr.load(dataBus.read());
                pc.load(mdr.output());
                reg("SP").load(reg("SP").output() + 2);
            },
            "IP", "Mem[SS:SP]", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    /** RET imm16: pop the return address, then discard imm16 extra bytes of arguments. */
    public MicroOperation ret_imm(int imm) {
        return new MicroOperation(
            MicroOperationType.SP_INCREMENT,
            "POP IP ; SP <- SP+2+" + imm,
            () -> {
                controlBus.clearAll();
                int physAddr = computePhysical("SS", reg("SP").output());
                mar.load(physAddr);
                addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead();
                mdr.load(dataBus.read());
                pc.load(mdr.output());
                reg("SP").load((reg("SP").output() + 2 + imm) & 0xFFFF);
            },
            "IP", "Mem[SS:SP]", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    // ============================================================
    //  EXECUTE — INT / IRET
    // ============================================================

    public MicroOperation int_(int vector) {
        return new MicroOperation(
            MicroOperationType.INT_OP,
            "INT " + vector + " ; PUSH FLAGS,CS,IP",
            () -> {
                controlBus.clearAll();
                // Push FLAGS
                reg("SP").load(reg("SP").output() - 2);
                int addr1 = computePhysical("SS", reg("SP").output());
                mar.load(addr1); addressBus.drive(addr1);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(flags.output()); memory.busWrite();
                // Push CS
                reg("SP").load(reg("SP").output() - 2);
                int addr2 = computePhysical("SS", reg("SP").output());
                mar.load(addr2); addressBus.drive(addr2);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(reg("CS").output()); memory.busWrite();
                // Push IP
                reg("SP").load(reg("SP").output() - 2);
                int addr3 = computePhysical("SS", reg("SP").output());
                mar.load(addr3); addressBus.drive(addr3);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(pc.output()); memory.busWrite();
                // Load IVT entry: vector * 4 (CS:IP pair, but simplified to vector*2 for IP)
                int ivtAddr = vector * 4;
                if (ivtAddr + 1 < memory.getSize()) {
                    pc.load(memory.directRead(ivtAddr));
                    reg("CS").load(memory.directRead(ivtAddr + 2));
                }
            },
            "---", "INT " + vector, MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation iret() {
        return new MicroOperation(
            MicroOperationType.IRET_OP,
            "IRET ; POP IP,CS,FLAGS",
            () -> {
                controlBus.clearAll();
                // Pop IP
                int addr1 = computePhysical("SS", reg("SP").output());
                mar.load(addr1); addressBus.drive(addr1);
                controlBus.assert_(ControlSignal.MEMORY_READ); controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead(); mdr.load(dataBus.read()); pc.load(mdr.output());
                reg("SP").load(reg("SP").output() + 2);
                // Pop CS
                int addr2 = computePhysical("SS", reg("SP").output());
                mar.load(addr2); addressBus.drive(addr2);
                controlBus.assert_(ControlSignal.MEMORY_READ); controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead(); mdr.load(dataBus.read()); reg("CS").load(mdr.output());
                reg("SP").load(reg("SP").output() + 2);
                // Pop FLAGS
                int addr3 = computePhysical("SS", reg("SP").output());
                mar.load(addr3); addressBus.drive(addr3);
                controlBus.assert_(ControlSignal.MEMORY_READ); controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead(); mdr.load(dataBus.read()); flags.load(mdr.output());
                reg("SP").load(reg("SP").output() + 2);
            },
            "---", "IRET", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation into() {
        return new MicroOperation(
            MicroOperationType.INT_OP,
            "INTO ; IF OF=1 THEN INT 4 (overflow trap)",
            () -> {
                if (flags.isOverflow()) {
                    int_(4).execute();
                }
            },
            "---", "OF", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    // ============================================================
    //  WAIT / LOCK / ESC — explicitly scoped, NOT silent stubs.
    //  Single-CPU model with no x87 coprocessor and no external bus
    //  master: these decode and retire with no architectural effect.
    //  The RTL text states the scope so traces never lie.
    // ============================================================
    public MicroOperation wait_() {
        return new MicroOperation(
            MicroOperationType.NOP_OP,
            "WAIT ; no x87 coprocessor modeled - no effect (PARTIAL)",
            () -> { controlBus.clearAll(); alu.setActive(false); },
            "---", "---", MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation lock_() {
        return new MicroOperation(
            MicroOperationType.NOP_OP,
            "LOCK ; single CPU, no external bus master - no effect (PARTIAL)",
            () -> { controlBus.clearAll(); alu.setActive(false); },
            "---", "---", MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation esc_() {
        return new MicroOperation(
            MicroOperationType.NOP_OP,
            "ESC ; no external coprocessor modeled - no effect (PARTIAL)",
            () -> { controlBus.clearAll(); alu.setActive(false); },
            "---", "---", MicroOperation.BusActivity.NONE
        );
    }

    // ============================================================
    //  EXECUTE — Flag operations
    // ============================================================

    public MicroOperation flag_clear_cf() {
        return new MicroOperation(MicroOperationType.FLAGS_CLEAR_CF, "CLC",
            () -> { controlBus.clearAll(); flags.setCarry(false); },
            "FLAGS", "CF=0", MicroOperation.BusActivity.NONE);
    }

    public MicroOperation flag_set_cf() {
        return new MicroOperation(MicroOperationType.FLAGS_SET_CF, "STC",
            () -> { controlBus.clearAll(); flags.setCarry(true); },
            "FLAGS", "CF=1", MicroOperation.BusActivity.NONE);
    }

    public MicroOperation flag_complement_cf() {
        return new MicroOperation(MicroOperationType.FLAGS_COMPLEMENT_CF, "CMC",
            () -> { controlBus.clearAll(); flags.setCarry(!flags.isCarry()); },
            "FLAGS", "CF=~CF", MicroOperation.BusActivity.NONE);
    }

    public MicroOperation flag_clear_df() {
        return new MicroOperation(MicroOperationType.FLAGS_CLEAR_DF, "CLD",
            () -> { controlBus.clearAll(); flags.setDirection(false); },
            "FLAGS", "DF=0", MicroOperation.BusActivity.NONE);
    }

    public MicroOperation flag_set_df() {
        return new MicroOperation(MicroOperationType.FLAGS_SET_DF, "STD",
            () -> { controlBus.clearAll(); flags.setDirection(true); },
            "FLAGS", "DF=1", MicroOperation.BusActivity.NONE);
    }

    public MicroOperation flag_clear_if() {
        return new MicroOperation(MicroOperationType.FLAGS_CLEAR_IF, "CLI",
            () -> { controlBus.clearAll(); flags.setInterrupt(false); },
            "FLAGS", "IF=0", MicroOperation.BusActivity.NONE);
    }

    public MicroOperation flag_set_if() {
        return new MicroOperation(MicroOperationType.FLAGS_SET_IF, "STI",
            () -> { controlBus.clearAll(); flags.setInterrupt(true); },
            "FLAGS", "IF=1", MicroOperation.BusActivity.NONE);
    }

    public MicroOperation push_flags() {
        return new MicroOperation(
            MicroOperationType.PUSH_FLAGS,
            "SP <- SP-2 ; Mem[SS:SP] <- FLAGS",
            () -> {
                controlBus.clearAll();
                reg("SP").load(reg("SP").output() - 2);
                int physAddr = computePhysical("SS", reg("SP").output());
                mar.load(physAddr); addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(flags.output()); memory.busWrite();
            },
            "Mem[SS:SP]", "FLAGS", MicroOperation.BusActivity.ADDRESS_AND_DATA);
    }

    public MicroOperation pop_flags() {
        return new MicroOperation(
            MicroOperationType.POP_FLAGS,
            "FLAGS <- Mem[SS:SP] ; SP <- SP+2",
            () -> {
                controlBus.clearAll();
                int physAddr = computePhysical("SS", reg("SP").output());
                mar.load(physAddr); addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead(); mdr.load(dataBus.read());
                flags.load(mdr.output());
                reg("SP").load(reg("SP").output() + 2);
            },
            "FLAGS", "Mem[SS:SP]", MicroOperation.BusActivity.ADDRESS_AND_DATA);
    }

    // ============================================================
    //  EXECUTE — Segment register operations
    // ============================================================

    public MicroOperation push_seg(String segReg) {
        return new MicroOperation(
            MicroOperationType.PUSH_SEG,
            "SP <- SP-2 ; Mem[SS:SP] <- " + segReg,
            () -> {
                controlBus.clearAll();
                reg("SP").load(reg("SP").output() - 2);
                int physAddr = computePhysical("SS", reg("SP").output());
                mar.load(physAddr); addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_WRITE);
                dataBus.drive(reg(segReg).output()); memory.busWrite();
            },
            "Mem[SS:SP]", segReg, MicroOperation.BusActivity.ADDRESS_AND_DATA);
    }

    public MicroOperation pop_seg(String segReg) {
        return new MicroOperation(
            MicroOperationType.POP_SEG,
            segReg + " <- Mem[SS:SP] ; SP <- SP+2",
            () -> {
                controlBus.clearAll();
                int physAddr = computePhysical("SS", reg("SP").output());
                mar.load(physAddr); addressBus.drive(physAddr);
                controlBus.assert_(ControlSignal.MEMORY_READ);
                controlBus.assert_(ControlSignal.MDR_LOAD);
                memory.busRead(); mdr.load(dataBus.read());
                reg(segReg).load(mdr.output());
                reg("SP").load(reg("SP").output() + 2);
            },
            segReg, "Mem[SS:SP]", MicroOperation.BusActivity.ADDRESS_AND_DATA);
    }

    // ============================================================
    //  EXECUTE — NOP / HLT
    // ============================================================

    public MicroOperation nop() {
        return new MicroOperation(
            MicroOperationType.NOP_OP,
            "NOP",
            () -> { controlBus.clearAll(); alu.setActive(false); },
            "---", "---", MicroOperation.BusActivity.NONE
        );
    }

    public MicroOperation halt() {
        return new MicroOperation(
            MicroOperationType.HALT,
            "HALT — execution stopped",
            () -> {
                controlBus.clearAll();
                controlBus.assert_(ControlSignal.HALT);
                dataBus.clear();
                addressBus.clear();
                alu.setActive(false);
            },
            "---", "---", MicroOperation.BusActivity.NONE
        );
    }

    // ============================================================
    //  String Operations
    // ============================================================

    public MicroOperation movsb() {
        return new MicroOperation(
            MicroOperationType.STRING_MOVS,
            "MOVSB: [DS:SI] -> [ES:DI], SI++, DI++",
            () -> {
                controlBus.clearAll();
                int srcAddr = computePhysical("DS", reg("SI").output());
                int dstAddr = computePhysical("ES", reg("DI").output());
                int byteVal = memory.directRead(srcAddr) & 0xFF;
                memory.directWrite(dstAddr, byteVal);
                int delta = flags.isDirection() ? -1 : 1;
                reg("SI").load((reg("SI").output() + delta) & 0xFFFF);
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "ES:DI", "DS:SI", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation movsw() {
        return new MicroOperation(
            MicroOperationType.STRING_MOVS,
            "MOVSW: [DS:SI] -> [ES:DI], SI+=2, DI+=2",
            () -> {
                controlBus.clearAll();
                int srcAddr = computePhysical("DS", reg("SI").output());
                int dstAddr = computePhysical("ES", reg("DI").output());
                int low = memory.directRead(srcAddr) & 0xFF;
                int high = memory.directRead(srcAddr + 1) & 0xFF;
                int wordVal = low | (high << 8);
                memory.directWrite(dstAddr, wordVal & 0xFF);
                memory.directWrite(dstAddr + 1, (wordVal >> 8) & 0xFF);
                int delta = flags.isDirection() ? -2 : 2;
                reg("SI").load((reg("SI").output() + delta) & 0xFFFF);
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "ES:DI", "DS:SI", MicroOperation.BusActivity.ADDRESS_AND_DATA
        );
    }

    public MicroOperation lodsb() {
        return new MicroOperation(
            MicroOperationType.STRING_LODS,
            "LODSB: AL <- [DS:SI], SI updates",
            () -> {
                controlBus.clearAll();
                int srcAddr = computePhysical("DS", reg("SI").output());
                int byteVal = memory.directRead(srcAddr) & 0xFF;
                reg("AX").loadLow(byteVal);
                int delta = flags.isDirection() ? -1 : 1;
                reg("SI").load((reg("SI").output() + delta) & 0xFFFF);
            },
            "AX", "DS:SI", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation lodsw() {
        return new MicroOperation(
            MicroOperationType.STRING_LODS,
            "LODSW: AX <- [DS:SI], SI+=2",
            () -> {
                controlBus.clearAll();
                int srcAddr = computePhysical("DS", reg("SI").output());
                int low = memory.directRead(srcAddr) & 0xFF;
                int high = memory.directRead(srcAddr + 1) & 0xFF;
                int wordVal = low | (high << 8);
                reg("AX").load(wordVal);
                int delta = flags.isDirection() ? -2 : 2;
                reg("SI").load((reg("SI").output() + delta) & 0xFFFF);
            },
            "AX", "DS:SI", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation stosb() {
        return new MicroOperation(
            MicroOperationType.STRING_STOS,
            "STOSB: [ES:DI] <- AL, DI updates",
            () -> {
                controlBus.clearAll();
                int dstAddr = computePhysical("ES", reg("DI").output());
                int byteVal = reg("AX").lowByte();
                memory.directWrite(dstAddr, byteVal);
                int delta = flags.isDirection() ? -1 : 1;
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "ES:DI", "AX", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation stosw() {
        return new MicroOperation(
            MicroOperationType.STRING_STOS,
            "STOSW: [ES:DI] <- AX, DI+=2",
            () -> {
                controlBus.clearAll();
                int dstAddr = computePhysical("ES", reg("DI").output());
                int wordVal = reg("AX").output();
                memory.directWrite(dstAddr, wordVal & 0xFF);
                memory.directWrite(dstAddr + 1, (wordVal >> 8) & 0xFF);
                int delta = flags.isDirection() ? -2 : 2;
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "ES:DI", "AX", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation cmpsb() {
        return new MicroOperation(
            MicroOperationType.STRING_CMPS,
            "CMPSB: [DS:SI] - [ES:DI], SI++, DI++, flags",
            () -> {
                controlBus.clearAll();
                int srcAddr = computePhysical("DS", reg("SI").output());
                int dstAddr = computePhysical("ES", reg("DI").output());
                int srcByte = memory.directRead(srcAddr) & 0xFF;
                int dstByte = memory.directRead(dstAddr) & 0xFF;
                int diff = srcByte - dstByte;
                flags.setZero(diff == 0);
                flags.setSign((diff & 0x8000) != 0);
                int delta = flags.isDirection() ? -1 : 1;
                reg("SI").load((reg("SI").output() + delta) & 0xFFFF);
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "FLAGS", "DS:SI/ES:DI", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation cmpsw() {
        return new MicroOperation(
            MicroOperationType.STRING_CMPS,
            "CMPSW: [DS:SI] - [ES:DI], SI+=2, DI+=2, flags",
            () -> {
                controlBus.clearAll();
                int srcAddr = computePhysical("DS", reg("SI").output());
                int dstAddr = computePhysical("ES", reg("DI").output());
                int lowSrc = memory.directRead(srcAddr) & 0xFF;
                int highSrc = memory.directRead(srcAddr + 1) & 0xFF;
                int srcWord = lowSrc | (highSrc << 8);
                int lowDst = memory.directRead(dstAddr) & 0xFF;
                int highDst = memory.directRead(dstAddr + 1) & 0xFF;
                int dstWord = lowDst | (highDst << 8);
                int diff = srcWord - dstWord;
                flags.setZero(diff == 0);
                flags.setSign((diff & 0x8000) != 0);
                int delta = flags.isDirection() ? -2 : 2;
                reg("SI").load((reg("SI").output() + delta) & 0xFFFF);
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "FLAGS", "DS:SI/ES:DI", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation scasb() {
        return new MicroOperation(
            MicroOperationType.STRING_SCAS,
            "SCASB: AL - [ES:DI], DI updates, flags",
            () -> {
                controlBus.clearAll();
                int dstAddr = computePhysical("ES", reg("DI").output());
                int dstByte = memory.directRead(dstAddr) & 0xFF;
                int alByte = reg("AX").lowByte();
                int diff = alByte - dstByte;
                flags.setZero(diff == 0);
                flags.setSign((diff & 0x8000) != 0);
                int delta = flags.isDirection() ? -1 : 1;
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "FLAGS", "ES:DI", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation scasw() {
        return new MicroOperation(
            MicroOperationType.STRING_SCAS,
            "SCASW: AX - [ES:DI], DI+=2, flags",
            () -> {
                controlBus.clearAll();
                int dstAddr = computePhysical("ES", reg("DI").output());
                int lowDst = memory.directRead(dstAddr) & 0xFF;
                int highDst = memory.directRead(dstAddr + 1) & 0xFF;
                int dstWord = lowDst | (highDst << 8);
                int axVal = reg("AX").output();
                int diff = axVal - dstWord;
                flags.setZero(diff == 0);
                flags.setSign((diff & 0x8000) != 0);
                int delta = flags.isDirection() ? -2 : 2;
                reg("DI").load((reg("DI").output() + delta) & 0xFFFF);
            },
            "FLAGS", "ES:DI", MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ============================================================
    //  I/O Operations
    // ============================================================

    public MicroOperation io_read_byte(String regName, int port) {
        return new MicroOperation(
            MicroOperationType.IO_READ_BYTE,
            "IN " + regName + ", port " + port,
            () -> {
                controlBus.clearAll();
                int ioAddr = 0x1000 + (port & 0xFF);
                int value = memory.directRead(ioAddr) & 0xFF;
                reg(regName).loadLow(value);
            },
            regName, "IO[" + port + "]", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation io_read_word(String regName, int port) {
        return new MicroOperation(
            MicroOperationType.IO_READ_WORD,
            "IN " + regName + ", port " + port,
            () -> {
                controlBus.clearAll();
                int ioAddr = 0x1000 + (port & 0xFF);
                int low = memory.directRead(ioAddr) & 0xFF;
                int high = memory.directRead(ioAddr + 1) & 0xFF;
                int value = low | (high << 8);
                reg(regName).load(value);
            },
            regName, "IO[" + port + "]", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation io_read_byte_dx(String regName) {
        return new MicroOperation(
            MicroOperationType.IO_READ_BYTE,
            "IN AL, DX",
            () -> {
                controlBus.clearAll();
                int port = reg("DX").output() & 0xFF;
                int ioAddr = 0x1000 + port;
                int value = memory.directRead(ioAddr) & 0xFF;
                reg(regName).loadLow(value);
            },
            regName, "IO[DX]", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation io_read_word_dx(String regName) {
        return new MicroOperation(
            MicroOperationType.IO_READ_WORD,
            "IN AX, DX",
            () -> {
                controlBus.clearAll();
                int port = reg("DX").output() & 0xFF;
                int ioAddr = 0x1000 + port;
                int low = memory.directRead(ioAddr) & 0xFF;
                int high = memory.directRead(ioAddr + 1) & 0xFF;
                int value = low | (high << 8);
                reg(regName).load(value);
            },
            regName, "IO[DX]", MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation io_write_byte(int port, String regName) {
        return new MicroOperation(
            MicroOperationType.IO_WRITE_BYTE,
            "OUT port " + port + ", " + regName,
            () -> {
                controlBus.clearAll();
                int ioAddr = 0x1000 + (port & 0xFF);
                int value = reg(regName).lowByte();
                memory.directWrite(ioAddr, value);
            },
            "IO[" + port + "]", regName, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation io_write_word(int port, String regName) {
        return new MicroOperation(
            MicroOperationType.IO_WRITE_WORD,
            "OUT port " + port + ", " + regName,
            () -> {
                controlBus.clearAll();
                int ioAddr = 0x1000 + (port & 0xFF);
                int value = reg(regName).output();
                memory.directWrite(ioAddr, value & 0xFF);
                memory.directWrite(ioAddr + 1, (value >> 8) & 0xFF);
            },
            "IO[" + port + "]", regName, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation io_write_byte_dx(String regName) {
        return new MicroOperation(
            MicroOperationType.IO_WRITE_BYTE,
            "OUT DX, " + regName,
            () -> {
                controlBus.clearAll();
                int port = reg("DX").output() & 0xFF;
                int ioAddr = 0x1000 + port;
                int value = reg(regName).lowByte();
                memory.directWrite(ioAddr, value);
            },
            "IO[DX]", regName, MicroOperation.BusActivity.DATA_BUS
        );
    }

    public MicroOperation io_write_word_dx(String regName) {
        return new MicroOperation(
            MicroOperationType.IO_WRITE_WORD,
            "OUT DX, " + regName,
            () -> {
                controlBus.clearAll();
                int port = reg("DX").output() & 0xFF;
                int ioAddr = 0x1000 + port;
                int value = reg(regName).output();
                memory.directWrite(ioAddr, value & 0xFF);
                memory.directWrite(ioAddr + 1, (value >> 8) & 0xFF);
            },
            "IO[DX]", regName, MicroOperation.BusActivity.DATA_BUS
        );
    }

    // ---- Helpers --------------------------------------------------------

    private Register reg(String name) {
        String upper = name.toUpperCase();
        String lookup = switch (upper) {
            case "AL", "AH" -> "AX";
            case "BL", "BH" -> "BX";
            case "CL", "CH" -> "CX";
            case "DL", "DH" -> "DX";
            default -> upper;
        };
        Register r = regs.get(lookup);
        if (r == null) throw new IllegalArgumentException("Unknown register: " + name);
        return r;
    }

    private boolean isByteReg(String name) {
        return ISA.isGPR8(name.toUpperCase());
    }

    private int computePhysical(String segName, int offset) {
        int segment = reg(segName).output();
        return ((segment * 16) + offset) & 0xFFFFF;
    }

    private String formatAddr(int addr) {
        return String.format("0x%X", addr);
    }

    // ============================================================
    //  ASCII & BCD Adjustments
    // ============================================================
    public MicroOperation aaa() {
        return new MicroOperation(MicroOperationType.ALU_ADJUST, "AAA", () -> {
            int al = reg("AX").lowByte();
            int ah = reg("AX").highByte();
            if ((al & 0x0F) > 9 || flags.isAuxCarry()) {
                reg("AX").loadLow((al + 6) & 0x0F);
                reg("AX").loadHigh((ah + 1) & 0xFF);
                flags.setAuxCarry(true);
                flags.setCarry(true);
            } else {
                flags.setAuxCarry(false);
                flags.setCarry(false);
                reg("AX").loadLow(al & 0x0F);
            }
            flags.setZero(reg("AX").lowByte() == 0);
            flags.setSign((reg("AX").lowByte() & 0x80) != 0);
            flags.setParity(cpu.registers.FLAGS.computeParity(reg("AX").lowByte()));
        }, "FLAGS/AL/AH", "AX", MicroOperation.BusActivity.DATA_BUS);
    }

    public MicroOperation aas() {
        return new MicroOperation(MicroOperationType.ALU_ADJUST, "AAS", () -> {
            int al = reg("AX").lowByte();
            int ah = reg("AX").highByte();
            if ((al & 0x0F) > 9 || flags.isAuxCarry()) {
                reg("AX").loadLow((al - 6) & 0x0F);
                reg("AX").loadHigh((ah - 1) & 0xFF);
                flags.setAuxCarry(true);
                flags.setCarry(true);
            } else {
                flags.setAuxCarry(false);
                flags.setCarry(false);
                reg("AX").loadLow(al & 0x0F);
            }
            flags.setZero(reg("AX").lowByte() == 0);
            flags.setSign((reg("AX").lowByte() & 0x80) != 0);
            flags.setParity(cpu.registers.FLAGS.computeParity(reg("AX").lowByte()));
        }, "FLAGS/AL/AH", "AX", MicroOperation.BusActivity.DATA_BUS);
    }

    public MicroOperation aam(int base) {
        return new MicroOperation(MicroOperationType.ALU_ADJUST, "AAM", () -> {
            int al = reg("AX").lowByte();
            int b = base == 0 ? 10 : base;
            reg("AX").loadHigh((al / b) & 0xFF);
            reg("AX").loadLow((al % b) & 0xFF);
            int res = reg("AX").lowByte();
            flags.setZero(res == 0);
            flags.setSign((res & 0x80) != 0);
            flags.setParity(cpu.registers.FLAGS.computeParity(res));
            flags.setAuxCarry(false);
            flags.setCarry(false);
        }, "AX/FLAGS", "#" + base, MicroOperation.BusActivity.DATA_BUS);
    }

    public MicroOperation aad(int base) {
        return new MicroOperation(MicroOperationType.ALU_ADJUST, "AAD", () -> {
            int al = reg("AX").lowByte();
            int ah = reg("AX").highByte();
            int b = base == 0 ? 10 : base;
            reg("AX").loadLow((ah * b + al) & 0xFF);
            reg("AX").loadHigh(0);
            int res = reg("AX").lowByte();
            flags.setZero(res == 0);
            flags.setSign((res & 0x80) != 0);
            flags.setParity(cpu.registers.FLAGS.computeParity(res));
            flags.setCarry(false);
            flags.setOverflow(false);
        }, "AX/FLAGS", "#" + base, MicroOperation.BusActivity.DATA_BUS);
    }

    // ============================================================
    //  DAA / DAS (Decimal Adjust after Addition / Subtraction)
    //  Intel 8086 semantics:
    //   DAA: IF (AL&0FH)>9 OR AF=1 THEN AL+=6,AF=1 ELSE AF=0;
    //        IF AL>9FH OR CF=1 THEN AL+=60H,CF=1 ELSE CF=0.
    //   DAS: same shape with -6/-60H.
    //  SF/ZF/PF recomputed from result; OF preserved (undefined per Intel).
    // ============================================================
    public MicroOperation daa() {
        return new MicroOperation(MicroOperationType.ALU_ADJUST, "DAA", () -> {
            int al = reg("AX").lowByte();
            boolean oldCF = flags.isCarry();
            if ((al & 0x0F) > 9 || flags.isAuxCarry()) {
                al = (al + 6) & 0xFF;
                flags.setAuxCarry(true);
            } else {
                flags.setAuxCarry(false);
            }
            if (al > 0x9F || oldCF) {
                al = (al + 0x60) & 0xFF;
                flags.setCarry(true);
            } else {
                flags.setCarry(false);
            }
            reg("AX").loadLow(al);
            flags.setZero(al == 0);
            flags.setSign((al & 0x80) != 0);
            flags.setParity(cpu.registers.FLAGS.computeParity(al));
        }, "AL/FLAGS", "AL", MicroOperation.BusActivity.DATA_BUS);
    }

    public MicroOperation das() {
        return new MicroOperation(MicroOperationType.ALU_ADJUST, "DAS", () -> {
            int al = reg("AX").lowByte();
            boolean oldCF = flags.isCarry();
            if ((al & 0x0F) > 9 || flags.isAuxCarry()) {
                al = (al - 6) & 0xFF;
                flags.setAuxCarry(true);
            } else {
                flags.setAuxCarry(false);
            }
            if (al > 0x9F || oldCF) {
                al = (al - 0x60) & 0xFF;
                flags.setCarry(true);
            } else {
                flags.setCarry(false);
            }
            reg("AX").loadLow(al);
            flags.setZero(al == 0);
            flags.setSign((al & 0x80) != 0);
            flags.setParity(cpu.registers.FLAGS.computeParity(al));
        }, "AL/FLAGS", "AL", MicroOperation.BusActivity.DATA_BUS);
    }

    // ============================================================
    //  XLAT (Table Look-up Translation)
    // ============================================================
    public MicroOperation xlat() {
        return new MicroOperation(MicroOperationType.MEM_READ, "AL <- Mem[DS:BX + AL]", () -> {
            controlBus.clearAll();
            int bx = reg("BX").output();
            int al = reg("AX").lowByte();
            int phys = computePhysical("DS", (bx + al) & 0xFFFF);
            controlBus.assert_(ControlSignal.MAR_LOAD);
            mar.load(phys);
            addressBus.drive(phys);
            controlBus.assert_(ControlSignal.MEMORY_READ);
            controlBus.assert_(ControlSignal.MDR_LOAD);
            memory.busRead();
            mdr.load(dataBus.read());
            reg("AX").loadLow(mdr.output() & 0xFF);
        }, "AL", "DS:BX+AL", MicroOperation.BusActivity.ADDRESS_AND_DATA);
    }

    // ============================================================
    //  LDS / LES (Load Far Pointer — register + segment)
    // ============================================================
    public MicroOperation lds_reg_mem(String dst, int physAddr) {
        return new MicroOperation(MicroOperationType.REG_LOAD_MDR, dst + " <- Mem[" + physAddr + "] ; DS <- Mem[" + ((physAddr + 2) & 0xFFFFF) + "]", () -> {
            controlBus.clearAll();
            // Load register (low word at address)
            controlBus.assert_(ControlSignal.MAR_LOAD); mar.load(physAddr); addressBus.drive(physAddr);
            controlBus.assert_(ControlSignal.MEMORY_READ); controlBus.assert_(ControlSignal.MDR_LOAD);
            memory.busRead(); mdr.load(dataBus.read()); reg(dst).load(mdr.output());
            // Load DS (high word at address + 2)
            int physDS = (physAddr + 2) & 0xFFFFF;
            controlBus.assert_(ControlSignal.MAR_LOAD); mar.load(physDS); addressBus.drive(physDS);
            controlBus.assert_(ControlSignal.MEMORY_READ); controlBus.assert_(ControlSignal.MDR_LOAD);
            memory.busRead(); mdr.load(dataBus.read()); reg("DS").load(mdr.output());
        }, dst + "/DS", "Far[" + physAddr + "]", MicroOperation.BusActivity.ADDRESS_AND_DATA);
    }

    public MicroOperation les_reg_mem(String dst, int physAddr) {
        return new MicroOperation(MicroOperationType.REG_LOAD_MDR, dst + " <- Mem[" + physAddr + "] ; ES <- Mem[" + ((physAddr + 2) & 0xFFFFF) + "]", () -> {
            controlBus.clearAll();
            controlBus.assert_(ControlSignal.MAR_LOAD); mar.load(physAddr); addressBus.drive(physAddr);
            controlBus.assert_(ControlSignal.MEMORY_READ); controlBus.assert_(ControlSignal.MDR_LOAD);
            memory.busRead(); mdr.load(dataBus.read()); reg(dst).load(mdr.output());
            int physES = (physAddr + 2) & 0xFFFFF;
            controlBus.assert_(ControlSignal.MAR_LOAD); mar.load(physES); addressBus.drive(physES);
            controlBus.assert_(ControlSignal.MEMORY_READ); controlBus.assert_(ControlSignal.MDR_LOAD);
            memory.busRead(); mdr.load(dataBus.read()); reg("ES").load(mdr.output());
        }, dst + "/ES", "Far[" + physAddr + "]", MicroOperation.BusActivity.ADDRESS_AND_DATA);
    }
}
