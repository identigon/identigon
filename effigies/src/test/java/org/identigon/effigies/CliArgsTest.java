package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CliArgsTest {

  private static final Set<String> VALUES = Set.of("--policy", "--source-url");
  private static final Set<String> SWITCHES = Set.of("--force");

  private static CliArgs.Parsed parse(String... args) throws CliArgs.UsageException {
    return CliArgs.parse(args, VALUES, SWITCHES);
  }

  private static String usageError(String... args) {
    return assertThrows(CliArgs.UsageException.class, () -> parse(args)).getMessage();
  }

  @Test
  void parsesValueOptionsAndSwitches() throws Exception {
    CliArgs.Parsed parsed = parse("--policy", "p.yaml", "--force");
    assertEquals(Optional.of("p.yaml"), parsed.value("--policy"));
    assertEquals(Optional.empty(), parsed.value("--source-url"));
    assertTrue(parsed.has("--force"));
  }

  @Test
  void absentSwitchIsNotPresent() throws Exception {
    assertFalse(parse().has("--force"));
  }

  @Test
  void unknownOptionIsRejected() {
    assertEquals("Unknown option: '--polcy'", usageError("--polcy", "p.yaml"));
  }

  @Test
  void strayPositionalArgumentIsRejected() {
    assertEquals("Unexpected argument: 'p.yaml'", usageError("p.yaml"));
  }

  @Test
  void valueOptionAtTheEndWithNoValueIsRejected() {
    assertEquals("Option '--policy' requires a value", usageError("--force", "--policy"));
  }

  @Test
  void valueOptionFollowedByAnotherOptionIsRejected() {
    assertEquals("Option '--policy' requires a value", usageError("--policy", "--force"));
  }

  @Test
  void repeatedOptionIsRejected() {
    assertEquals(
        "Option given more than once: '--policy'",
        usageError("--policy", "a.yaml", "--policy", "b.yaml"));
  }
}
