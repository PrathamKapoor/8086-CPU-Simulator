package cpu;

import cpu.registers.FLAGS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALU Test Suite — comprehensive tests for all ALU operations.
 */
class ALUTest {

    private FLAGS flags;
    private ALU alu;

    @BeforeEach
    void setUp() {
        flags = new FLAGS();
        alu = new ALU(flags);
    }

    // ---- ADD ----
    @Test void add_basic() { assertEquals(0x0300, alu.execute(ALU.Operation.ADD, 0x0100, 0x0200)); }
    @Test void add_carry() { alu.execute(ALU.Operation.ADD, 0xFFFF, 1); assertTrue(flags.isCarry()); }
    @Test void add_overflow() { alu.execute(ALU.Operation.ADD, 0x7FFF, 1); assertTrue(flags.isOverflow()); }
    @Test void add_zero() { alu.execute(ALU.Operation.ADD, 0, 0); assertTrue(flags.isZero()); }
    @Test void add_sign() { alu.execute(ALU.Operation.ADD, 0x4000, 0x4000); assertTrue(flags.isSign()); }
    @Test void add_wrap() { assertEquals(0x0001, alu.execute(ALU.Operation.ADD, 0xFFFF, 2)); }

    // ---- SUB ----
    @Test void sub_basic() { assertEquals(0x0100, alu.execute(ALU.Operation.SUB, 0x0300, 0x0200)); }
    @Test void sub_borrow() { alu.execute(ALU.Operation.SUB, 0, 1); assertTrue(flags.isCarry()); }
    @Test void sub_zero() { alu.execute(ALU.Operation.SUB, 100, 100); assertTrue(flags.isZero()); }
    @Test void sub_overflow() { alu.execute(ALU.Operation.SUB, 0x8000, 1); assertTrue(flags.isOverflow()); }

    // ---- ADC ----
    @Test void adc_no_carry() { assertEquals(0x0003, alu.execute(ALU.Operation.ADC, 1, 2)); }
    @Test void adc_with_carry() { flags.setCarry(true); assertEquals(0x0004, alu.execute(ALU.Operation.ADC, 1, 2)); }

    // ---- SBB ----
    @Test void sbb_no_borrow() { assertEquals(0x0001, alu.execute(ALU.Operation.SBB, 3, 2)); }
    @Test void sbb_with_borrow() { flags.setCarry(true); assertEquals(0x0000, alu.execute(ALU.Operation.SBB, 3, 2)); }

    // ---- INC (preserves CF) ----
    @Test void inc_preserves_cf() {
        flags.setCarry(true);
        alu.execute(ALU.Operation.INC, 0x0005, 0);
        assertTrue(flags.isCarry()); // CF unchanged
        assertFalse(flags.isZero());
    }
    @Test void inc_wrap() { assertEquals(0x0000, alu.execute(ALU.Operation.INC, 0xFFFF, 0)); }

    // ---- DEC (preserves CF) ----
    @Test void dec_preserves_cf() {
        flags.setCarry(true);
        alu.execute(ALU.Operation.DEC, 0x0005, 0);
        assertTrue(flags.isCarry()); // CF unchanged
    }
    @Test void dec_underflow() { assertEquals(0xFFFF, alu.execute(ALU.Operation.DEC, 0, 0)); }

    // ---- NEG ----
    @Test void neg_positive() { assertEquals(0xFFFB, alu.execute(ALU.Operation.NEG, 5, 0)); }
    @Test void neg_zero() { assertEquals(0, alu.execute(ALU.Operation.NEG, 0, 0)); assertFalse(flags.isCarry()); }
    @Test void neg_sets_cf() { assertEquals(0xFFFF, alu.execute(ALU.Operation.NEG, 1, 0)); assertTrue(flags.isCarry()); }
    @Test void neg_overflow() { assertEquals(0x8000, alu.execute(ALU.Operation.NEG, 0x8000, 0)); assertTrue(flags.isOverflow()); }

    // ---- AND ----
    @Test void and_basic() { assertEquals(0x0F00, alu.execute(ALU.Operation.AND, 0xFF00, 0x0F0F)); }
    @Test void and_clears_cf() { alu.execute(ALU.Operation.AND, 0xFFFF, 0xFFFF); assertFalse(flags.isCarry()); }
    @Test void and_zero() { alu.execute(ALU.Operation.AND, 0x00FF, 0xFF00); assertTrue(flags.isZero()); }

    // ---- OR ----
    @Test void or_basic() { assertEquals(0xFF0F, alu.execute(ALU.Operation.OR, 0xFF00, 0x0F0F)); }
    @Test void or_clears_cf() { alu.execute(ALU.Operation.OR, 0xFFFF, 0); assertFalse(flags.isCarry()); }

    // ---- XOR ----
    @Test void xor_basic() { assertEquals(0xF00F, alu.execute(ALU.Operation.XOR, 0xFF00, 0x0F0F)); }
    @Test void xor_self() { assertEquals(0, alu.execute(ALU.Operation.XOR, 0x1234, 0x1234)); assertTrue(flags.isZero()); }

    // ---- NOT (does not affect flags) ----
    @Test void not_basic() { assertEquals(0xF0F0, alu.execute(ALU.Operation.NOT, 0x0F0F, 0)); }
    @Test void not_preserves_flags() {
        flags.setZero(true);
        flags.setCarry(true);
        alu.execute(ALU.Operation.NOT, 0x0F0F, 0);
        assertTrue(flags.isZero()); // unchanged
        assertTrue(flags.isCarry()); // unchanged
    }

    // ---- CMP ----
    @Test void cmp_equal() { alu.execute(ALU.Operation.CMP, 100, 100); assertTrue(flags.isZero()); }
    @Test void cmp_less() { alu.execute(ALU.Operation.CMP, 50, 100); assertTrue(flags.isSign()); }
    @Test void cmp_greater() { alu.execute(ALU.Operation.CMP, 100, 50); assertFalse(flags.isSign()); assertFalse(flags.isZero()); }

    // ---- MUL ----
    @Test void mul_basic() { assertEquals(100, alu.execute(ALU.Operation.MUL, 10, 10)); }
    @Test void mul_large() { assertEquals(0, alu.execute(ALU.Operation.MUL, 0x1000, 0x10)); } // low 16 bits

    // ---- DIV ----
    @Test void div_basic() { assertEquals(5, alu.execute(ALU.Operation.DIV, 20, 4)); }
    @Test void div_by_zero() { assertThrows(ArithmeticException.class, () -> alu.execute(ALU.Operation.DIV, 10, 0)); }

    // ---- SHL ----
    @Test void shl_basic() { assertEquals(0x0002, alu.execute(ALU.Operation.SHL, 1, 1)); }
    @Test void shl_cf() { alu.execute(ALU.Operation.SHL, 0x8000, 1); assertTrue(flags.isCarry()); }
    @Test void shl_zero_count() { assertEquals(0x0005, alu.execute(ALU.Operation.SHL, 5, 0)); }

    // ---- SHR ----
    @Test void shr_basic() { assertEquals(0x0004, alu.execute(ALU.Operation.SHR, 0x0008, 1)); }
    @Test void shr_cf() { alu.execute(ALU.Operation.SHR, 0x0001, 1); assertTrue(flags.isCarry()); }

    // ---- SAR ----
    @Test void sar_preserves_sign() { assertEquals(0xC000, alu.execute(ALU.Operation.SAR, 0x8000, 1)); }

    // ---- ROL ----
    @Test void rol_basic() { assertEquals(0x0003, alu.execute(ALU.Operation.ROL, 0x8001, 1)); }

    // ---- ROR ----
    @Test void ror_basic() { assertEquals(0xC000, alu.execute(ALU.Operation.ROR, 0x8001, 1)); }

    // ---- Parity ----
    @Test void parity_even() { alu.execute(ALU.Operation.ADD, 3, 0); assertTrue(flags.isParity()); }
    @Test void parity_odd() { alu.execute(ALU.Operation.ADD, 1, 0); assertFalse(flags.isParity()); }
}
