package instruction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.List;

/**
 * InstructionParser Test Suite — validates parsing of all 8086 instructions.
 */
class InstructionParserTest {

    private InstructionParser parser;

    @BeforeEach
    void setUp() { parser = new InstructionParser(); }

    // ---- REP prefix (regression: "REP STOSB" on one line used to attach the
    // prefix to whatever instruction followed on the NEXT line instead,
    // because the line-continuation lookahead ran unconditionally instead of
    // only when the same line had nothing after the mnemonic) ----
    @Test void rep_prefix_same_line_attaches_to_its_own_instruction() {
        List<Instruction> program = parser.parseProgram("REP STOSB\nHLT\n");
        Assertions.assertEquals(Opcode.STOSB, program.get(0).getOpcode());
        Assertions.assertEquals(Opcode.REP, program.get(0).getPrefix());
        Assertions.assertEquals(Opcode.HLT, program.get(1).getOpcode());
        Assertions.assertNull(program.get(1).getPrefix());
    }

    @Test void rep_prefix_split_across_lines_still_works() {
        List<Instruction> program = parser.parseProgram("REP\nSTOSB\nHLT\n");
        Assertions.assertEquals(Opcode.STOSB, program.get(0).getOpcode());
        Assertions.assertEquals(Opcode.REP, program.get(0).getPrefix());
    }

    // ---- Basic MOV ----
    @Test void mov_reg_reg() {
        Instruction i = parser.parseLine("MOV AX, BX");
        Assertions.assertEquals(Opcode.MOV, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.REG_REG, i.getFormat());
        Assertions.assertEquals("AX", i.getDestReg());
        Assertions.assertEquals("BX", i.getSrcReg());
    }

    @Test void mov_reg_imm() {
        Instruction i = parser.parseLine("MOV AX, 100");
        Assertions.assertEquals(Opcode.MOV, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.REG_IMM, i.getFormat());
        Assertions.assertEquals("AX", i.getDestReg());
        Assertions.assertEquals(100, i.getImmediate());
    }

    @Test void mov_hex_imm() {
        Instruction i = parser.parseLine("MOV AX, 0xFF");
        Assertions.assertEquals(255, i.getImmediate());
    }

    // ---- Segment register MOV restrictions ----
    @Test void mov_cs_ax_illegal() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> parser.parseLine("MOV CS, AX"));
    }

    @Test void mov_ds_ax_legal() {
        Instruction i = parser.parseLine("MOV DS, AX");
        Assertions.assertEquals(Opcode.MOV, i.getOpcode());
    }

    // ---- LOAD/STORE ----
    @Test void load_reg_mem() {
        Instruction i = parser.parseLine("LOAD AX, [100]");
        Assertions.assertEquals(Opcode.LOAD, i.getOpcode());
        Assertions.assertEquals("AX", i.getDestReg());
    }

    @Test void load_reg_indirect() {
        Instruction i = parser.parseLine("LOAD AX, [BX]");
        Assertions.assertEquals(Opcode.LOAD, i.getOpcode());
        Assertions.assertEquals("BX", i.getBaseReg());
    }

    @Test void store_mem_reg() {
        Instruction i = parser.parseLine("STORE [200], AX");
        Assertions.assertEquals(Opcode.STORE, i.getOpcode());
        Assertions.assertEquals("AX", i.getDestReg());
    }

    // ---- Effective address parsing ----
    @Test void effective_address_bx_si_disp() {
        Instruction i = parser.parseLine("LOAD AX, [BX+SI+10]");
        Assertions.assertEquals("BX", i.getBaseReg());
        Assertions.assertEquals("SI", i.getIndexReg());
        Assertions.assertEquals(10, i.getDisplacement());
    }

    @Test void effective_address_bp_di() {
        Instruction i = parser.parseLine("LOAD AX, [BP+DI]");
        Assertions.assertEquals("BP", i.getBaseReg());
        Assertions.assertEquals("DI", i.getIndexReg());
    }

    @Test void effective_address_negative_disp() {
        Instruction i = parser.parseLine("LOAD AX, [BX-5]");
        Assertions.assertEquals("BX", i.getBaseReg());
        Assertions.assertEquals(-5, i.getDisplacement());
    }

    // ---- Arithmetic ----
    @Test void add_reg_reg() {
        Instruction i = parser.parseLine("ADD AX, BX");
        Assertions.assertEquals(Opcode.ADD, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.REG_REG, i.getFormat());
    }

    @Test void sub_reg_imm() {
        Instruction i = parser.parseLine("SUB AX, 50");
        Assertions.assertEquals(Opcode.SUB, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.REG_IMM, i.getFormat());
        Assertions.assertEquals(50, i.getImmediate());
    }

    // ---- Inc/Dec ----
    @Test void inc_reg() {
        Instruction i = parser.parseLine("INC AX");
        Assertions.assertEquals(Opcode.INC, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.REG_ONLY, i.getFormat());
        Assertions.assertEquals("AX", i.getDestReg());
    }

    // ---- Shifts ----
    @Test void shl_reg_imm() {
        Instruction i = parser.parseLine("SHL AX, 4");
        Assertions.assertEquals(Opcode.SHL_SAL, i.getOpcode());
        Assertions.assertEquals(4, i.getImmediate());
    }

    // ---- Push/Pop ----
    @Test void push_reg() {
        Instruction i = parser.parseLine("PUSH AX");
        Assertions.assertEquals(Opcode.PUSH, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.REG_ONLY, i.getFormat());
    }

    @Test void push_seg() {
        Instruction i = parser.parseLine("PUSH DS");
        Assertions.assertEquals(Opcode.PUSH, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.SEG_REG_ONLY, i.getFormat());
    }

    // ---- Control flow ----
    @Test void jmp() {
        Instruction i = parser.parseLine("JMP 100");
        Assertions.assertEquals(Opcode.JMP, i.getOpcode());
        Assertions.assertEquals(100, i.getAddress());
    }

    @Test void jz() {
        Instruction i = parser.parseLine("JZ 50");
        Assertions.assertEquals(Opcode.JZ_JE, i.getOpcode());
        Assertions.assertEquals(50, i.getAddress());
    }

    @Test void call() {
        Instruction i = parser.parseLine("CALL 200");
        Assertions.assertEquals(Opcode.CALL, i.getOpcode());
        Assertions.assertEquals(200, i.getAddress());
    }

    // ---- No-operand instructions ----
    @Test void hlt() {
        Instruction i = parser.parseLine("HLT");
        Assertions.assertEquals(Opcode.HLT, i.getOpcode());
        Assertions.assertEquals(InstructionFormat.NO_OPERAND, i.getFormat());
    }

    @Test void nop() {
        Instruction i = parser.parseLine("NOP");
        Assertions.assertEquals(Opcode.NOP, i.getOpcode());
    }

    @Test void ret() {
        Instruction i = parser.parseLine("RET");
        Assertions.assertEquals(Opcode.RET, i.getOpcode());
    }

    @Test void clc() {
        Instruction i = parser.parseLine("CLC");
        Assertions.assertEquals(Opcode.CLC, i.getOpcode());
    }

    // ---- INT ----
    @Test void int_imm() {
        Instruction i = parser.parseLine("INT 0x21");
        Assertions.assertEquals(Opcode.INT, i.getOpcode());
        Assertions.assertEquals(0x21, i.getImmediate());
    }

    // ---- Program parsing ----
    @Test void parse_program() {
        String prog = "MOV AX, 10\nADD AX, 20\nHLT\n";
        List<Instruction> instructions = parser.parseProgram(prog);
        Assertions.assertEquals(3, instructions.size());
        Assertions.assertEquals(Opcode.MOV, instructions.get(0).getOpcode());
        Assertions.assertEquals(Opcode.ADD, instructions.get(1).getOpcode());
        Assertions.assertEquals(Opcode.HLT, instructions.get(2).getOpcode());
    }

    @Test void skip_comments() {
        String prog = "; comment\nMOV AX, 1 ; inline\n";
        List<Instruction> instructions = parser.parseProgram(prog);
        Assertions.assertEquals(1, instructions.size());
    }

    // ---- Error handling ----
    @Test void unknown_opcode() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> parser.parseLine("BOGUS AX"));
    }

    @Test void invalid_register() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> parser.parseLine("MOV ZZ, AX"));
    }
}
