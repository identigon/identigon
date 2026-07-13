package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Exact expected outputs of every M1 built-in transformation for the reference salt (see
 * {@code docs/tasks/M1.md}). Generated once, eyeballed for plausibility (each output matches
 * its declared pattern shape), then frozen: a future change to the Appendix A algorithms or
 * this class's logic that alters any of these values is a breaking change (CLAUDE.md invariant
 * 6) and must stop and be flagged, not silently "fixed" here.
 */
class GoldenOutputsTest {

  private static final byte[] REFERENCE_SALT = VectorGenerator.REFERENCE_SALT;

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(REFERENCE_SALT).build();
  }

  @Test
  void patternGoldenOutputs() {
    Transformation<String> t = alterego().pattern("DLDDDL");
    assertEquals("2Z127S", t.apply("Alice"));
    assertEquals("8L969P", t.apply("Bob"));
  }

  @Test
  void patternWithLiteralGoldenOutput() {
    assertEquals("ZA73 4UE", alterego().pattern("LLDD DLL").apply("SW1A 1AA"));
  }

  @Test
  void patternAllLetterTokenGoldenOutput() {
    assertEquals("GjXVJp", alterego().pattern("AAAAAA").apply("x"));
  }

  @Test
  void constantGoldenOutputs() {
    assertEquals("REDACTED", alterego().constant("REDACTED").apply("anything"));
    assertEquals(LocalDate.of(1900, 1, 1), alterego().constant(LocalDate.of(1900, 1, 1)).apply(LocalDate.now()));
  }

  @Test
  void maskGoldenOutputs() {
    assertEquals("************1234", alterego().mask('*', 4).apply("credit-card-1234"));
    assertEquals("XXXXXX", alterego().mask('X', 0).apply("secret"));
  }
}
