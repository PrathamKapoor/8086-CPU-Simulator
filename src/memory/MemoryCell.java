package memory;

/**
 * MemoryCell — a single 16-bit word-addressable cell in simulated RAM.
 */
public class MemoryCell {
    private int value;
    private boolean written;

    public MemoryCell() { value = 0; written = false; }

    public void write(int val) {
        this.value = val & 0xFFFF;
        this.written = true;
    }

    public int read() { return value; }

    public boolean isWritten() { return written; }

    public void clear() { value = 0; written = false; }

    @Override
    public String toString() {
        return String.format("0x%04X", value);
    }
}
