package research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON parser for exactly the shapes
 * {@link ExperimentArtifact} needs to round-trip (objects, arrays, strings,
 * numbers, booleans, null). Not a general-purpose library — matching this
 * codebase's existing convention (see simulator.verify.VectorJson) of a
 * small hand-rolled parser scoped to one schema rather than an external
 * dependency.
 */
final class MiniJson {
    private final String text;
    private int pos;

    private MiniJson(String text) { this.text = text; }

    static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.skipWs();
        Object value = p.parseValue();
        p.skipWs();
        return value;
    }

    private Object parseValue() {
        skipWs();
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> { pos += 4; yield Boolean.TRUE; }
            case 'f' -> { pos += 5; yield Boolean.FALSE; }
            case 'n' -> { pos += 4; yield null; }
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            pos++; // :
            Object value = parseValue();
            map.put(key, value);
            skipWs();
            if (peek() == ',') { pos++; continue; }
            if (peek() == '}') { pos++; break; }
            throw new IllegalArgumentException("malformed JSON object at " + pos);
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(parseValue());
            skipWs();
            if (peek() == ',') { pos++; continue; }
            if (peek() == ']') { pos++; break; }
            throw new IllegalArgumentException("malformed JSON array at " + pos);
        }
        return list;
    }

    private String parseString() {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (text.charAt(pos) != '"') {
            char c = text.charAt(pos);
            if (c == '\\') {
                pos++;
                char esc = text.charAt(pos);
                sb.append(switch (esc) {
                    case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case '"' -> '"'; case '\\' -> '\\'; case '/' -> '/';
                    default -> esc;
                });
            } else {
                sb.append(c);
            }
            pos++;
        }
        pos++; // closing quote
        return sb.toString();
    }

    private Double parseNumber() {
        int start = pos;
        while (pos < text.length() && "-+.eE0123456789".indexOf(text.charAt(pos)) >= 0) pos++;
        return Double.parseDouble(text.substring(start, pos));
    }

    private void skipWs() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
    private char peek() { return text.charAt(pos); }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked")
    static List<Object> asArray(Object o) { return (List<Object>) o; }
    static String asString(Object o) { return (String) o; }
    static int asInt(Object o) { return (int) Math.round((Double) o); }
    static boolean asBoolean(Object o) { return (Boolean) o; }
}
