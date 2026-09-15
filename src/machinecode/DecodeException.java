package machinecode;

/** Raised when a byte stream cannot provide one complete supported instruction. */
public final class DecodeException extends RuntimeException {
    public DecodeException(String message) {
        super(message);
    }
}
