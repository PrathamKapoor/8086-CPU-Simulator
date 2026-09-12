package utils;

/** BinaryConverter — integer <-> binary-string utilities. */
public class BinaryConverter {
    public static String toBinary(int value, int bits) {
        return String.format("%" + bits + "s",
               Integer.toBinaryString(value & ((1 << bits) - 1))).replace(' ', '0');
    }
    public static int fromBinary(String s) { return Integer.parseInt(s, 2); }
}
