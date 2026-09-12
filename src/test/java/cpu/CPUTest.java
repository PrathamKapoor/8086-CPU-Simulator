package cpu;

import instruction.Instruction;
import instruction.InstructionParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * CPU Test Suite — end-to-end execution tests.
 */
class CPUTest {

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

    private void loadAndStep(String program, int steps) {
        List<Instruction> instructions = parser.parseProgram(program);
        cpu.loadProgram(instructions);
        for (int i = 0; i < steps && !cpu.isHalted(); i++) {
            cpu.step();
        }
    }

    // ---- MOV ----
    @Test void mov_reg_reg() {
        loadAndRun("MOV AX, 0x1234\nMOV BX, AX\nHLT");
        Assertions.assertEquals(0x1234, cpu.getRegister("BX").output());
    }

    @Test void mov_reg_imm() {
        loadAndRun("MOV CX, 42\nHLT");
        Assertions.assertEquals(42, cpu.getRegister("CX").output());
    }

    @Test void mov_hex_imm() {
        loadAndRun("MOV AX, 0xFF00\nHLT");
        Assertions.assertEquals(0xFF00, cpu.getRegister("AX").output());
    }

    // ---- ADD/SUB ----
    @Test void add_sub() {
        loadAndRun("MOV AX, 100\nMOV BX, 50\nADD AX, BX\nHLT");
        Assertions.assertEquals(150, cpu.getRegister("AX").output());
    }

    @Test void sub_result() {
        loadAndRun("MOV AX, 200\nSUB AX, 75\nHLT");
        Assertions.assertEquals(125, cpu.getRegister("AX").output());
    }

    // ---- INC/DEC ----
    @Test void inc_dec() {
        loadAndRun("MOV AX, 10\nINC AX\nDEC AX\nHLT");
        Assertions.assertEquals(10, cpu.getRegister("AX").output());
    }

    // ---- CMP and JZ ----
    @Test void cmp_jz_taken() {
        loadAndRun("MOV AX, 5\nCMP AX, 5\nJZ 4\nMOV BX, 0\nHLT\nMOV BX, 1\nHLT");
        // If JZ taken, we jump to index 4 (HLT), BX stays 0
        // Actually: [0]=MOV AX,5  [1]=CMP AX,5  [2]=JZ 4  [3]=MOV BX,0  [4]=HLT  [5]=MOV BX,1
        // JZ 4 should jump to index 4 which is HLT
        Assertions.assertEquals(0, cpu.getRegister("BX").output());
    }

    @Test void cmp_jz_not_taken() {
        loadAndRun("MOV AX, 5\nCMP AX, 3\nJZ 4\nMOV BX, 1\nHLT");
        // [0]=MOV AX,5  [1]=CMP AX,3  [2]=JZ 4  [3]=MOV BX,1  [4]=HLT
        // JZ should NOT be taken (ZF=0), fall through to MOV BX,1
        Assertions.assertEquals(1, cpu.getRegister("BX").output());
    }

    // ---- LOOP ----
    @Test void loop_countdown() {
        loadAndRun("MOV CX, 3\nMOV AX, 0\nADD AX, 1\nLOOP 2\nHLT");
        // CX starts at 3, LOOP decrements CX and jumps to 2 while CX!=0
        // Iterations: CX=2, CX=1, CX=0 (stop)
        // ADD AX,1 executes 3 times
        Assertions.assertEquals(3, cpu.getRegister("AX").output());
    }

    // ---- PUSH/POP ----
    @Test void push_pop() {
        loadAndRun("MOV AX, 0xABCD\nPUSH AX\nPOP BX\nHLT");
        Assertions.assertEquals(0xABCD, cpu.getRegister("BX").output());
    }

    @Test void stack_pointer() {
        loadAndRun("MOV AX, 10\nPUSH AX\nHLT");
        // SP resets to 0xFFFE (top of 64K stack segment); PUSH decrements by 2
        Assertions.assertEquals(0xFFFC, cpu.getRegister("SP").output());
    }

    // ---- AND/OR/XOR ----
    @Test void logic_ops() {
        loadAndRun("MOV AX, 0xFF00\nAND AX, 0x0F0F\nHLT");
        Assertions.assertEquals(0x0F00, cpu.getRegister("AX").output());
    }

    @Test void or_op() {
        loadAndRun("MOV AX, 0xF000\nOR AX, 0x0F00\nHLT");
        Assertions.assertEquals(0xFF00, cpu.getRegister("AX").output());
    }

    @Test void xor_op() {
        loadAndRun("MOV AX, 0xFFFF\nXOR AX, 0xFFFF\nHLT");
        Assertions.assertEquals(0x0000, cpu.getRegister("AX").output());
    }

    // ---- NOT ----
    @Test void not_op() {
        loadAndRun("MOV AX, 0x0F0F\nNOT AX\nHLT");
        Assertions.assertEquals(0xF0F0, cpu.getRegister("AX").output());
    }

    // ---- MUL ----
    @Test void mul() {
        loadAndRun("MOV AX, 10\nMOV BX, 10\nMUL BX\nHLT");
        Assertions.assertEquals(100, cpu.getRegister("AX").output());
        Assertions.assertEquals(0, cpu.getRegister("DX").output());
    }

    // ---- DIV ----
    @Test void div() {
        loadAndRun("MOV AX, 100\nMOV BX, 7\nDIV BX\nHLT");
        Assertions.assertEquals(14, cpu.getRegister("AX").output()); // quotient
        Assertions.assertEquals(2, cpu.getRegister("DX").output()); // remainder
    }

    // ---- Segment addressing ----
    @Test void segment_mov() {
        loadAndRun("MOV AX, 0x1000\nMOV DS, AX\nHLT");
        Assertions.assertEquals(0x1000, cpu.getRegister("DS").output());
    }

    @Test void physical_address() {
        int phys = cpu.computePhysicalAddress(0x1000, 0x0200);
        Assertions.assertEquals(0x10200, phys);
    }

    // ---- Flags ----
    @Test void zero_flag_set() {
        loadAndRun("MOV AX, 5\nSUB AX, 5\nHLT");
        Assertions.assertTrue(cpu.getFlags().isZero());
    }

    @Test void carry_flag_set() {
        loadAndRun("MOV AX, 0xFFFF\nADD AX, 1\nHLT");
        Assertions.assertTrue(cpu.getFlags().isCarry());
    }

    // ---- Halt ----
    @Test void halts() {
        loadAndRun("HLT");
        Assertions.assertTrue(cpu.isHalted());
    }

    // ---- Program counter advances ----
    @Test void pc_advances() {
        loadAndRun("NOP\nNOP\nNOP\nHLT");
        Assertions.assertTrue(cpu.isHalted());
    }

    // ---- CLC/STC ----
    @Test void clc_stc() {
        loadAndRun("STC\nHLT");
        Assertions.assertTrue(cpu.getFlags().isCarry());
        cpu.reset();
        loadAndRun("STC\nCLC\nHLT");
        Assertions.assertFalse(cpu.getFlags().isCarry());
    }
}
