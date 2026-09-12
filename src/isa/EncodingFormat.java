package isa;

/**
 * EncodingFormat — how an instruction is encoded in machine code.
 * Models the 8086 instruction encoding formats.
 */
public enum EncodingFormat {
    SIMPLE,             // Single opcode byte, no operands (NOP, HLT, RET, etc.)
    REG_OPCODE,         // Opcode + /r extension in low 3 bits (INC/DEC/PUSH/POP reg)
    REG_MEM,            // Opcode + ModR/M byte (r/m16, r16 or r16, r/m16)
    REG_IMM,            // Opcode + imm16 (MOV reg, imm)
    REG_IMM8,           // Opcode + imm8 (MOV reg, imm8)
    MEM_IMM,            // Opcode + ModR/M + imm16 (ADD r/m16, imm16)
    MEM_IMM8,           // Opcode + ModR/M + imm8 (ADD r/m16, imm8)
    SHORT_JMP,          // Opcode + rel8
    NEAR_JMP,           // Opcode + rel16
    FIXED_REG,          // Opcode encodes both src and dst (MOV reg,AX etc.)
    FIXED_AL_IMM,        // Opcode + imm8, implicit AL operand (IN/OUT AL, imm8)
    FIXED_AX_IMM,       // Opcode + imm8/16, implicit AL/AX operand (IN/OUT imm8)
    FIXED_AL_DX,        // Opcode + implicit AL/AX:DX (IN/OUT DX)
    FIXED_DXAX,         // Implicit DX:AX pair (MUL/DIV/IMUL/IDIV)
    FIXED_CL,           // Implicit CL count (shifts)
    FIXED_AL,           // Implicit AL (XLAT)
    SEG_PREFIX,         // Segment override prefix + instruction
    STRING_OP,          // String instruction (no explicit operands)
    INT_BYTE,           // INT imm8
    MODRM               // Opcode + ModR/M byte (general, /r extension)
}
