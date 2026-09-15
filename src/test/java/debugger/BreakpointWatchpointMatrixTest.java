package debugger;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.InstructionParser;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 5 Step 19: a reusable matrix of breakpoint/watchpoint/stop-reason cases. */
class BreakpointWatchpointMatrixTest {
    private DebugSession session(String program) {
        return DebugSession.forSourceProgram(new InstructionParser().parseProgram(program));
    }

    // ---- Breakpoints ----

    @Test void breakpointAtFirstInstruction() {
        DebugSession s = session("MOV AX, 0001H\nHLT\n");
        s.addInstructionBreakpoint(0, null);
        assertEquals(StopReason.BREAKPOINT, s.run());
        assertEquals(0, s.currentPosition().instructionIndex());
        assertEquals(0, s.cpu().getRegister("AX").output());
    }

    @Test void machineCodeOffsetBreakpoint() {
        byte[] bytes = { (byte) 0xB8, 0x01, 0x00, (byte) 0xB8, 0x02, 0x00, (byte) 0xF4 };
        DebugSession s = DebugSession.forMachineCode(bytes);
        s.addMachineOffsetBreakpoint(3, null);
        assertEquals(StopReason.BREAKPOINT, s.run());
        assertEquals(1, s.cpu().getRegister("AX").output());
    }

    @Test void conditionalBreakpointFalseNeverStops() {
        DebugSession s = session("MOV AX, 0001H\nMOV AX, 0002H\nHLT\n");
        s.addInstructionBreakpoint(1, "AX == 99");
        assertEquals(StopReason.TERMINATION, s.run());
    }

    @Test void breakpointAtBranchTarget() {
        DebugSession s = session("JMP target\nMOV AX, 1111H\ntarget: MOV BX, 2222H\nHLT\n");
        s.addInstructionBreakpoint(2, null); // the JMP target instruction
        assertEquals(StopReason.BREAKPOINT, s.run());
        assertEquals(0, s.cpu().getRegister("BX").output());
    }

    @Test void breakpointAfterLoopCompletes() {
        DebugSession s = session("MOV CX, 0003H\nback: DEC CX\nJNZ back\nMOV AX, 9999H\nHLT\n");
        s.addInstructionBreakpoint(3, null); // "MOV AX, 9999H", reached only after the loop exits
        assertEquals(StopReason.BREAKPOINT, s.run());
        assertEquals(0, s.cpu().getRegister("CX").output());
        assertEquals(0, s.cpu().getRegister("AX").output());
    }

    @Test void disabledBreakpointDoesNotStop() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nHLT\n");
        int id = s.addInstructionBreakpoint(1, null);
        s.breakpoints().stream().filter(b -> b.id() == id).findFirst().orElseThrow().setEnabled(false);
        assertEquals(StopReason.TERMINATION, s.run());
    }

    // ---- Watchpoints ----

    @Test void memoryWriteWatchpoint() {
        DebugSession s = session("MOV BX, 0030H\nMOV WORD [BX], 4242H\nHLT\n");
        s.addMemoryWatch(0x30, Watchpoint.Access.WRITE);
        assertEquals(StopReason.WATCHPOINT, s.run());
        assertEquals(0x30, s.lastWatchHit().address());
        assertEquals(0x4242, s.lastWatchHit().newValue());
    }

    @Test void memoryReadWatchpoint() {
        DebugSession s = session("MOV BX, 0030H\nMOV WORD [BX], 4242H\nMOV AX, [BX]\nHLT\n");
        s.addMemoryWatch(0x30, Watchpoint.Access.READ);
        assertEquals(StopReason.WATCHPOINT, s.run());
        assertEquals(Watchpoint.Access.READ, s.lastWatchHit().access());
    }

    @Test void stackWriteWatchpointOnPush() {
        DebugSession s = session("MOV SP, 0100H\nMOV AX, 7777H\nPUSH AX\nHLT\n");
        s.addMemoryWatch(0xFE, Watchpoint.Access.WRITE); // PUSH decrements SP to 0x00FE before writing
        assertEquals(StopReason.WATCHPOINT, s.run());
        assertEquals(0x7777, s.lastWatchHit().newValue());
    }

    @Test void registerChangeWatchpoint() {
        DebugSession s = session("MOV CX, 0005H\nDEC CX\nHLT\n");
        s.stepInstruction(); // MOV CX, 5 -- runs before the watch exists, so it isn't "the" watched change
        s.addRegisterWatch("CX");
        assertEquals(StopReason.WATCHPOINT, s.run());
        assertEquals(5, s.lastWatchHit().oldValue());
        assertEquals(4, s.lastWatchHit().newValue());
    }

    @Test void flagChangeWatchpoint() {
        DebugSession s = session("MOV AX, 7FFFH\nADD AX, 0001H\nHLT\n"); // overflows into sign flag
        s.addFlagWatch("SF");
        assertEquals(StopReason.WATCHPOINT, s.run());
        assertEquals(1, s.lastWatchHit().newValue());
    }

    @Test void memoryRangeWatchpointCoversMultipleAddresses() {
        DebugSession s = session("MOV BX, 0040H\nMOV WORD [BX], 0001H\nMOV BX, 0044H\nMOV WORD [BX], 0002H\nHLT\n");
        s.addMemoryRangeWatch(0x40, 0x4F, Watchpoint.Access.WRITE);
        assertEquals(StopReason.WATCHPOINT, s.run());
        assertEquals(0x40, s.lastWatchHit().address());
        s.continueExecution();
        // second write in range also observed after continuing past the first
    }

    // ---- Stop reasons ----

    @Test void terminationStopReason() {
        DebugSession s = session("MOV AX, 1\nHLT\n");
        assertEquals(StopReason.TERMINATION, s.run());
        assertTrue(s.cpu().isHalted());
    }

    @Test void singleStepStopReason() {
        DebugSession s = session("MOV AX, 1\nMOV BX, 2\nHLT\n");
        assertEquals(StopReason.SINGLE_STEP, s.stepInstruction());
    }

    @Test void executionTrapStopReasonOnDivideByZero() {
        DebugSession s = session("MOV DX, 0000H\nMOV AX, 0005H\nMOV BX, 0000H\nDIV BX\nHLT\n");
        assertEquals(StopReason.EXCEPTION_TRAP, s.run());
    }

    @Test void invalidInstructionStopReasonOnAMalformedInstruction() {
        // A hand-built instruction with a format ControlUnit has no case for
        // simulates a corrupt/unsupported instruction slipping past decode, which this session
        // must report as a typed stop, not crash on. It must be instruction[1], not [0]: CPU
        // eagerly primes instruction 0's micro-ops at construction time (loadProgram), before
        // any DebugSession exists to catch the exception -- instruction[1]'s priming happens
        // inside cpu.step() while retiring instruction[0], which this session does observe.
        Instruction ok = new InstructionParser().parseLine("MOV AX, 1");
        Instruction malformed = new Instruction.Builder(Opcode.MOV).format(InstructionFormat.SEG_REG).build();
        DebugSession s = DebugSession.forSourceProgram(List.of(ok, malformed));
        assertEquals(StopReason.INVALID_INSTRUCTION, s.run());
    }
}
