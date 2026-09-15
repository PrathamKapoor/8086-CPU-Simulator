package machinecode;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.Opcode;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** Encodes supported 8086 semantic instructions into their canonical byte form. */
public final class Intel8086Encoder {
    public EncodedInstruction encode(Instruction instruction, int instructionAddress) {
        if (instruction == null) throw new IllegalArgumentException("instruction must not be null");
        if (instructionAddress < 0 || instructionAddress > 0xFFFF) {
            throw new EncodeException("instruction address outside 16-bit range: " + instructionAddress);
        }
        byte[] bytes = switch (instruction.getOpcode()) {
            case NOP -> new byte[] { (byte) 0x90 };
            case HLT -> new byte[] { (byte) 0xF4 };
            case CLC, STC, CMC, CLD, STD, CLI, STI, PUSHF, POPF, LAHF, SAHF,
                 AAA, DAA, AAS, DAS, CBW, CWD, RET, RETF, IRET, INTO, WAIT,
                 XLAT, MOVSB, MOVSW, CMPSB, CMPSW, SCASB, SCASW, LODSB, LODSW,
                 STOSB, STOSW -> new byte[] { (byte) fixedOpcode(instruction.getOpcode()) };
            case AAM, AAD -> new byte[] { (byte) fixedOpcode(instruction.getOpcode()),
                (byte) (instruction.getImmediate() == 0 ? 10 : instruction.getImmediate()) };
            case INT -> new byte[] { (byte) 0xCD, (byte) instruction.getImmediate() };
            case INC, DEC -> encodeIncDec(instruction);
            case PUSH, POP -> encodePushPop(instruction);
            case NEG, NOT -> encodeUnary(instruction);
            case MOV -> encodeMov(instruction);
            case XCHG -> encodeXchg(instruction);
            case ADD, OR, ADC, SBB, AND, SUB, XOR, CMP, TEST -> encodeAlu(instruction);
            case JMP, CALL, JZ_JE, JNZ_JNE, JC_JB, JNC_JNB, JO, JNO, JS, JNS,
                 JP_JPE, JNP_JPO, JL_JNGE, JNL_JGE, JLE_JNG, JNLE_JG, JBE_JNA,
                 JNBE_JA, LOOP, LOOPZ, LOOPNZ, JCXZ -> encodeRelativeControlTransfer(instruction, instructionAddress);
            default -> throw new EncodeException("8086 encoder does not yet support " + instruction.getOpcode());
        };
        int segmentPrefix = segmentPrefix(instruction.getSegmentOverride());
        int repeatPrefix = instruction.getPrefix() == null ? -1 : switch (instruction.getPrefix()) {
            case REP, REPE -> 0xF3;
            case REPNE -> 0xF2;
            default -> throw new EncodeException("unsupported instruction prefix: " + instruction.getPrefix());
        };
        if (segmentPrefix < 0 && repeatPrefix < 0) return new EncodedInstruction(instruction, bytes);
        byte[] prefixed = new byte[bytes.length + (segmentPrefix < 0 ? 0 : 1) + (repeatPrefix < 0 ? 0 : 1)];
        int p = 0;
        if (segmentPrefix >= 0) prefixed[p++] = (byte) segmentPrefix;
        if (repeatPrefix >= 0) prefixed[p++] = (byte) repeatPrefix;
        System.arraycopy(bytes, 0, prefixed, p, bytes.length);
        return new EncodedInstruction(instruction, prefixed);
    }

    private int segmentPrefix(String segment) {
        if (segment == null) return -1;
        return switch (segment.toUpperCase(Locale.ROOT)) {
            case "ES" -> 0x26; case "CS" -> 0x2E; case "SS" -> 0x36; case "DS" -> 0x3E;
            default -> throw new EncodeException("unsupported segment override: " + segment);
        };
    }

    private byte[] encodeMov(Instruction instruction) {
        String dest = normalized(instruction.getDestReg());
        String src = normalized(instruction.getSrcReg());
        if (instruction.getFormat() == InstructionFormat.REG_IMM || instruction.getFormat() == InstructionFormat.REG_IMM8) {
            boolean byteWidth = (dest != null && isByteRegister(dest))
                || (instruction.getRawText() != null && instruction.getRawText().toUpperCase(Locale.ROOT).startsWith("BYTE"));
            int immediate = instruction.getImmediate();
            if (byteWidth && (immediate < -128 || immediate > 0xFF)) throw new EncodeException("imm8 outside range: " + immediate);
            if (!byteWidth && (immediate < Short.MIN_VALUE || immediate > 0xFFFF)) throw new EncodeException("imm16 outside range: " + immediate);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write((byteWidth ? 0xB0 : 0xB8) + generalRegisterCode(dest));
            output.write(immediate & 0xFF);
            if (!byteWidth) output.write((immediate >>> 8) & 0xFF);
            return output.toByteArray();
        }
        boolean destinationMemory = isMemory(instruction.getDestReg());
        boolean sourceMemory = isMemory(instruction.getSrcReg());
        if (destinationMemory && sourceMemory) throw new EncodeException("MOV does not encode memory-to-memory operands");
        if (!destinationMemory && !sourceMemory && instruction.getFormat() == InstructionFormat.REG_REG) {
            boolean byteWidth = isByteRegister(dest);
            ensureSameWidth(dest, src);
            return concatenate(new byte[] { (byte) (byteWidth ? 0x88 : 0x89) }, ModRm.registerDirect(generalRegisterCode(src), generalRegisterCode(dest)).toBytes());
        }
        if (sourceMemory && !destinationMemory) {
            boolean byteWidth = isByteRegister(dest);
            return concatenate(new byte[] { (byte) (byteWidth ? 0x8A : 0x8B) }, memoryOperand(instruction, generalRegisterCode(dest)).toBytes());
        }
        if (destinationMemory && !sourceMemory) {
            boolean byteWidth = isByteRegister(src);
            return concatenate(new byte[] { (byte) (byteWidth ? 0x88 : 0x89) }, memoryOperand(instruction, generalRegisterCode(src)).toBytes());
        }
        throw new EncodeException("unsupported MOV operand layout: " + instruction.getFormat());
    }

    private byte[] encodeAlu(Instruction instruction) {
        int base = aluBase(instruction.getOpcode());
        String dest = normalized(instruction.getDestReg());
        String src = normalized(instruction.getSrcReg());
        if (instruction.getFormat() == InstructionFormat.REG_IMM || instruction.getFormat() == InstructionFormat.REG_IMM8
            || instruction.getFormat() == InstructionFormat.REG_INDIRECT_IMM) {
            boolean byteWidth = (dest != null && isByteRegister(dest))
                || (instruction.getRawText() != null && instruction.getRawText().toUpperCase(Locale.ROOT).startsWith("BYTE"));
            int immediate = instruction.getImmediate();
            if (byteWidth && (immediate < -128 || immediate > 0xFF)) throw new EncodeException("imm8 outside range: " + immediate);
            if (!byteWidth && (immediate < Short.MIN_VALUE || immediate > 0xFFFF)) throw new EncodeException("imm16 outside range: " + immediate);
            ModRm modRm = isMemory(dest)
                ? memoryOperand(instruction, aluExtension(instruction.getOpcode()))
                : ModRm.registerDirect(aluExtension(instruction.getOpcode()), generalRegisterCode(dest));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(byteWidth ? 0x80 : 0x81);
            output.writeBytes(modRm.toBytes());
            output.write(immediate & 0xFF);
            if (!byteWidth) output.write((immediate >>> 8) & 0xFF);
            return output.toByteArray();
        }
        boolean destinationMemory = isMemory(instruction.getDestReg());
        boolean sourceMemory = isMemory(instruction.getSrcReg());
        if (destinationMemory && sourceMemory) throw new EncodeException(instruction.getOpcode() + " does not encode memory-to-memory operands");
        if (!destinationMemory && !sourceMemory && instruction.getFormat() == InstructionFormat.REG_REG) {
            boolean byteWidth = isByteRegister(dest);
            ensureSameWidth(dest, src);
            return concatenate(new byte[] { (byte) (base + (byteWidth ? 0 : 1)) }, ModRm.registerDirect(generalRegisterCode(src), generalRegisterCode(dest)).toBytes());
        }
        if (sourceMemory && !destinationMemory) {
            boolean byteWidth = isByteRegister(dest);
            return concatenate(new byte[] { (byte) (base + (byteWidth ? 2 : 3)) }, memoryOperand(instruction, generalRegisterCode(dest)).toBytes());
        }
        if (destinationMemory && !sourceMemory) {
            boolean byteWidth = isByteRegister(src);
            return concatenate(new byte[] { (byte) (base + (byteWidth ? 0 : 1)) }, memoryOperand(instruction, generalRegisterCode(src)).toBytes());
        }
        throw new EncodeException("unsupported " + instruction.getOpcode() + " operand layout: " + instruction.getFormat());
    }

    private byte[] encodeXchg(Instruction instruction) {
        String dest = normalized(instruction.getDestReg());
        String src = normalized(instruction.getSrcReg());
        if (instruction.getFormat() != InstructionFormat.REG_REG) throw new EncodeException("unsupported XCHG operand layout: " + instruction.getFormat());
        ensureSameWidth(dest, src);
        if (!isByteRegister(dest) && "AX".equals(dest)) return new byte[] {(byte) (0x90 + generalRegisterCode(src))};
        if (!isByteRegister(src) && "AX".equals(src)) return new byte[] {(byte) (0x90 + generalRegisterCode(dest))};
        return concatenate(new byte[] {(byte) (isByteRegister(dest) ? 0x86 : 0x87)},
            ModRm.registerDirect(generalRegisterCode(src), generalRegisterCode(dest)).toBytes());
    }

    private byte[] encodeRelativeControlTransfer(Instruction instruction, int address) {
        int target = instruction.getAddress();
        if (instruction.getOpcode() == Opcode.CALL) {
            int displacement = target - (address + 3);
            if (displacement < Short.MIN_VALUE || displacement > Short.MAX_VALUE) throw new EncodeException("CALL target out of rel16 range: " + target);
            return new byte[] { (byte) 0xE8, (byte) displacement, (byte) (displacement >>> 8) };
        }
        if (instruction.getOpcode() == Opcode.JMP) {
            int shortDisplacement = target - (address + 2);
            if (shortDisplacement >= -128 && shortDisplacement <= 127) return new byte[] { (byte) 0xEB, (byte) shortDisplacement };
            int nearDisplacement = target - (address + 3);
            if (nearDisplacement < Short.MIN_VALUE || nearDisplacement > Short.MAX_VALUE) throw new EncodeException("JMP target out of rel16 range: " + target);
            return new byte[] { (byte) 0xE9, (byte) nearDisplacement, (byte) (nearDisplacement >>> 8) };
        }
        int displacement = target - (address + 2);
        if (displacement < -128 || displacement > 127) throw new EncodeException(instruction.getOpcode() + " target out of rel8 range: " + target);
        return new byte[] { (byte) controlOpcode(instruction.getOpcode()), (byte) displacement };
    }

    private byte[] encodeIncDec(Instruction instruction) {
        String target = normalized(instruction.getDestReg());
        if (isByteRegister(target)) return new byte[] { (byte) 0xFE, (byte) ((instruction.getOpcode() == Opcode.INC ? 0xC0 : 0xC8) | generalRegisterCode(target)) };
        return new byte[] { (byte) ((instruction.getOpcode() == Opcode.INC ? 0x40 : 0x48) + generalRegisterCode(target)) };
    }

    private byte[] encodePushPop(Instruction instruction) {
        String target = normalized(instruction.getDestReg());
        if (isByteRegister(target)) throw new EncodeException("8086 PUSH/POP requires a word operand");
        return new byte[] { (byte) ((instruction.getOpcode() == Opcode.PUSH ? 0x50 : 0x58) + generalRegisterCode(target)) };
    }

    private byte[] encodeUnary(Instruction instruction) {
        String target = normalized(instruction.getDestReg());
        int extension = instruction.getOpcode() == Opcode.NOT ? 2 : 3;
        boolean byteWidth = isByteRegister(target);
        return new byte[] { (byte) (byteWidth ? 0xF6 : 0xF7), (byte) (0xC0 | (extension << 3) | generalRegisterCode(target)) };
    }

    private static ModRm memoryOperand(Instruction instruction, int regField) {
        return ModRm.memory(regField, instruction.getBaseReg(), instruction.getIndexReg(), instruction.getDisplacement(), false);
    }

    private static byte[] concatenate(byte[] left, byte[] right) {
        byte[] both = new byte[left.length + right.length];
        System.arraycopy(left, 0, both, 0, left.length);
        System.arraycopy(right, 0, both, left.length, right.length);
        return both;
    }

    static int generalRegisterCode(String register) {
        return switch (normalized(register)) {
            case "AL", "AX" -> 0; case "CL", "CX" -> 1; case "DL", "DX" -> 2; case "BL", "BX" -> 3;
            case "AH", "SP" -> 4; case "CH", "BP" -> 5; case "DH", "SI" -> 6; case "BH", "DI" -> 7;
            default -> throw new EncodeException("not a general 8086 register: " + register);
        };
    }

    static int aluBase(Opcode opcode) {
        return switch (opcode) {
            case ADD -> 0x00; case OR -> 0x08; case ADC -> 0x10; case SBB -> 0x18;
            case AND -> 0x20; case SUB -> 0x28; case XOR -> 0x30; case CMP -> 0x38;
            case TEST -> 0x84;
            default -> throw new EncodeException("not an ALU binary opcode: " + opcode);
        };
    }

    static int aluExtension(Opcode opcode) { return (aluBase(opcode) >>> 3) & 7; }

    static int controlOpcode(Opcode opcode) {
        return switch (opcode) {
            case JO -> 0x70; case JNO -> 0x71; case JC_JB -> 0x72; case JNC_JNB -> 0x73;
            case JZ_JE -> 0x74; case JNZ_JNE -> 0x75; case JBE_JNA -> 0x76; case JNBE_JA -> 0x77;
            case JS -> 0x78; case JNS -> 0x79; case JP_JPE -> 0x7A; case JNP_JPO -> 0x7B;
            case JL_JNGE -> 0x7C; case JNL_JGE -> 0x7D; case JLE_JNG -> 0x7E; case JNLE_JG -> 0x7F;
            case LOOPNZ -> 0xE0; case LOOPZ -> 0xE1; case LOOP -> 0xE2; case JCXZ -> 0xE3;
            default -> throw new EncodeException("not an 8086 rel8 control-transfer opcode: " + opcode);
        };
    }

    static int fixedOpcode(Opcode opcode) {
        return switch (opcode) {
            case CLC -> 0xF8; case STC -> 0xF9; case CMC -> 0xF5; case CLD -> 0xFC;
            case STD -> 0xFD; case CLI -> 0xFA; case STI -> 0xFB; case PUSHF -> 0x9C;
            case POPF -> 0x9D; case LAHF -> 0x9F; case SAHF -> 0x9E; case AAA -> 0x37;
            case DAA -> 0x27; case AAS -> 0x3F; case DAS -> 0x2F; case AAM -> 0xD4;
            case AAD -> 0xD5; case CBW -> 0x98; case CWD -> 0x99; case RET -> 0xC3;
            case RETF -> 0xCB; case IRET -> 0xCF; case INTO -> 0xCE; case WAIT -> 0x9B;
            case XLAT -> 0xD7; case MOVSB -> 0xA4; case MOVSW -> 0xA5; case CMPSB -> 0xA6;
            case CMPSW -> 0xA7; case SCASB -> 0xAE; case SCASW -> 0xAF; case LODSB -> 0xAC;
            case LODSW -> 0xAD; case STOSB -> 0xAA; case STOSW -> 0xAB;
            default -> throw new EncodeException("not a fixed 8086 opcode: " + opcode);
        };
    }

    static String normalized(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    static boolean isByteRegister(String register) { return switch (normalized(register)) { case "AL", "CL", "DL", "BL", "AH", "CH", "DH", "BH" -> true; default -> false; }; }
    static boolean isMemory(String value) { return value != null && value.contains("["); }
    private static void ensureSameWidth(String first, String second) {
        if (isByteRegister(first) != isByteRegister(second)) throw new EncodeException("register widths differ: " + first + ", " + second);
    }
}
