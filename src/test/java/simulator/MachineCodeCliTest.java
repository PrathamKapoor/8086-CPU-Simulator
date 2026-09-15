package simulator;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
