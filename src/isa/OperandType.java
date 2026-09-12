package isa;

/**
 * OperandType — what kind of operand an instruction accepts at each position.
 */
public enum OperandType {
    NONE,           // No operand (NOP, HLT, RET, IRET)
    REG16,          // 16-bit general register: AX,BX,CX,DX,SP,BP,SI,DI
    REG8,           // 8-bit register: AH,AL,BH,BL,CH,CL,DH,DL
    SEG_REG,        // Segment register: CS,DS,SS,ES
    IMM8,           // 8-bit immediate
    IMM16,          // 16-bit immediate
    IMM_SIGNED8,    // Sign-extended 8-bit immediate (for short jumps)
    MEM16,          // 16-bit memory reference (all effective addresses)
    MEM8,           // 8-bit memory reference
    REL8,           // Relative 8-bit offset (short jump)
    REL16,          // Relative 16-bit offset (near jump/call)
    FIXED_AL,       // Implicit AL register
    FIXED_AX,       // Implicit AX register
    FIXED_DXAX,     // Implicit DX:AX register pair
    FIXED_CL,       // Implicit CL register (shift count)
    FIXED_IP,       // Implicit IP (for stack operations)
    VECTOR8,        // Interrupt vector (0-255)
    STRING_SRC,     // Implicit DS:SI
    STRING_DST      // Implicit ES:DI
}
