package instruction;

import isa.ISA;
import isa.OperandType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InstructionParser — converts raw assembly text into validated Instruction objects.
 *
 * Uses ISA metadata for:
 * - Legal operand validation
 * - Segment register MOV restrictions (MOV CS,AX -> INVALID 8086 INSTRUCTION)
 * - Effective address computation [BX+SI+disp] etc.
 * - Addressing mode detection
 *
 * Supports all 8086 addressing modes:
 *   REG, REG       — MOV AX, BX
 *   REG, IMM       — MOV AX, 5
 *   REG, [addr]    — MOV AX, [100]  (direct)
 *   REG, [reg]     — MOV AX, [BX]   (register indirect)
 *   REG, [reg+disp]— MOV AX, [BX+10] (register indirect + displacement)
 *   REG, [BX+SI]   — MOV AX, [BX+SI] (base + index)
 *   REG, [BX+SI+d] — MOV AX, [BX+SI+10] (base + index + displacement)
 *   [addr], REG    — STORE [100], AX
 *   [reg], REG     — STORE [BX], AX
 *   [reg+disp], REG
 *   PUSH/POP reg/mem/seg
 *   SHL/SHR/ROL/ROR reg, imm8/CL
 *   JMP/CALL addr/mem/reg
 *   Jcc rel8
 *   INT imm8
 *   MUL/DIV/IMUL/IDIV reg/mem
 *
 * Lines beginning with ';' and blank lines are ignored.
 * Labels (e.g. LOOP_START:) are resolved in a two-pass parser.
 */
public class InstructionParser {

    private final ISA isa = ISA.get();

    // Canonical opcode lookup resolving standard assembly mnemonics to internal enum values
    private static final Map<String, Opcode> OPCODE_MAP = new HashMap<>();
    static {
        for (Opcode op : Opcode.values()) {
            OPCODE_MAP.put(op.name(), op);
        }
        OPCODE_MAP.put("JZ",   Opcode.JZ_JE);
        OPCODE_MAP.put("JE",   Opcode.JZ_JE);
        OPCODE_MAP.put("JNZ",  Opcode.JNZ_JNE);
        OPCODE_MAP.put("JNE",  Opcode.JNZ_JNE);
        OPCODE_MAP.put("JC",   Opcode.JC_JB);
        OPCODE_MAP.put("JB",   Opcode.JC_JB);
        OPCODE_MAP.put("JNAE", Opcode.JC_JB);
        OPCODE_MAP.put("JNC",  Opcode.JNC_JNB);
        OPCODE_MAP.put("JNB",  Opcode.JNC_JNB);
        OPCODE_MAP.put("JAE",  Opcode.JNC_JNB);
        OPCODE_MAP.put("SHL",  Opcode.SHL_SAL);
        OPCODE_MAP.put("SAL",  Opcode.SHL_SAL);
        OPCODE_MAP.put("JP",   Opcode.JP_JPE);
        OPCODE_MAP.put("JPE",  Opcode.JP_JPE);
        OPCODE_MAP.put("JNP",  Opcode.JNP_JPO);
        OPCODE_MAP.put("JPO",  Opcode.JNP_JPO);
        OPCODE_MAP.put("JL",   Opcode.JL_JNGE);
        OPCODE_MAP.put("JNGE", Opcode.JL_JNGE);
        OPCODE_MAP.put("JNL",  Opcode.JNL_JGE);
        OPCODE_MAP.put("JGE",  Opcode.JNL_JGE);
        OPCODE_MAP.put("JLE",  Opcode.JLE_JNG);
        OPCODE_MAP.put("JNG",  Opcode.JLE_JNG);
        OPCODE_MAP.put("JNLE", Opcode.JNLE_JG);
        OPCODE_MAP.put("JG",   Opcode.JNLE_JG);
        OPCODE_MAP.put("JBE",  Opcode.JBE_JNA);
        OPCODE_MAP.put("JNA",  Opcode.JBE_JNA);
        OPCODE_MAP.put("JNBE", Opcode.JNBE_JA);
        OPCODE_MAP.put("JA",   Opcode.JNBE_JA);
        OPCODE_MAP.put("LOOPE", Opcode.LOOPZ);
        OPCODE_MAP.put("LOOPNE", Opcode.LOOPNZ);
        OPCODE_MAP.put("REPZ",  Opcode.REPE);
        OPCODE_MAP.put("REPNZ", Opcode.REPNE);
    }

    /**
     * Parse an entire assembly program with two-pass label resolution.
     */
    public List<Instruction> parseProgram(String programText) {
        Map<String, Integer> labelTable = new HashMap<>();
        List<String> rawLines = new ArrayList<>();
        List<Opcode> prefixes = new ArrayList<>();

        String[] lines = programText.split("\\r?\\n");
        int instructionIndex = 0;
        int lineIdx = 0;

        while (lineIdx < lines.length) {
            String raw = lines[lineIdx++].trim();
            int commentIdx = raw.indexOf(';');
            if (commentIdx != -1) raw = raw.substring(0, commentIdx).trim();
            if (raw.isEmpty()) continue;

            Opcode prefixOpcode = null;
            String mnemonic = raw.split("\\s+")[0].toUpperCase();
            try {
                Opcode op = OPCODE_MAP.get(mnemonic);
                if (op != null && (op == Opcode.REP || op == Opcode.REPE || op == Opcode.REPNE)) {
                    prefixOpcode = op;
                    String rest = raw.substring(mnemonic.length()).trim();
                    String nextRaw = "";
                    while (lineIdx < lines.length) {
                        nextRaw = lines[lineIdx++].trim();
                        int c = nextRaw.indexOf(';');
                        if (c != -1) nextRaw = nextRaw.substring(0, c).trim();
                        if (!nextRaw.isEmpty()) {
                            rest = nextRaw;
                            break;
                        }
                    }
                    raw = rest;
                }
            } catch (Exception e) {
                // Ignore for prefix check
            }

            // Handle label definitions (e.g. "LOOP_START:" or "LOOP_START: MOV AX, 1")
            String working = raw;
            while (working.contains(":")) {
                int colon = working.indexOf(':');
                String label = working.substring(0, colon).trim();
                if (!label.isEmpty()) {
                    labelTable.put(label.toUpperCase(), instructionIndex);
                }
                working = working.substring(colon + 1).trim();
            }
            if (working.isEmpty()) continue;

            rawLines.add(working);
            prefixes.add(prefixOpcode);
            instructionIndex++;
        }

        // PASS 2: Substitute label references and build validated Instruction objects
        List<Instruction> instructions = new ArrayList<>();
        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            Opcode prefix = prefixes.get(i);

            // Replace label references in branch/loop instructions
            String[] parts = line.split("\\s+", 2);
            String mnemonic = parts[0].toUpperCase();
            String operands = parts.length > 1 ? parts[1].trim() : "";

            if (!operands.isEmpty()) {
                String target = operands.trim().toUpperCase();
                if (labelTable.containsKey(target)) {
                    operands = String.valueOf(labelTable.get(target));
                    line = mnemonic + " " + operands;
                }
            }

            try {
                Instruction instr = parseLine(line);
                if (prefix != null) {
                    instr = new Instruction.Builder(instr.getOpcode())
                        .format(instr.getFormat())
                        .dest(instr.getDestReg())
                        .src(instr.getSrcReg())
                        .imm(instr.getImmediate())
                        .addr(instr.getAddress())
                        .raw(instr.getRawText())
                        .baseReg(instr.getBaseReg())
                        .indexReg(instr.getIndexReg())
                        .disp(instr.getDisplacement())
                        .segOverride(instr.getSegmentOverride())
                        .encoded(instr.getEncoded())
                        .prefix(prefix)
                        .build();
                }
                instructions.add(instr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Parse error: '" + line + "' — " + e.getMessage());
            }
        }
        return instructions;
    }

    /** Parse a single assembly line. */
    public Instruction parseLine(String line) {
        String upper  = line.trim().toUpperCase();
        String[] parts = upper.split("\\s+", 2);
        String mnemonic = parts[0];
        String operandsRaw = parts.length > 1 ? parts[1].trim() : "";

        Opcode opcode = OPCODE_MAP.get(mnemonic);
        if (opcode == null) {
            throw new IllegalArgumentException("Unknown opcode: " + mnemonic);
        }

        return switch (opcode) {

            // ================================================================
            //  NO-OPERAND instructions
            // ================================================================
            case HLT, NOP, RET, RETF, IRET, CLC, STC, CMC, CLD, STD, CLI, STI,
                 PUSHF, POPF, LAHF, SAHF, CBW, CWD, XLAT, INTO, WAIT, LOCK, ESC,
                 AAA, DAA, AAS, DAS, AAM, AAD -> {
                validateNoOperands(operandsRaw);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.NO_OPERAND)
                    .raw(line.trim()).build();
            }

            // ================================================================
            //  String operations (no operands)
            // ================================================================
            case MOVSB, MOVSW, CMPSB, CMPSW, SCASB, SCASW, LODSB, LODSW, STOSB, STOSW,
                 REP, REPE, REPNE -> {
                validateNoOperands(operandsRaw);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.STRING_ONLY)
                    .raw(line.trim()).build();
            }

            // ================================================================
            //  INC / DEC / NEG / NOT
            // ================================================================
            case INC, DEC, NOT, NEG -> {
                String operand = operandsRaw.trim();
                if (isMemoryRef(operand)) {
                    ParsedMem mem = parseMemoryRef(operand);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.ADDR_ONLY)
                        .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                        .raw(line.trim()).build();
                }
                validateReg16OrByteReg(operand);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_ONLY)
                    .dest(operand).raw(line.trim()).build();
            }

            // ================================================================
            //  PUSH / POP
            // ================================================================
            case PUSH, POP -> {
                String operand = operandsRaw.trim();
                if (isSegmentReg(operand)) {
                    if (opcode == Opcode.POP && operand.equals("CS")) {
                        throw new IllegalArgumentException("INVALID 8086 INSTRUCTION: POP CS is not allowed");
                    }
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.SEG_REG_ONLY)
                        .dest(operand).src(operand).raw(line.trim()).build();
                }
                if (isValidReg16(operand)) {
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_ONLY)
                        .dest(operand).src(operand).raw(line.trim()).build();
                }
                if (isMemoryRef(operand)) {
                    ParsedMem mem = parseMemoryRef(operand);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_REG_INDIRECT)
                        .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                        .raw(line.trim()).build();
                }
                if (opcode == Opcode.PUSH && isIntegerLiteral(operand)) {
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_IMM)
                        .imm(parseIntLiteral(operand)).raw(line.trim()).build();
                }
                throw new IllegalArgumentException("Invalid " + opcode + " operand: " + operand);
            }

            // ================================================================
            //  XCHG
            // ================================================================
            case XCHG -> {
                String[] ops = splitOperands(operandsRaw, 2);
                String op0 = ops[0];
                String op1 = ops[1];
                if (isMemoryRef(op1)) {
                    ParsedMem mem = parseMemoryRef(op1);
                    validateReg16(op0);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_REG_INDIRECT)
                        .dest(op0).src(op1)
                        .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                        .raw(line.trim()).build();
                }
                validateReg16(op0);
                validateReg16(op1);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_REG)
                    .dest(op0).src(op1).raw(line.trim()).build();
            }

            // ================================================================
            //  MOV
            // ================================================================
            case MOV -> {
                String[] ops = splitOperands(operandsRaw, 2);
                String dst = ops[0];
                String src = ops[1];

                // Segment register destination: MOV DS, AX (legal) / MOV CS, AX (illegal)
                if (isSegmentReg(dst)) {
                    if (!isa.isLegalSegmentMov(dst, OperandType.REG16)) {
                        throw new IllegalArgumentException("INVALID 8086 INSTRUCTION: MOV " + dst + ",... is not allowed on 8086");
                    }
                    if (isMemoryRef(src)) {
                        ParsedMem mem = parseMemoryRef(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.SEG_REG_REG)
                            .dest(dst).src(src)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    }
                    validateReg16(src);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_SEG)
                        .dest(dst).src(src).raw(line.trim()).build();
                }

                // Segment register source: MOV AX, DS
                if (isSegmentReg(src)) {
                    validateReg16(dst);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_SEG)
                        .dest(dst).src(src).raw(line.trim()).build();
                }

                // Byte register destination
                if (isByteReg(dst)) {
                    if (isByteReg(src)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_REG)
                            .dest(dst).src(src).raw(line.trim()).build();
                    } else if (isMemoryRef(src)) {
                        ParsedMem mem = parseMemoryRef(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_REG_INDIRECT)
                            .dest(dst).src(src)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    } else {
                        int imm = parseIntLiteral(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_IMM8)
                            .dest(dst).imm(imm).raw(line.trim()).build();
                    }
                }

                // 16-bit register destination
                if (isValidReg16(dst)) {
                    if (isValidReg16(src)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_REG)
                            .dest(dst).src(src).raw(line.trim()).build();
                    } else if (isMemoryRef(src)) {
                        ParsedMem mem = parseMemoryRef(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_REG_INDIRECT)
                            .dest(dst).src(src)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    } else {
                        int imm = parseIntLiteral(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_IMM)
                            .dest(dst).imm(imm).raw(line.trim()).build();
                    }
                }

                // Memory destination: MOV [mem], reg / MOV [mem], imm
                if (isMemoryRef(dst)) {
                    ParsedMem mem = parseMemoryRef(dst);
                    if (isValidReg16(src) || isByteReg(src)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_INDIRECT_REG)
                            .dest(dst).src(src)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    } else {
                        int imm = parseIntLiteral(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_INDIRECT_IMM)
                            .dest(dst).imm(imm)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    }
                }

                throw new IllegalArgumentException("Invalid MOV operands: " + dst + ", " + src);
            }

            // ================================================================
            //  LOAD / STORE
            // ================================================================
            case LOAD -> {
                String[] ops = splitOperands(operandsRaw, 2);
                validateReg16(ops[0]);
                ParsedMem mem = parseMemoryRef(ops[1]);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_REG_INDIRECT)
                    .dest(ops[0]).src(ops[1])
                    .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                    .raw(line.trim()).build();
            }
            case STORE -> {
                String[] ops = splitOperands(operandsRaw, 2);
                // ops[0] should be memory reference, ops[1] should be register
                String memOperand = ops[0].trim();
                String regOperand = ops[1].trim();
                if (!isMemoryRef(memOperand)) {
                    throw new IllegalArgumentException("STORE first operand must be memory: " + memOperand);
                }
                validateReg16OrByteReg(regOperand);
                ParsedMem mem = parseMemoryRef(memOperand);
                // Convention (matches ControlUnit STORE, which reads the value
                // from getDestReg()): dest = value register, src = memory.
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_INDIRECT_REG)
                    .dest(regOperand).src(memOperand)
                    .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                    .raw(line.trim()).build();
            }

            // ================================================================
            //  LEA / LDS / LES
            // ================================================================
            case LEA, LDS, LES -> {
                String[] ops = splitOperands(operandsRaw, 2);
                validateReg16(ops[0]);
                ParsedMem mem = parseMemoryRef(ops[1]);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_REG_INDIRECT)
                    .dest(ops[0]).src(ops[1])
                    .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                    .raw(line.trim()).build();
            }

            // ================================================================
            //  Binary ALU: ADD, SUB, ADC, SBB, AND, OR, XOR, CMP, TEST
            // ================================================================
            case ADD, SUB, ADC, SBB, AND, OR, XOR, CMP, TEST -> {
                String[] ops = splitOperands(operandsRaw, 2);
                String dst = ops[0].trim();
                String src = ops[1].trim();
                if (!isMemoryRef(dst)) validateReg16OrByteReg(dst);

                if (isMemoryRef(dst)) {
                    ParsedMem mem = parseMemoryRef(dst);
                    if (isValidReg16(src) || isByteReg(src)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_INDIRECT_REG)
                            .dest(dst).src(src)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    }
                    int imm = parseIntLiteral(src);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_INDIRECT_IMM)
                        .dest(dst).imm(imm)
                        .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                        .raw(line.trim()).build();
                }

                if (isMemoryRef(src)) {
                    ParsedMem mem = parseMemoryRef(src);
                    if (isValidReg16(dst) || isByteReg(dst)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_REG_INDIRECT)
                            .dest(dst).src(src)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    }
                    throw new IllegalArgumentException("Memory source not valid with memory destination: " + dst + ", " + src);
                }

                if (isByteReg(dst) && isByteReg(src)) {
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_REG)
                        .dest(dst).src(src).raw(line.trim()).build();
                }

                if (isValidReg16(dst) || isByteReg(dst)) {
                    if (isValidReg16(src) || isByteReg(src)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_REG)
                            .dest(dst).src(src).raw(line.trim()).build();
                    } else {
                        int imm = parseIntLiteral(src);
                        yield new Instruction.Builder(opcode)
                            .format(opcode == Opcode.CMP || opcode == Opcode.TEST ? InstructionFormat.REG_IMM : InstructionFormat.REG_IMM)
                            .dest(dst).imm(imm).raw(line.trim()).build();
                    }
                }

                throw new IllegalArgumentException("Invalid operands for " + opcode + ": " + dst + ", " + src);
            }

            // ================================================================
            //  MUL / IMUL / DIV / IDIV
            // ================================================================
            case MUL, IMUL, DIV, IDIV -> {
                String operand = operandsRaw.trim();
                if (isMemoryRef(operand)) {
                    ParsedMem mem = parseMemoryRef(operand);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_REG_INDIRECT)
                        .dest(operand)
                        .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                        .raw(line.trim()).build();
                }
                validateReg16OrByteReg(operand);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_ONLY)
                    .dest(operand).raw(line.trim()).build();
            }

            // ================================================================
            //  Shifts / Rotates
            // ================================================================
            case SHL_SAL, SHR, SAR, ROL, ROR, RCL, RCR -> {
                String[] ops = splitOperands(operandsRaw, 2);
                String dst = ops[0].trim();
                String countStr = ops[1].trim();
                validateReg16OrByteReg(dst);
                int count;
                if (countStr.equals("CL")) {
                    count = -1; // marker for CL
                } else {
                    count = parseIntLiteral(countStr);
                }
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_IMM)
                    .dest(dst).imm(count).raw(line.trim()).build();
            }

            // ================================================================
            //  JMP / CALL
            // ================================================================
            case JMP, CALL -> {
                String operand = operandsRaw.trim();
                if (isValidReg16(operand)) {
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_ONLY)
                        .dest(operand).raw(line.trim()).build();
                }
                if (isMemoryRef(operand)) {
                    ParsedMem mem = parseMemoryRef(operand);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_REG_INDIRECT)
                        .dest(operand)
                        .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                        .raw(line.trim()).build();
                }
                int addr = parseIntLiteral(operand);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.ADDR_ONLY)
                    .addr(addr).raw(line.trim()).build();
            }

            // ================================================================
            //  Conditional jumps — all use REL_ONLY format
            // ================================================================
            case JZ_JE, JNZ_JNE, JC_JB, JNC_JNB, JO, JNO, JS, JNS,
                 JP_JPE, JNP_JPO, JL_JNGE, JNL_JGE, JLE_JNG, JNLE_JG,
                 JBE_JNA, JNBE_JA -> {
                int addr = parseIntLiteral(operandsRaw);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REL_ONLY)
                    .addr(addr).raw(line.trim()).build();
            }

            // ================================================================
            //  LOOP / LOOPZ / LOOPNZ / JCXZ
            // ================================================================
            case LOOP, LOOPZ, LOOPNZ, JCXZ -> {
                int addr = parseIntLiteral(operandsRaw);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REL_ONLY)
                    .addr(addr).raw(line.trim()).build();
            }

            // ================================================================
            //  IN / OUT (I/O ports)
            //  IN AL|AX, imm8  | IN AL|AX, DX
            //  OUT imm8, AL|AX | OUT DX, AL|AX
            // ================================================================
            case IN -> {
                String[] ops = splitOperands(operandsRaw, 2);
                String dst = ops[0].trim();
                String src = ops[1].trim();
                boolean isAL = dst.equals("AL");
                boolean isAX = dst.equals("AX");
                if (!isAL && !isAX) {
                    throw new IllegalArgumentException("Invalid IN destination: " + dst);
                }
                if (src.equals("DX")) {
                    yield new Instruction.Builder(opcode)
                        .format(isAL ? InstructionFormat.FIXED_AL_DX : InstructionFormat.FIXED_AX_DX)
                        .dest(dst).src(src).imm(0).raw(line.trim()).build();
                }
                int port = parseIntLiteral(src);
                yield new Instruction.Builder(opcode)
                    .format(isAL ? InstructionFormat.FIXED_AL_IMM : InstructionFormat.FIXED_AX_IMM)
                    .dest(dst).src(src).imm(port).raw(line.trim()).build();
            }
            case OUT -> {
                String[] ops = splitOperands(operandsRaw, 2);
                String portOp = ops[0].trim();
                String regOp = ops[1].trim();
                boolean isAL = regOp.equals("AL");
                boolean isAX = regOp.equals("AX");
                if (!isAL && !isAX) {
                    throw new IllegalArgumentException("Invalid OUT source: " + regOp);
                }
                if (portOp.equals("DX")) {
                    yield new Instruction.Builder(opcode)
                        .format(isAL ? InstructionFormat.FIXED_AL_DX : InstructionFormat.FIXED_AX_DX)
                        .dest(portOp).src(regOp).imm(0).raw(line.trim()).build();
                }
                int port = parseIntLiteral(portOp);
                yield new Instruction.Builder(opcode)
                    .format(isAL ? InstructionFormat.FIXED_AL_IMM : InstructionFormat.FIXED_AX_IMM)
                    .dest(regOp).src(regOp).imm(port).raw(line.trim()).build();
            }

            // ================================================================
            //  INT
            // ================================================================
            case INT -> {
                int vec = parseIntLiteral(operandsRaw);
                yield new Instruction.Builder(opcode)
                    .format(InstructionFormat.REG_IMM)
                    .imm(vec).raw(line.trim()).build();
            }

            // ================================================================
            //  Default binary ALU
            // ================================================================
            default -> {
                String[] ops = splitOperands(operandsRaw, 2);
                String dst = ops[0].trim();
                String src = ops[1].trim();
                validateReg16OrByteReg(dst);

                if (isMemoryRef(dst)) {
                    ParsedMem mem = parseMemoryRef(dst);
                    if (isValidReg16(src) || isByteReg(src)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_INDIRECT_REG)
                            .dest(dst).src(src)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    } else {
                        int imm = parseIntLiteral(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_INDIRECT_IMM)
                            .dest(dst).imm(imm)
                            .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                            .raw(line.trim()).build();
                    }
                }

                if (isMemoryRef(src)) {
                    ParsedMem mem = parseMemoryRef(src);
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_REG_INDIRECT)
                        .dest(dst).src(src)
                        .baseReg(mem.baseReg).indexReg(mem.indexReg).disp(mem.disp)
                        .raw(line.trim()).build();
                }

                if (isByteReg(dst) && isByteReg(src)) {
                    yield new Instruction.Builder(opcode)
                        .format(InstructionFormat.REG_REG)
                        .dest(dst).src(src).raw(line.trim()).build();
                }

                if (isValidReg16(dst) || isByteReg(dst)) {
                    if (isValidReg16(src) || isByteReg(src)) {
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_REG)
                            .dest(dst).src(src).raw(line.trim()).build();
                    } else {
                        int imm = parseIntLiteral(src);
                        yield new Instruction.Builder(opcode)
                            .format(InstructionFormat.REG_IMM)
                            .dest(dst).imm(imm).raw(line.trim()).build();
                    }
                }

                throw new IllegalArgumentException("Invalid operands: " + dst + ", " + src);
            }
        };
    }

    // ---- Memory Reference Parsing ----
    private static class ParsedMem {
        String baseReg = null;
        String indexReg = null;
        int    disp = 0;
    }

    private ParsedMem parseMemoryRef(String s) {
        String inner = stripSizeSpecifier(s).trim();
        if (!inner.startsWith("[") || !inner.endsWith("]")) {
            throw new IllegalArgumentException("Expected [address], got: " + inner + " (original: " + s + ")");
        }
        inner = inner.substring(1, inner.length() - 1).trim();
        ParsedMem mem = new ParsedMem();

        String[] parts = inner.split("(?=[+\\-])");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            // A leading sign belongs to a numeric displacement, never to a
            // register: "[BX+SI]" splits into "BX", "+SI" — "+SI" is a register.
            boolean negative = p.startsWith("-");
            String bare = (p.startsWith("+") || negative) ? p.substring(1).trim() : p;
            String upper = bare.toUpperCase();
            if (upper.equals("BX") || upper.equals("BP") || upper.equals("SI") || upper.equals("DI")) {
                if (upper.equals("BX") || upper.equals("BP")) {
                    if (mem.baseReg == null) mem.baseReg = upper;
                    else mem.indexReg = upper;
                } else {
                    // SI / DI are index registers (also when appearing alone: [SI])
                    mem.indexReg = upper;
                }
            } else {
                int val = parseIntLiteral(bare);
                if (negative) val = -val;
                mem.disp += val;
            }
        }
        return mem;
    }

    private String stripSizeSpecifier(String s) {
        String t = s.trim();
        String upper = t.toUpperCase();
        if (upper.startsWith("WORD PTR ")) return t.substring(9).trim();
        if (upper.startsWith("BYTE PTR ")) return t.substring(9).trim();
        if (upper.startsWith("WORD ")) return t.substring(5).trim();
        if (upper.startsWith("BYTE ")) return t.substring(5).trim();
        return t;
    }

    private boolean isMemoryRef(String s) {
        String t = stripSizeSpecifier(s);
        return t.startsWith("[") && t.endsWith("]");
    }

    // ---- Validation Helpers ----
    private void validateNoOperands(String operandsRaw) {
        if (!operandsRaw.isEmpty()) {
            throw new IllegalArgumentException("Instruction takes no operands, got: " + operandsRaw);
        }
    }

    private void validateReg16(String name) {
        String upper = name.trim().toUpperCase();
        if (!ISA.isGPR16(upper)) {
            throw new IllegalArgumentException("Expected 16-bit register, got: " + name);
        }
    }

    private boolean isValidReg16(String name) {
        return ISA.isGPR16(name.trim().toUpperCase());
    }

    private boolean isByteReg(String name) {
        return ISA.isGPR8(name.trim().toUpperCase());
    }

    private boolean isSegmentReg(String name) {
        return ISA.isSegReg(name.trim().toUpperCase());
    }

    private void validateReg16OrByteReg(String name) {
        String upper = name.trim().toUpperCase();
        if (!ISA.isGPR16(upper) && !ISA.isGPR8(upper)) {
            throw new IllegalArgumentException("Expected register, got: " + name);
        }
    }

    private boolean isIntegerLiteral(String s) {
        try {
            parseIntLiteral(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String[] splitOperands(String operandsRaw, int expectedCount) {
        String[] ops = operandsRaw.split(",", expectedCount);
        if (ops.length < expectedCount) {
            throw new IllegalArgumentException("Expected " + expectedCount + " operands, got " + ops.length + " (raw: '" + operandsRaw + "')");
        }
        for (int i = 0; i < ops.length; i++) {
            ops[i] = ops[i].trim();
        }
        return ops;
    }

    private int parseIntLiteral(String s) {
        s = s.trim();
        try {
            if (s.startsWith("+")) s = s.substring(1).trim();
            if (s.startsWith("0X") || s.startsWith("0x")) {
                return Integer.parseInt(s.substring(2), 16);
            }
            if (s.toUpperCase().endsWith("H")) {
                return Integer.parseInt(s.substring(0, s.length() - 1), 16);
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected integer, got: " + s);
        }
    }
}
