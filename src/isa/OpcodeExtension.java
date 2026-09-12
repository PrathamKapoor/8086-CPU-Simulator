package isa;

/**
 * OpcodeExtension — /digit extension byte for ModR/M instructions.
 * The 8086 uses bits 5-3 of the ModR/M byte as an opcode extension
 * when the instruction has a fixed opcode but variable operation.
 */
public enum OpcodeExtension {
    NONE(-1),
    ADD(0),    // /0
    OR(1),     // /1
    ADC(2),    // /2
    SBB(3),    // /3
    AND(4),    // /4
    SUB(5),    // /5
    XOR(6),    // /6
    CMP(7);    // /7
    // Shift/Rotate group: /0=ROL, /1=ROR, /2=RCL, /3=RCR, /4=SHL, /5=SHR, /6= sal, /7=SAR
    // INC/DEC group: /0=INC, /1=DEC (for /r+ encoding)
    // PUSH/POP group: /0=PUSH, /1=POP (for segment registers)

    private final int value;
    OpcodeExtension(int value) { this.value = value; }
    public int getValue() { return value; }
    public boolean isDefined() { return value >= 0; }
}
