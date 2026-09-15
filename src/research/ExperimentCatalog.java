package research;

import simulator.profiler.TimingModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A meaningful, versioned initial experiment catalog (Phase 6 Step 6),
 * separate from {@code simulator.experiment.BenchmarkCatalog} (Phase 3's
 * minimal benchmark set, left untouched and still used by
 * {@code MainSimulator --benchmark}). Every workload here is deterministic
 * source assembly; several are deliberately paired so exactly one variable
 * changes between them (see {@link #PAIRS}), making an A/B comparison's
 * conclusion causally attributable rather than a guess.
 *
 * Every catalog entry opts into {@code tracingEnabled} (the bare
 * {@link ExperimentConfiguration#timed()} default is still off, per Step 3
 * — this is a deliberate curation choice for a catalog built specifically to
 * study control-flow/debugger behavior, not a change to that default).
 */
public final class ExperimentCatalog {
    private ExperimentCatalog() { }

    /** One documented pair: two catalog experiment ids that differ by exactly one named variable. */
    public record Pair(String idA, String idB, String differingVariable) { }

    public static final java.util.List<Pair> PAIRS = java.util.List.of(
        new Pair("taken-branch", "not-taken-branch", "whether the CMP condition makes the JZ take the branch"),
        new Pair("string-operation", "rep-workload", "whether the string operation is issued once per instruction or driven by a REP prefix"),
        new Pair("source-representation", "machine-code-representation", "source-token program vs. the byte-identical machine-code encoding of the same program")
    );

    public static Map<String, Experiment> all() {
        Map<String, Experiment> c = new LinkedHashMap<>();

        c.put("sequential-alu", timed("sequential-alu",
            "A straight-line sequence of register-only ALU operations with no branches or memory access.",
            "MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nSUB BX, AX\nINC AX\nDEC BX\nHLT\n",
            // AX: 1 -> 3 (ADD) -> 4 (INC). BX: 2 -> 2-3=-1=0xFFFF (SUB) -> 0xFFFE (DEC).
            Map.of("AX", 4, "BX", 0xFFFE)));

        c.put("memory-heavy", timed("memory-heavy",
            "Repeated memory read/write through one pointer register, to study bus/memory metrics in isolation from control flow.",
            "MOV BX, 0100H\nMOV WORD [BX], 0005H\nADD WORD [BX], 0003H\nMOV AX, [BX]\nMOV WORD [BX+2], AX\nHLT\n",
            Map.of("AX", 8)));

        c.put("branch-heavy", timed("branch-heavy",
            "A decrement loop: three taken iterations of JNZ followed by one not-taken exit -- a mixed taken/not-taken workload.",
            "MOV CX, 0004H\nback: DEC CX\nJNZ back\nHLT\n",
            Map.of("CX", 0)));

        c.put("taken-branch", timed("taken-branch",
            "A single conditional jump whose condition is always true -- paired with not-taken-branch (see PAIRS).",
            "MOV AX, 0001H\nCMP AX, 0001H\nJZ target\nMOV BX, 9999H\ntarget: MOV BX, 1111H\nHLT\n",
            Map.of("BX", 0x1111)));

        c.put("not-taken-branch", timed("not-taken-branch",
            "A single conditional jump whose condition is always false -- paired with taken-branch (see PAIRS).",
            "MOV AX, 0001H\nCMP AX, 0002H\nJZ target\nMOV BX, 1111H\ntarget: MOV BX, 9999H\nHLT\n",
            Map.of("BX", 0x9999)));

        c.put("loop-workload", timed("loop-workload",
            "A LOOP-driven counting loop, exercising the LOOP-specific control-transfer path.",
            "MOV CX, 0005H\nMOV AX, 0000H\nback: INC AX\nLOOP back\nHLT\n",
            Map.of("AX", 5, "CX", 0)));

        c.put("string-operation", timed("string-operation",
            "Two MOVSB instructions issued individually (no REP) -- paired with rep-workload (see PAIRS).",
            "MOV SI, 0010H\nMOV DI, 0020H\nMOV BYTE [0010H], 41H\nMOV BYTE [0011H], 42H\nMOVSB\nMOVSB\nHLT\n",
            Map.of()));

        c.put("rep-workload", timed("rep-workload",
            "The same kind of string store, but driven by a REP prefix over four iterations in one instruction.",
            "MOV CX, 0004H\nMOV AL, 58H\nMOV DI, 0030H\nCLD\nREP STOSB\nHLT\n",
            Map.of("CX", 0)));

        c.put("queue-starvation", timed("queue-starvation",
            "Eight consecutive one-byte INC instructions: each decodes and executes in roughly the time the BIU needs to fetch one byte, so the EU repeatedly outruns the prefetch queue.",
            "MOV AX, 0000H\nINC AX\nINC AX\nINC AX\nINC AX\nINC AX\nINC AX\nINC AX\nINC AX\nHLT\n",
            Map.of("AX", 8)));

        c.put("bus-contention", timed("bus-contention",
            "Three memory writes immediately followed by three memory reads through the same pointer: the EU holds the bus for each memory access, repeatedly deferring the BIU's fetch.",
            "MOV BX, 0100H\nMOV WORD [BX], 0001H\nMOV WORD [BX+2], 0002H\nMOV WORD [BX+4], 0003H\nMOV AX, [BX]\nMOV CX, [BX+2]\nMOV DX, [BX+4]\nHLT\n",
            Map.of("AX", 1, "CX", 2, "DX", 3)));

        c.put("control-transfer-flush", timed("control-transfer-flush",
            "A chain of four consecutive unconditional JMPs, each of which discards and refills the prefetch queue.",
            "JMP l1\nl1: JMP l2\nl2: JMP l3\nl3: JMP l4\nl4: MOV AX, 9999H\nHLT\n",
            Map.of("AX", 0x9999)));

        c.put("source-representation", timed("source-representation",
            "MOV AX,1234H ; HLT expressed as source assembly -- paired with machine-code-representation (see PAIRS).",
            "MOV AX, 1234H\nHLT\n",
            Map.of("AX", 0x1234)));

        c.put("machine-code-representation", machineCode("machine-code-representation",
            "The byte-identical machine-code encoding (B8 34 12 F4) of source-representation's program -- paired with it (see PAIRS).",
            new byte[] { (byte) 0xB8, 0x34, 0x12, (byte) 0xF4 },
            Map.of("AX", 0x1234)));

        return java.util.Collections.unmodifiableMap(c);
    }

    public static Experiment get(String id) {
        Experiment e = all().get(id);
        if (e == null) throw new IllegalArgumentException("no such catalog experiment: " + id);
        return e;
    }

    private static Experiment timed(String id, String description, String source, Map<String, Integer> expected) {
        return new Experiment(id, description, Workload.fromSource(id, description, source),
            new ExperimentConfiguration(TimingModel.SIMPLIFIED_8086, ExperimentConfiguration.DEFAULT_MICRO_OP_LIMIT,
                true, false, java.util.List.of(), expected));
    }

    private static Experiment machineCode(String id, String description, byte[] bytes, Map<String, Integer> expected) {
        return new Experiment(id, description, Workload.fromMachineCode(id, description, bytes),
            new ExperimentConfiguration(TimingModel.SIMPLIFIED_8086, ExperimentConfiguration.DEFAULT_MICRO_OP_LIMIT,
                true, false, java.util.List.of(), expected));
    }
}
