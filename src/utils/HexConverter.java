package utils;

/** HexConverter — integer <-> hex-string utilities. */
public class HexConverter {
    public static String toHex(int value)             { return String.format("0x%04X", value & 0xFFFF); }
    public static String toHex20(int value)           { return String.format("0x%05X", value & 0xFFFFF); }
    public static String toHex(int value, int digits) { return String.format("0x%0" + digits + "X", value); }
    public static int    fromHex(String s)            { return Integer.parseInt(s.replaceAll("0[xX]",""), 16); }
}
