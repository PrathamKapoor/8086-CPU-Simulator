package debugger;

import java.util.List;
import java.util.function.Function;

/** Minimal hand-rolled JSON helpers, matching the escaping convention already used by simulator.MainSimulator. */
final class Json {
    private Json() { }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String str(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    static <T> String array(List<T> items, Function<T, String> toJson) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(toJson.apply(items.get(i)));
        }
        return sb.append(']').toString();
    }
}
