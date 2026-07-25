package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code emailAddress()}: last-{@code @} splitting, class-wise local-part replacement,
 * the RFC 2606 fictionality guarantee, and the preserve/map domain options (section 4.1,
 * section 4.4).
 */
class EmailAddressTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.net", "example.org");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void splitsAtTheLastAtSign() {
    // A quoted local part containing an internal '@' — the split must use the last '@', not the first.
    Transformation<String> t = alterego().emailAddress();
    String result = t.apply("\"a@b\"@example.org");
    int lastAt = result.lastIndexOf('@');
    assertTrue(RESERVED_DOMAINS.contains(result.substring(lastAt + 1)));
    // Local part must still contain exactly one literal '@' (the one inside the quotes), preserved untouched.
    String localPart = result.substring(0, lastAt);
    assertEquals(1, localPart.chars().filter(c -> c == '@').count());
  }

  @Test
  void noAtSignTreatsWholeInputAsLocalPartAndAppendsADomain() {
    Transformation<String> t = alterego().emailAddress();
    String result = t.apply("bareusername");
    assertTrue(result.contains("@"));
    String domain = result.substring(result.indexOf('@') + 1);
    assertTrue(RESERVED_DOMAINS.contains(domain));
  }

  @Test
  void classWiseReplacementPreservesCaseAndLeavesOtherCharsUntouched() {
    Transformation<String> t = alterego().emailAddress();
    String result = t.apply("Jo.Smith+tag99@example.org");
    String localPart = result.substring(0, result.lastIndexOf('@'));
    assertEquals("Jo.Smith+tag99".length(), localPart.length()); // same length as input local part
    assertTrue(Character.isUpperCase(localPart.charAt(0))); // 'J' -> upper
    assertTrue(Character.isLowerCase(localPart.charAt(1))); // 'o' -> lower
    assertEquals('.', localPart.charAt(2)); // '.' untouched
    assertTrue(Character.isUpperCase(localPart.charAt(3))); // 'S' -> upper
    assertEquals('+', localPart.charAt(8)); // '+' untouched
    assertTrue(Character.isDigit(localPart.charAt(12))); // '9' -> digit
    assertTrue(Character.isDigit(localPart.charAt(13))); // '9' -> digit
  }

  // The fictionality property test (every default output uses a reserved domain, large sample)
  // now lives in FictionalityTest, alongside postcode()'s and phoneNumber()'s.

  @Test
  void preserveDomainKeepsTheInputsOwnDomain() {
    Transformation<String> t = alterego().emailAddress(EmailOptions.preserveDomain());
    String result = t.apply("someone@my-real-company.example-real.com");
    assertTrue(result.endsWith("@my-real-company.example-real.com"));
  }

  @Test
  void preserveDomainFallsBackToReservedWhenInputHasNoDomain() {
    Transformation<String> t = alterego().emailAddress(EmailOptions.preserveDomain());
    String result = t.apply("bareusername");
    String domain = result.substring(result.indexOf('@') + 1);
    assertTrue(RESERVED_DOMAINS.contains(domain));
  }

  @Test
  void mapDomainAlwaysUsesTheSuppliedDomain() {
    Transformation<String> t = alterego().emailAddress(EmailOptions.mapDomain("mapped.test"));
    assertTrue(t.apply("someone@real.com").endsWith("@mapped.test"));
    assertTrue(t.apply("bareusername").endsWith("@mapped.test"));
  }

  @Test
  void isDeterministic() {
    Transformation<String> t = alterego().emailAddress();
    assertEquals(t.apply("original@example.com"), alterego().emailAddress().apply("original@example.com"));
  }

  @Test
  void nonAsciiAndEmptyInputsAreHandled() {
    Transformation<String> t = alterego().emailAddress();
    String result = t.apply("Örebro.Müller@例え.jp");
    assertTrue(result.contains("@"));
    String empty = t.apply("");
    assertTrue(empty.startsWith("@"));
  }
}
