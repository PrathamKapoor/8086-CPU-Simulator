package simulator.verify;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ArchVector — one golden architectural test vector.
 *
 * A vector pins down 8086 behavior: given initial registers/flags/memory and
 * a short program, the machine must reach the expected state. Expected maps
 * are partial: only listed entries are asserted.
 *
 * Expected values in JSON may be decimal numbers or "0x.." hex strings.
 */
public final class ArchVector {

    public final String name;
    public final Map<String, Integer> initialRegs;
    public final Map<String, Integer> initialFlags;
    public final Map<Integer, Integer> initialMem;
    public final List<String> program;
    public final Map<String, Integer> expectedRegs;
    public final Map<String, Integer> expectedFlags;
    public final Map<Integer, Integer> expectedMem;
    public final boolean expectedHalted;

    public ArchVector(String name,
                      Map<String, Integer> initialRegs,
                      Map<String, Integer> initialFlags,
                      Map<Integer, Integer> initialMem,
                      List<String> program,
                      Map<String, Integer> expectedRegs,
                      Map<String, Integer> expectedFlags,
                      Map<Integer, Integer> expectedMem,
                      boolean expectedHalted) {
        this.name = name;
        this.initialRegs = unmodifiable(initialRegs);
        this.initialFlags = unmodifiable(initialFlags);
        this.initialMem = unmodifiable(initialMem);
        this.program = program == null ? List.of() : List.copyOf(program);
        this.expectedRegs = unmodifiable(expectedRegs);
        this.expectedFlags = unmodifiable(expectedFlags);
        this.expectedMem = unmodifiable(expectedMem);
        this.expectedHalted = expectedHalted;
    }

    private static <K, V> Map<K, V> unmodifiable(Map<K, V> m) {
        return Collections.unmodifiableMap(m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m));
    }
}
