package machinecode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModRmTest {
    @Test
    void decodesAll8086BaseIndexFormsWithoutStringParsing() {
        String[] bases = { "BX", "BX", "BP", "BP", null, null, "BP", "BX" };
        String[] indexes = { "SI", "DI", "SI", "DI", "SI", "DI", null, null };

        for (int rm = 0; rm < 8; rm++) {
            ModRm value = ModRm.decode(new ByteCursor(new byte[] { (byte) (0x40 | rm), 0 }, 0));
            assertFalse(value.registerDirect());
            assertEquals(bases[rm], value.baseRegister(), "r/m=" + rm);
            assertEquals(indexes[rm], value.indexRegister(), "r/m=" + rm);
            assertEquals(0, value.displacement(), "r/m=" + rm);
        }
    }

    @Test
    void recognisesDirectAddressOnlyForModZeroRmSix() {
        ModRm direct = ModRm.decode(new ByteCursor(new byte[] { 0x06, 0x34, 0x12 }, 0));

        assertTrue(direct.directAddress());
        assertEquals(0x1234, direct.displacement());
        assertEquals(null, direct.baseRegister());
        assertEquals(null, direct.indexRegister());
    }

    @Test
    void encodesBpWithoutExplicitDisplacementAsDisp8Zero() {
        ModRm value = ModRm.memory(3, "BP", null, 0, false);

        assertEquals(0x5E, value.firstByte());
        assertArrayEquals(new byte[] { 0x5E, 0x00 }, value.toBytes());
    }

    @Test
    void roundTripsRegisterDirectAndSignedDisplacements() {
        ModRm register = ModRm.registerDirect(2, 3);
        assertTrue(register.registerDirect());
        assertEquals(0xD3, register.firstByte());

        ModRm disp8 = ModRm.memory(0, "BX", "SI", -2, true);
        assertArrayEquals(new byte[] { 0x40, (byte) 0xFE }, disp8.toBytes());
        ModRm decoded = ModRm.decode(new ByteCursor(disp8.toBytes(), 0));
        assertEquals(-2, decoded.displacement());
        assertEquals("BX", decoded.baseRegister());
        assertEquals("SI", decoded.indexRegister());
    }
}
