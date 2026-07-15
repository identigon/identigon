package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Exact expected outputs of every M1 and M2 built-in transformation for the reference salt (see
 * {@code docs/tasks/M1.md}, {@code docs/tasks/M2.md}). Generated once, eyeballed for
 * plausibility (each output matches its declared pattern shape, and M2's dictionary-drawn
 * outputs are real dictionary entries), then frozen: a future change to the Appendix A
 * algorithms, a dictionary file, or this class's logic that alters any of these values is a
 * breaking change and MUST stop and be flagged, not silently "fixed" here.
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

  @Test
  void firstNameGoldenOutputs() {
    assertEquals("Alexander", alterego().firstName().apply("Alice"));
    assertEquals("Leo", alterego().firstName().apply("Bob"));
  }

  @Test
  void lastNameGoldenOutputs() {
    assertEquals("Thomson", alterego().lastName().apply("Smith"));
    assertEquals("Anderson", alterego().lastName().apply("Jones"));
  }

  @Test
  void fullNameGoldenOutput() {
    assertEquals("Alexander Thomson", alterego().fullName().apply("Alice Smith"));
  }

  @Test
  void cityGoldenOutput() {
    assertEquals("Kingston upon Hull", alterego().city().apply("original"));
  }

  @Test
  void streetAddressGoldenOutput() {
    assertEquals("5 Albert Close", alterego().streetAddress().apply("original"));
  }

  @Test
  void postcodeGoldenOutput() {
    assertEquals("LH23 9QV", alterego().postcode().apply("original"));
  }

  @Test
  void organisationNameGoldenOutputs() {
    assertEquals("Edinburgh Media Care Ltd", alterego().organisationName().apply("Acme Trading Ltd"));
    assertEquals("Medical Training Foods", alterego().organisationName().apply("Foo Bar"));
  }
}
