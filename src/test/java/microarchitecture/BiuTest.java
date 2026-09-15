package microarchitecture;

import bus.AddressBus;
import bus.ControlBus;
import bus.DataBus;
import cpu.biu.BusInterfaceUnit;
import cpu.registers.CS;
import cpu.registers.PC;
import memory.Memory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BiuTest {
    @Test
    void fetches_sequential_tokens_from_an_independent_fetch_pointer() {
        CS cs = new CS();
        PC ip = new PC();
        Memory memory = new Memory(new AddressBus(), new DataBus(), new ControlBus());
        memory.directWrite(0, 0x11);
        memory.directWrite(1, 0x22);
        BusInterfaceUnit biu = new BusInterfaceUnit(cs, ip, memory);

        biu.tick();
        biu.tick();

        assertArrayEquals(new int[] {0x11, 0x22}, biu.getPrefetchQueue().getContents());
        assertEquals(2, biu.getFetchState().getNextFetchOffset());
    }
}
