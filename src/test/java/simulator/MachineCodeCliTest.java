package simulator;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineCodeCliTest {
    private record Result(int code, String output) { }

    @Test
    void encodeDecodeAndDisassembleProduceDeterministicMachineReadableJson() throws Exception {
        Result encoded = run("--encode", "MOV AX, BX", "--json");
        Result decoded = run("--decode", "89 D8", "--json");
        Result disassembled = run("--disassemble", "8B 40 FE", "--json");

        assertEquals(0, encoded.code());
        assertEquals("{\"assembly\":\"MOV AX, BX\",\"bytes\":\"89 D8\",\"length\":2}" + System.lineSeparator(), encoded.output());
        assertEquals("{\"assembly\":\"MOV AX, BX\",\"bytes\":\"89 D8\",\"length\":2}" + System.lineSeparator(), decoded.output());
        assertTrue(disassembled.output().contains("\"assembly\":\"MOV AX, [BX+SI-2]\""));
    }

    @Test
    void rejectsInvalidHexAndUnknownMachineCodeOptions() throws Exception {
        assertNotEquals(0, run("--decode", "8B GG").code());
        assertNotEquals(0, run("--unknown-machine-option").code());
    }

    @Test
    void assemblesSourceFileToDeterministicJson() throws Exception {
        Path source = Files.createTempFile("cpu-phase4-", ".asm");
        try {
            Files.writeString(source, "MOV AX, 1\nHLT\n", StandardCharsets.UTF_8);
            Result result = run("--assemble", source.toString(), "--json");
            assertEquals(0, result.code());
            assertEquals("{\"bytes\":\"B8 01 00 F4\",\"length\":4}" + System.lineSeparator(), result.output());
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void machineCodeFileExecutesThroughTheMachineMode() throws Exception {
        Path binary = Files.createTempFile("cpu-phase4-", ".bin");
        try {
            Files.write(binary, new byte[] { (byte) 0xB8, 0x34, 0x12, (byte) 0xF4 });
            Result result = run("--machine-code", binary.toString(), "--json");
            assertEquals(0, result.code());
            assertTrue(result.output().contains("\"AX\":\"1234\""));
        } finally {
            Files.deleteIfExists(binary);
        }
    }

    private Result run(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add("-cp"); command.add(System.getProperty("java.class.path")); command.add("simulator.MainSimulator");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }
}
