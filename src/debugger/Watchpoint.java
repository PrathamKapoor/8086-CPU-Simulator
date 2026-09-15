package debugger;

/** A memory, register, or flag watch, hooked into the actual mutation points — never polled. */
public final class Watchpoint {
    public enum Kind { MEMORY, REGISTER, FLAG }
    public enum Access { READ, WRITE, READ_WRITE }

    private final int id;
    private final Kind kind;
    private final int addressLow;   // MEMORY only
    private final int addressHigh;  // MEMORY only (inclusive; == addressLow for a single cell)
    private final String name;      // REGISTER / FLAG name
    private final Access access;
    private boolean enabled = true;
    private int hitCount = 0;

    static Watchpoint memory(int id, int addressLow, int addressHigh, Access access) {
        return new Watchpoint(id, Kind.MEMORY, addressLow, addressHigh, null, access);
    }

    static Watchpoint register(int id, String name) {
        return new Watchpoint(id, Kind.REGISTER, -1, -1, name.toUpperCase(java.util.Locale.ROOT), Access.WRITE);
    }

    static Watchpoint flag(int id, String name) {
        return new Watchpoint(id, Kind.FLAG, -1, -1, name.toUpperCase(java.util.Locale.ROOT), Access.WRITE);
    }

    private Watchpoint(int id, Kind kind, int addressLow, int addressHigh, String name, Access access) {
        this.id = id;
        this.kind = kind;
        this.addressLow = addressLow;
        this.addressHigh = addressHigh;
        this.name = name;
        this.access = access;
    }

    public int id() { return id; }
    public Kind kind() { return kind; }
    public int addressLow() { return addressLow; }
    public int addressHigh() { return addressHigh; }
    public String name() { return name; }
    public Access access() { return access; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int hitCount() { return hitCount; }
    void recordHit() { hitCount++; }

    boolean coversAddress(int address) { return kind == Kind.MEMORY && address >= addressLow && address <= addressHigh; }

    public String describe() {
        return switch (kind) {
            case MEMORY -> addressLow == addressHigh
                ? String.format("memory[0x%05X]", addressLow)
                : String.format("memory[0x%05X..0x%05X]", addressLow, addressHigh);
            case REGISTER -> "register " + name;
            case FLAG -> "flag " + name;
        };
    }

    public String toJson() {
        return "{\"id\":" + id
            + ",\"kind\":\"" + kind + "\""
            + ",\"addressLow\":" + addressLow
            + ",\"addressHigh\":" + addressHigh
            + ",\"name\":" + Json.str(name)
            + ",\"access\":\"" + access + "\""
            + ",\"enabled\":" + enabled
            + ",\"hitCount\":" + hitCount + "}";
    }
}
