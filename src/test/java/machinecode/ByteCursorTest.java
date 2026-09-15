package machinecode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteCursorTest {
    @Test
    void readsUnsignedBytesAndLittleEndianWordsAtItsExplicitOffset() {
        ByteCursor cursor = new ByteCursor(new byte[] { 0x10, 0x34, 0x12 }, 1);

        assertEquals(0x34, cursor.readU8());
        assertEquals(2, cursor.position());
        assertEquals(1, cursor.remaining());
        assertEquals(0x12, cursor.readU8());
    }

    @Test
    void readsLittleEndianWords() {
        ByteCursor cursor = new ByteCursor(new byte[] { 0x78, 0x56 }, 0);

        assertEquals(0x5678, cursor.readU16LE());
        assertEquals(2, cursor.position());
    }

    @Test
    void rejectsTruncatedStreamsWithTheInstructionStartOffset() {
        ByteCursor cursor = new ByteCursor(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0x11 }, 7);

        DecodeException error = assertThrows(DecodeException.class, cursor::readU16LE);
        assertTrue(error.getMessage().contains("offset 7"));
    }
}
