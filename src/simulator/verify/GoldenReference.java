package simulator.verify;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GoldenReference — authoritative expected results per architectural vector.
 *
 * Each instruction or small sequence is paired with independently derived
 * results (not copied from the production ALU). These values are the
 * reference against which the simulator is compared by VectorRunner.
 */
public final class GoldenReference {

    private GoldenReference() { }

    // ALU binary operations: ADD, SUB, ADC, SBB, AND, OR, XOR
    public static Map<String, Integer> resultAdd(int a, int b) {
        int r = (a + b) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", ((a + b) > 0xFFFF) ? 1 : 0);
        m.put("OF", (((a ^ r) & (b ^ r) & 0x8000) != 0) ? 1 : 0);
        m.put("ZF", (r == 0) ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", (((a ^ b ^ r) & 0x10) != 0) ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }

    public static Map<String, Integer> resultSub(int a, int b) {
        int r = (a - b) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", ((a - b) & 0x10000) != 0 ? 1 : 0);
        m.put("OF", (((a ^ b) & (a ^ r) & 0x8000) != 0) ? 1 : 0);
        m.put("ZF", (r == 0) ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", (((a ^ b ^ r) & 0x10) != 0) ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }

    public static Map<String, Integer> resultAnd(int a, int b) {
        int r = (a & b) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", 0);
        m.put("OF", 0);
        m.put("ZF", (r == 0) ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    public static Map<String, Integer> resultOr(int a, int b) {
        int r = (a | b) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", 0);
        m.put("OF", 0);
        m.put("ZF", (r == 0) ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    public static Map<String, Integer> resultXor(int a, int b) {
        int r = (a ^ b) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", 0);
        m.put("OF", 0);
        m.put("ZF", (r == 0) ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    // DAA/DAS independent oracle results for given AL, AF, CF inputs.
    public static Map<String, Integer> resultDAA(int al, boolean af, boolean cf) {
        int alVal = al;
        boolean afVal = af, cfVal = cf;
        if ((alVal & 0x0F) > 9 || afVal) { alVal = (alVal + 6) & 0xFF; afVal = true; }
        else { afVal = false; }
        if (alVal > 0x9F || cfVal) { alVal = (alVal + 0x60) & 0xFF; cfVal = true; }
        else { cfVal = false; }
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", alVal);
        m.put("CF", cfVal ? 1 : 0);
        m.put("AF", afVal ? 1 : 0);
        m.put("ZF", (alVal == 0) ? 1 : 0);
        m.put("SF", (alVal & 0x80) != 0 ? 1 : 0);
        m.put("PF", parityEven(alVal));
        m.put("OF", 1); // preserved (undefined per Intel, preserved by model)
        return Collections.unmodifiableMap(m);
    }

    public static Map<String, Integer> resultDAS(int al, boolean af, boolean cf) {
        int alVal = al;
        boolean afVal = af, cfVal = cf;
        if ((alVal & 0x0F) > 9 || afVal) { alVal = (alVal - 6) & 0xFF; afVal = true; }
        else { afVal = false; }
        if (alVal > 0x9F || cfVal) { alVal = (alVal - 0x60) & 0xFF; cfVal = true; }
        else { cfVal = false; }
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", alVal);
        m.put("CF", cfVal ? 1 : 0);
        m.put("AF", afVal ? 1 : 0);
        m.put("ZF", (alVal == 0) ? 1 : 0);
        m.put("SF", (alVal & 0x80) != 0 ? 1 : 0);
        m.put("PF", parityEven(alVal));
        m.put("OF", 1);
        return Collections.unmodifiableMap(m);
    }

    public static int parityEven(int val) {
        int b = val & 0xFF;
        int p = 0;
        for (int i = 0; i < 8; i++) {
            p += (b >> i) & 1;
        }
        return (p % 2 == 0) ? 1 : 0;
    }

    // Independent reference for ADC (with initial carry)
    public static Map<String, Integer> resultAdc(int a, int b, boolean initialCF) {
        int carry = initialCF ? 1 : 0;
        int full = a + b + carry;
        int r = full & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", full > 0xFFFF ? 1 : 0);
        m.put("OF", (((a ^ r) & (b ^ r) & 0x8000) != 0) ? 1 : 0);
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", (((a ^ b ^ r) & 0x10) != 0) ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }

    // Independent reference for SBB (with initial carry/borrow)
    public static Map<String, Integer> resultSbb(int a, int b, boolean initialCF) {
        int carry = initialCF ? 1 : 0;
        int full = a - b - carry;
        int r = full & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", (full & 0x10000) != 0 ? 1 : 0);
        m.put("OF", (((a ^ b) & (a ^ r) & 0x8000) != 0) ? 1 : 0);
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", (((a ^ b ^ r) & 0x10) != 0) ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }

    // INC preserves CF; sets/recomputes arithmetic flags
    public static Map<String, Integer> resultInc(int a, boolean initialCF) {
        int r = (a + 1) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", initialCF ? 1 : 0); // preserved
        m.put("OF", a == 0x7FFF ? 1 : 0);
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", ((a ^ r) & 0x10) != 0 ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }

    // DEC preserves CF
    public static Map<String, Integer> resultDec(int a, boolean initialCF) {
        int r = (a - 1) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", initialCF ? 1 : 0); // preserved
        m.put("OF", a == 0x8000 ? 1 : 0);
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", ((a ^ r) & 0x10) != 0 ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }

    // NEG: result = (-a) & 0xFFFF; CF=1 if a!=0; flags recomputed
    public static Map<String, Integer> resultNeg(int a) {
        int r = (-a) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", a != 0 ? 1 : 0);
        m.put("OF", a == 0x8000 ? 1 : 0);
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", ((a ^ r) & 0x10) != 0 ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }

    // SHL: full reference including flags (independent derivation)
    public static Map<String, Integer> resultShl(int a, int count) {
        int c = count & 0x1F;
        int full = a << c;
        int r = full & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", (c > 0 && ((full >> 16) & 1) == 1) ? 1 : 0);
        m.put("OF", (c == 1 && ((a ^ r) & 0x8000) != 0) ? 1 : 0);
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0); // SHL does not set AF deterministically per ALU contract (preserved/undefined)
        return Collections.unmodifiableMap(m);
    }

    // SHR: logical shift right
    public static Map<String, Integer> resultShr(int a, int count) {
        int c = count & 0x1F;
        int r = a >>> c;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r & 0xFFFF);
        m.put("CF", ((a >> (c - 1)) & 1) == 1 ? 1 : 0);
        m.put("OF", (c == 1 && (a & 0x8000) != 0) ? 1 : 0);
        m.put("ZF", (r & 0xFFFF) == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r & 0xFFFF));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    // SAR: arithmetic shift right (sign-extend)
    public static Map<String, Integer> resultSar(int a, int count) {
        int c = count & 0x1F;
        int r = ((short) a) >> c;
        int masked = r & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", masked);
        m.put("CF", ((a >> (c - 1)) & 1) == 1 ? 1 : 0);
        m.put("OF", c == 1 ? 0 : 0); // ALU contract: false for count==1, preserved otherwise (fresh flags = false)
        m.put("ZF", masked == 0 ? 1 : 0);
        m.put("SF", (masked & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(masked));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    // ROL: rotate left
    public static Map<String, Integer> resultRol(int a, int count) {
        int c = (count % 16) & 0x1F;
        int r = ((a << c) | (a >>> (16 - c))) & 0xFFFF;
        int full = (a << c) | (a >>> (16 - c));
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        if (count == 0) {
            m.put("CF", 0); // preserved (fresh flags = false)
            m.put("OF", 0); // preserved
        } else {
            m.put("CF", (r & 1) == 1 ? 1 : 0);
            m.put("OF", c == 1 ? (((a ^ r) & 0x8000) != 0 ? 1 : 0) : 0);
        }
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    // ROR: rotate right
    public static Map<String, Integer> resultRor(int a, int count) {
        int c = (count % 16) & 0x1F;
        int r = ((a >>> c) | (a << (16 - c))) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        if (count == 0) {
            m.put("CF", 0); // preserved (fresh flags = false)
            m.put("OF", 0); // preserved
        } else {
            m.put("CF", ((r >> 15) & 1) == 1 ? 1 : 0);
            m.put("OF", c == 1 ? (((a ^ r) & 0x8000) != 0 ? 1 : 0) : 0);
        }
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    // RCL: rotate through carry left
    public static Map<String, Integer> resultRcl(int a, int count, boolean initialCF) {
        int c = (count % 17) & 0x1F;
        int cf = initialCF ? 1 : 0;
        int full = (a << c) | (cf << (c - 1));
        if (c > 1) full |= (a >>> (17 - c));
        int r = full & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        if (count == 0) {
            m.put("CF", initialCF ? 1 : 0); // preserved from initial CF
            m.put("OF", 0); // preserved (fresh flags = false)
        } else {
            m.put("CF", ((full >> 16) & 1) == 1 ? 1 : 0);
            m.put("OF", c == 1 ? (((a ^ r) & 0x8000) != 0 ? 1 : 0) : 0);
        }
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    // RCR: rotate through carry right
    public static Map<String, Integer> resultRcr(int a, int count, boolean initialCF) {
        int c = (count % 17) & 0x1F;
        int cf = initialCF ? 1 : 0;
        int full = (a >>> c) | (cf << (16 - c)) | (a << (17 - c));
        int r = full & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        if (count == 0) {
            m.put("CF", initialCF ? 1 : 0); // preserved from initial CF
            m.put("OF", 0); // preserved (fresh flags = false)
        } else {
            m.put("CF", ((full >> 16) & 1) == 1 ? 1 : 0);
            m.put("OF", c == 1 ? (((a ^ r) & 0x8000) != 0 ? 1 : 0) : 0);
        }
        m.put("ZF", r == 0 ? 1 : 0);
        m.put("SF", (r & 0x8000) != 0 ? 1 : 0);
        m.put("PF", parityEven(r));
        m.put("AF", 0);
        return Collections.unmodifiableMap(m);
    }

    // Shift/rotate results (simplified for initial golden set); full shift
    // behavior (CF from LSB/MSB) should be verified by property tests.
    public static Map<String, Integer> resultShiftLeft(int a, int count) {
        int c = count & 0x1F;
        int r = (a << c) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", (r == a) ? 0 : 1);
        return Collections.unmodifiableMap(m);
    }

    public static Map<String, Integer> resultShiftRightLogical(int a, int count) {
        int c = count & 0x1F;
        int r = (a >>> c) & 0xFFFF;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("AX", r);
        m.put("CF", ((a >> (c - 1)) & 1) == 1 ? 1 : 0);
        return Collections.unmodifiableMap(m);
    }
}


