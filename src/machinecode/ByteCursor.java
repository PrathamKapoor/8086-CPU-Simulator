package machinecode;

import java.util.Arrays;

/**
 * Bounds-checked cursor over an immutable 8086 instruction-byte stream.
 * Multi-byte values use Intel's little-endian byte order.
 */
public final class ByteCursor {
    private final byte[] bytes;
    private final int startOffset;
    private int position;

    public ByteCursor(byte[] bytes, int offset) {
        if (bytes == null) throw new IllegalArgumentException("bytes must not be null");
        if (offset < 0 || offset > bytes.length) {
            throw new IllegalArgumentException("offset outside byte stream: " + offset);
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.startOffset = offset;
        this.position = offset;
    }

    public int readU8() {
        require(1);
        return bytes[position++] & 0xFF;
    }

    public int readU16LE() {
        int low = readU8();
        int high = readU8();
        return low | (high << 8);
    }

    public int position() {
        return position;
    }

    public int remaining() {
        return bytes.length - position;
    }

    private void require(int count) {
        if (remaining() < count) {
            throw new DecodeException("Truncated instruction at offset " + startOffset
                + ": need " + count + " byte(s), only " + remaining() + " remain");
        }
    }
}
