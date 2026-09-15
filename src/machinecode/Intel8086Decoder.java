package machinecode;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.Opcode;

import java.util.Arrays;
import java.util.List;

/** Decodes one supported 8086 instruction from an explicit byte-stream offset. */
public final class Intel8086Decoder {
    public DecodedInstruction decode(byte[] bytes, int offset) {
        ByteCursor cursor = new ByteCursor(bytes, offset);
        int opcode = cursor.readU8();
        Instruction instruction;
        if (opcode >= 0xB0 && opcode <= 0xB7) {
            String register = byteRegister(opcode & 7);
            int immediate = cursor.readU8();
            instruction = new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_IMM8)
                .dest(register).imm(immediate).raw("MOV " + register + ", " + hex(immediate, 2)).build();
        } else if (opcode >= 0xB8 && opcode <= 0xBF) {
            String register = wordRegister(opcode & 7);
            int immediate = cursor.readU16LE();
            instruction = new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_IMM)
                .dest(register).imm(immediate).raw("MOV " + register + ", " + hex(immediate, 4)).build();
        } else {
            instruction = switch (opcode) {
                case 0x90 -> new Instruction.Builder(Opcode.NOP).format(InstructionFormat.NO_OPERAND).raw("NOP").build();
                case 0xF4 -> new Instruction.Builder(Opcode.HLT).format(InstructionFormat.NO_OPERAND).raw("HLT").build();
                case 0x88, 0x89, 0x8A, 0x8B -> decodeMov(cursor, opcode);
                case 0x00, 0x01, 0x02, 0x03, 0x08, 0x09, 0x0A, 0x0B,
                     0x10, 0x11, 0x12, 0x13, 0x18, 0x19, 0x1A, 0x1B,
                     0x20, 0x21, 0x22, 0x23, 0x28, 0x29, 0x2A, 0x2B,
                     0x30, 0x31, 0x32, 0x33, 0x38, 0x39, 0x3A, 0x3B -> decodeAluModRm(cursor, opcode);
                case 0x80, 0x81 -> decodeAluImmediate(cursor, opcode);
                case 0xEB -> decodeRelative(cursor, Opcode.JMP, 2, 1, offset);
                case 0xE9 -> decodeRelative(cursor, Opcode.JMP, 3, 2, offset);
                case 0xE8 -> decodeRelative(cursor, Opcode.CALL, 3, 2, offset);
                case 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,
                     0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F,
                     0xE0, 0xE1, 0xE2, 0xE3 -> decodeRelative(cursor, controlOpcode(opcode), 2, 1, offset);
                case 0xD4, 0xD5 -> decodeAdjust(cursor, opcode);
                case 0xCD -> decodeInterrupt(cursor);
                case 0x27, 0x2F, 0x37, 0x3F, 0x98, 0x99, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F,
                     0xA4, 0xA5, 0xA6, 0xA7, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF,
                     0xC3, 0xCB, 0xCE, 0xCF, 0xD7, 0xF5, 0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD -> fixedInstruction(opcode);
                default -> throw new DecodeException("Unsupported 8086 opcode 0x" + String.format("%02X", opcode) + " at offset " + offset);
            };
        }
        int length = cursor.position() - offset;
        return new DecodedInstruction(offset, Arrays.copyOfRange(bytes, offset, cursor.position()), length, instruction, List.of());
    }

    private Instruction decodeMov(ByteCursor cursor, int opcode) {
        boolean byteWidth = (opcode & 1) == 0;
        boolean destinationIsReg = opcode == 0x8A || opcode == 0x8B;
        ModRm modRm = ModRm.decode(cursor);
        String reg = byteWidth ? byteRegister(modRm.reg()) : wordRegister(modRm.reg());
        if (modRm.registerDirect()) {
            String rm = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
            String dest = destinationIsReg ? reg : rm;
            String src = destinationIsReg ? rm : reg;
            return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_REG).dest(dest).src(src)
                .raw("MOV " + dest + ", " + src).build();
        }
        String memory = memoryText(modRm);
        if (destinationIsReg) {
            return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_REG_INDIRECT).dest(reg).src(memory)
                .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
                .raw("MOV " + reg + ", " + memory).build();
        }
        return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_INDIRECT_REG).dest(memory).src(reg)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
            .raw("MOV " + memory + ", " + reg).build();
    }

    private Instruction decodeAluModRm(ByteCursor cursor, int opcode) {
        boolean byteWidth = (opcode & 1) == 0;
        boolean destinationIsReg = (opcode & 2) != 0;
        Opcode semanticOpcode = aluOpcode((opcode & 0xF8) >>> 3);
        ModRm modRm = ModRm.decode(cursor);
        String reg = byteWidth ? byteRegister(modRm.reg()) : wordRegister(modRm.reg());
        if (modRm.registerDirect()) {
            String rm = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
            String dest = destinationIsReg ? reg : rm;
            String src = destinationIsReg ? rm : reg;
            return new Instruction.Builder(semanticOpcode).format(InstructionFormat.REG_REG).dest(dest).src(src)
                .raw(semanticOpcode + " " + dest + ", " + src).build();
        }
        String memory = memoryText(modRm);
        if (destinationIsReg) {
            return new Instruction.Builder(semanticOpcode).format(InstructionFormat.REG_REG_INDIRECT).dest(reg).src(memory)
                .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
                .raw(semanticOpcode + " " + reg + ", " + memory).build();
        }
        return new Instruction.Builder(semanticOpcode).format(InstructionFormat.REG_INDIRECT_REG).dest(memory).src(reg)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
            .raw(semanticOpcode + " " + memory + ", " + reg).build();
    }

    private Instruction decodeAluImmediate(ByteCursor cursor, int opcode) {
        boolean byteWidth = opcode == 0x80;
        ModRm modRm = ModRm.decode(cursor);
        Opcode semanticOpcode = aluOpcode(modRm.reg());
        int immediate = byteWidth ? cursor.readU8() : cursor.readU16LE();
        String target = modRm.registerDirect() ? (byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm())) : memoryText(modRm);
        InstructionFormat format = modRm.registerDirect() ? (byteWidth ? InstructionFormat.REG_IMM8 : InstructionFormat.REG_IMM) : InstructionFormat.REG_INDIRECT_IMM;
        return new Instruction.Builder(semanticOpcode).format(format).dest(target).imm(immediate)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
            .raw(semanticOpcode + " " + target + ", " + hex(immediate, byteWidth ? 2 : 4)).build();
    }

    private Instruction decodeRelative(ByteCursor cursor, Opcode opcode, int length, int displacementBytes, int startOffset) {
        int displacement = displacementBytes == 1 ? (byte) cursor.readU8() : (short) cursor.readU16LE();
        int target = startOffset + length + displacement;
        return new Instruction.Builder(opcode).format(InstructionFormat.REL_ONLY).addr(target)
            .raw(opcode + " " + target).build();
    }

    private Instruction decodeAdjust(ByteCursor cursor, int opcode) {
        Opcode semantic = opcode == 0xD4 ? Opcode.AAM : Opcode.AAD;
        int base = cursor.readU8();
        return new Instruction.Builder(semantic).format(InstructionFormat.NO_OPERAND).imm(base)
            .raw(semantic + " " + hex(base, 2)).build();
    }

    private Instruction decodeInterrupt(ByteCursor cursor) {
        int vector = cursor.readU8();
        return new Instruction.Builder(Opcode.INT).format(InstructionFormat.IMM_ONLY).imm(vector)
            .raw("INT " + hex(vector, 2)).build();
    }

    private static Instruction fixedInstruction(int opcode) {
        Opcode semantic = switch (opcode) {
            case 0xF8 -> Opcode.CLC; case 0xF9 -> Opcode.STC; case 0xF5 -> Opcode.CMC; case 0xFC -> Opcode.CLD;
            case 0xFD -> Opcode.STD; case 0xFA -> Opcode.CLI; case 0xFB -> Opcode.STI; case 0x9C -> Opcode.PUSHF;
            case 0x9D -> Opcode.POPF; case 0x9F -> Opcode.LAHF; case 0x9E -> Opcode.SAHF; case 0x37 -> Opcode.AAA;
            case 0x27 -> Opcode.DAA; case 0x3F -> Opcode.AAS; case 0x2F -> Opcode.DAS; case 0x98 -> Opcode.CBW;
            case 0x99 -> Opcode.CWD; case 0xC3 -> Opcode.RET; case 0xCB -> Opcode.RETF; case 0xCF -> Opcode.IRET;
            case 0xCE -> Opcode.INTO; case 0x9B -> Opcode.WAIT; case 0xD7 -> Opcode.XLAT; case 0xA4 -> Opcode.MOVSB;
            case 0xA5 -> Opcode.MOVSW; case 0xA6 -> Opcode.CMPSB; case 0xA7 -> Opcode.CMPSW; case 0xAA -> Opcode.STOSB;
            case 0xAB -> Opcode.STOSW; case 0xAC -> Opcode.LODSB; case 0xAD -> Opcode.LODSW; case 0xAE -> Opcode.SCASB;
            case 0xAF -> Opcode.SCASW; default -> throw new DecodeException("not a fixed 8086 opcode: 0x" + String.format("%02X", opcode));
        };
        InstructionFormat format = switch (semantic) {
            case MOVSB, MOVSW, CMPSB, CMPSW, SCASB, SCASW, LODSB, LODSW, STOSB, STOSW -> InstructionFormat.STRING_ONLY;
            default -> InstructionFormat.NO_OPERAND;
        };
        return new Instruction.Builder(semantic).format(format).raw(semantic.toString()).build();
    }

    private static Opcode aluOpcode(int extension) {
        return switch (extension) {
            case 0 -> Opcode.ADD; case 1 -> Opcode.OR; case 2 -> Opcode.ADC; case 3 -> Opcode.SBB;
            case 4 -> Opcode.AND; case 5 -> Opcode.SUB; case 6 -> Opcode.XOR; case 7 -> Opcode.CMP;
            default -> throw new DecodeException("invalid ALU opcode extension: " + extension);
        };
    }

    private static Opcode controlOpcode(int opcode) {
        return switch (opcode) {
            case 0x70 -> Opcode.JO; case 0x71 -> Opcode.JNO; case 0x72 -> Opcode.JC_JB; case 0x73 -> Opcode.JNC_JNB;
            case 0x74 -> Opcode.JZ_JE; case 0x75 -> Opcode.JNZ_JNE; case 0x76 -> Opcode.JBE_JNA; case 0x77 -> Opcode.JNBE_JA;
            case 0x78 -> Opcode.JS; case 0x79 -> Opcode.JNS; case 0x7A -> Opcode.JP_JPE; case 0x7B -> Opcode.JNP_JPO;
            case 0x7C -> Opcode.JL_JNGE; case 0x7D -> Opcode.JNL_JGE; case 0x7E -> Opcode.JLE_JNG; case 0x7F -> Opcode.JNLE_JG;
            case 0xE0 -> Opcode.LOOPNZ; case 0xE1 -> Opcode.LOOPZ; case 0xE2 -> Opcode.LOOP; case 0xE3 -> Opcode.JCXZ;
            default -> throw new DecodeException("not an 8086 rel8 control-transfer opcode: 0x" + String.format("%02X", opcode));
        };
    }

    static String byteRegister(int code) { return new String[] { "AL", "CL", "DL", "BL", "AH", "CH", "DH", "BH" }[code]; }
    static String wordRegister(int code) { return new String[] { "AX", "CX", "DX", "BX", "SP", "BP", "SI", "DI" }[code]; }
    static String hex(int value, int digits) { return String.format("%0" + digits + "XH", value & ((1 << (digits * 4)) - 1)); }

    static String memoryText(ModRm modRm) {
        if (modRm.directAddress()) return "[" + hex(modRm.displacement(), 4) + "]";
        StringBuilder builder = new StringBuilder("[");
        if (modRm.baseRegister() != null) builder.append(modRm.baseRegister());
        if (modRm.indexRegister() != null) { if (builder.length() > 1) builder.append('+'); builder.append(modRm.indexRegister()); }
        int displacement = modRm.displacement();
        if (displacement > 0) builder.append('+').append(displacement);
        if (displacement < 0) builder.append(displacement);
        return builder.append(']').toString();
    }
}
