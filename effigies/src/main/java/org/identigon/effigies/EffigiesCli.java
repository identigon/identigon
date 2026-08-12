package org.identigon.effigies;

import java.io.PrintStream;

/**
 * Command-line entry point for <b>Effigies</b> — a thin authoring and orchestration front-end above
 * <a href="https://github.com/identigon/identigon/tree/main/incognito">incognito</a>. Effigies discovers a source
 * schema, helps author (and, later, infer) the declarative anonymisation policy, and drives the engine
 * to produce the anonymised clone. The engine stays deterministic and judgment-free; all inference and
 * scaffolding lives here (see {@code docs/adr/0001-authoring-above-the-engine.md}).
 *
 * <p>The {@code discover}, {@code scaffold}, and {@code run} subcommands are implemented; see
 * {@code PLAN.md} for the roadmap.
 */
public final class EffigiesCli {

    private EffigiesCli() {}

    /** Exit code returned for an unknown subcommand or bad usage. */
    static final int EXIT_USAGE = 2;

    /**
     * Runs the CLI.
     *
     * @param args the command-line arguments; the first is the subcommand
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * The testable core of {@link #main}: dispatches on the subcommand and returns the process exit
     * code instead of calling {@link System#exit}.
     *
     * @param args the command-line arguments
     * @param out  the stream for normal output
     * @param err  the stream for usage/error output
     * @return the process exit code
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        String command = args.length == 0 ? "help" : args[0];
        return switch (command) {
            case "help", "-h", "--help" -> {
                printUsage(out);
                yield 0;
            }
            case "version", "--version" -> {
                out.println("Identigon " + version() + " (engine: incognito on classpath)");
                yield 0;
            }
            case "discover" -> {
                yield DiscoverCommand.execute(args, out, err);
            }
            case "scaffold" -> {
                yield ScaffoldCommand.execute(args, out, err);
            }
            case "run" -> {
                yield RunCommand.execute(args, out, err);
            }
            default -> {
                err.println("Unknown command: '" + command + "'");
                printUsage(err);
                yield EXIT_USAGE;
            }
        };
    }

    private static void printUsage(PrintStream w) {
        w.println("""
            Identigon — author and run an incognito anonymisation from a source schema.

            Usage: java -jar identigon.jar <command> [options]

            Commands:
              discover    Inspect a source database and describe its schema (metadata only, no data).
              scaffold    Emit a starter policy.yaml (fail-closed: every column left to be classified).
              run         Execute incognito against a finished policy.yaml to produce the clone.
              version     Print the version.
              help        Show this help.""");
    }

    /** The build version from the jar manifest, or {@code "dev"} when run from the classpath. */
    private static String version() {
        String v = EffigiesCli.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
