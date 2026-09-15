package machinecode;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.Opcode;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/** Decodes one supported 8086 instruction from an explicit byte-stream offset. */
public final class Intel8086Decoder {
    public List<DecodedInstruction> decodeAll(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("bytes must not be null");
        List<DecodedInstruction> decoded = new ArrayList<>();
        int offset = 0;
        while (offset < bytes.length) {
            DecodedInstruction instruction = decode(bytes, offset);
            decoded.add(instruction);
            offset = instruction.nextOffset();
        }
        return List.copyOf(decoded);
    }

    public DecodedInstruction decode(byte[] bytes, int offset) {
        ByteCursor cursor = new ByteCursor(bytes, offset);
        int opcode = cursor.readU8();
        List<Integer> prefixBytes = new ArrayList<>();
        Opcode repeatPrefix = null;
        String segmentOverride = null;
        while (opcode == 0x26 || opcode == 0x2E || opcode == 0x36 || opcode == 0x3E || opcode == 0xF2 || opcode == 0xF3) {
            prefixBytes.add(opcode);
            if (opcode == 0xF2 || opcode == 0xF3) {
                if (repeatPrefix != null) throw new DecodeException("duplicate repeat prefix at offset " + offset);
                repeatPrefix = opcode == 0xF2 ? Opcode.REPNE : Opcode.REP;
            } else {
                if (segmentOverride != null) throw new DecodeException("duplicate segment override at offset " + offset);
                segmentOverride = switch (opcode) { case 0x26 -> "ES"; case 0x2E -> "CS"; case 0x36 -> "SS"; default -> "DS"; };
            }
            opcode = cursor.readU8();
        }
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
                case 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97 -> new Instruction.Builder(Opcode.XCHG).format(InstructionFormat.REG_REG)
                    .dest("AX").src(wordRegister(opcode & 7)).raw("XCHG AX, " + wordRegister(opcode & 7)).build();
                case 0xF4 -> new Instruction.Builder(Opcode.HLT).format(InstructionFormat.NO_OPERAND).raw("HLT").build();
                case 0x88, 0x89, 0x8A, 0x8B -> decodeMov(cursor, opcode);
                case 0x8C, 0x8E -> decodeMovSeg(cursor, opcode);
                case 0xC6, 0xC7 -> decodeMovImmediate(cursor, opcode);
                case 0xA0, 0xA1, 0xA2, 0xA3 -> decodeMovAccumulator(cursor, opcode);
                case 0x86, 0x87 -> decodeXchg(cursor, opcode);
                case 0x00, 0x01, 0x02, 0x03, 0x08, 0x09, 0x0A, 0x0B,
                     0x10, 0x11, 0x12, 0x13, 0x18, 0x19, 0x1A, 0x1B,
                     0x20, 0x21, 0x22, 0x23, 0x28, 0x29, 0x2A, 0x2B,
                     0x30, 0x31, 0x32, 0x33, 0x38, 0x39, 0x3A, 0x3B, 0x84, 0x85 -> decodeAluModRm(cursor, opcode);
                case 0x80, 0x81 -> decodeAluImmediate(cursor, opcode);
                case 0xA8, 0xA9 -> decodeTestAccumulator(cursor, opcode);
                case 0xEB -> decodeRelative(cursor, Opcode.JMP, 2, 1, offset);
                case 0xE9 -> decodeRelative(cursor, Opcode.JMP, 3, 2, offset);
                case 0xE8 -> decodeRelative(cursor, Opcode.CALL, 3, 2, offset);
                case 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,
                     0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F,
                     0xE0, 0xE1, 0xE2, 0xE3 -> decodeRelative(cursor, controlOpcode(opcode), 2, 1, offset);
                case 0xD4, 0xD5 -> decodeAdjust(cursor, opcode);
                case 0xCD -> decodeInterrupt(cursor);
                case 0xCC -> new Instruction.Builder(Opcode.INT).format(InstructionFormat.IMM_ONLY).imm(3).raw("INT3").build();
                case 0xC2 -> decodeRetImm(cursor);
                case 0x27, 0x2F, 0x37, 0x3F, 0x98, 0x99, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F,
                     0xA4, 0xA5, 0xA6, 0xA7, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF,
                     0xC3, 0xCB, 0xCE, 0xCF, 0xD7, 0xF5, 0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD -> fixedInstruction(opcode);
                case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47 -> registerUnary(opcode, Opcode.INC);
                case 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F -> registerUnary(opcode, Opcode.DEC);
                case 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57 -> registerUnary(opcode, Opcode.PUSH);
                case 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F -> registerUnary(opcode, Opcode.POP);
                case 0x06 -> segRegOp(Opcode.PUSH, "ES");
                case 0x0E -> segRegOp(Opcode.PUSH, "CS");
                case 0x16 -> segRegOp(Opcode.PUSH, "SS");
                case 0x1E -> segRegOp(Opcode.PUSH, "DS");
                case 0x07 -> segRegOp(Opcode.POP, "ES");
                case 0x17 -> segRegOp(Opcode.POP, "SS");
                case 0x1F -> segRegOp(Opcode.POP, "DS");
                case 0x8D -> decodeLeaLdsLes(cursor, Opcode.LEA);
                case 0xC5 -> decodeLeaLdsLes(cursor, Opcode.LDS);
                case 0xC4 -> decodeLeaLdsLes(cursor, Opcode.LES);
                case 0xD0, 0xD1 -> decodeShift(cursor, opcode);
                case 0xE4, 0xE5 -> decodeInFixed(cursor, opcode, false);
                case 0xEC, 0xED -> decodeInFixed(cursor, opcode, true);
                case 0xE6, 0xE7 -> decodeOutFixed(cursor, opcode, false);
                case 0xEE, 0xEF -> decodeOutFixed(cursor, opcode, true);
                case 0xF6, 0xF7 -> decodeF6F7Group(cursor, opcode);
                case 0xFE -> decodeFEGroup(cursor);
                case 0xFF -> decodeFFGroup(cursor);
                case 0x8F -> decodePopMemory(cursor);
                default -> throw new DecodeException("Unsupported 8086 opcode 0x" + String.format("%02X", opcode) + " at offset " + offset);
            };
        }
        int length = cursor.position() - offset;
        byte[] raw = Arrays.copyOfRange(bytes, offset, cursor.position());
        instruction = copyWithEncoded(instruction, raw, repeatPrefix, segmentOverride);
        return new DecodedInstruction(offset, raw, length, instruction, prefixBytes);
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
        Opcode semanticOpcode = opcode == 0x84 || opcode == 0x85 ? Opcode.TEST : aluOpcode((opcode & 0xF8) >>> 3);
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

    private Instruction decodeXchg(ByteCursor cursor, int opcode) {
        boolean byteWidth = opcode == 0x86;
        ModRm modRm = ModRm.decode(cursor);
        if (modRm.registerDirect()) {
            String dest = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
            String src = byteWidth ? byteRegister(modRm.reg()) : wordRegister(modRm.reg());
            return new Instruction.Builder(Opcode.XCHG).format(InstructionFormat.REG_REG).dest(dest).src(src)
                .raw("XCHG " + dest + ", " + src).build();
        }
        String reg = byteWidth ? byteRegister(modRm.reg()) : wordRegister(modRm.reg());
        String memory = memoryText(modRm);
        return new Instruction.Builder(Opcode.XCHG).format(InstructionFormat.REG_REG_INDIRECT).dest(reg).src(memory)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
            .raw("XCHG " + reg + ", " + memory).build();
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

    private static Instruction registerUnary(int opcode, Opcode semantic) {
        String register = wordRegister(opcode & 7);
        return new Instruction.Builder(semantic).format(InstructionFormat.REG_ONLY).dest(register).src(register)
            .raw(semantic + " " + register).build();
    }

    /** Group 3 (0xF6/0xF7): TEST imm (/0), NOT (/2), NEG (/3), MUL/IMUL/DIV/IDIV (/4../7). */
    private Instruction decodeF6F7Group(ByteCursor cursor, int opcode) {
        boolean byteWidth = opcode == 0xF6;
        ModRm modRm = ModRm.decode(cursor);
        int ext = modRm.reg();
        if (ext == 1) throw new DecodeException("invalid 8086 opcode extension /1 for 0x" + String.format("%02X", opcode));
        if (ext == 0) {
            int immediate = byteWidth ? cursor.readU8() : cursor.readU16LE();
            if (modRm.registerDirect()) {
                String register = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
                return new Instruction.Builder(Opcode.TEST).format(byteWidth ? InstructionFormat.REG_IMM8 : InstructionFormat.REG_IMM)
                    .dest(register).imm(immediate).raw("TEST " + register + ", " + hex(immediate, byteWidth ? 2 : 4)).build();
            }
            String memory = memoryText(modRm);
            return new Instruction.Builder(Opcode.TEST).format(InstructionFormat.REG_INDIRECT_IMM)
                .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement()).imm(immediate)
                .raw("TEST " + (byteWidth ? "BYTE " : "WORD ") + memory + ", " + hex(immediate, byteWidth ? 2 : 4)).build();
        }
        if (ext == 2 || ext == 3) {
            Opcode semantic = ext == 2 ? Opcode.NOT : Opcode.NEG;
            if (modRm.registerDirect()) {
                String register = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
                return new Instruction.Builder(semantic).format(InstructionFormat.REG_ONLY).dest(register).src(register)
                    .raw(semantic + " " + register).build();
            }
            String memory = memoryText(modRm);
            return new Instruction.Builder(semantic).format(InstructionFormat.ADDR_ONLY)
                .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
                .raw(semantic + " " + (byteWidth ? "BYTE " : "WORD ") + memory).build();
        }
        if (!modRm.registerDirect()) {
            throw new DecodeException("MUL/IMUL/DIV/IDIV memory operands are not supported by the semantic executor (0x"
                + String.format("%02X", opcode) + " /" + ext + ")");
        }
        Opcode semantic = switch (ext) {
            case 4 -> Opcode.MUL; case 5 -> Opcode.IMUL; case 6 -> Opcode.DIV; case 7 -> Opcode.IDIV;
            default -> throw new DecodeException("unreachable opcode extension /" + ext);
        };
        String register = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
        return new Instruction.Builder(semantic).format(InstructionFormat.REG_ONLY).dest(register)
            .raw(semantic + " " + register).build();
    }

    /** Group 4 (0xFE): INC/DEC r/m8 — register-direct only. */
    private Instruction decodeFEGroup(ByteCursor cursor) {
        ModRm modRm = ModRm.decode(cursor);
        if (modRm.reg() > 1) throw new DecodeException("invalid opcode extension /" + modRm.reg() + " for 0xFE");
        if (!modRm.registerDirect()) throw new DecodeException("byte INC/DEC memory operands are not supported by the semantic executor (0xFE /" + modRm.reg() + ")");
        Opcode semantic = modRm.reg() == 0 ? Opcode.INC : Opcode.DEC;
        String register = byteRegister(modRm.rm());
        return new Instruction.Builder(semantic).format(InstructionFormat.REG_ONLY).dest(register).src(register)
            .raw(semantic + " " + register).build();
    }

    /** Group 5 (0xFF): INC/DEC r/m16 (/0,/1), PUSH r/m16 (/6); indirect/far control transfer (/2,/3,/4,/5) and /7 are unsupported. */
    private Instruction decodeFFGroup(ByteCursor cursor) {
        ModRm modRm = ModRm.decode(cursor);
        int ext = modRm.reg();
        if (ext == 0 || ext == 1) {
            Opcode semantic = ext == 0 ? Opcode.INC : Opcode.DEC;
            if (modRm.registerDirect()) {
                String register = wordRegister(modRm.rm());
                return new Instruction.Builder(semantic).format(InstructionFormat.REG_ONLY).dest(register).src(register)
                    .raw(semantic + " " + register).build();
            }
            String memory = memoryText(modRm);
            return new Instruction.Builder(semantic).format(InstructionFormat.ADDR_ONLY)
                .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
                .raw(semantic + " WORD " + memory).build();
        }
        if (ext == 6) {
            if (modRm.registerDirect()) {
                String register = wordRegister(modRm.rm());
                return new Instruction.Builder(Opcode.PUSH).format(InstructionFormat.REG_ONLY).dest(register).src(register)
                    .raw("PUSH " + register).build();
            }
            String memory = memoryText(modRm);
            return new Instruction.Builder(Opcode.PUSH).format(InstructionFormat.REG_REG_INDIRECT)
                .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
                .raw("PUSH WORD " + memory).build();
        }
        throw new DecodeException("8086 group FF /" + ext
            + " (indirect/far control transfer or a reserved extension) is not supported by the instruction-index execution model");
    }

    /** 0x8F /0: POP r/m16. */
    private Instruction decodePopMemory(ByteCursor cursor) {
        ModRm modRm = ModRm.decode(cursor);
        if (modRm.reg() != 0) throw new DecodeException("invalid opcode extension /" + modRm.reg() + " for 0x8F");
        if (modRm.registerDirect()) {
            String register = wordRegister(modRm.rm());
            return new Instruction.Builder(Opcode.POP).format(InstructionFormat.REG_ONLY).dest(register).src(register)
                .raw("POP " + register).build();
        }
        String memory = memoryText(modRm);
        return new Instruction.Builder(Opcode.POP).format(InstructionFormat.REG_REG_INDIRECT)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
            .raw("POP WORD " + memory).build();
    }

    private static Instruction segRegOp(Opcode opcode, String segment) {
        return new Instruction.Builder(opcode).format(InstructionFormat.SEG_REG_ONLY).dest(segment).src(segment)
            .raw(opcode + " " + segment).build();
    }

    private Instruction decodeMovSeg(ByteCursor cursor, int opcode) {
        ModRm modRm = ModRm.decode(cursor);
        String seg = segmentRegister(modRm.reg());
        boolean destIsSeg = opcode == 0x8E;
        if (modRm.registerDirect()) {
            String gpr = wordRegister(modRm.rm());
            String dest = destIsSeg ? seg : gpr;
            String src = destIsSeg ? gpr : seg;
            return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_SEG).dest(dest).src(src)
                .raw("MOV " + dest + ", " + src).build();
        }
        if (!destIsSeg) throw new DecodeException("MOV [mem], Sreg (storing a segment register to memory) is not supported by the semantic executor");
        String memory = memoryText(modRm);
        return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.SEG_REG_REG).dest(seg).src(memory)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
            .raw("MOV " + seg + ", " + memory).build();
    }

    private Instruction decodeMovImmediate(ByteCursor cursor, int opcode) {
        boolean byteWidth = opcode == 0xC6;
        ModRm modRm = ModRm.decode(cursor);
        if (modRm.reg() != 0) throw new DecodeException("invalid opcode extension /" + modRm.reg() + " for 0x" + String.format("%02X", opcode));
        int immediate = byteWidth ? cursor.readU8() : cursor.readU16LE();
        if (modRm.registerDirect()) {
            String register = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
            return new Instruction.Builder(Opcode.MOV).format(byteWidth ? InstructionFormat.REG_IMM8 : InstructionFormat.REG_IMM)
                .dest(register).imm(immediate).raw("MOV " + register + ", " + hex(immediate, byteWidth ? 2 : 4)).build();
        }
        String memory = memoryText(modRm);
        return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_INDIRECT_IMM)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement()).imm(immediate)
            .raw("MOV " + (byteWidth ? "BYTE " : "WORD ") + memory + ", " + hex(immediate, byteWidth ? 2 : 4)).build();
    }

    private Instruction decodeMovAccumulator(ByteCursor cursor, int opcode) {
        boolean byteWidth = (opcode & 1) == 0;
        boolean accumulatorIsDest = opcode == 0xA0 || opcode == 0xA1;
        int addr = cursor.readU16LE();
        String accumulator = byteWidth ? "AL" : "AX";
        String memory = "[" + hex(addr, 4) + "]";
        if (accumulatorIsDest) {
            return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_REG_INDIRECT).dest(accumulator).src(memory)
                .disp(addr).raw("MOV " + accumulator + ", " + memory).build();
        }
        return new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_INDIRECT_REG).dest(memory).src(accumulator)
            .disp(addr).raw("MOV " + memory + ", " + accumulator).build();
    }

    private Instruction decodeTestAccumulator(ByteCursor cursor, int opcode) {
        boolean byteWidth = opcode == 0xA8;
        String accumulator = byteWidth ? "AL" : "AX";
        int immediate = byteWidth ? cursor.readU8() : cursor.readU16LE();
        return new Instruction.Builder(Opcode.TEST).format(byteWidth ? InstructionFormat.REG_IMM8 : InstructionFormat.REG_IMM)
            .dest(accumulator).imm(immediate).raw("TEST " + accumulator + ", " + hex(immediate, byteWidth ? 2 : 4)).build();
    }

    private Instruction decodeLeaLdsLes(ByteCursor cursor, Opcode opcode) {
        ModRm modRm = ModRm.decode(cursor);
        if (modRm.registerDirect()) throw new DecodeException(opcode + " requires a memory source operand, not register-direct ModR/M");
        String dest = wordRegister(modRm.reg());
        String memory = memoryText(modRm);
        return new Instruction.Builder(opcode).format(InstructionFormat.REG_REG_INDIRECT).dest(dest).src(memory)
            .baseReg(modRm.baseRegister()).indexReg(modRm.indexRegister()).disp(modRm.displacement())
            .raw(opcode + " " + dest + ", " + memory).build();
    }

    private Instruction decodeShift(ByteCursor cursor, int opcode) {
        boolean byteWidth = opcode == 0xD0;
        ModRm modRm = ModRm.decode(cursor);
        if (!modRm.registerDirect()) throw new DecodeException("shift/rotate memory operands are not supported by the semantic executor");
        Opcode semantic = switch (modRm.reg()) {
            case 0 -> Opcode.ROL; case 1 -> Opcode.ROR; case 2 -> Opcode.RCL; case 3 -> Opcode.RCR;
            case 4 -> Opcode.SHL_SAL; case 5 -> Opcode.SHR; case 7 -> Opcode.SAR;
            default -> throw new DecodeException("invalid shift/rotate opcode extension /" + modRm.reg());
        };
        String register = byteWidth ? byteRegister(modRm.rm()) : wordRegister(modRm.rm());
        return new Instruction.Builder(semantic).format(InstructionFormat.REG_IMM).dest(register).imm(1)
            .raw(semantic + " " + register + ", 1").build();
    }

    private Instruction decodeInFixed(ByteCursor cursor, int opcode, boolean dx) {
        boolean isAL = opcode == 0xE4 || opcode == 0xEC;
        String dest = isAL ? "AL" : "AX";
        if (dx) {
            return new Instruction.Builder(Opcode.IN).format(isAL ? InstructionFormat.FIXED_AL_DX : InstructionFormat.FIXED_AX_DX)
                .dest(dest).src("DX").raw("IN " + dest + ", DX").build();
        }
        int port = cursor.readU8();
        return new Instruction.Builder(Opcode.IN).format(isAL ? InstructionFormat.FIXED_AL_IMM : InstructionFormat.FIXED_AX_IMM)
            .dest(dest).src(hex(port, 2)).imm(port).raw("IN " + dest + ", " + hex(port, 2)).build();
    }

    private Instruction decodeOutFixed(ByteCursor cursor, int opcode, boolean dx) {
        boolean isAL = opcode == 0xE6 || opcode == 0xEE;
        String src = isAL ? "AL" : "AX";
        if (dx) {
            return new Instruction.Builder(Opcode.OUT).format(isAL ? InstructionFormat.FIXED_AL_DX : InstructionFormat.FIXED_AX_DX)
                .dest("DX").src(src).raw("OUT DX, " + src).build();
        }
        int port = cursor.readU8();
        return new Instruction.Builder(Opcode.OUT).format(isAL ? InstructionFormat.FIXED_AL_IMM : InstructionFormat.FIXED_AX_IMM)
            .dest(src).src(src).imm(port).raw("OUT " + hex(port, 2) + ", " + src).build();
    }

    private Instruction decodeRetImm(ByteCursor cursor) {
        int imm = cursor.readU16LE();
        return new Instruction.Builder(Opcode.RET).format(InstructionFormat.IMM_ONLY).imm(imm)
            .raw("RET " + hex(imm, 4)).build();
    }

    private static String segmentRegister(int code) {
        return switch (code) { case 0 -> "ES"; case 1 -> "CS"; case 2 -> "SS"; case 3 -> "DS";
            default -> throw new DecodeException("invalid segment register encoding: " + code); };
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

    private static Instruction copyWithEncoded(Instruction instruction, byte[] encoded, Opcode prefix, String segmentOverride) {
        return new Instruction.Builder(instruction.getOpcode()).format(instruction.getFormat())
            .dest(instruction.getDestReg()).src(instruction.getSrcReg()).imm(instruction.getImmediate())
            .addr(instruction.getAddress()).raw(instruction.getRawText()).baseReg(instruction.getBaseReg())
            .indexReg(instruction.getIndexReg()).disp(instruction.getDisplacement()).segOverride(segmentOverride == null ? instruction.getSegmentOverride() : segmentOverride)
            .encoded(encoded).prefix(prefix == null ? instruction.getPrefix() : prefix).build();
    }
}
