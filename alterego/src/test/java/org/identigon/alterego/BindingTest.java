package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BindingTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  private static final Strategy<String> PICK_A_NAME =
      (in, ctx) -> ctx.random().pick(List.of("Alice", "Bob", "Carol", "Dave", "Eve"));

  @Test
  void bindProducesDeterministicOutputForSameInput() {
    Transformation<String> t = alterego().bind("test:name", PICK_A_NAME);
    String first = t.apply("original-value");
    String second = alterego().bind("test:name", PICK_A_NAME).apply("original-value");
    assertEquals(first, second);
  }

  @Test
  void bindIsOrderIndependentAcrossDifferentInputs() {
    Transformation<String> t = alterego().bind("test:name", PICK_A_NAME);
    String a1 = t.apply("input-a");
    String b1 = t.apply("input-b");

    Transformation<String> t2 = alterego().bind("test:name", PICK_A_NAME);
    String b2 = t2.apply("input-b");
    String a2 = t2.apply("input-a");

    assertEquals(a1, a2);
    assertEquals(b1, b2);
  }

  @Test
  void differentDomainsProduceDifferentOutputsForSameInput() {
    Transformation<String> t1 = alterego().bind("test:domain-one", PICK_A_NAME);
    Transformation<String> t2 = alterego().bind("test:domain-two", PICK_A_NAME);
    // Not a hard guarantee for any single input, but overwhelmingly likely across many.
    boolean anyDifferent = false;
    for (int i = 0; i < 20; i++) {
      if (!t1.apply("value-" + i).equals(t2.apply("value-" + i))) {
        anyDifferent = true;
      }
    }
    assertTrue(anyDifferent);
  }

  @Test
  void bindRejectsInvalidDomain() {
    assertThrows(
        AlterEgoConfigException.class, () -> alterego().bind("bad domain!", PICK_A_NAME));
  }

  @Test
  void typedBindRejectsUnsupportedType() {
    Strategy<Double> strategy = (in, ctx) -> in;
    assertThrows(
        AlterEgoConfigException.class, () -> alterego().bind("test:x", Double.class, strategy));
  }

  @Test
  void typedBindWorksForSupportedType() {
    Strategy<LocalDate> strategy = (in, ctx) -> in.plusDays(ctx.random().nextInt(10));
    Transformation<LocalDate> t = alterego().bind("test:date", LocalDate.class, strategy);
    LocalDate result = t.apply(LocalDate.of(2026, 1, 1));
    assertTrue(!result.isBefore(LocalDate.of(2026, 1, 1)));
  }

  @Test
  void nullPassesThroughByDefault() {
    Transformation<String> t = alterego().bind("test:name", PICK_A_NAME);
    assertNull(t.apply(null));
  }

  @Test
  void nullThrowsUnderFailPolicy() {
    AlterEgo strict = AlterEgo.builder().salt(SALT).nullPolicy(NullPolicy.FAIL).build();
    Transformation<String> t = strict.bind("test:name", PICK_A_NAME);
    assertThrows(AlterEgoException.class, () -> t.apply(null));
  }

  @Test
  void contextCarriesConfiguredLocale() {
    Strategy<String> revealsLocale = (in, ctx) -> ctx.locale().toString();
    AlterEgo us = AlterEgo.builder().salt(SALT).locale(Locale.US).build();
    assertEquals(Locale.US.toString(), us.bind("test:locale", revealsLocale).apply("x"));
  }

  @Test
  void defaultLocaleIsUk() {
    Strategy<String> revealsLocale = (in, ctx) -> ctx.locale().toString();
    assertEquals(Locale.UK.toString(), alterego().bind("test:locale", revealsLocale).apply("x"));
  }

  @Test
  void uniqueThrowsImmediatelyWithoutConfiguredStore() {
    Transformation<String> t = alterego().bind("test:name", PICK_A_NAME);
    assertThrows(AlterEgoStoreException.class, t::unique);
  }

  @Test
  void storedThrowsImmediatelyWithoutConfiguredStore() {
    Transformation<String> t = alterego().bind("test:name", PICK_A_NAME);
    assertThrows(AlterEgoStoreException.class, t::stored);
  }

  @Test
  void fullNameStyleCompositeAgreesWithStandaloneDelegation() {
    Strategy<String> firstNameStrategy =
        (in, ctx) -> ctx.random().pick(List.of("Alice", "Bob", "Carol"));
    Strategy<String> fullNameStrategy =
        (in, ctx) -> {
          String[] parts = in.split(" ", 2);
          TransformationContext firstCtx = ctx.derived("test:first-name", parts[0]);
          return firstNameStrategy.transform(parts[0], firstCtx);
        };

    AlterEgo eg = alterego();
    Transformation<String> firstName = eg.bind("test:first-name", firstNameStrategy);
    Transformation<String> fullName = eg.bind("test:full-name", fullNameStrategy);

    assertEquals(firstName.apply("Alice"), fullName.apply("Alice Smith"));
  }
}
