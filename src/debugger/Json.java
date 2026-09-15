package debugger;

import java.util.List;
import java.util.function.Function;

/**
 * Minimal hand-rolled JSON helpers, matching the escaping convention already
 * used by simulator.MainSimulator. Public so research.Json can delegate to
 * it instead of duplicating the same three lines.
 */
public final class Json {
    private Json() { }

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String str(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    public static <T> String array(List<T> items, Function<T, String> toJson) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(toJson.apply(items.get(i)));
        }
        return sb.append(']').toString();
    }
}
