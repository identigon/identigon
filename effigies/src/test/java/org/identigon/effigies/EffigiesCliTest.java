package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Smoke coverage for the CLI dispatch skeleton. */
class EffigiesCliTest {

    private record Result(int code, String out, String err) {}

    private static Result invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = EffigiesCli.run(args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void noArgsPrintsHelpAndSucceeds() {
        Result r = invoke();
        assertEquals(0, r.code());
        assertTrue(r.out().contains("Usage: java -jar"), "prints usage");
    }

    @Test
    void versionSucceeds() {
        Result r = invoke("version");
        assertEquals(0, r.code());
        assertTrue(r.out().startsWith("Effigies "), "prints the version line");
    }

    @Test
    void plannedCommandsReportNotImplemented() {
        for (String cmd : new String[] {"discover", "scaffold", "run"}) {
            Result r = invoke(cmd);
            assertEquals(EffigiesCli.EXIT_NOT_IMPLEMENTED, r.code(), cmd + " exit code");
            assertTrue(r.err().contains("not yet implemented"), cmd + " message");
        }
    }

    @Test
    void unknownCommandFailsWithUsage() {
        Result r = invoke("frobnicate");
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("Unknown command"), "names the bad command");
    }
}
