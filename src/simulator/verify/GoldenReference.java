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


