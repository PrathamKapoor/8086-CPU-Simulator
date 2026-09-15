package gui;

import machinecode.DecodedInstruction;
import machinecode.Intel8086Decoder;

import java.util.HexFormat;

/** Immutable, UI-neutral projection of the real machine-code decoder state. */
public record MachineCodeInspection(int offset, String bytes, int length, String instruction,
                                    String opcode, String prefixes, int displacement,
                                    int immediate) {
    public static MachineCodeInspection from(DecodedInstruction decoded) {
        if (decoded == null) throw new IllegalArgumentException("decoded instruction must not be null");
        byte[] raw = decoded.rawBytes();
        String opcode = raw.length == 0 ? "" : String.format("%02X", raw[decoded.prefixes().size()] & 0xFF);
        String prefixes = decoded.prefixes().stream().map(v -> String.format("%02X", v)).reduce((a,b) -> a + " " + b).orElse("");
        return new MachineCodeInspection(decoded.startOffset(), HexFormat.of().withUpperCase().formatHex(raw), decoded.length(),
            decoded.instruction().toString(), opcode, prefixes, decoded.instruction().getDisplacement(), decoded.instruction().getImmediate());
    }
}
