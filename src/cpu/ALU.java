package cpu;

import cpu.registers.FLAGS;

/**
 * ALU — Arithmetic Logic Unit for 8086 CPU.
 *
 * Full 8086 flag behavior:
 * - INC/DEC preserve CF (real 8086 behavior)
 * - ADD/SUB/ADC/SBB set all 6 arithmetic flags
 * - AND/OR/XOR/TEST clear CF and OF, set SF/ZF/PF/AF
 * - MUL/IMUL set CF=OF=(high half != 0), undefined SF/ZF/PF/AF
 * - DIV/IDIV: all flags undefined
 * - SHL/SHR/SAR/ROL/ROR/RCL/RCR set CF, OF as per 8086 spec
 * - NEG: CF=1 if operand != 0
 *
 * Signed multiply: IMUL uses signed arithmetic.
 * Signed divide: IDIV uses signed arithmetic, quotient truncates toward zero.
 */
public class ALU {

    public enum Operation {
        ADD, SUB, ADC, SBB, INC, DEC, NEG, AND, OR, XOR, NOT, CMP, TEST,
        MUL, IMUL, DIV, IDIV,
        SHL, SHR, SAR, ROL, ROR, RCL, RCR
    }

    private final FLAGS flags;
    private int       lastResult;
    private int       lastA;
    private int       lastB;
    private Operation lastOperation;
    private boolean   active;

    public ALU(FLAGS flags) {
        this.flags = flags;
    }

    /**
     * Execute an ALU operation and update FLAGS.
     * @return 16-bit result (operand1 is returned unchanged for CMP/TEST)
     */
    public int execute(Operation op, int operand1, int operand2) {
        lastOperation = op;
        lastA         = operand1 & 0xFFFF;
        lastB         = operand2 & 0xFFFF;
        active        = true;

        final int MASK = 0xFFFF;
        operand1 &= MASK;
        operand2 &= MASK;

        int result;
        boolean oldCF = flags.isCarry();

        switch (op) {

            // ---- Arithmetic ----
            case ADD -> {
                int full = operand1 + operand2;
                result = full & MASK;
                flags.setCarry(full > MASK);
                flags.setOverflow(((operand1 ^ result) & (operand2 ^ result) & 0x8000) != 0);
                flags.setAuxCarry(((operand1 ^ operand2 ^ result) & 0x10) != 0);
            }
            case ADC -> {
                int carry = oldCF ? 1 : 0;
                int full = operand1 + operand2 + carry;
                result = full & MASK;
                flags.setCarry(full > MASK);
                flags.setOverflow(((operand1 ^ result) & (operand2 ^ result) & 0x8000) != 0);
                flags.setAuxCarry(((operand1 ^ operand2 ^ result) & 0x10) != 0);
            }
            case SUB, CMP -> {
                int full = operand1 - operand2;
                result = full & MASK;
                flags.setCarry((full & 0x10000) != 0);
                flags.setOverflow(((operand1 ^ operand2) & (operand1 ^ result) & 0x8000) != 0);
                flags.setAuxCarry(((operand1 ^ operand2 ^ result) & 0x10) != 0);
                if (op == Operation.CMP) {
                    // CMP discards the difference: flags must reflect it, so set
                    // ZF/SF/PF now, before result is replaced by operand1 below.
                    flags.setZero(result == 0);
                    flags.setSign((result & 0x8000) != 0);
                    flags.setParity(computeParity(result));
                    result = operand1;
                }
            }
            case SBB -> {
                int carry = oldCF ? 1 : 0;
                int full = operand1 - operand2 - carry;
                result = full & MASK;
                flags.setCarry((full & 0x10000) != 0);
                flags.setOverflow(((operand1 ^ operand2) & (operand1 ^ result) & 0x8000) != 0);
                flags.setAuxCarry(((operand1 ^ operand2 ^ result) & 0x10) != 0);
            }
            case INC -> {
                result = (operand1 + 1) & MASK;
                flags.setOverflow(operand1 == 0x7FFF);
                flags.setCarry(oldCF); // INC preserves CF
                flags.setAuxCarry(((operand1 ^ result) & 0x10) != 0);
            }
            case DEC -> {
                result = (operand1 - 1) & MASK;
                flags.setOverflow(operand1 == 0x8000);
                flags.setCarry(oldCF); // DEC preserves CF
                flags.setAuxCarry(((operand1 ^ result) & 0x10) != 0);
            }
            case NEG -> {
                result = (-operand1) & MASK;
                flags.setCarry(operand1 != 0);
                flags.setOverflow(operand1 == 0x8000);
                flags.setAuxCarry(((operand1 ^ result) & 0x10) != 0);
            }

            // ---- Logic ----
            case AND -> {
                result = (operand1 & operand2) & MASK;
                flags.setCarry(false);
                flags.setOverflow(false);
                flags.setAuxCarry(false);
            }
            case OR -> {
                result = (operand1 | operand2) & MASK;
                flags.setCarry(false);
                flags.setOverflow(false);
                flags.setAuxCarry(false);
            }
            case XOR -> {
                result = (operand1 ^ operand2) & MASK;
                flags.setCarry(false);
                flags.setOverflow(false);
                flags.setAuxCarry(false);
            }
            case NOT -> {
                result = (~operand1) & MASK;
                // NOT does not affect flags
                lastResult = result;
                return result;
            }
            case TEST -> {
                result = (operand1 & operand2) & MASK;
                flags.setCarry(false);
                flags.setOverflow(false);
                flags.setAuxCarry(false);
                // Fall through to common ZF/SF/PF update below
            }

            // ---- Multiply ----
            case MUL -> {
                // Unsigned: AX * r/m16 -> DX:AX
                long fullResult = (long) (operand1 & 0xFFFF) * (long) (operand2 & 0xFFFF);
                int lo = (int) (fullResult & 0xFFFF);
                int hi = (int) ((fullResult >> 16) & 0xFFFF);
                flags.setCarry(hi != 0);
                flags.setOverflow(hi != 0);
                // SF, ZF, PF, AF are undefined after MUL
                result = lo;
            }
            case IMUL -> {
                // Signed: AX * r/m16 -> DX:AX
                int a = (short) operand1;
                int b = (short) operand2;
                long fullResult = (long) a * (long) b;
                int lo = (int) (fullResult & 0xFFFF);
                int hi = (int) ((fullResult >> 16) & 0xFFFF);
                // CF=OF=1 if high part is sign extension of low part
                boolean signExt = (hi == 0 || hi == -1);
                flags.setCarry(!signExt);
                flags.setOverflow(!signExt);
                result = lo;
            }

            // ---- Divide ----
            case DIV -> {
                // AX / operand2 -> quotient in AX, remainder in DX
                // NOTE: Full DX:AX division is done in MicroOperationExecutor.div_reg()
                // This path handles simple AX-only divide for testing
                if (operand2 == 0) throw new ArithmeticException("Division by zero");
                int dividend = operand1 & 0xFFFF;
                int divisor = operand2 & 0xFFFF;
                int quotient = dividend / divisor;
                int remainder = dividend % divisor;
                if (quotient > 0xFFFF) {
                    throw new ArithmeticException("Divide overflow: quotient does not fit in 16 bits");
                }
                result = quotient;
                // Flags are undefined after DIV
            }
            case IDIV -> {
                // Signed AX / operand2
                if (operand2 == 0) throw new ArithmeticException("Division by zero");
                int dividend = (short) operand1;
                int divisor = (short) operand2;
                int quotient = dividend / divisor;
                int remainder = dividend % divisor;
                if (quotient > 32767 || quotient < -32768) {
                    throw new ArithmeticException("Divide overflow: quotient does not fit in signed 16 bits");
                }
                result = quotient & 0xFFFF;
                // Flags are undefined after IDIV
            }

            // ---- Shifts ----
            case SHL -> {
                int count = operand2 & 0x1F;
                if (count == 0) { result = operand1; break; }
                int full = operand1 << count;
                result = full & MASK;
                flags.setCarry(count > 0 && ((full >> 16) & 1) == 1);
                flags.setOverflow(count == 1 && ((operand1 ^ result) & 0x8000) != 0);
            }
            case SHR -> {
                int count = operand2 & 0x1F;
                if (count == 0) { result = operand1; break; }
                result = operand1 >>> count;
                flags.setCarry(((operand1 >> (count - 1)) & 1) == 1);
                flags.setOverflow(count == 1 && (operand1 & 0x8000) != 0);
            }
            case SAR -> {
                int count = operand2 & 0x1F;
                if (count == 0) { result = operand1; break; }
                // Sign-extend to 32 bits before arithmetic shift
                result = (((short) operand1) >> count) & MASK;
                flags.setCarry(((operand1 >> (count - 1)) & 1) == 1);
                flags.setOverflow(count == 1 ? false : flags.isOverflow());
            }
            case ROL -> {
                int count = operand2 & 0x1F;
                if (count == 0) { result = operand1; break; }
                count = count % 16;
                result = ((operand1 << count) | (operand1 >>> (16 - count))) & MASK;
                flags.setCarry((result & 1) == 1);
                if (count == 1) flags.setOverflow(((operand1 ^ result) & 0x8000) != 0);
            }
            case ROR -> {
                int count = operand2 & 0x1F;
                if (count == 0) { result = operand1; break; }
                count = count % 16;
                result = ((operand1 >>> count) | (operand1 << (16 - count))) & MASK;
                flags.setCarry(((result >> 15) & 1) == 1);
                if (count == 1) flags.setOverflow(((operand1 ^ result) & 0x8000) != 0);
            }
            case RCL -> {
                int count = operand2 & 0x1F;
                if (count == 0) { result = operand1; break; }
                count = count % 17; // RCL cycles every 17 shifts
                int cf = oldCF ? 1 : 0;
                int full = (operand1 << count) | (cf << (count - 1));
                if (count > 1) full |= (operand1 >>> (17 - count));
                result = full & MASK;
                flags.setCarry(((full >> 16) & 1) == 1);
                flags.setOverflow(count == 1 && ((operand1 ^ result) & 0x8000) != 0);
            }
            case RCR -> {
                int count = operand2 & 0x1F;
                if (count == 0) { result = operand1; break; }
                count = count % 17;
                int cf = oldCF ? 1 : 0;
                int full = (operand1 >>> count) | (cf << (16 - count)) | (operand1 << (17 - count));
                result = full & MASK;
                flags.setCarry(((full >> 16) & 1) == 1);
                flags.setOverflow(count == 1 && ((operand1 ^ result) & 0x8000) != 0);
            }

            default -> result = operand1;
        }

        // Update SF, ZF, PF for operations that affect them
        // (CMP sets them from the discarded difference inside its own case.)
        if (op != Operation.NOT && op != Operation.CMP && op != Operation.MUL && op != Operation.IMUL
            && op != Operation.DIV && op != Operation.IDIV) {
            flags.setZero(result == 0);
            flags.setSign((result & 0x8000) != 0);
            flags.setParity(computeParity(result));
        }

        lastResult = result;
        return result;
    }

    /**
     * MUL: unsigned AX * src -> DX:AX.
     * Returns DX (high part). Caller sets AX and calls execute(MUL,...) separately.
     */
    public int mulHigh(int operand1, int operand2) {
        long fullResult = (long) (operand1 & 0xFFFF) * (long) (operand2 & 0xFFFF);
        return (int) ((fullResult >> 16) & 0xFFFF);
    }

    /**
     * IMUL: signed AX * src -> DX:AX.
     * Returns DX (high part).
     */
    public int imulHigh(int operand1, int operand2) {
        long fullResult = (long) (short) operand1 * (long) (short) operand2;
        return (int) ((fullResult >> 16) & 0xFFFF);
    }

    /**
     * DIV: DX:AX / src -> quotient in AX, remainder in DX.
     * Returns remainder (DX). Caller must set DX:AX appropriately.
     */
    public int divRemainder(int dx, int ax, int divisor) {
        if (divisor == 0) throw new ArithmeticException("Division by zero");
        long dividend = ((long) dx << 16) | (ax & 0xFFFF);
        int quotient = (int) (dividend / divisor);
        if (quotient > 0xFFFF || quotient < 0) {
            throw new ArithmeticException("Divide overflow");
        }
        return (int) (dividend % divisor) & 0xFFFF;
    }

    /**
     * IDIV: signed DX:AX / src -> quotient in AX, remainder in DX.
     * Returns remainder (DX).
     */
    public int idivRemainder(int dx, int ax, int divisor) {
        if (divisor == 0) throw new ArithmeticException("Division by zero");
        long dividend = ((long) dx << 16) | (ax & 0xFFFF);
        // Sign extend to 32-bit
        if ((dividend & 0x80000000L) != 0) dividend |= 0xFFFFFFFF00000000L;
        int quotient = (int) (dividend / (short) divisor);
        if (quotient > 32767 || quotient < -32768) {
            throw new ArithmeticException("Divide overflow");
        }
        return (int) (dividend % (short) divisor) & 0xFFFF;
    }

    // ---- Accessors ----
    public int       getLastResult()        { return lastResult; }
    public int       getLastA()             { return lastA; }
    public int       getLastB()             { return lastB; }
    public Operation getLastOperation()     { return lastOperation; }
    public boolean   isActive()             { return active; }
    public void      setActive(boolean a)   { active = a; }

    public String getOperationString() {
        return lastOperation != null ? lastOperation.name() : "---";
    }

    public void reset() {
        lastResult    = 0;
        lastA         = 0;
        lastB         = 0;
        lastOperation = null;
        active        = false;
    }

    // ---- Parity lookup (even parity for low 8 bits) ----
    private static final boolean[] PARITY_TABLE = new boolean[256];
    static {
        for (int i = 0; i < 256; i++) {
            int bits = i;
            bits ^= bits >> 4;
            bits ^= bits >> 2;
            bits ^= bits >> 1;
            PARITY_TABLE[i] = (~bits & 1) == 1;
        }
    }
    private static boolean computeParity(int val) {
        return PARITY_TABLE[val & 0xFF];
    }
}
