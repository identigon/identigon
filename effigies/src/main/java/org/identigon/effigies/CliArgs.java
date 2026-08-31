package org.identigon.effigies;

/**
 * Small argument-parsing helpers shared across the {@code discover}/{@code scaffold}/{@code run}
 * subcommands.
 */
final class CliArgs {

    private CliArgs() {}

    /**
     * Whether {@code args} carries {@code --help} or {@code -h} anywhere, not just in the first
     * position - {@code discover --source-url x --help} should show help just as readily as
     * {@code discover --help}, and a subcommand's own args never include its own name (unlike
     * {@link EffigiesCli#run}, which only checks {@code args[0]} for the top-level command).
     *
     * @param args the subcommand's arguments
     * @return true if a help flag is present anywhere in {@code args}
     */
    static boolean hasHelpFlag(String[] args) {
        for (String a : args) {
            if ("--help".equals(a) || "-h".equals(a)) {
                return true;
            }
        }
        return false;
    }
}
