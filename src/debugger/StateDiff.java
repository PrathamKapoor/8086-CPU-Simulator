package debugger;

import cpu.registers.FLAGS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deterministic architectural diff between two points in one execution
 * timeline. Powers both the CLI {@code diff} command and the GUI's state-
 * change panel — neither duplicates this logic.
 */
public record StateDiff(
    List<RegisterChange> registerChanges,
    List<FlagChange> flagChanges,
    List<MemoryChange> memoryChanges
) {
    public record RegisterChange(String register, int oldValue, int newValue) {
        public String toJson() {
            return "{\"register\":" + Json.str(register) + ",\"oldValue\":" + oldValue + ",\"newValue\":" + newValue + "}";
        }
    }

    public record FlagChange(String flag, boolean oldValue, boolean newValue) {
        public String toJson() {
            return "{\"flag\":" + Json.str(flag) + ",\"oldValue\":" + oldValue + ",\"newValue\":" + newValue + "}";
        }
    }

    public record MemoryChange(int address, int oldValue, int newValue) {
        public String toJson() {
            return "{\"address\":" + address + ",\"oldValue\":" + oldValue + ",\"newValue\":" + newValue + "}";
        }
    }

    private static final String[] FLAG_NAMES = { "CF", "PF", "AF", "ZF", "SF", "TF", "IF", "DF", "OF" };
    private static final int[] FLAG_BITS = {
        FLAGS.CF_BIT, FLAGS.PF_BIT, FLAGS.AF_BIT, FLAGS.ZF_BIT, FLAGS.SF_BIT,
        FLAGS.TF_BIT, FLAGS.IF_BIT, FLAGS.DF_BIT, FLAGS.OF_BIT
    };

    /** Registers + flags only (no memory range implied). */
    public static StateDiff of(ExecutionSnapshot before, ExecutionSnapshot after) {
        return of(before, after, Map.of());
    }

    /** Registers + flags, plus memory changes derived from a journal range (see {@link MemoryJournal#netChanges}). */
    public static StateDiff of(ExecutionSnapshot before, ExecutionSnapshot after, Map<Integer, int[]> memoryDelta) {
        List<RegisterChange> registers = new ArrayList<>();
        Map<String, Integer> beforeRegs = before.registers();
        Map<String, Integer> afterRegs = after.registers();
        for (String name : afterRegs.keySet()) {
            int newValue = afterRegs.get(name);
            int oldValue = beforeRegs.getOrDefault(name, newValue);
            if (oldValue != newValue) registers.add(new RegisterChange(name, oldValue, newValue));
        }

        List<FlagChange> flags = new ArrayList<>();
        for (int i = 0; i < FLAG_NAMES.length; i++) {
            boolean oldValue = ((before.flags() >> FLAG_BITS[i]) & 1) == 1;
            boolean newValue = ((after.flags() >> FLAG_BITS[i]) & 1) == 1;
            if (oldValue != newValue) flags.add(new FlagChange(FLAG_NAMES[i], oldValue, newValue));
        }

        List<MemoryChange> memory = new ArrayList<>();
        Map<Integer, int[]> sorted = new LinkedHashMap<>(memoryDelta);
        sorted.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                int[] range = e.getValue();
                if (range[0] != range[1]) memory.add(new MemoryChange(e.getKey(), range[0], range[1]));
            });

        return new StateDiff(List.copyOf(registers), List.copyOf(flags), List.copyOf(memory));
    }

    public boolean isEmpty() { return registerChanges.isEmpty() && flagChanges.isEmpty() && memoryChanges.isEmpty(); }

    /** Human-readable form for the CLI, e.g. "AX: 0001 -> 0002". */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (RegisterChange r : registerChanges) {
            lines.add(r.register() + ": " + hex4(r.oldValue()) + " -> " + hex4(r.newValue()));
        }
        for (FlagChange f : flagChanges) {
            lines.add(f.flag() + ": " + (f.oldValue() ? 1 : 0) + " -> " + (f.newValue() ? 1 : 0));
        }
        for (MemoryChange m : memoryChanges) {
            lines.add(String.format("[%05X]: %02X -> %02X", m.address(), m.oldValue() & 0xFF, m.newValue() & 0xFF));
        }
        return lines;
    }

    private static String hex4(int v) { return String.format("%04X", v & 0xFFFF); }

    public String toJson() {
        return "{\"registerChanges\":" + Json.array(registerChanges, RegisterChange::toJson)
            + ",\"flagChanges\":" + Json.array(flagChanges, FlagChange::toJson)
            + ",\"memoryChanges\":" + Json.array(memoryChanges, MemoryChange::toJson) + "}";
    }
}
