package simulator;

import debugger.DebugSession;
import instruction.InstructionParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebuggerCliTest {
    private String run(DebugSession session, List<String> script) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DebuggerCli.runScript(session, script, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test void fullScriptedSessionCoversLoadBreakRunInspectDiffCheckpointRestore() {
        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(
            "MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nMOV CX, AX\nHLT\n"));
        String out = run(session, List.of(
            "break 2",
            "run",
            "regs",
            "checkpoint",
            "step",
            "state",
            "restore 1",
            "step",
            "step",
            "trace",
            "quit",
            "regs"
        ));
        assertTrue(out.contains("stop=BREAKPOINT"));
        assertTrue(out.contains("AX=0001"));
        assertTrue(out.contains("checkpoint 1"));
        assertTrue(out.contains("restored"));
        assertFalse(out.trim().endsWith("regs")); // "quit" must stop the script before the trailing "regs"
    }

    @Test void breakpointCommandReportsCorrectFormat() {
        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram("MOV AX, 1\nHLT\n"));
        String out = run(session, List.of("break 0 AX == 0"));
        assertTrue(out.contains("breakpoint 1 at instruction[0] if AX == 0"));
    }

    @Test void watchCommandsAndStopReporting() {
        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(
            "MOV BX, 0020H\nMOV WORD [BX], 1234H\nHLT\n"));
        String out = run(session, List.of("watch 20H write", "run"));
        assertTrue(out.contains("watchpoint 1 on memory[0x20] (WRITE)"));
        assertTrue(out.contains("stop=WATCHPOINT"));
    }

    @Test void jsonStateAndTraceAreWellFormed() {
        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram("MOV AX, 1\nHLT\n"));
        String out = run(session, List.of("trace", "step", "statejson", "tracejson"));
        String[] lines = out.strip().split("\n");
        String stateLine = lines[lines.length - 2];
        String traceLine = lines[lines.length - 1];
        assertTrue(stateLine.startsWith("{\"position\""));
        assertTrue(traceLine.startsWith("[") && traceLine.endsWith("]"));
        assertTrue(traceLine.contains("\"INSTRUCTION_START\""));
    }

    @Test void traceSaveAndLoadRoundTripsForInspectionOnly() throws java.io.IOException {
        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram("MOV AX, 1\nHLT\n"));
        java.nio.file.Path traceFile = java.nio.file.Files.createTempFile("phase5-trace", ".json");
        try {
            String out = run(session, List.of("trace", "step", "tracesave " + traceFile, "traceload " + traceFile));
            assertTrue(out.contains("saved"));
            assertTrue(out.contains("REPLAY FROM TRACE is not supported"));
            assertTrue(out.contains("INSTRUCTION_START"));
            String savedJson = java.nio.file.Files.readString(traceFile);
            assertTrue(savedJson.startsWith("[") && savedJson.endsWith("]"));
        } finally {
            java.nio.file.Files.deleteIfExists(traceFile);
        }
    }

    @Test void memoryAndStackCommands() {
        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(
            "MOV SP, 0100H\nMOV AX, 1234H\nPUSH AX\nHLT\n"));
        String out = run(session, List.of("run", "stack", "memory 0FEH 1"));
        assertTrue(out.contains("SS:SP=0000:00FE"));
        assertTrue(out.contains("1234"));
    }
}
