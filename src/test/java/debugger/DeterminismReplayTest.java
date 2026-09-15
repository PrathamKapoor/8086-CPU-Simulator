package debugger;

import instruction.InstructionParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 5 Step 18: running the same program twice, and checkpoint/restore/continue, must be exactly reproducible. */
class DeterminismReplayTest {
    private static final String PROGRAM =
        "MOV CX, 0003H\nMOV AX, 0000H\nback: INC AX\nADD AX, CX\nLOOP back\nMOV BX, AX\nHLT\n";

    private DebugSession freshSession() {
        DebugSession s = DebugSession.forSourceProgram(new InstructionParser().parseProgram(PROGRAM));
        s.setTracing(true);
        return s;
    }

    @Test void sameProgramTwiceProducesIdenticalFinalStateAndTrace() {
        DebugSession s1 = freshSession();
        DebugSession s2 = freshSession();
        s1.run();
        s2.run();

        assertEquals(s1.snapshotNow(), s2.snapshotNow());
        assertEquals(traceSignature(s1), traceSignature(s2));
        assertEquals(instructionSequence(s1), instructionSequence(s2));
        assertEquals(microOpSequence(s1), microOpSequence(s2));
    }

    @Test void checkpointRestoreContinueReproducesTheSameSuffix() {
        DebugSession s = freshSession();
        // Run to a fixed point mid-loop.
        for (int i = 0; i < 4; i++) s.stepInstruction();
        int checkpoint = s.checkpoint();
        s.clearTrace(); // isolate the first suffix's trace from the setup steps above

        s.run();
        String firstSuffixTrace = traceSignature(s);
        ExecutionSnapshot firstFinal = s.snapshotNow();

        assertTrue(s.restore(checkpoint));
        s.clearTrace(); // isolate the second suffix's trace from the first
        s.run();
        String secondSuffixTrace = traceSignature(s);
        ExecutionSnapshot secondFinal = s.snapshotNow();

        assertEquals(firstFinal, secondFinal);
        assertEquals(firstSuffixTrace, secondSuffixTrace);
    }

    @Test void machineCodeProgramIsAlsoDeterministicAcrossRuns() {
        byte[] bytes = { (byte) 0xB9, 0x03, 0x00, (byte) 0xB8, 0x00, 0x00, 0x40, (byte) 0xF4 }; // MOV CX,3; MOV AX,0; INC AX; HLT
        DebugSession s1 = DebugSession.forMachineCode(bytes);
        DebugSession s2 = DebugSession.forMachineCode(bytes);
        s1.setTracing(true);
        s2.setTracing(true);
        s1.run();
        s2.run();
        assertEquals(s1.snapshotNow(), s2.snapshotNow());
        assertEquals(traceSignature(s1), traceSignature(s2));
    }

    private static String traceSignature(DebugSession s) {
        return s.trace().stream().map(TraceEvent::toJson).collect(Collectors.joining("\n"));
    }

    private static List<Integer> instructionSequence(DebugSession s) {
        return s.trace().stream()
            .filter(e -> e instanceof TraceEvent.InstructionStart)
            .map(e -> ((TraceEvent.InstructionStart) e).instructionIndex())
            .collect(Collectors.toList());
    }

    private static List<String> microOpSequence(DebugSession s) {
        return s.trace().stream()
            .filter(e -> e instanceof TraceEvent.MicroOpExecuted)
            .map(e -> ((TraceEvent.MicroOpExecuted) e).microOpType())
            .collect(Collectors.toList());
    }
}
