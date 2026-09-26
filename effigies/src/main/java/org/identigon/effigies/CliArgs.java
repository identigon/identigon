package org.identigon.effigies;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Small argument-parsing helpers shared across the {@code discover}/{@code scaffold}/{@code
 * validate}/{@code run} subcommands.
 */
final class CliArgs {

  private CliArgs() {}

  /**
   * Whether {@code args} carries {@code --help} or {@code -h} anywhere, not just in the first
   * position - {@code discover --source-url x --help} should show help just as readily as {@code
   * discover --help}, and a subcommand's own args never include its own name (unlike {@link
   * EffigiesCli#run}, which only checks {@code args[0]} for the top-level command).
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

  /**
   * Parses a subcommand's arguments strictly: every argument must be one of {@code valueOptions}
   * followed by its value, or one of {@code switches}. An unrecognised option, a stray positional
   * argument, an option given twice, or a value option with no value is a usage error - a mistyped
   * {@code --polcy} must not silently fall back to the default policy file.
   *
   * @param args the subcommand's arguments, without the subcommand name
   * @param valueOptions options that take a value, e.g. {@code --source-url}
   * @param switches options that take no value, e.g. {@code --force}
   * @return the parsed options
   * @throws UsageException if the arguments do not match the options
   */
  static Parsed parse(String[] args, Set<String> valueOptions, Set<String> switches)
      throws UsageException {
    Map<String, String> values = new HashMap<>();
    Set<String> present = new HashSet<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (!valueOptions.contains(arg) && !switches.contains(arg)) {
        throw new UsageException(
            arg.startsWith("-")
                ? "Unknown option: '" + arg + "'"
                : "Unexpected argument: '" + arg + "'");
      }
      if (!present.add(arg)) {
        throw new UsageException("Option given more than once: '" + arg + "'");
      }
      if (valueOptions.contains(arg)) {
        if (i + 1 == args.length || args[i + 1].startsWith("--")) {
          throw new UsageException("Option '" + arg + "' requires a value");
        }
        values.put(arg, args[++i]);
      }
    }
    return new Parsed(values, present);
  }

  /**
   * The result of {@link #parse}.
   *
   * @param values each value option given, mapped to its value
   * @param present every option given, value options and switches alike
   */
  record Parsed(Map<String, String> values, Set<String> present) {

    /**
     * The value given for {@code option}, if it was given.
     *
     * @param option the value option
     * @return its value, or empty
     */
    Optional<String> value(String option) {
      return Optional.ofNullable(values.get(option));
    }

    /**
     * Whether the switch {@code option} was given.
     *
     * @param option the switch
     * @return true if present
     */
    boolean has(String option) {
      return present.contains(option);
    }
  }

  /** The arguments did not match what the subcommand accepts. */
  static final class UsageException extends Exception {
    private static final long serialVersionUID = 1L;

    UsageException(String message) {
      super(message);
    }
  }
}
