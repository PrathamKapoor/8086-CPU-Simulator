package research;

import java.util.Locale;

/**
 * What an experiment executes: either 8086 source assembly (parsed the same
 * way {@code CPU.loadProgram} always has) or raw machine-code bytes (loaded
 * via {@code CPU.loadMachineCode}, unchanged from Phase 4/5). A workload is
 * pure data — it does not know how to run itself; {@link ExperimentRunner}
 * does, so results never depend on anything beyond this record's fields.
 */
public record Workload(String name, String description, Kind kind, String sourceAssembly, byte[] machineCodeBytes) {
    public enum Kind { SOURCE, MACHINE_CODE }

    public Workload {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("workload name must not be blank");
        if (kind == Kind.SOURCE && (sourceAssembly == null || sourceAssembly.isBlank())) {
            throw new IllegalArgumentException("SOURCE workload requires sourceAssembly");
        }
        if (kind == Kind.MACHINE_CODE && (machineCodeBytes == null || machineCodeBytes.length == 0)) {
            throw new IllegalArgumentException("MACHINE_CODE workload requires machineCodeBytes");
        }
        machineCodeBytes = machineCodeBytes == null ? new byte[0] : machineCodeBytes.clone();
    }

    @Override
    public byte[] machineCodeBytes() { return machineCodeBytes.clone(); }

    public static Workload fromSource(String name, String description, String assembly) {
        return new Workload(name, description, Kind.SOURCE, assembly, null);
    }

    public static Workload fromMachineCode(String name, String description, byte[] bytes) {
        return new Workload(name, description, Kind.MACHINE_CODE, null, bytes);
    }

    /** Deterministic content fingerprint used by {@link ResultHasher} — never a Java object identity. */
    String contentFingerprint() {
        return kind == Kind.SOURCE
            ? "SRC:" + sourceAssembly
            : "MC:" + hex(machineCodeBytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02X", b));
        return sb.toString();
    }
}
