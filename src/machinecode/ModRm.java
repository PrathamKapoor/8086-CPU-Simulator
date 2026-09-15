package machinecode;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** Explicit 8086 ModR/M byte and its optional displacement. */
public final class ModRm {
    private static final String[] BASE = { "BX", "BX", "BP", "BP", null, null, "BP", "BX" };
    private static final String[] INDEX = { "SI", "DI", "SI", "DI", "SI", "DI", null, null };

    private final int mod;
    private final int reg;
    private final int rm;
    private final int displacement;

    private ModRm(int mod, int reg, int rm, int displacement) {
        this.mod = check3(mod, "mod");
        this.reg = check3(reg, "reg");
        this.rm = check3(rm, "r/m");
        if (mod > 3) throw new IllegalArgumentException("invalid ModR/M mod: " + mod);
        this.displacement = displacement;
    }

    public static ModRm decode(ByteCursor cursor) {
        int first = cursor.readU8();
        int mod = (first >>> 6) & 0x3;
        int reg = (first >>> 3) & 0x7;
        int rm = first & 0x7;
        int displacement = switch (mod) {
            case 0 -> rm == 6 ? cursor.readU16LE() : 0;
            case 1 -> (byte) cursor.readU8();
            case 2 -> (short) cursor.readU16LE();
            default -> 0;
        };
        return new ModRm(mod, reg, rm, displacement);
    }

    public static ModRm registerDirect(int reg, int rm) {
        return new ModRm(3, reg, rm, 0);
    }

    /**
     * Creates a memory ModR/M value from one legal 8086 effective address.
     * preferDisp8 selects an 8-bit displacement when its signed range permits it.
     */
    public static ModRm memory(int reg, String base, String index, int displacement, boolean preferDisp8) {
        String normalizedBase = normalize(base);
        String normalizedIndex = normalize(index);
        int rm = findRm(normalizedBase, normalizedIndex);
        if (normalizedBase == null && normalizedIndex == null) {
            if (displacement < 0 || displacement > 0xFFFF) {
                throw new IllegalArgumentException("direct address outside 16-bit range: " + displacement);
            }
            return new ModRm(0, reg, 6, displacement);
        }
        if (displacement == 0 && !("BP".equals(normalizedBase) && normalizedIndex == null)) {
            return new ModRm(0, reg, rm, 0);
        }
        if (displacement >= -128 && displacement <= 127 && (preferDisp8 || displacement != 0 || "BP".equals(normalizedBase))) {
            return new ModRm(1, reg, rm, displacement);
        }
        if (displacement < Short.MIN_VALUE || displacement > 0xFFFF) {
            throw new IllegalArgumentException("16-bit displacement outside range: " + displacement);
        }
        return new ModRm(2, reg, rm, displacement);
    }

    public boolean registerDirect() { return mod == 3; }
    public boolean directAddress() { return mod == 0 && rm == 6; }
    public int mod() { return mod; }
    public int reg() { return reg; }
    public int rm() { return rm; }
    public int displacement() { return displacement; }
    public String baseRegister() { return registerDirect() || directAddress() ? null : BASE[rm]; }
    public String indexRegister() { return registerDirect() || directAddress() ? null : INDEX[rm]; }
    public int firstByte() { return (mod << 6) | (reg << 3) | rm; }

    public byte[] toBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(firstByte());
        if (directAddress() || mod == 2) writeU16LE(output, displacement);
        else if (mod == 1) output.write(displacement & 0xFF);
        return output.toByteArray();
    }

    private static void writeU16LE(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }

    private static int findRm(String base, String index) {
        for (int i = 0; i < 8; i++) {
            if (same(BASE[i], base) && same(INDEX[i], index)) return i;
        }
        throw new IllegalArgumentException("not an 8086 effective address: base=" + base + ", index=" + index);
    }

    private static boolean same(String left, String right) { return left == null ? right == null : left.equals(right); }
    private static String normalize(String value) { return value == null ? null : value.toUpperCase(Locale.ROOT); }
    private static int check3(int value, String name) {
        if (value < 0 || value > 7) throw new IllegalArgumentException(name + " must fit 3 bits: " + value);
        return value;
    }
}
