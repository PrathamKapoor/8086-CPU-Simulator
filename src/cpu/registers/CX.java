package cpu.registers;

/** CX — 16-bit count register with CH/CL byte access. */
public class CX extends Register {
    public CX() { super("CX"); }
    public int getCH() { return highByte(); }
    public int getCL() { return lowByte(); }
    public void loadCH(int v) { loadHigh(v); }
    public void loadCL(int v) { loadLow(v); }
}
