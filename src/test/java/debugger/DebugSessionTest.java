package debugger;

import instruction.InstructionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebugSessionTest {
    private DebugSession session(String program) {
        return DebugSession.forSourceProgram(new InstructionParser().parseProgram(program));
    }

    @Test void stepInstructionAdvancesOneInstructionAtATime() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nHLT\n");
        assertEquals(StopReason.SINGLE_STEP, s.stepInstruction());
        assertEquals(1, s.cpu().getRegister("AX").output());
        assertEquals(StopReason.SINGLE_STEP, s.stepInstruction());
        assertEquals(2, s.cpu().getRegister("BX").output());
        assertEquals(StopReason.TERMINATION, s.stepInstruction());
        assertTrue(s.cpu().isHalted());
    }

    @Test void stepMicroOpIsFinerGrainedThanStepInstruction() {
        DebugSession s = session("MOV AX, 0001H\nHLT\n");
        long before = s.microOpsExecuted();
        s.stepMicroOp();
        assertEquals(before + 1, s.microOpsExecuted());
        assertNotEquals(0, s.cpu().getBatchIndex()); // still mid-instruction (FETCH/DECODE micro-ops remain)
    }

    @Test void instructionBreakpointStopsBeforeExecutingIt() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nMOV CX, 0003H\nHLT\n");
        s.addInstructionBreakpoint(1, null); // the "MOV BX, 2" instruction
        StopReason r = s.run();
        assertEquals(StopReason.BREAKPOINT, r);
        assertEquals(1, s.lastBreakpointId());
        assertEquals(1, s.cpu().getRegister("AX").output());
        assertEquals(0, s.cpu().getRegister("BX").output()); // not yet executed
        assertEquals(1, s.currentPosition().instructionIndex());
    }

    @Test void conditionalBreakpointOnlyStopsWhenTrue() {
        DebugSession s = session("MOV CX, 0003H\nback: DEC CX\nJNZ back\nHLT\n");
        // rewrite with resolved label offsets manually since InstructionParser resolves labels itself
        s = session("MOV CX, 0003H\nback: DEC CX\nJNZ back\nHLT\n");
        s.addInstructionBreakpoint(1, "CX == 1");
        StopReason r = s.run();
        assertEquals(StopReason.BREAKPOINT, r);
        assertEquals(1, s.cpu().getRegister("CX").output());
    }

    @Test void machineOffsetBreakpointStopsAtTheRightByte() {
        byte[] bytes = { (byte) 0xB8, 0x01, 0x00, (byte) 0xB8, 0x02, 0x00, (byte) 0xF4 }; // MOV AX,1 ; MOV AX,2 ; HLT
        DebugSession s = DebugSession.forMachineCode(bytes);
        s.addMachineOffsetBreakpoint(3, null);
        StopReason r = s.run();
        assertEquals(StopReason.BREAKPOINT, r);
        assertEquals(1, s.cpu().getRegister("AX").output());
    }

    @Test void memoryWriteWatchpointReportsOldAndNewValue() {
        DebugSession s = session("MOV BX, 0020H\nMOV WORD [BX], 1234H\nHLT\n");
        s.addMemoryWatch(0x20, Watchpoint.Access.WRITE);
        StopReason r = s.run();
        assertEquals(StopReason.WATCHPOINT, r);
        WatchHit hit = s.lastWatchHit();
        assertEquals(0x20, hit.address());
        assertEquals(0, hit.oldValue());
        assertEquals(0x1234, hit.newValue());
        assertEquals(Watchpoint.Access.WRITE, hit.access());
    }

    @Test void memoryReadWatchpointStopsOnActualRead() {
        DebugSession s = session("MOV BX, 0020H\nMOV WORD [BX], 1234H\nMOV AX, [BX]\nHLT\n");
        s.addMemoryWatch(0x20, Watchpoint.Access.READ);
        StopReason r = s.run();
        assertEquals(StopReason.WATCHPOINT, r);
        assertEquals(Watchpoint.Access.READ, s.lastWatchHit().access());
        assertEquals(0x1234, s.lastWatchHit().newValue());
        // The watchpoint fires at the moment of the read micro-op, one step before
        // the subsequent micro-op commits the value into AX -- precise granularity,
        // not "after the whole instruction".
        assertEquals(0, s.cpu().getRegister("AX").output());
    }

    @Test void registerWatchpointReportsChange() {
        DebugSession s = session("MOV AX, 0001H\nMOV AX, 0002H\nHLT\n");
        s.addRegisterWatch("AX");
        StopReason r = s.run();
        assertEquals(StopReason.WATCHPOINT, r);
        assertEquals(0, s.lastWatchHit().oldValue());
        assertEquals(1, s.lastWatchHit().newValue());
    }

    @Test void flagWatchpointReportsChange() {
        DebugSession s = session("MOV AX, 0000H\nADD AX, 0000H\nHLT\n"); // ADD 0+0 sets ZF
        s.addFlagWatch("ZF");
        StopReason r = s.run();
        assertEquals(StopReason.WATCHPOINT, r);
        assertEquals("ZF", s.lastWatchHit().name());
        assertEquals(1, s.lastWatchHit().newValue());
    }

    @Test void checkpointRestoreProducesIdenticalReExecution() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nMOV CX, AX\nHLT\n");
        s.stepInstruction(); // MOV AX,1
        s.stepInstruction(); // MOV BX,2
        int checkpoint = s.checkpoint(); // right before ADD AX,BX
        s.stepInstruction(); // ADD
        s.stepInstruction(); // MOV CX,AX
        ExecutionSnapshot firstRun = s.snapshotNow();

        assertTrue(s.restore(checkpoint));
        assertEquals(1, s.cpu().getRegister("AX").output());
        assertEquals(2, s.cpu().getRegister("BX").output());

        s.stepInstruction();
        s.stepInstruction();
        ExecutionSnapshot secondRun = s.snapshotNow();

        assertEquals(firstRun, secondRun);
        assertEquals(3, s.cpu().getRegister("CX").output());
    }

    @Test void restoreUndoesMemoryWrites() {
        DebugSession s = session("MOV BX, 0020H\nMOV WORD [BX], 1234H\nHLT\n");
        int checkpoint = s.checkpoint(); // before anything runs
        s.stepInstruction();
        s.stepInstruction();
        assertEquals(0x1234, s.cpu().getMemory().directRead(0x20));
        assertTrue(s.restore(checkpoint));
        assertEquals(0, s.cpu().getMemory().directRead(0x20));
        assertEquals(0, s.cpu().getRegister("BX").output());
    }

    @Test void rewindInstructionsReturnsToAnEarlierAutoCheckpoint() {
        DebugSession s = session("MOV AX, 0001H\nMOV AX, 0002H\nMOV AX, 0003H\nHLT\n");
        s.stepInstruction();
        s.stepInstruction();
        s.stepInstruction();
        assertEquals(3, s.cpu().getRegister("AX").output());
        assertTrue(s.rewindInstructions(2));
        assertEquals(1, s.cpu().getRegister("AX").output());
    }

    @Test void traceRecordsTypedInstructionAndMicroOpEvents() {
        DebugSession s = session("MOV AX, 0001H\nHLT\n");
        s.setTracing(true);
        s.stepInstruction();
        boolean hasStart = s.trace().stream().anyMatch(e -> e instanceof TraceEvent.InstructionStart);
        boolean hasRetired = s.trace().stream().anyMatch(e -> e instanceof TraceEvent.InstructionRetired);
        boolean hasMicroOp = s.trace().stream().anyMatch(e -> e instanceof TraceEvent.MicroOpExecuted);
        boolean hasRegMutation = s.trace().stream().anyMatch(e -> e instanceof TraceEvent.RegisterMutation rm && rm.register().equals("AX"));
        assertTrue(hasStart);
        assertTrue(hasRetired);
        assertTrue(hasMicroOp);
        assertTrue(hasRegMutation);
    }

    @Test void stateDiffReportsRegisterAndFlagChanges() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nHLT\n");
        s.stepInstruction();
        s.stepInstruction();
        ExecutionSnapshot before = s.snapshotNow();
        s.stepInstruction();
        ExecutionSnapshot after = s.snapshotNow();
        StateDiff diff = DebugSession.diff(before, after);
        assertTrue(diff.registerChanges().stream().anyMatch(c -> c.register().equals("AX") && c.oldValue() == 1 && c.newValue() == 3));
        assertFalse(diff.isEmpty());
    }

    @Test void explainLastRetiredInstructionDerivesFromRealTraceEvents() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nHLT\n");
        s.setTracing(true);
        s.stepInstruction();
        s.stepInstruction();
        s.stepInstruction(); // ADD AX, BX
        InstructionExplanation explanation = s.explainLastRetiredInstruction();
        assertNotNull(explanation);
        assertTrue(explanation.registersWritten().contains("AX"));
        assertTrue(explanation.registersRead().contains("BX"));
    }

    @Test void stepOverSkipsAnEntireSubroutine() {
        DebugSession s = session("MOV SP, 0100H\nCALL sub\nMOV AX, 9999H\nHLT\nsub: MOV BX, 1111H\nRET\n");
        s.stepInstruction(); // MOV SP
        StopReason r = s.stepOver(); // CALL sub -> runs sub to completion as one logical step
        assertEquals(StopReason.SINGLE_STEP, r);
        assertEquals(0x1111, s.cpu().getRegister("BX").output());
        assertEquals(2, s.currentPosition().instructionIndex()); // back at "MOV AX, 9999H"
    }

    @Test void stepOutReturnsFromTheCurrentSubroutine() {
        DebugSession s = session("MOV SP, 0100H\nCALL sub\nMOV AX, 9999H\nHLT\nsub: MOV BX, 1111H\nMOV CX, 2222H\nRET\n");
        s.stepInstruction(); // MOV SP
        s.stepInstruction(); // CALL sub (enters subroutine)
        StopReason r = s.stepOut();
        assertEquals(StopReason.SINGLE_STEP, r);
        assertEquals(2, s.currentPosition().instructionIndex());
    }

    @Test void runRespectsAnExplicitExecutionLimit() {
        DebugSession s = session("back: MOV AX, 0001H\nJMP back\n");
        StopReason r = s.run(10);
        assertEquals(StopReason.EXECUTION_LIMIT, r);
    }

    @Test void determinismAcrossTwoIndependentSessions() {
        String program = "MOV AX, 0001H\nMOV BX, 0002H\nMOV CX, 0003H\nADD AX, BX\nSUB CX, AX\nHLT\n";
        DebugSession s1 = session(program);
        DebugSession s2 = session(program);
        s1.run();
        s2.run();
        assertEquals(s1.snapshotNow(), s2.snapshotNow());
    }
}
