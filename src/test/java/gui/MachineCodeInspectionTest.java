package gui;

import machinecode.Intel8086Decoder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineCodeInspectionTest {
    @Test void projectsActualDecodedState() {
        var view = MachineCodeInspection.from(new Intel8086Decoder().decode(new byte[]{0x26, (byte)0x8B, 0x40, (byte)0xFE}, 0));
        assertEquals(0, view.offset()); assertEquals("268B40FE", view.bytes()); assertEquals(4, view.length());
        assertEquals("8B", view.opcode()); assertEquals("26", view.prefixes()); assertEquals(-2, view.displacement());
        assertTrue(view.instruction().startsWith("MOV AX"));
    }
}
