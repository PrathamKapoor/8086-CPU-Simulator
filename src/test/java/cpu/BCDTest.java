package cpu;

import bus.AddressBus;
import bus.ControlBus;
import bus.DataBus;
import clock.Clock;
import cpu.registers.*;
import memory.Memory;
import microoperation.MicroOperationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BCD adjust verification: exhaustive DAA/DAS over all 256 AL values x AF x CF
 * against the Intel 8086 pseudocode, plus known-answer vectors and an
 * AH-preservation guard (byte ops must not clobber the other half of AX).
 */
class BCDTest {

    private MicroOperationExecutor ex;
    private FLAGS flags;
    private Map<String, Register> regs;

    @BeforeEach void setup() {
        AddressBus ab = new AddressBus();
        DataBus db = new DataBus();
        ControlBus cb = new ControlBus();
        Clock clock = new Clock();
        flags = new FLAGS();
        PC pc = new PC(); IR ir = new IR(); MAR mar = new MAR(); MDR mdr = new MDR();
        regs = new LinkedHashMap<>();
        regs.put("AX", new AX()); regs.put("BX", new BX());
        regs.put("CX", new CX()); regs.put("DX", new DX());
        regs.put("SP", new SP()); regs.put("BP", new BP());
        regs.put("SI", new SI()); regs.put("DI", new DI());
        regs.put("CS", new CS()); regs.put("DS", new DS());
        regs.put("SS", new SS()); regs.put("ES", new ES());
        regs.put("IP", pc); regs.put("IR", ir);
        regs.put("MAR", mar); regs.put("MDR", mdr);
        regs.put("FLAGS", flags);
        ALU alu = new ALU(flags);
        Memory mem = new Memory(ab, db, cb);
        ex = new MicroOperationExecutor(regs, pc, ir, mar, mdr, flags, alu, mem, ab, db, cb, clock);
    }

    private void loadAL(int al, boolean af, boolean cf) {
        regs.get("AX").load(0x4200 | al);
        flags.setAuxCarry(af);
        flags.setCarry(cf);
    }

    private void assertCommon(int expAl, boolean expAf, boolean expCf) {
        int al = regs.get("AX").lowByte();
        assertEquals(expAl, al, "AL");
        assertEquals(0x42, regs.get("AX").highByte(), "AH preserved");
        assertEquals(expAf, flags.isAuxCarry(), "AF");
        assertEquals(expCf, flags.isCarry(), "CF");
        assertEquals(expAl == 0, flags.isZero(), "ZF");
        assertEquals((expAl & 0x80) != 0, flags.isSign(), "SF");
        assertEquals(FLAGS.computeParity(expAl), flags.isParity(), "PF");
    }

    @Test void daa_exhaustive() {
        for (int al0 = 0; al0 < 256; al0++) {
            for (boolean af0 : new boolean[]{false, true}) {
                for (boolean cf0 : new boolean[]{false, true}) {
                    int al = al0;
                    boolean expAf, expCf;
                    if ((al & 0x0F) > 9 || af0) { al = (al + 6) & 0xFF; expAf = true; }
                    else { expAf = false; }
                    if (al > 0x9F || cf0) { al = (al + 0x60) & 0xFF; expCf = true; }
                    else { expCf = false; }
                    flags.setOverflow(true);
                    loadAL(al0, af0, cf0);
                    ex.daa().execute();
                    assertCommon(al, expAf, expCf);
                    assertTrue(flags.isOverflow(), "OF preserved (undefined per Intel)");
                }
            }
        }
    }

    @Test void das_exhaustive() {
        for (int al0 = 0; al0 < 256; al0++) {
            for (boolean af0 : new boolean[]{false, true}) {
                for (boolean cf0 : new boolean[]{false, true}) {
                    int al = al0;
                    boolean expAf, expCf;
                    if ((al & 0x0F) > 9 || af0) { al = (al - 6) & 0xFF; expAf = true; }
                    else { expAf = false; }
                    if (al > 0x9F || cf0) { al = (al - 0x60) & 0xFF; expCf = true; }
                    else { expCf = false; }
                    flags.setOverflow(false);
                    loadAL(al0, af0, cf0);
                    ex.das().execute();
                    assertCommon(al, expAf, expCf);
                    assertFalse(flags.isOverflow(), "OF preserved (undefined per Intel)");
                }
            }
        }
    }

    @Test void daa_known_vectors() {
        loadAL(0x7F, true, false);  ex.daa().execute(); assertCommon(0x85, true, false);  // 58+27=85
        loadAL(0x9B, false, false); ex.daa().execute(); assertCommon(0x01, true, true);   // 59+42=101
        loadAL(0x00, false, false); ex.daa().execute(); assertCommon(0x00, false, false); // 0+0
    }

    @Test void das_known_vectors() {
        loadAL(0x1A, true, false);  ex.das().execute(); assertCommon(0x14, true, false);  // 43-29=14
        loadAL(0xEF, true, true);   ex.das().execute(); assertCommon(0x89, true, true);   // 30-41, borrow
        loadAL(0x00, false, false); ex.das().execute(); assertCommon(0x00, false, false); // 0-0
    }

    @Test void aaa_preserves_ah() {
        regs.get("AX").load(0x020B);
        flags.setAuxCarry(false);
        ex.aaa().execute();
        assertEquals(0x01, regs.get("AX").lowByte(), "AL");
        assertEquals(0x03, regs.get("AX").highByte(), "AH incremented, not clobbered");
        assertTrue(flags.isCarry() && flags.isAuxCarry());
    }

    @Test void aam_uses_al_only() {
        regs.get("AX").load(0xFF19); // AH garbage must not leak into AL input
        ex.aam(10).execute();
        assertEquals(0x02, regs.get("AX").highByte(), "AH = 25/10");
        assertEquals(0x05, regs.get("AX").lowByte(), "AL = 25%10");
    }
}
