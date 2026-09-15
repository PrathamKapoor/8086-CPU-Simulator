package cpu;

import bus.ControlBus;
import bus.ControlSignal;
import bus.DataBus;
import instruction.Instruction;
import instruction.Opcode;
import instruction.InstructionFormat;
import microoperation.MicroOperation;
import microoperation.MicroOperationExecutor;
import microoperation.MicroOperationType;

import cpu.registers.FLAGS;
import cpu.registers.MDR;
import cpu.registers.Register;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ControlUnit — orchestrates the Fetch-Decode-Execute cycle.
 *
 * For each instruction it generates:
 *   1. Three FETCH micro-operations  (MAR<-PC, MDR<-Mem[MAR], IR<-MDR + PC++)
 *   2. One   DECODE micro-operation
 *   3. N     EXECUTE micro-operations based on the instruction opcode
 *
 * Now supports all 8086 opcodes: MOV, ADD, SUB, ADC, SBB, MUL, IMUL,
 * DIV, IDIV, SHL, SHR, SAR, ROL, ROR, RCL, RCR, AND, OR, XOR, NOT,
 * INC, DEC, NEG, CMP, TEST, JMP, JZ, JNZ, JC, JNC, JO, LOOP, PUSH,
 * POP, CALL, RET, INT, IRET, NOP, HLT, CLC, STC, CMC, CLD, STD, CLI, STI.
 */
public class ControlUnit {

    private final MicroOperationExecutor executor;
    private final InstructionDecoder     decoder;
    private final Map<String, Register>  registerFile;
    private ControlBus controlBusRef;
    private DataBus    dataBusRef;

    private String currentPhase = "IDLE";

    public ControlUnit(MicroOperationExecutor executor, InstructionDecoder decoder,
                       Map<String, Register> registerFile) {
        this.executor = executor;
        this.decoder  = decoder;
        this.registerFile = registerFile;
    }

    public void setBuses(ControlBus cb, DataBus db) {
        this.controlBusRef = cb;
        this.dataBusRef = db;
    }

    /**
     * Produce the full micro-operation sequence for a single instruction.
     * (FETCH + DECODE + EXECUTE)
     */
    public List<MicroOperation> generateMicroOps(Instruction instruction) {
        List<MicroOperation> ops = new ArrayList<>();

        // FETCH (3 cycles)
        ops.add(executor.fetchStep1_MAR_from_PC());
        ops.add(executor.fetchStep2_MDR_from_Memory());
        ops.add(executor.fetchStep3_IR_from_MDR_and_PC_inc());

        // DECODE (1 cycle)
        decoder.decode(instruction);
        ops.add(executor.decode(instruction.toString()));

        // EXECUTE (1-N cycles depending on instruction)
        ops.addAll(generateExecuteMicroOps(instruction));

        return ops;
    }

    private List<MicroOperation> generateExecuteMicroOps(Instruction instr) {
        List<MicroOperation> ops = new ArrayList<>();
        Opcode op = instr.getOpcode();

        switch (op) {

            // ============================================================
            //  Data Transfer
            // ============================================================
            case MOV -> {
                switch (instr.getFormat()) {
                    case REG_REG -> ops.add(executor.mov_reg_reg(
                            instr.getDestReg(), instr.getSrcReg()));
                    case REG_IMM, REG_IMM8 -> ops.add(executor.mov_reg_imm(
                            instr.getDestReg(), instr.getImmediate()));
                    case REG_SEG -> {
                        // MOV DS, AX  or  MOV AX, DS
                        if (isMemoryRef(instr.getSrcReg())) {
                            // MOV DS, [mem] — load segment from memory
                            ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                            ops.add(executor.load_step2_MDR());
                            ops.add(new microoperation.MicroOperation(
                                microoperation.MicroOperationType.LOAD_SEG,
                                instr.getDestReg() + " <- MDR",
                                () -> { reg(instr.getDestReg()).load(mdr().output()); },
                                instr.getDestReg(), "MDR", microoperation.MicroOperation.BusActivity.DATA_BUS
                            ));
                        } else {
                            // MOV DS, AX  or  MOV AX, DS
                            ops.add(executor.mov_reg_reg(instr.getDestReg(), instr.getSrcReg()));
                        }
                    }
                    case SEG_REG_REG -> {
                        // MOV DS, [mem] — load far pointer
                        ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                        ops.add(executor.load_step2_MDR());
                        ops.add(new microoperation.MicroOperation(
                            microoperation.MicroOperationType.LOAD_SEG,
                            instr.getDestReg() + " <- MDR",
                            () -> { reg(instr.getDestReg()).load(mdr().output()); },
                            instr.getDestReg(), "MDR", microoperation.MicroOperation.BusActivity.DATA_BUS
                        ));
                    }
                    case REG_REG_INDIRECT -> {
                        // MOV reg, [mem] or MOV [mem], reg
                        if (isMemoryRef(instr.getSrcReg())) {
                            ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                            ops.add(executor.load_step2_MDR());
                            ops.add(executor.load_step3_reg_from_MDR(instr.getDestReg()));
                        } else {
                            ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                            ops.add(executor.store_step2_MDR(instr.getSrcReg()));
                            ops.add(executor.store_step3_write());
                        }
                    }
                    case REG_INDIRECT_REG -> {
                        // MOV [mem], reg
                        ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                        ops.add(executor.store_step2_MDR(instr.getSrcReg()));
                        ops.add(executor.store_step3_write());
                    }
                    case REG_INDIRECT_IMM -> {
                        // MOV [mem], imm
                        ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                        ops.add(new microoperation.MicroOperation(
                            microoperation.MicroOperationType.MDR_LOAD_IMM,
                            "MDR <- #" + instr.getImmediate(),
                            () -> {
                                controlBus().clearAll();
                                controlBus().assert_(ControlSignal.MDR_LOAD);
                                dataBus().drive(instr.getImmediate());
                                mdr().load(instr.getImmediate());
                            },
                            "MDR", "#" + instr.getImmediate(), microoperation.MicroOperation.BusActivity.DATA_BUS
                        ));
                        ops.add(executor.store_step3_write());
                    }
                    default -> throw new IllegalArgumentException("Unsupported MOV format: " + instr.getFormat());
                }
            }

            case LOAD -> {
                switch (instr.getFormat()) {
                    case REG_ADDR -> {
                        ops.add(executor.load_step1_MAR(instr.getAddress()));
                        ops.add(executor.load_step2_MDR());
                        ops.add(executor.load_step3_reg_from_MDR(instr.getDestReg()));
                    }
                    case REG_REG_INDIRECT, REG_REG -> {
                        ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                        ops.add(executor.load_step2_MDR());
                        ops.add(executor.load_step3_reg_from_MDR(instr.getDestReg()));
                    }
                    default -> throw new IllegalArgumentException("Unsupported LOAD format");
                }
            }

            case STORE -> {
                switch (instr.getFormat()) {
                    case REG_ADDR -> {
                        ops.add(executor.store_step1_MAR(instr.getAddress()));
                        ops.add(executor.store_step2_MDR(instr.getDestReg()));
                        ops.add(executor.store_step3_write());
                    }
                    case REG_REG_INDIRECT, REG_REG, REG_INDIRECT_REG -> {
                        ops.add(executor.load_effective_addr(computeEffectiveAddr(instr)));
                        ops.add(executor.store_step2_MDR(instr.getDestReg()));
                        ops.add(executor.store_step3_write());
                    }
                    default -> throw new IllegalArgumentException("Unsupported STORE format");
                }
            }

            // ============================================================
            //  Arithmetic
            // ============================================================
            case ADD -> addAluOp(ops, instr, MicroOperationType.ALU_ADD, ALU.Operation.ADD);
            case SUB -> addAluOp(ops, instr, MicroOperationType.ALU_SUB, ALU.Operation.SUB);
            case ADC -> addAluOp(ops, instr, MicroOperationType.ALU_ADC, ALU.Operation.ADC);
            case SBB -> addAluOp(ops, instr, MicroOperationType.ALU_SBB, ALU.Operation.SBB);
            case AND -> addAluOp(ops, instr, MicroOperationType.ALU_AND, ALU.Operation.AND);
            case OR  -> addAluOp(ops, instr, MicroOperationType.ALU_OR,  ALU.Operation.OR);
            case XOR -> addAluOp(ops, instr, MicroOperationType.ALU_XOR, ALU.Operation.XOR);

            case CMP -> {
                if (instr.getFormat() == InstructionFormat.REG_IMM)
                    ops.add(executor.alu_cmp_imm(instr.getDestReg(), instr.getImmediate()));
                else
                    ops.add(executor.alu_cmp(instr.getDestReg(), instr.getSrcReg()));
            }

            case TEST -> {
                if (instr.getFormat() == InstructionFormat.REG_IMM)
                    ops.add(executor.alu_test_imm(instr.getDestReg(), instr.getImmediate()));
                else
                    ops.add(executor.alu_test(instr.getDestReg(), instr.getSrcReg()));
            }

            case INC -> ops.add(executor.alu_unary(MicroOperationType.ALU_INC,
                    ALU.Operation.INC, instr.getDestReg()));
            case DEC -> ops.add(executor.alu_unary(MicroOperationType.ALU_DEC,
                    ALU.Operation.DEC, instr.getDestReg()));
            case NEG -> ops.add(executor.alu_unary(MicroOperationType.ALU_NEG,
                    ALU.Operation.NEG, instr.getDestReg()));
            case NOT -> ops.add(executor.alu_unary(MicroOperationType.ALU_NOT,
                    ALU.Operation.NOT, instr.getDestReg()));

            case MUL -> ops.add(executor.mul_reg(instr.getDestReg()));
            case IMUL -> ops.add(executor.imul_reg(instr.getDestReg()));
            case DIV -> ops.add(executor.div_reg(instr.getDestReg()));
            case IDIV -> ops.add(executor.idiv_reg(instr.getDestReg()));

            // ============================================================
            //  Shifts / Rotates
            // ============================================================
            case SHL_SAL -> ops.add(executor.shift_reg(MicroOperationType.ALU_SHL,
                    ALU.Operation.SHL, instr.getDestReg(), instr.getImmediate()));
            case SHR -> ops.add(executor.shift_reg(MicroOperationType.ALU_SHR,
                    ALU.Operation.SHR, instr.getDestReg(), instr.getImmediate()));
            case SAR -> ops.add(executor.shift_reg(MicroOperationType.ALU_SAR,
                    ALU.Operation.SAR, instr.getDestReg(), instr.getImmediate()));
            case ROL -> ops.add(executor.shift_reg(MicroOperationType.ALU_ROL,
                    ALU.Operation.ROL, instr.getDestReg(), instr.getImmediate()));
            case ROR -> ops.add(executor.shift_reg(MicroOperationType.ALU_ROR,
                    ALU.Operation.ROR, instr.getDestReg(), instr.getImmediate()));
            case RCL -> ops.add(executor.shift_reg(MicroOperationType.ALU_RCL,
                    ALU.Operation.RCL, instr.getDestReg(), instr.getImmediate()));
            case RCR -> ops.add(executor.shift_reg(MicroOperationType.ALU_RCR,
                    ALU.Operation.RCR, instr.getDestReg(), instr.getImmediate()));

            // ============================================================
            //  Control Transfer
            // ============================================================
            case JMP -> ops.add(executor.jmp(instr.getAddress()));
            case CALL -> ops.add(executor.call(instr.getAddress()));
            case RET -> ops.add(executor.ret());
            case RETF -> ops.add(executor.ret()); // simplified

            // Conditional jumps — all use jcc()
            case JZ_JE -> ops.add(executor.jcc(
                flags().isZero(), instr.getAddress(), "ZF=1"));
            case JNZ_JNE -> ops.add(executor.jcc(
                !flags().isZero(), instr.getAddress(), "ZF=0"));
            case JC_JB -> ops.add(executor.jcc(
                flags().isCarry(), instr.getAddress(), "CF=1"));
            case JNC_JNB -> ops.add(executor.jcc(
                !flags().isCarry(), instr.getAddress(), "CF=0"));
            case JO -> ops.add(executor.jcc(
                flags().isOverflow(), instr.getAddress(), "OF=1"));
            case JNO -> ops.add(executor.jcc(
                !flags().isOverflow(), instr.getAddress(), "OF=0"));
            case JS -> ops.add(executor.jcc(
                flags().isSign(), instr.getAddress(), "SF=1"));
            case JNS -> ops.add(executor.jcc(
                !flags().isSign(), instr.getAddress(), "SF=0"));
            case JP_JPE -> ops.add(executor.jcc(
                flags().isParity(), instr.getAddress(), "PF=1"));
            case JNP_JPO -> ops.add(executor.jcc(
                !flags().isParity(), instr.getAddress(), "PF=0"));
            case JL_JNGE -> ops.add(executor.jcc(
                flags().isSign() != flags().isOverflow(), instr.getAddress(), "SF!=OF"));
            case JNL_JGE -> ops.add(executor.jcc(
                flags().isSign() == flags().isOverflow(), instr.getAddress(), "SF=OF"));
            case JLE_JNG -> ops.add(executor.jcc(
                flags().isZero() || flags().isSign() != flags().isOverflow(),
                instr.getAddress(), "ZF=1|SF!=OF"));
            case JNLE_JG -> ops.add(executor.jcc(
                !flags().isZero() && flags().isSign() == flags().isOverflow(),
                instr.getAddress(), "ZF=0&SF=OF"));
            case JBE_JNA -> ops.add(executor.jcc(
                flags().isCarry() || flags().isZero(), instr.getAddress(), "CF=1|ZF=1"));
            case JNBE_JA -> ops.add(executor.jcc(
                !flags().isCarry() && !flags().isZero(), instr.getAddress(), "CF=0&ZF=0"));

            case LOOP -> ops.add(executor.loop_(instr.getAddress()));
            case LOOPZ -> ops.add(executor.loopz(instr.getAddress()));
            case LOOPNZ -> ops.add(executor.loopnz(instr.getAddress()));
            case JCXZ -> ops.add(executor.jcc(
                reg("CX").output() == 0, instr.getAddress(), "CX=0"));

            // ============================================================
            //  Push / Pop
            // ============================================================
            case PUSH -> {
                // Parser emits PUSH-imm as REG_IMM and PUSH-seg as SEG_REG.
                if (instr.getFormat() == InstructionFormat.IMM_ONLY
                        || instr.getFormat() == InstructionFormat.REG_IMM)
                    ops.add(executor.push_imm(instr.getImmediate()));
                else if (instr.getFormat() == InstructionFormat.SEG_REG_ONLY
                        || instr.getFormat() == InstructionFormat.SEG_REG)
                    ops.add(executor.push_seg(instr.getDestReg()));
                else
                    ops.add(executor.push_reg(instr.getDestReg()));
            }
            case POP -> {
                if (instr.getFormat() == InstructionFormat.SEG_REG_ONLY
                        || instr.getFormat() == InstructionFormat.SEG_REG)
                    ops.add(executor.pop_seg(instr.getDestReg()));
                else
                    ops.add(executor.pop_reg(instr.getDestReg()));
            }

            // ============================================================
            //  Interrupts
            // ============================================================
            case INT -> ops.add(executor.int_(instr.getImmediate()));
            case IRET -> ops.add(executor.iret());

            // ============================================================
            //  Processor Control
            // ============================================================
            case NOP -> ops.add(executor.nop());
            case HLT -> ops.add(executor.halt());
            case CLC -> ops.add(executor.flag_clear_cf());
            case STC -> ops.add(executor.flag_set_cf());
            case CMC -> ops.add(executor.flag_complement_cf());
            case CLD -> ops.add(executor.flag_clear_df());
            case STD -> ops.add(executor.flag_set_df());
            case CLI -> ops.add(executor.flag_clear_if());
            case STI -> ops.add(executor.flag_set_if());
            case PUSHF -> ops.add(executor.push_flags());
            case POPF -> ops.add(executor.pop_flags());

            // ============================================================
            //  XCHG — reg, reg (swap two registers)
            // ============================================================
            case XCHG -> {
                String dst = instr.getDestReg();
                String src = instr.getSrcReg();
                ops.add(new microoperation.MicroOperation(
                    microoperation.MicroOperationType.REG_LOAD_REG,
                    dst + " <-> " + src,
                    () -> {
                        int tmp = reg(dst).output();
                        reg(dst).load(reg(src).output());
                        reg(src).load(tmp);
                    },
                    dst, src, microoperation.MicroOperation.BusActivity.DATA_BUS
                ));
            }

            // ============================================================
            //  LEA — Load Effective Address (simplified: treat as MOV)
            // ============================================================
            case LEA -> {
                int ea = computeEffectiveAddr(instr);
                ops.add(executor.mov_reg_imm(instr.getDestReg(), ea));
            }

            // ============================================================
            //  String operations (simplified: single-step)
            // ============================================================
            case MOVSB, MOVSW, CMPSB, CMPSW, SCASB, SCASW, LODSB, LODSW, STOSB, STOSW -> {
                MicroOperation baseOp;
                switch (op) {
                    case MOVSB -> baseOp = executor.movsb();
                    case MOVSW -> baseOp = executor.movsw();
                    case LODSB -> baseOp = executor.lodsb();
                    case LODSW -> baseOp = executor.lodsw();
                    case STOSB -> baseOp = executor.stosb();
                    case STOSW -> baseOp = executor.stosw();
                    case CMPSB -> baseOp = executor.cmpsb();
                    case CMPSW -> baseOp = executor.cmpsw();
                    case SCASB -> baseOp = executor.scasb();
                    case SCASW -> baseOp = executor.scasw();
                    default -> baseOp = executor.nop();
                }
                Opcode prefix = instr.getPrefix();
                if (prefix == null) {
                    ops.add(baseOp);
                } else {
                    ops.add(new microoperation.MicroOperation(
                        baseOp.getType(),
                        (prefix == Opcode.REPE ? "REPE " : prefix == Opcode.REPNE ? "REPNE " : "REP ") + baseOp.getRtlDescription(),
                        () -> {
                            int cxVal = reg("CX").output();
                            int count = 0;
                            boolean cont = true;
                            while (count < cxVal && cont) {
                                baseOp.execute();
                                count++;
                                if (prefix == Opcode.REPE) {
                                    cont = flags().isZero();
                                } else if (prefix == Opcode.REPNE) {
                                    cont = !flags().isZero();
                                }
                            }
                            // Real 8086 leaves CX = remaining count (0 when REP
                            // runs to completion); previously CX was untouched.
                            reg("CX").load((cxVal - count) & 0xFFFF);
                        },
                        baseOp.getDestReg(),
                        baseOp.getSrcReg(),
                        baseOp.getBusActivity()
                    ));
                }
            }

            // ============================================================
            //  Flags-only instructions
            // ============================================================
            case LAHF -> {
                ops.add(new microoperation.MicroOperation(
                    microoperation.MicroOperationType.REG_LOAD_IMM,
                    "AH <- FLAGS (low byte)",
                    () -> {
                        reg("AX").loadLow(flags().output() & 0xFF);
                    },
                    "AH", "FLAGS", microoperation.MicroOperation.BusActivity.NONE
                ));
            }
            case SAHF -> {
                ops.add(new microoperation.MicroOperation(
                    microoperation.MicroOperationType.FLAGS_LOAD,
                    "FLAGS (low byte) <- AH",
                    () -> {
                        int ah = reg("AX").highByte();
                        int newFlags = (flags().output() & 0xFF00) | ah;
                        flags().load(newFlags);
                    },
                    "FLAGS", "AH", microoperation.MicroOperation.BusActivity.NONE
                ));
            }
            case CBW -> {
                ops.add(new microoperation.MicroOperation(
                    microoperation.MicroOperationType.REG_LOAD_REG,
                    "AX <- sign-extend(AL)",
                    () -> {
                        int al = reg("AX").lowByte();
                        reg("AX").load((al & 0x80) != 0 ? 0xFF00 | al : al);
                    },
                    "AX", "AL", microoperation.MicroOperation.BusActivity.NONE
                ));
            }
            case CWD -> {
                ops.add(new microoperation.MicroOperation(
                    microoperation.MicroOperationType.REG_LOAD_REG,
                    "DX:AX <- sign-extend(AX)",
                    () -> {
                        int ax = reg("AX").output();
                        reg("DX").load((ax & 0x8000) != 0 ? 0xFFFF : 0x0000);
                    },
                    "DX:AX", "AX", microoperation.MicroOperation.BusActivity.NONE
                ));
            }

            // ============================================================
            //  Explicitly scoped partial behavior; execution records the model limitation.
            // ============================================================
            case IN -> {
                if (instr.getFormat() == InstructionFormat.FIXED_AL_IMM) {
                    ops.add(executor.io_read_byte("AL", instr.getImmediate()));
                } else if (instr.getFormat() == InstructionFormat.FIXED_AX_IMM) {
                    ops.add(executor.io_read_word("AX", instr.getImmediate()));
                } else if (instr.getFormat() == InstructionFormat.FIXED_AL_DX) {
                    ops.add(executor.io_read_byte_dx("AL"));
                } else if (instr.getFormat() == InstructionFormat.FIXED_AX_DX) {
                    ops.add(executor.io_read_word_dx("AX"));
                } else {
                    ops.add(executor.nop());
                }
            }
            case OUT -> {
                if (instr.getFormat() == InstructionFormat.FIXED_AL_IMM) {
                    ops.add(executor.io_write_byte(instr.getImmediate(), instr.getDestReg()));
                } else if (instr.getFormat() == InstructionFormat.FIXED_AX_IMM) {
                    ops.add(executor.io_write_word(instr.getImmediate(), instr.getDestReg()));
                } else if (instr.getFormat() == InstructionFormat.FIXED_AL_DX) {
                    ops.add(executor.io_write_byte_dx(instr.getSrcReg()));
                } else if (instr.getFormat() == InstructionFormat.FIXED_AX_DX) {
                    ops.add(executor.io_write_word_dx(instr.getSrcReg()));
                } else {
                    ops.add(executor.nop());
                }
            }
            case AAA -> ops.add(executor.aaa());
            case AAS -> ops.add(executor.aas());
            case AAM -> {
                int base = instr.getImmediate() == 0 ? 10 : instr.getImmediate();
                ops.add(executor.aam(base));
            }
            case AAD -> {
                int base = instr.getImmediate() == 0 ? 10 : instr.getImmediate();
                ops.add(executor.aad(base));
            }
            case XLAT -> ops.add(executor.xlat());
            case LDS -> {
                int phys = computeEffectiveAddr(instr);
                ops.add(executor.lds_reg_mem(instr.getDestReg(), phys));
            }
            case LES -> {
                int phys = computeEffectiveAddr(instr);
                ops.add(executor.les_reg_mem(instr.getDestReg(), phys));
            }
            case DAA -> ops.add(executor.daa());
            case DAS -> ops.add(executor.das());
            case INTO -> ops.add(executor.into());
            case WAIT -> ops.add(executor.wait_());
            case LOCK -> ops.add(executor.lock_());
            case ESC -> ops.add(executor.esc_());
            case REP, REPE, REPNE -> {
                // Repeat prefixes handled in parseProgram (prefixOpcode) and CPU loop
                ops.add(executor.nop());
            }
            // WAIT / LOCK / ESC / INTO are wired above; nothing left is an unexplained NOP.
        }

        return ops;
    }

    // ---- Helpers ----

    private void addAluOp(List<MicroOperation> ops, Instruction instr,
                          MicroOperationType type, ALU.Operation aluOp) {
        if (instr.getFormat() == InstructionFormat.REG_IMM)
            ops.add(executor.alu_imm(type, aluOp, instr.getDestReg(), instr.getImmediate()));
        else
            ops.add(executor.alu_binary(type, aluOp, instr.getDestReg(), instr.getSrcReg()));
    }

    private boolean isMemoryRef(String s) {
        return s != null && s.contains("[");
    }

    private int computeEffectiveAddr(Instruction instr) {
        String override = instr.getSegmentOverride();
        if (instr.getBaseReg() == null && instr.getIndexReg() == null) {
            int offset = instr.getAddress() != 0 ? instr.getAddress() : instr.getDisplacement();
            String segmentName = override != null ? override.toUpperCase() : "DS";
            return ((reg(segmentName).output() * 16) + offset) & 0xFFFFF;
        }
        if (override != null) {
            int offset = instr.getDisplacement();
            if (instr.getBaseReg() != null) offset += reg(instr.getBaseReg()).output();
            if (instr.getIndexReg() != null) offset += reg(instr.getIndexReg()).output();
            return ((reg(override.toUpperCase()).output() * 16) + (offset & 0xFFFF)) & 0xFFFFF;
        }

        // Compute segmented address from baseReg + indexReg + displacement
        return executor.computeSegmentedAddr(
            instr.getBaseReg(), instr.getIndexReg(), instr.getDisplacement());
    }

    private cpu.registers.FLAGS flags;
    public void setFlags(cpu.registers.FLAGS f) { this.flags = f; }
    private cpu.registers.FLAGS flags() { return flags; }

    private Register reg(String name) {
        Register r = registerFile.get(name.toUpperCase());
        if (r == null) throw new IllegalArgumentException("Unknown register: " + name);
        return r;
    }

    private MDR mdr() { return (MDR) registerFile.get("MDR"); }
    private ControlBus controlBus() { return controlBusRef; }
    private DataBus dataBus() { return dataBusRef; }

    public String getCurrentPhase()          { return currentPhase; }
    public void   setCurrentPhase(String p)  { currentPhase = p; }
    public InstructionDecoder getDecoder()   { return decoder; }
}
