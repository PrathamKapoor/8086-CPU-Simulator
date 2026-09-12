package cpu.registers;

/**
 * FLAGS — condition codes updated by the ALU after every arithmetic/logic operation.
 *
 * 8086 flag layout (16-bit):
 *   Bit  0: CF — Carry Flag
 *   Bit  2: PF — Parity Flag (low byte)
 *   Bit  4: AF — Auxiliary Carry Flag (bit 3)
 *   Bit  6: ZF — Zero Flag
 *   Bit  7: SF — Sign Flag
 *   Bit  8: TF — Trap Flag
 *   Bit  9: IF — Interrupt Enable Flag
 *   Bit 10: DF — Direction Flag
 *   Bit 11: OF — Overflow Flag
 */
public class FLAGS extends Register {

    public static final int CF_BIT = 0;
    public static final int PF_BIT = 2;
    public static final int AF_BIT = 4;
    public static final int ZF_BIT = 6;
    public static final int SF_BIT = 7;
    public static final int TF_BIT = 8;
    public static final int IF_BIT = 9;
    public static final int DF_BIT = 10;
    public static final int OF_BIT = 11;

    public FLAGS() { super("FLAGS"); }

    public void setCarry(boolean v)     { setBit(CF_BIT, v); }
    public void setParity(boolean v)    { setBit(PF_BIT, v); }
    public void setAuxCarry(boolean v)  { setBit(AF_BIT, v); }
    public void setZero(boolean v)      { setBit(ZF_BIT, v); }
    public void setSign(boolean v)      { setBit(SF_BIT, v); }
    public void setTrap(boolean v)      { setBit(TF_BIT, v); }
    public void setInterrupt(boolean v) { setBit(IF_BIT, v); }
    public void setDirection(boolean v) { setBit(DF_BIT, v); }
    public void setOverflow(boolean v)  { setBit(OF_BIT, v); }

    public boolean isCarry()     { return getBit(CF_BIT); }
    public boolean isParity()    { return getBit(PF_BIT); }
    public boolean isAuxCarry()  { return getBit(AF_BIT); }
    public boolean isZero()      { return getBit(ZF_BIT); }
    public boolean isSign()      { return getBit(SF_BIT); }
    public boolean isTrap()      { return getBit(TF_BIT); }
    public boolean isInterrupt() { return getBit(IF_BIT); }
    public boolean isDirection() { return getBit(DF_BIT); }
    public boolean isOverflow()  { return getBit(OF_BIT); }

    private void setBit(int bit, boolean v) {
        if (v) value |=  (1 << bit);
        else   value &= ~(1 << bit);
    }

    private boolean getBit(int bit) {
        return ((value >> bit) & 1) == 1;
    }

    /**
     * Compute parity flag: PF=1 if number of set bits in low byte is even.
     */
    public static boolean computeParity(int result) {
        int low = result & 0xFF;
        int count = Integer.bitCount(low);
        return (count % 2) == 0;
    }

    /**
     * Compute auxiliary carry: AF=1 if carry from bit 3 to bit 4.
     */
    public static boolean computeAuxCarry(int a, int b, boolean isSub) {
        if (isSub) {
            return (a & 0xF) < (b & 0xF);
        }
        return ((a & 0xF) + (b & 0xF)) > 0xF;
    }

    /** Human-readable flags string for GUI display. */
    public String flagsString() {
        return "CF=" + b(isCarry())
             + " PF=" + b(isParity())
             + " AF=" + b(isAuxCarry())
             + " ZF=" + b(isZero())
             + " SF=" + b(isSign())
             + " OF=" + b(isOverflow());
    }

    /** Flags string with direction/interrupt/trap for detailed view. */
    public String flagsStringFull() {
        return flagsString()
             + " DF=" + b(isDirection())
             + " IF=" + b(isInterrupt())
             + " TF=" + b(isTrap());
    }

    private static int b(boolean v) { return v ? 1 : 0; }
}
