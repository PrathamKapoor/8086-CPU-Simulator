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
            case MOV -> encodeMov(instruction);
            case ADD, OR, ADC, SBB, AND, SUB, XOR, CMP -> encodeAlu(instruction);
            case JMP, CALL, JZ_JE, JNZ_JNE, JC_JB, JNC_JNB, JO, JNO, JS, JNS,
                 JP_JPE, JNP_JPO, JL_JNGE, JNL_JGE, JLE_JNG, JNLE_JG, JBE_JNA,
                 JNBE_JA, LOOP, LOOPZ, LOOPNZ, JCXZ -> encodeRelativeControlTransfer(instruction, instructionAddress);
            default -> throw new EncodeException("8086 encoder does not yet support " + instruction.getOpcode());
        };
        return new EncodedInstruction(instruction, bytes);
    }

    private byte[] encodeMov(Instruction instruction) {
        String dest = normalized(instruction.getDestReg());
        String src = normalized(instruction.getSrcReg());
        if (instruction.getFormat() == InstructionFormat.REG_IMM || instruction.getFormat() == InstructionFormat.REG_IMM8) {
            boolean byteWidth = isByteRegister(dest);
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
        if (instruction.getFormat() == InstructionFormat.REG_IMM || instruction.getFormat() == InstructionFormat.REG_IMM8) {
            boolean byteWidth = isByteRegister(dest);
            int immediate = instruction.getImmediate();
            if (byteWidth && (immediate < -128 || immediate > 0xFF)) throw new EncodeException("imm8 outside range: " + immediate);
            if (!byteWidth && (immediate < Short.MIN_VALUE || immediate > 0xFFFF)) throw new EncodeException("imm16 outside range: " + immediate);
            ModRm modRm = ModRm.registerDirect(aluExtension(instruction.getOpcode()), generalRegisterCode(dest));
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

    static String normalized(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    static boolean isByteRegister(String register) { return switch (normalized(register)) { case "AL", "CL", "DL", "BL", "AH", "CH", "DH", "BH" -> true; default -> false; }; }
    static boolean isMemory(String value) { return value != null && value.contains("["); }
    private static void ensureSameWidth(String first, String second) {
        if (isByteRegister(first) != isByteRegister(second)) throw new EncodeException("register widths differ: " + first + ", " + second);
    }
}
