package cpu.registers;

/**
 * Register — base class for all 16-bit CPU registers.
 * Models a hardware register at RTL level: load / output / increment / clear.
 * Supports 8-bit high/low byte access for AX, BX, CX, DX.
 */
public class Register {

    private final String name;
    protected int value;
    private static final int MASK = 0xFFFF;

    public Register(String name) {
        this.name = name;
        this.value = 0;
    }

    /** Load a value into the register (RegisterLoad control signal). */
    public void load(int val) {
        this.value = val & MASK;
    }

    /** Read the full 16-bit register value (RegisterOutputEnable control signal). */
    public int output() {
        return this.value & MASK;
    }

    /** Read the high byte (bits 15..8). */
    public int highByte() {
        return (value >> 8) & 0xFF;
    }

    /** Read the low byte (bits 7..0). */
    public int lowByte() {
        return value & 0xFF;
    }

    /** Write the high byte (bits 15..8), preserving low byte. */
    public void loadHigh(int val) {
        value = ((val & 0xFF) << 8) | (value & 0xFF);
    }

    /** Write the low byte (bits 7..0), preserving high byte. */
    public void loadLow(int val) {
        value = (value & 0xFF00) | (val & 0xFF);
    }

    /** Increment by 1 (PCIncrement control signal). */
    public void increment() {
        this.value = (this.value + 1) & MASK;
    }

    /** Decrement by 1. */
    public void decrement() {
        this.value = (this.value - 1) & MASK;
    }

    /** Reset to zero. */
    public void clear() {
        this.value = 0;
    }

    public String getName() { return name; }

    public String toHex() {
        return String.format("0x%04X", value & MASK);
    }

    public String toBinary() {
        return String.format("%16s", Integer.toBinaryString(value & MASK)).replace(' ', '0');
    }

    @Override
    public String toString() {
        return name + "=" + toHex();
    }
}
