package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The M2 milestone gate (docs/tasks/M2.md step 6): every M2 built-in resolves purely by the
 * locale's country (spec section 4, ADR 0006 "language never implies location"), so {@code
 * en-GB} and {@code cy-GB} — same country, different language — must produce byte-identical
 * output for the same input and salt. Covers every M2 built-in, not just {@code fullName()}.
 */
class LocaleEquivalenceTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Locale EN_GB = Locale.UK;
  private static final Locale CY_GB = Locale.of("cy", "GB");
  private static final List<String> INPUTS = List.of("Alice Smith", "original", "123 Some Road", "Acme Ltd", "");

  private static AlterEgo egFor(Locale locale) {
    return AlterEgo.builder().salt(SALT).locale(locale).build();
  }

  @Test
  void firstNameAgreesAcrossEnGbAndCyGb() {
    assertLocaleAgreement(AlterEgo::firstName);
  }

  @Test
  void lastNameAgreesAcrossEnGbAndCyGb() {
    assertLocaleAgreement(AlterEgo::lastName);
  }

  @Test
  void fullNameAgreesAcrossEnGbAndCyGb() {
    assertLocaleAgreement(AlterEgo::fullName);
  }

  @Test
  void cityAgreesAcrossEnGbAndCyGb() {
    assertLocaleAgreement(AlterEgo::city);
  }

  @Test
  void streetAddressAgreesAcrossEnGbAndCyGb() {
    assertLocaleAgreement(AlterEgo::streetAddress);
  }

  @Test
  void postcodeAgreesAcrossEnGbAndCyGb() {
    assertLocaleAgreement(AlterEgo::postcode);
  }

  @Test
  void organisationNameAgreesAcrossEnGbAndCyGb() {
    assertLocaleAgreement(AlterEgo::organisationName);
  }

  private static void assertLocaleAgreement(java.util.function.Function<AlterEgo, Transformation<String>> builtIn) {
    Transformation<String> enGb = builtIn.apply(egFor(EN_GB));
    Transformation<String> cyGb = builtIn.apply(egFor(CY_GB));
    for (String input : INPUTS) {
      assertEquals(enGb.apply(input), cyGb.apply(input), "mismatch for input: '" + input + "'");
    }
  }
}
