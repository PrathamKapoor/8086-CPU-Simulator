package research;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Best-effort simulator version/commit identification for reproducibility
 * metadata (Phase 6 Step 2/9). Never fails a run: falls back to "unknown"
 * rather than shelling out to git (which would make experiment execution
 * depend on an external process and the working directory being a repo).
 */
public final class SimulatorMetadata {
    private SimulatorMetadata() { }

    /** {@code SIMULATOR_COMMIT} env var, if set (e.g. wired by CI to the exact commit SHA); else the VERSION file; else "unknown". */
    public static String commitOrVersion() {
        String fromEnv = System.getenv("SIMULATOR_COMMIT");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        try {
            String version = Files.readString(Path.of("VERSION")).trim();
            if (!version.isBlank()) return "v" + version;
        } catch (IOException ignored) {
            // VERSION not found relative to the working directory -- fall through.
        }
        return "unknown";
    }
}
