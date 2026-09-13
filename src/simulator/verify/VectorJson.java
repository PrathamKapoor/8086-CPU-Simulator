package simulator.verify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VectorJson — minimal strict JSON reader for the golden-vector schema.
 *
 * Supports exactly what the vector files use: objects with string keys,
 * arrays, decimal integers, "0x.." hex strings, booleans. Anything else is a
 * parse error with position info. Dependency-free by design: the vector
 * format is fixed and tiny, and the shaded JAR stays lean.
 */
public final class VectorJson {

    private VectorJson() { }

    public static List<ArchVector> parseAll(String text, String sourceName) {
        Parser p = new Parser(text, sourceName);
        Object top = p.parseValue();
        p.skipWs();
        if (!p.eof()) throw p.error("trailing content after top-level value");
        List<ArchVector> out = new ArrayList<>();
        if (top instanceof List) {
            for (Object o : (List<?>) top) out.add(toVector(o, sourceName));
        } else {
            out.add(toVector(top, sourceName));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ArchVector toVector(Object o, String sourceName) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException(sourceName + ": vector must be a JSON object");
        }
        Map<String, Object> m = (Map<String, Object>) o;
        String name = str(m, "name", sourceName);
        Map<String, Object> initial = obj(m, "initial", true, sourceName);
        Map<String, Object> expected = obj(m, "expected", false, sourceName);
        List<String> program = strList(m, "program", sourceName);
        return new ArchVector(
            name,
            intStrMap(obj(initial, "registers", true, sourceName), sourceName),
            flagMap(obj(initial, "flags", true, sourceName), sourceName),
            intAddrMap(obj(initial, "memory", true, sourceName), sourceName),
            program,
            intStrMap(obj(expected, "registers", true, sourceName), sourceName),
            flagMap(obj(expected, "flags", true, sourceName), sourceName),
            intAddrMap(obj(expected, "memory", true, sourceName), sourceName),
            bool(expected, "halted", true, sourceName));
    }

    private static String str(Map<String, Object> m, String key, String src) {
        Object v = m.get(key);
        if (v instanceof String) return (String) v;
        throw new IllegalArgumentException(src + ": '" + key + "' must be a string");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Map<String, Object> m, String key,
                                           boolean optional, String src) {
        Object v = m.get(key);
        if (v == null) {
            if (optional) return new LinkedHashMap<>();
            throw new IllegalArgumentException(src + ": missing object '" + key + "'");
        }
        if (v instanceof Map) return (Map<String, Object>) v;
        throw new IllegalArgumentException(src + ": '" + key + "' must be an object");
    }

    private static Map<String, Object> obj(Map<String, Object> m, String key,
                                           boolean optional, String src, boolean unused) {
        return obj(m, key, optional, src);
    }

    private static List<String> strList(Map<String, Object> m, String key, String src) {
        Object v = m.get(key);
        if (!(v instanceof List)) {
            throw new IllegalArgumentException(src + ": '" + key + "' must be an array of strings");
        }
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) v) {
            if (!(o instanceof String)) {
                throw new IllegalArgumentException(src + ": '" + key + "' must contain only strings");
            }
            out.add((String) o);
        }
        return out;
    }

    private static boolean bool(Map<String, Object> m, String key, boolean dflt, String src) {
        Object v = m.get(key);
        if (v == null) return dflt;
        if (v instanceof Boolean) return (Boolean) v;
        throw new IllegalArgumentException(src + ": '" + key + "' must be true/false");
    }

    private static Map<String, Integer> intStrMap(Map<String, Object> m, String src) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            out.put(e.getKey().toUpperCase(), toInt(e.getValue(), src));
        }
        return out;
    }

    private static Map<String, Integer> flagMap(Map<String, Object> m, String src) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String k = e.getKey().toUpperCase();
            if (!VectorRunner.FLAG_SETTERS.contains(k)) {
                throw new IllegalArgumentException(src + ": unknown flag '" + e.getKey() + "'");
            }
            int v = toInt(e.getValue(), src);
            if (v != 0 && v != 1) {
                throw new IllegalArgumentException(src + ": flag '" + k + "' must be 0 or 1");
            }
            out.put(k, v);
        }
        return out;
    }

    private static Map<Integer, Integer> intAddrMap(Map<String, Object> m, String src) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            out.put(toInt(e.getKey(), src), toInt(e.getValue(), src));
        }
        return out;
    }

    private static int toInt(Object v, String src) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            try {
                if (s.startsWith("0x") || s.startsWith("0X")) {
                    return Integer.parseUnsignedInt(s.substring(2), 16);
                }
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(src + ": bad integer '" + s + "'");
            }
        }
        throw new IllegalArgumentException(src + ": expected integer, got " + v);
    }

    // ---- Lexer/parser ----

    private static final class Parser {
        private final String text;
        private final String src;
        private int pos;

        Parser(String text, String src) { this.text = text; this.src = src; }

        boolean eof() { return pos >= text.length(); }

        IllegalArgumentException error(String msg) {
            int line = 1;
            for (int i = 0; i < pos && i < text.length(); i++) {
                if (text.charAt(i) == '\n') line++;
            }
            return new IllegalArgumentException(src + " line " + line + ": " + msg);
        }

        void skipWs() {
            while (!eof()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object parseValue() {
            skipWs();
            if (eof()) throw error("unexpected end of input");
            char c = text.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw error("unexpected character '" + c + "'");
        }

        Map<String, Object> parseObject() {
            pos++; // {
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (!eof() && text.charAt(pos) == '}') { pos++; return m; }
            while (true) {
                skipWs();
                if (eof() || text.charAt(pos) != '"') throw error("expected string key");
                String key = parseString();
                skipWs();
                if (eof() || text.charAt(pos) != ':') throw error("expected ':'");
                pos++;
                m.put(key, parseValue());
                skipWs();
                if (eof()) throw error("unterminated object");
                char d = text.charAt(pos);
                if (d == ',') { pos++; continue; }
                if (d == '}') { pos++; return m; }
                throw error("expected ',' or '}'");
            }
        }

        List<Object> parseArray() {
            pos++; // [
            List<Object> l = new ArrayList<>();
            skipWs();
            if (!eof() && text.charAt(pos) == ']') { pos++; return l; }
            while (true) {
                l.add(parseValue());
                skipWs();
                if (eof()) throw error("unterminated array");
                char d = text.charAt(pos);
                if (d == ',') { pos++; continue; }
                if (d == ']') { pos++; return l; }
                throw error("expected ',' or ']'");
            }
        }

        String parseString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) throw error("unterminated string");
                char c = text.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (eof()) throw error("unterminated escape");
                    char e = text.charAt(pos++);
                    if (e == '"') sb.append('"');
                    else if (e == '\\') sb.append('\\');
                    else if (e == 'n') sb.append('\n');
                    else if (e == 't') sb.append('\t');
                    else throw error("unsupported escape '\\" + e + "'");
                } else {
                    sb.append(c);
                }
            }
        }

        Boolean parseBool() {
            if (text.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw error("expected true/false");
        }

        Long parseNumber() {
            int start = pos;
            if (!eof() && text.charAt(pos) == '-') pos++;
            while (!eof() && Character.isDigit(text.charAt(pos))) pos++;
            try {
                return Long.parseLong(text.substring(start, pos));
            } catch (NumberFormatException e) {
                throw error("bad number");
            }
        }
    }

    // Remove once: overload kept for binary-compat during refactor; unused.
    private static Map<String, Object> obj(Map<String, Object> m, String key, String src) {
        return obj(m, key, false, src);
    }
}
