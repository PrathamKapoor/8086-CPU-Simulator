package cpu;

import instruction.Instruction;
import instruction.InstructionParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prior to Phase 4's machine-code work, ControlUnit only ever resolved ALU,
 * XCHG, PUSH/POP and unary (INC/DEC/NEG/NOT) memory operands as if they were
 * register names, which throws (or silently misbehaves) for every memory
 * form the parser otherwise accepts. These tests pin the fix: memory operands
 * for these families now route through the same MAR/MDR load-effective-address
 * path already used by MOV/LEA/LDS/LES.
 */
class MemoryOperandExecutionTest {
    private CPU cpu;
    private InstructionParser parser;

    @BeforeEach
    void setUp() {
        cpu = new CPU();
        parser = new InstructionParser();
    }

    private void loadAndRun(String program) {
        List<Instruction> instructions = parser.parseProgram(program);
        cpu.loadProgram(instructions);
        cpu.run();
    }

    @Test void add_reg_from_memory() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0005H\nMOV AX, 0002H\nADD AX, [BX]\nHLT\n");
        assertEquals(0x0007, cpu.getRegister("AX").output());
    }

    @Test void add_memory_from_reg_reads_modifies_and_writes_back() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0005H\nMOV AX, 0002H\nADD [BX], AX\nMOV DX, [BX]\nHLT\n");
        assertEquals(0x0007, cpu.getRegister("DX").output());
    }

    @Test void add_memory_immediate() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0005H\nADD WORD [BX], 3\nMOV DX, [BX]\nHLT\n");
        assertEquals(0x0008, cpu.getRegister("DX").output());
    }

    @Test void sub_memory_from_reg_writes_back() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0009H\nMOV AX, 0004H\nSUB [BX], AX\nMOV DX, [BX]\nHLT\n");
        assertEquals(0x0005, cpu.getRegister("DX").output());
    }

    @Test void cmp_memory_does_not_modify_memory() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0009H\nCMP [BX], 9\nMOV DX, [BX]\nHLT\n");
        // CMP is compare-only: memory must be unchanged.
        assertEquals(0x0009, cpu.getRegister("DX").output());
        assertEquals(1, cpu.getFlags().isZero() ? 1 : 0);
    }

    @Test void test_memory_sets_flags_without_modifying_memory() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0FF00H\nMOV AX, 00FFH\nTEST [BX], AX\nMOV DX, [BX]\nHLT\n");
        assertEquals(0xFF00, cpu.getRegister("DX").output());
        assertEquals(1, cpu.getFlags().isZero() ? 1 : 0);
    }

    @Test void xchg_register_and_memory_swaps_both_operands() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 1234H\nMOV AX, 5678H\nXCHG AX, [BX]\nMOV DX, [BX]\nHLT\n");
        assertEquals(0x1234, cpu.getRegister("AX").output());
        assertEquals(0x5678, cpu.getRegister("DX").output());
    }

    @Test void push_memory_operand() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 4242H\nPUSH [BX]\nPOP AX\nHLT\n");
        assertEquals(0x4242, cpu.getRegister("AX").output());
    }

    @Test void pop_memory_operand() {
        loadAndRun("MOV BX, 0010H\nMOV AX, 7777H\nPUSH AX\nPOP [BX]\nMOV DX, [BX]\nHLT\n");
        assertEquals(0x7777, cpu.getRegister("DX").output());
    }

    @Test void inc_memory_operand() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0009H\nINC WORD [BX]\nMOV DX, [BX]\nHLT\n");
        assertEquals(0x000A, cpu.getRegister("DX").output());
    }

    @Test void dec_memory_operand() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0009H\nDEC WORD [BX]\nMOV DX, [BX]\nHLT\n");
        assertEquals(0x0008, cpu.getRegister("DX").output());
    }

    @Test void neg_memory_operand() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0001H\nNEG WORD [BX]\nMOV DX, [BX]\nHLT\n");
        assertEquals(0xFFFF, cpu.getRegister("DX").output());
    }

    @Test void not_memory_operand() {
        loadAndRun("MOV BX, 0010H\nMOV WORD [BX], 0000H\nNOT WORD [BX]\nMOV DX, [BX]\nHLT\n");
        assertEquals(0xFFFF, cpu.getRegister("DX").output());
    }
}
