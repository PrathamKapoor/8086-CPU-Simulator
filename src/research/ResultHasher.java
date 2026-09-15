package research;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic result fingerprints (Phase 6 Step 10). The hash input is a
 * fixed-order canonical string built only from: the experiment id, its
 * workload's content fingerprint, its configuration's description, the
 * final architectural state, and the metric set — explicitly never a
 * timestamp, a random/object identity, or anything touched by unordered map
 * iteration (every canonical-text builder here uses a fixed field order or
 * an already-ordered {@code MetricSet}/registers list).
 */
public final class ResultHasher {
    private ResultHasher() { }

    public static String hash(Experiment experiment, ArchitecturalState finalState, MetricSet metrics) {
        String canonical = "id=" + experiment.id()
            + ";workload=" + experiment.workload().contentFingerprint()
            + ";config=" + experiment.configuration().describe()
            + ";state=" + finalState.canonicalText()
            + ";metrics=" + metrics.canonicalText();
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to be available on every JVM", e);
        }
    }
}
