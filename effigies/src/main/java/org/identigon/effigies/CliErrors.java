package org.identigon.effigies;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Formats a full {@code Throwable} cause chain for CLI error output. A bare {@code e.toString()} or
 * {@code e.getMessage()} - the pattern every subcommand's catch block used to reach for - only ever
 * shows the outermost exception; when that is a generic wrapper around a genuinely unexpected
 * failure (e.g. {@code DefaultIncognitoPipeline}'s {@code IncognitoException("Pipeline execution
 * failed", e)}), the actual diagnostic sits in the cause and never reached the user (v3.1.0
 * tutorial-feedback finding).
 */
final class CliErrors {

    private CliErrors() {}

    /**
     * Renders {@code t} followed by every {@code getCause()} beneath it, one per line, each
     * prefixed {@code "Caused by: "} - short {@code toString()} entries (class + message), not full
     * stack traces, matching this CLI's terse error style.
     *
     * @param t the top-level exception
     * @return {@code t}'s own {@code toString()}, plus one {@code "Caused by: "} line per cause
     */
    static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        appendCauses(sb, t);
        return sb.toString();
    }

    /**
     * The cause-chain lines alone, with no leading line for {@code t} itself - for a caller that
     * already prints {@code t}'s own message on its own terms (e.g. a fail-closed
     * {@code ConfigException}, whose message alone is already the clean diagnostic) and only wants
     * to reveal a hidden cause, if {@code t} has one.
     *
     * @param t the top-level exception
     * @return one {@code "Caused by: "} line per cause beneath {@code t}, each preceded by a line
     *     separator, or {@code ""} if {@code t} has no cause
     */
    static String causesOnly(Throwable t) {
        StringBuilder sb = new StringBuilder();
        appendCauses(sb, t);
        return sb.toString();
    }

    // Guards against a cycle (a cause that is its own ancestor) rather than looping forever, though
    // the JDK itself already guards against the direct self-cause case.
    private static void appendCauses(StringBuilder sb, Throwable t) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(t);
        for (Throwable cause = t.getCause(); cause != null && seen.add(cause); cause = cause.getCause()) {
            sb.append(System.lineSeparator()).append("Caused by: ").append(cause);
        }
    }
}
