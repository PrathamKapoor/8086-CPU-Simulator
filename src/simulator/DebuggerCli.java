package simulator;

import debugger.DebugSession;
import debugger.DebugState;
import debugger.ExecutionSnapshot;
import debugger.StateDiff;
import debugger.StopReason;
import debugger.Watchpoint;
import instruction.InstructionParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A deterministic, scriptable headless debugger CLI. No JavaFX dependency;
 * every capability here has a corresponding {@code debugger.DebugSession}
 * API call, so this class is a thin command parser over that model, not a
 * second place capabilities live.
 */
public final class DebuggerCli {
    private DebuggerCli() { }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: DebuggerCli <program.asm|--hex HEXBYTES> [scriptFile]");
            System.exit(2);
        }
        try {
            DebugSession session;
            int nextArg;
            if (args[0].equals("--hex")) {
                byte[] bytes = parseHex(args[1]);
                session = DebugSession.forMachineCode(bytes);
                nextArg = 2;
            } else {
                String source = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
                session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(source));
                nextArg = 1;
            }
            List<String> commands = new ArrayList<>();
            if (args.length > nextArg) {
                commands.addAll(Files.readAllLines(Path.of(args[nextArg]), StandardCharsets.UTF_8));
                runScript(session, commands, System.out);
            } else {
                runInteractive(session, System.in, System.out);
            }
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void runInteractive(DebugSession session, java.io.InputStream in, PrintStream out) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!executeCommand(session, line, out)) break;
            }
        }
    }

    /** Deterministic scripted execution — what tests drive. Returns after "quit" or end of script. */
    public static void runScript(DebugSession session, List<String> commands, PrintStream out) {
        for (String line : commands) {
            if (!executeCommand(session, line, out)) return;
        }
    }

    /** @return false if the session should stop (a "quit" command). */
    public static boolean executeCommand(DebugSession session, String rawLine, PrintStream out) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) return true;
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "quit", "exit" -> { return false; }
            case "break" -> {
                String[] p = rest.split("\\s+", 2);
                int index = Integer.parseInt(p[0]);
                String condition = p.length > 1 ? p[1] : null;
                int id = session.addInstructionBreakpoint(index, condition);
                out.println("breakpoint " + id + " at instruction[" + index + "]" + (condition != null ? " if " + condition : ""));
            }
            case "breakoffset" -> {
                String[] p = rest.split("\\s+", 2);
                int offset = parseIntFlexible(p[0]);
                String condition = p.length > 1 ? p[1] : null;
                int id = session.addMachineOffsetBreakpoint(offset, condition);
                out.println("breakpoint " + id + " at offset 0x" + Integer.toHexString(offset).toUpperCase(Locale.ROOT));
            }
            case "watch" -> {
                String[] p = rest.split("\\s+");
                int address = parseIntFlexible(p[0]);
                Watchpoint.Access access = p.length > 1 ? parseAccess(p[1]) : Watchpoint.Access.WRITE;
                int id = session.addMemoryWatch(address, access);
                out.println("watchpoint " + id + " on memory[0x" + Integer.toHexString(address).toUpperCase(Locale.ROOT) + "] (" + access + ")");
            }
            case "watchreg" -> {
                int id = session.addRegisterWatch(rest.trim());
                out.println("watchpoint " + id + " on register " + rest.trim().toUpperCase(Locale.ROOT));
            }
            case "watchflag" -> {
                int id = session.addFlagWatch(rest.trim());
                out.println("watchpoint " + id + " on flag " + rest.trim().toUpperCase(Locale.ROOT));
            }
            case "run" -> printStop(out, session, session.run());
            case "continue" -> printStop(out, session, session.continueExecution());
            case "step" -> printStop(out, session, session.stepInstruction());
            case "stepmicro" -> printStop(out, session, session.stepMicroOp());
            case "stepover" -> printStop(out, session, session.stepOver());
            case "stepout" -> printStop(out, session, session.stepOut());
            case "reset" -> { session.reset(); out.println("reset"); }
            case "trace" -> { session.setTracing(!rest.equals("off")); out.println("tracing " + (session.isTracing() ? "on" : "off")); }
            case "regs" -> printRegs(out, session);
            case "flags" -> out.println(session.cpu().getFlags().flagsStringFull());
            case "memory" -> printMemory(out, session, rest);
            case "stack" -> printStack(out, session);
            case "checkpoint" -> {
                int id = session.checkpoint();
                out.println("checkpoint " + id);
            }
            case "restore" -> {
                boolean ok = session.restore(Integer.parseInt(rest.trim()));
                out.println(ok ? "restored" : "no such checkpoint");
            }
            case "rewind" -> {
                int count = rest.isBlank() ? 1 : Integer.parseInt(rest.trim());
                boolean ok = session.rewindInstructions(count);
                out.println(ok ? "rewound " + count + " instruction(s)" : "cannot rewind that far");
            }
            case "explain" -> {
                var explanation = session.explainLastRetiredInstruction();
                out.println(explanation == null ? "no retired instruction to explain (enable trace and step first)" : explanation.toJson());
            }
            case "state" -> printState(out, session, false);
            case "statejson" -> printState(out, session, true);
            case "tracejson" -> out.println("[" + String.join(",", session.trace().stream().map(e -> e.toJson()).toList()) + "]");
            case "tracesave" -> {
                String json = "[" + String.join(",", session.trace().stream().map(e -> e.toJson()).toList()) + "]";
                try {
                    Files.writeString(Path.of(rest.trim()), json, StandardCharsets.UTF_8);
                    out.println("saved " + session.trace().size() + " trace event(s) to " + rest.trim());
                } catch (IOException e) {
                    out.println("tracesave failed: " + e.getMessage());
                }
            }
            case "traceload" -> {
                // Trace files are for later INSPECTION, not re-execution: this simulator's
                // supported "replay" mechanism is snapshot-based (checkpoint/restore, see
                // docs/verification/phase-5-debugger-design-note.md), never reconstructed
                // from a trace log. Loading a trace here prints it back for review.
                try {
                    String json = Files.readString(Path.of(rest.trim()), StandardCharsets.UTF_8);
                    out.println("REPLAY FROM TRACE is not supported (this is inspection-only); loaded trace:");
                    out.println(json);
                } catch (IOException e) {
                    out.println("traceload failed: " + e.getMessage());
                }
            }
            default -> out.println("unknown command: " + cmd);
        }
        return true;
    }

    private static void printStop(PrintStream out, DebugSession session, StopReason reason) {
        out.println("stop=" + reason + " ip=" + session.currentPosition().instructionIndex()
            + " halted=" + session.cpu().isHalted()
            + (reason == StopReason.BREAKPOINT ? " breakpoint=" + session.lastBreakpointId() : "")
            + (reason == StopReason.WATCHPOINT ? " watch=" + session.lastWatchHit().toJson() : ""));
    }

    private static void printRegs(PrintStream out, DebugSession session) {
        for (String name : new String[] {"AX", "BX", "CX", "DX", "SP", "BP", "SI", "DI", "CS", "DS", "SS", "ES", "IP"}) {
            out.println(name + "=" + String.format("%04X", session.cpu().getRegister(name).output()));
        }
    }

    private static void printMemory(PrintStream out, DebugSession session, String rest) {
        String[] p = rest.split("\\s+");
        int address = parseIntFlexible(p[0]);
        int length = p.length > 1 ? Integer.parseInt(p[1]) : 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%04X", session.cpu().getMemory().directRead(address + i)));
        }
        out.println(sb);
    }

    private static void printStack(PrintStream out, DebugSession session) {
        int sp = session.cpu().getRegister("SP").output();
        int ss = session.cpu().getRegister("SS").output();
        out.println("SS:SP=" + String.format("%04X:%04X", ss, sp) + " top=" + String.format("%04X",
            session.cpu().getMemory().directRead(session.cpu().computePhysicalAddress(ss, sp))));
    }

    private static void printState(PrintStream out, DebugSession session, boolean json) {
        DebugState state = session.currentState();
        if (json) {
            out.println(state.toJson());
            return;
        }
        out.println("position=" + state.position() + " stopReason=" + state.lastStopReason() + " halted=" + state.halted());
        out.println("instruction=" + state.currentInstructionText());
    }

    private static Watchpoint.Access parseAccess(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "read", "r" -> Watchpoint.Access.READ;
            case "write", "w" -> Watchpoint.Access.WRITE;
            case "rw", "readwrite" -> Watchpoint.Access.READ_WRITE;
            default -> throw new IllegalArgumentException("unknown watch access: " + text);
        };
    }

    private static int parseIntFlexible(String text) {
        String t = text.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("0X")) return Integer.parseInt(t.substring(2), 16);
        if (t.endsWith("H")) return Integer.parseInt(t.substring(0, t.length() - 1), 16);
        return Integer.parseInt(t);
    }

    private static byte[] parseHex(String value) {
        String compact = value.replaceAll("[\\s_]", "");
        byte[] bytes = new byte[compact.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
