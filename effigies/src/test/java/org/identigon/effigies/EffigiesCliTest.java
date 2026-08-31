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
        assertTrue(r.out().startsWith("Identigon "), "prints the version line");
    }

    @Test
    void commandsRequireUsage() {
        for (String cmd : new String[] {"discover", "scaffold", "validate", "run"}) {
            Result r = invoke(cmd);
            assertEquals(EffigiesCli.EXIT_USAGE, r.code(), cmd + " exit code");
            assertTrue(r.err().contains("Usage: " + cmd), cmd + " usage message");
        }
    }

    @Test
    void perSubcommandHelpSucceedsWithoutRequiringItsOtherArgs() {
        // Previously `discover --help` fell through to DiscoverCommand's own arg parsing, which
        // only ever printed its usage line as a side effect of missing --source-url/--source-user
        // (exit EXIT_USAGE, not a deliberate help request). Each subcommand must recognise --help
        // itself now, and succeed.
        for (String cmd : new String[] {"discover", "scaffold", "validate", "run"}) {
            Result r = invoke(cmd, "--help");
            assertEquals(0, r.code(), cmd + " --help exit code");
            assertTrue(r.out().contains("Usage: " + cmd), cmd + " --help usage message");
        }
    }

    @Test
    void unknownCommandFailsWithUsage() {
        Result r = invoke("frobnicate");
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("Unknown command"), "names the bad command");
    }
}
