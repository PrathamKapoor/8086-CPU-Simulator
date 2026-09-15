package debugger;

import cpu.CPU;
import cpu.registers.FLAGS;
import cpu.registers.Register;

import java.util.Locale;
import java.util.Map;

/**
 * A small, deterministic, side-effect-free boolean expression over the
 * current architectural state: {@code <TOKEN> <OP> <VALUE>}, e.g.
 * {@code "AX == 0"}, {@code "CX != 0"}, {@code "ZF == 1"}, {@code "IP == 5"}.
 * TOKEN is a general/segment/IP register name or a flag name; OP is one of
 * {@code == != < <= > >=}; VALUE is a decimal or {@code 0x}/{@code H}-suffixed
 * hexadecimal integer, or {@code 0}/{@code 1} for a flag. This is
 * intentionally not a general expression language — one comparison only.
 */
public final class BreakpointCondition {
    private static final Map<String, Integer> FLAG_BITS = Map.of(
        "CF", FLAGS.CF_BIT, "PF", FLAGS.PF_BIT, "AF", FLAGS.AF_BIT, "ZF", FLAGS.ZF_BIT,
        "SF", FLAGS.SF_BIT, "TF", FLAGS.TF_BIT, "IF", FLAGS.IF_BIT, "DF", FLAGS.DF_BIT, "OF", FLAGS.OF_BIT
    );

    private final String rawText;
    private final String token;
    private final String operator;
    private final int value;
    private final boolean isFlag;

    public BreakpointCondition(String expression) {
        this.rawText = expression.trim();
        String[] parts = splitExpression(this.rawText);
        this.token = parts[0].toUpperCase(Locale.ROOT);
        this.operator = parts[1];
        this.value = parseValue(parts[2]);
        this.isFlag = FLAG_BITS.containsKey(token);
        if (!isFlag && !isKnownRegister(token)) {
            throw new IllegalArgumentException("Unknown breakpoint condition token: " + token);
        }
    }

    private static String[] splitExpression(String expr) {
        for (String op : new String[] {"==", "!=", "<=", ">=", "<", ">"}) {
            int idx = expr.indexOf(op);
            if (idx > 0) {
                return new String[] { expr.substring(0, idx).trim(), op, expr.substring(idx + op.length()).trim() };
            }
        }
        throw new IllegalArgumentException("Breakpoint condition must contain a comparison operator (==, !=, <, <=, >, >=): " + expr);
    }

    private static int parseValue(String text) {
        String t = text.trim().toUpperCase(Locale.ROOT);
        try {
            if (t.startsWith("0X")) return Integer.parseInt(t.substring(2), 16);
            if (t.endsWith("H")) return Integer.parseInt(t.substring(0, t.length() - 1), 16);
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid breakpoint condition value: " + text);
        }
    }

    private static boolean isKnownRegister(String name) {
        return switch (name) {
            case "AX", "BX", "CX", "DX", "SP", "BP", "SI", "DI",
                 "CS", "DS", "SS", "ES", "IP" -> true;
            default -> false;
        };
    }

    /** Pure, side-effect-free evaluation against the CPU's current state. */
    public boolean evaluate(CPU cpu) {
        int actual = isFlag
            ? (((cpu.getFlags().output() >> FLAG_BITS.get(token)) & 1))
            : registerValue(cpu, token);
        return switch (operator) {
            case "==" -> actual == value;
            case "!=" -> actual != value;
            case "<"  -> actual < value;
            case "<=" -> actual <= value;
            case ">"  -> actual > value;
            case ">=" -> actual >= value;
            default -> throw new IllegalStateException("unreachable operator: " + operator);
        };
    }

    private static int registerValue(CPU cpu, String name) {
        Register r = cpu.getRegister(name);
        if (r == null) throw new IllegalStateException("Unknown register in condition: " + name);
        return r.output();
    }

    public String rawText() { return rawText; }

    @Override
    public String toString() { return rawText; }
}
