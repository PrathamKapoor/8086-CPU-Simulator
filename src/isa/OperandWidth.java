package isa;

/**
 * OperandWidth — the data width of an operand or operation.
 */
public enum OperandWidth {
    BYTE(8),
    WORD(16),
    DWORD(32);   // Used for DX:AX pairs in MUL/DIV

    private final int bits;
    OperandWidth(int bits) { this.bits = bits; }
    public int getBits() { return bits; }
    public int getBytes() { return bits / 8; }
    public int getMask() { return (1 << bits) - 1; }
}
