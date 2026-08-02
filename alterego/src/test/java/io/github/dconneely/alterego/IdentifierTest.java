package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class IdentifierTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  private static AlterEgo alterego(Locale locale) {
    return AlterEgo.builder().salt(SALT).locale(locale).build();
  }

  private List<Function<AlterEgo, Transformation<String>>> allIdentifiers() {
    return List.of(
        AlterEgo::nhsNumber,
        AlterEgo::nationalInsuranceNumber,
        AlterEgo::drivingLicenceNumber,
        AlterEgo::passportNumber,
        AlterEgo::creditCardNumber
    );
  }

  private List<Function<AlterEgo, Transformation<String>>> ukIdentifiers() {
    return List.of(
        AlterEgo::nhsNumber,
        AlterEgo::nationalInsuranceNumber,
        AlterEgo::drivingLicenceNumber,
        AlterEgo::passportNumber
    );
  }

  @Test
  void determinismAndDistinctness() {
    AlterEgo eg = alterego();
    for (var factory : allIdentifiers()) {
      Transformation<String> t = factory.apply(eg);

      String res1 = t.apply("alice");
      String res2 = t.apply("alice");
      assertEquals(res1, res2, "expected determinism");

      String res3 = t.apply("bob");
      assertNotEquals(res1, res3, "expected distinct outputs for distinct inputs");
    }
  }

  @Test
  void emptyStringAndNonAsciiProduceFormatValidOutput() {
    AlterEgo eg = alterego();
    for (var factory : allIdentifiers()) {
      Transformation<String> t = factory.apply(eg);

      String emptyOutput = t.apply("");
      assertTrue(emptyOutput.length() > 0);

      String nonAsciiOutput = t.apply("olé😀");
      assertTrue(nonAsciiOutput.length() > 0);

      assertNotEquals(emptyOutput, nonAsciiOutput);
    }
  }

  @Test
  void ukIdentifiersThrowForNonGbLocales() {
    for (var factory : ukIdentifiers()) {
      assertThrows(AlterEgoConfigException.class, () -> factory.apply(alterego(Locale.US)));
      assertThrows(AlterEgoConfigException.class, () -> factory.apply(alterego(Locale.ENGLISH)));
    }
  }

  @Test
  void creditCardNumberWorksUnderNonGbLocales() {
    Transformation<String> usCard = alterego(Locale.US).creditCardNumber();
    assertTrue(usCard.apply("test").startsWith("0"));

    Transformation<String> noCountryCard = alterego(Locale.ENGLISH).creditCardNumber();
    assertTrue(noCountryCard.apply("test").startsWith("0"));
  }
}
