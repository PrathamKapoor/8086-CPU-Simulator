package debugger;

import instruction.InstructionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 Step 20: A -> B -> C, checkpoint at A, execute B and C, restore A,
 * execute B and C again -- the final state after the second pass must match
 * the first exactly, across every category of state the design note audited
 * (registers, memory, stack, flags, branches/loops, BIU queue, timing).
 */
class TimeTravelMatrixTest {
    private DebugSession session(String program) {
        return DebugSession.forSourceProgram(new InstructionParser().parseProgram(program));
    }

    private ExecutionSnapshot replayFromCheckpointAndCompare(DebugSession s, int stepsToA, int stepsBAndC) {
        for (int i = 0; i < stepsToA; i++) s.stepInstruction();
        int a = s.checkpoint();
        for (int i = 0; i < stepsBAndC; i++) s.stepInstruction();
        ExecutionSnapshot firstPass = s.snapshotNow();

        assertTrue(s.restore(a));
        for (int i = 0; i < stepsBAndC; i++) s.stepInstruction();
        ExecutionSnapshot secondPass = s.snapshotNow();

        assertEquals(firstPass, secondPass);
        return secondPass;
    }

    @Test void registersAndGeneralArithmetic() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nSUB BX, AX\nMOV CX, AX\nHLT\n");
        replayFromCheckpointAndCompare(s, 2, 3);
    }

    @Test void memoryMutations() {
        DebugSession s = session("MOV BX, 0020H\nMOV WORD [BX], 1111H\nADD WORD [BX], 2222H\nMOV AX, [BX]\nHLT\n");
        ExecutionSnapshot result = replayFromCheckpointAndCompare(s, 1, 3);
        assertEquals(0x3333, s.cpu().getRegister("AX").output());
    }

    @Test void stackMutations() {
        DebugSession s = session("MOV SP, 0100H\nMOV AX, 1234H\nPUSH AX\nMOV BX, 5678H\nPUSH BX\nPOP CX\nHLT\n");
        replayFromCheckpointAndCompare(s, 1, 5);
        assertEquals(0x5678, s.cpu().getRegister("CX").output());
    }

    @Test void flags() {
        DebugSession s = session("MOV AX, 7FFFH\nADD AX, 0001H\nMOV BX, 0000H\nCMP BX, 0000H\nHLT\n");
        replayFromCheckpointAndCompare(s, 0, 3);
    }

    @Test void branches() {
        DebugSession s = session("MOV AX, 0001H\nCMP AX, 0001H\nJZ eq\nMOV BX, 1111H\nJMP after\neq: MOV BX, 2222H\nafter: HLT\n");
        ExecutionSnapshot result = replayFromCheckpointAndCompare(s, 1, 3);
        assertEquals(0x2222, s.cpu().getRegister("BX").output());
    }

    @Test void loops() {
        DebugSession s = session("MOV CX, 0004H\nMOV AX, 0000H\nback: INC AX\nLOOP back\nHLT\n");
        replayFromCheckpointAndCompare(s, 1, 9); // several loop iterations across the checkpoint boundary, through HLT
        assertEquals(4, s.cpu().getRegister("AX").output());
        assertEquals(0, s.cpu().getRegister("CX").output());
    }

    @Test void machineCodeInstructions() {
        // MOV CX,4 ; back: INC AX ; LOOP back ; HLT
        byte[] bytes = { (byte) 0xB9, 0x04, 0x00, 0x40, (byte) 0xE2, (byte) 0xFD, (byte) 0xF4 };
        DebugSession s = DebugSession.forMachineCode(bytes);
        for (int i = 0; i < 1; i++) s.stepInstruction();
        int checkpoint = s.checkpoint();
        for (int i = 0; i < 3; i++) s.stepInstruction();
        ExecutionSnapshot firstPass = s.snapshotNow();

        assertTrue(s.restore(checkpoint));
        for (int i = 0; i < 3; i++) s.stepInstruction();
        ExecutionSnapshot secondPass = s.snapshotNow();

        assertEquals(firstPass, secondPass);
    }

    @Test void biuQueueAndTimingStateAreRestoredFaithfully() {
        DebugSession s = session("MOV AX, 0001H\nMOV BX, 0002H\nMOV CX, 0003H\nHLT\n");
        s.stepInstruction();
        int checkpoint = s.checkpoint();
        ExecutionSnapshot atCheckpoint = s.snapshotNow();

        s.stepInstruction();
        s.stepInstruction();
        assertTrue(s.restore(checkpoint));
        ExecutionSnapshot afterRestore = s.snapshotNow();

        assertEquals(atCheckpoint.prefetchQueueContents(), afterRestore.prefetchQueueContents());
        assertEquals(atCheckpoint.fetchPhysicalAddress(), afterRestore.fetchPhysicalAddress());
        assertEquals(atCheckpoint.nextFetchOffset(), afterRestore.nextFetchOffset());
        assertEquals(atCheckpoint.totalCyclesRun(), afterRestore.totalCyclesRun());
        assertEquals(atCheckpoint.bytesConsumed(), afterRestore.bytesConsumed());
        assertEquals(atCheckpoint, afterRestore);
    }

    @Test void multipleSequentialCheckpointsRestoreInAnyOrderUntilAnEarlierOneIsUsed() {
        DebugSession s = session("MOV AX, 0001H\nMOV AX, 0002H\nMOV AX, 0003H\nMOV AX, 0004H\nHLT\n");
        s.stepInstruction();
        int cp1 = s.checkpoint(); // AX==1
        s.stepInstruction();
        int cp2 = s.checkpoint(); // AX==2
        s.stepInstruction();
        int cp3 = s.checkpoint(); // AX==3

        assertTrue(s.restore(cp2));
        assertEquals(2, s.cpu().getRegister("AX").output());

        // Restoring to cp1 truncates the shared undo journal back to cp1's position.
        // cp2/cp3 pointed further along that now-discarded timeline (this is an
        // undo/redo-style divergence, not a branching snapshot DAG -- see
        // docs/verification/phase-5-debugger-design-note.md), so both become invalid.
        assertTrue(s.restore(cp1));
        assertEquals(1, s.cpu().getRegister("AX").output());
        assertFalse(s.restore(cp2));
        assertFalse(s.restore(cp3));
        assertEquals(1, s.cpu().getRegister("AX").output()); // unaffected by the failed restores
    }
}
