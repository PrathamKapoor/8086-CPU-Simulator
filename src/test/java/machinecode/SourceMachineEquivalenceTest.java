package machinecode;

import org.junit.jupiter.api.Test;

/**
 * Every SUPPORTED family in docs/verification/phase-4-8086-coverage-matrix.md
 * gets at least one source-vs-machine-code execution comparison here; families
 * with runtime-observable state (flags, carry propagation, stack effects) get
 * several. INT/INTO/IRET are intentionally not added here: the simplified IVT
 * read in MicroOperationExecutor.int_() jumps to whatever raw byte sits at
 * vector*4, which for an unpopulated vector table is instruction index 0 —
 * identical (and therefore still "equivalent") on both paths, but it turns
 * the program into an infinite loop instead of reaching HLT. Segment
 * override prefixes have no source-level syntax to compare against (they are
 * only ever produced directly by the decoder), so they stay covered by the
 * dedicated machine-code-only tests instead.
 */
class SourceMachineEquivalenceTest {
    @Test void movRegisterAndMemoryForms() {
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 1234H\nMOV BX, 0100H\nADD AX, BX\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV BX, 0020H\nMOV WORD [BX], 1234H\nMOV AX, [BX]\nMOV [BX+2], AX\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 0055H\nMOV DS, AX\nMOV BX, DS\nMOV ES, AX\nHLT\n");
    }

    @Test void xchgRegisterAndMemory() {
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 0001H\nMOV BX, 0002H\nXCHG AX, BX\nTEST AX, BX\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV BX, 0020H\nMOV WORD [BX], 1111H\nMOV AX, 2222H\nXCHG AX, [BX]\nMOV CX, [BX]\nHLT\n");
    }

    @Test void arithmeticWithCarryAndMemoryOperands() {
        SourceMachineEquivalenceHarness.assertEquivalent("STC\nMOV AX, 0001H\nMOV BX, 0002H\nADC AX, BX\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("STC\nMOV AX, 0005H\nMOV BX, 0002H\nSBB AX, BX\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV BX, 0020H\nMOV WORD [BX], 0005H\nMOV AX, 0002H\nADD [BX], AX\nSUB AX, [BX]\nHLT\n");
    }

    @Test void logicalAndTestWithMemoryOperands() {
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV BX, 0020H\nMOV WORD [BX], 0F0FH\nMOV AX, 00FFH\nAND AX, [BX]\nOR AX, [BX]\nXOR AX, [BX]\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV BX, 0020H\nMOV WORD [BX], 00FFH\nMOV AX, 000FH\nTEST AX, [BX]\nHLT\n");
    }

    @Test void incDecNegNotRegisterAndMemory() {
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV AX, 0005H\nINC AX\nDEC AX\nMOV BX, 0020H\nMOV WORD [BX], 0001H\nNEG WORD [BX]\nNOT WORD [BX]\nHLT\n");
    }

    @Test void shiftsAndRotatesByOne() {
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV AX, 0002H\nSHL AX, 1\nSHR AX, 1\nMOV BX, 8000H\nSAR BX, 1\n"
            + "MOV CX, 0001H\nROL CX, 1\nROR CX, 1\nSTC\nMOV DX, 0000H\nRCL DX, 1\nRCR DX, 1\nHLT\n");
    }

    @Test void pushPopRegisterMemoryAndSegment() {
        SourceMachineEquivalenceHarness.assertEquivalent("PUSH AX\nPOP DI\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV SP, 0100H\nMOV AX, 1234H\nPUSH AX\nPOP BX\nPUSH DS\nPOP ES\n"
            + "MOV BX, 0020H\nMOV WORD [BX], 5678H\nPUSH [BX]\nPOP DX\nHLT\n");
    }

    @Test void callAndRetThroughALabel() {
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV SP, 0100H\nCALL sub\nJMP done\nsub: MOV AX, 9999H\nRET\ndone: HLT\n");
    }

    @Test void jmpAndConditionalJumps() {
        SourceMachineEquivalenceHarness.assertEquivalent("JMP target\nMOV AX, 0001H\ntarget: MOV AX, 5678H\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV AX, 0001H\nCMP AX, 0001H\nJZ eq\nMOV BX, 0000H\nJMP after\neq: MOV BX, 1111H\nafter: HLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV AX, 0001H\nCMP AX, 0002H\nJZ eq\nMOV BX, 2222H\nJMP after\neq: MOV BX, 1111H\nafter: HLT\n");
    }

    @Test void loopFamily() {
        SourceMachineEquivalenceHarness.assertEquivalent("MOV CX, 0003H\nMOV AX, 0000H\nback: INC AX\nLOOP back\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("MOV CX, 0000H\nJCXZ skip\nMOV AX, 1111H\nskip: HLT\n");
    }

    @Test void stringOperationsWithRepPrefix() {
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV CX, 0003H\nMOV AL, 41H\nMOV DI, 0040H\nCLD\nREP STOSB\nHLT\n");
    }

    @Test void inOutFixedForms() {
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AL, 55H\nOUT 10H, AL\nIN AL, 10H\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV DX, 0020H\nMOV AL, 66H\nOUT DX, AL\nIN AL, DX\nHLT\n");
    }

    @Test void leaLdsLesAndXlat() {
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV BX, 0010H\nLEA AX, [BX+2]\nMOV WORD [BX], 1234H\nMOV WORD [BX+2], 5678H\n"
            + "LDS CX, [BX]\nLES DX, [BX]\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("MOV BX, 0050H\nMOV AL, 00H\nMOV BYTE [BX], 77H\nXLAT\nHLT\n");
    }

    @Test void adjustInstructions() {
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 0009H\nAAA\nAAS\nMOV AX, 0025H\nDAA\nDAS\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 0009H\nAAM\nAAD\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 0080H\nCBW\nCWD\nHLT\n");
    }

    @Test void flagInstructions() {
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV SP, 0100H\nCLC\nSTC\nCMC\nMOV AX, 0009H\nAAA\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent(
            "MOV SP, 0100H\nCLD\nSTD\nCLI\nSTI\nPUSHF\nPOP AX\nMOV BX, 00FFH\nPUSH BX\nPOPF\nLAHF\nSAHF\nHLT\n");
    }
}
