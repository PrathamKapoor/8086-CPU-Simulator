package machinecode;

/** Raised when an otherwise semantic instruction has no implemented legal 8086 encoding. */
public final class EncodeException extends RuntimeException {
    public EncodeException(String message) {
        super(message);
    }
}
