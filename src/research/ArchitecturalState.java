package research;

import cpu.CPU;
import debugger.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** The final architectural state of a run — registers, flags, halted — independent of any GUI object. */
public record ArchitecturalState(Map<String, Integer> registers, int flags, boolean halted) {
    private static final String[] TRACKED = {
        "AX", "BX", "CX", "DX", "SP", "BP", "SI", "DI", "CS", "DS", "SS", "ES", "IP"
    };

    public ArchitecturalState { registers = Map.copyOf(registers); }

    public static ArchitecturalState capture(CPU cpu) {
        Map<String, Integer> regs = new LinkedHashMap<>();
        for (String name : TRACKED) regs.put(name, cpu.getRegister(name).output());
        return new ArchitecturalState(regs, cpu.getFlags().output(), cpu.isHalted());
    }

    public String toJson() {
        StringBuilder regs = new StringBuilder("{");
        boolean first = true;
        for (var e : registers.entrySet()) {
            if (!first) regs.append(',');
            first = false;
            regs.append(Json.str(e.getKey())).append(':').append(e.getValue());
        }
        regs.append('}');
        return "{\"registers\":" + regs + ",\"flags\":" + flags + ",\"halted\":" + halted + "}";
    }

    /** Deterministic canonical text used by {@link ResultHasher} — fixed field order, no map iteration hazard. */
    String canonicalText() {
        StringBuilder sb = new StringBuilder();
        for (String name : TRACKED) sb.append(name).append('=').append(registers.getOrDefault(name, 0)).append(';');
        sb.append("FLAGS=").append(flags).append(";HALTED=").append(halted);
        return sb.toString();
    }
}
