package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Exact expected outputs of every built-in transformation for the reference salt (spec section
 * 10). Generated once, eyeballed for plausibility (each output matches its declared pattern
 * shape, dictionary-drawn outputs are real dictionary entries, and jitter/email/phone outputs
 * satisfy their own guarantees), then frozen: a future change to the Appendix A algorithms, a
 * dictionary file, or this class's logic that alters any of these values is a breaking change
 * and MUST stop and be flagged, not silently "fixed" here.
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
    assertEquals("Fictionalhurst", alterego().lastName().apply("Smith"));
    assertEquals("Fabricatedstead", alterego().lastName().apply("Jones"));
  }

  @Test
  void fullNameGoldenOutput() {
    assertEquals("Alexander Fictionalhurst", alterego().fullName().apply("Alice Smith"));
  }

  @Test
  void cityGoldenOutput() {
    assertEquals("Kingston upon Hull", alterego().city().apply("original"));
  }

  @Test
  void streetAddressGoldenOutput() {
    assertEquals("5 Pretend Close", alterego().streetAddress().apply("original"));
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

  @Test
  void nhsNumberGoldenOutputs() {
    assertEquals("999 709 0632", alterego().nhsNumber().apply("original"));
    assertEquals("999 863 3680", alterego().nhsNumber().apply("second"));
  }

  @Test
  void nationalInsuranceNumberGoldenOutputs() {
    assertEquals("QQ 30 40 08 D", alterego().nationalInsuranceNumber().apply("original"));
    assertEquals("QQ 28 77 59 A", alterego().nationalInsuranceNumber().apply("second"));
  }

  @Test
  void drivingLicenceNumberGoldenOutputs() {
    assertEquals("99999454110NW9XN", alterego().drivingLicenceNumber().apply("original"));
    assertEquals("99999609028SI9ZD", alterego().drivingLicenceNumber().apply("second"));
  }

  @Test
  void passportNumberGoldenOutputs() {
    assertEquals("ZZ0273960", alterego().passportNumber().apply("original"));
    assertEquals("ZZ0425340", alterego().passportNumber().apply("second"));
  }

  @Test
  void creditCardNumberGoldenOutputs() {
    assertEquals("0814 6733 3628 4153", alterego().creditCardNumber().apply("original"));
    assertEquals("0407 7733 5108 9975", alterego().creditCardNumber().apply("second"));
  }

  // --- M3: temporal jitter, a representative selection of the eight strategies (not all sixteen
  // methods), plus emailAddress() and phoneNumber() ---------------------------------------------

  private static final LocalDate GOLDEN_DAY = LocalDate.of(2026, 3, 15);
  private static final LocalDateTime GOLDEN_MOMENT = LocalDateTime.of(2026, 3, 15, 14, 30, 45, 123_456_789);

  @Test
  void shiftDateByDaysGoldenOutput() {
    assertEquals(LocalDate.of(2026, 2, 26), alterego().shiftDate(30).apply(GOLDEN_DAY));
  }

  @Test
  void shiftDateByMonthGoldenOutput() {
    assertEquals(LocalDate.of(2026, 3, 12), alterego().shiftDate(AlterEgo.DateField.MONTH).apply(GOLDEN_DAY));
  }

  @Test
  void shiftDateByYearGoldenOutput() {
    assertEquals(LocalDate.of(2026, 1, 27), alterego().shiftDate(AlterEgo.DateField.YEAR).apply(GOLDEN_DAY));
  }

  @Test
  void shiftDateTimeByDaysAndSecondsGoldenOutput() {
    assertEquals(
        LocalDateTime.of(2026, 2, 14, 14, 48, 2), alterego().shiftDateTime(30, 3600).apply(GOLDEN_MOMENT));
  }

  @Test
  void shiftInstantByDaysAndSecondsGoldenOutput() {
    assertEquals(
        Instant.parse("2026-03-28T14:27:10.123456789Z"),
        alterego().shiftInstant(30, 3600).apply(Instant.parse("2026-03-15T14:30:45.123456789Z")));
  }

  @Test
  void shiftDateTimeByDaysAndHourFieldGoldenOutput() {
    assertEquals(
        LocalDateTime.of(2026, 4, 12, 14, 33, 49),
        alterego().shiftDateTime(30, AlterEgo.TimeField.HOUR).apply(GOLDEN_MOMENT));
  }

  @Test
  void shiftDateTimeByYearFieldAndRangeGoldenOutput() {
    assertEquals(
        LocalDateTime.of(2026, 5, 24, 10, 53, 20),
        alterego()
            .shiftDateTime(AlterEgo.DateField.YEAR, LocalTime.of(9, 0), LocalTime.of(17, 0))
            .apply(GOLDEN_MOMENT));
  }

  @Test
  void emailAddressGoldenOutputs() {
    assertEquals("aufxv.mzfic@example.net", alterego().emailAddress().apply("alice.smith@realmail.com"));
    assertEquals("ttq88@example.com", alterego().emailAddress().apply("bob99"));
  }

  @Test
  void domainNameGoldenOutputs() {
    assertEquals("example.org", alterego().domainName().apply("original"));
    assertEquals("zzhrb.invalid", alterego().domainName().apply("second"));
  }

  @Test
  void urlGoldenOutputs() {
    assertEquals("http://example.net/vwgi", alterego().url().apply("original"));
    assertEquals("http://qqqepud.test/mpgrg", alterego().url().apply("second"));
  }

  @Test
  void phoneNumberGoldenOutputs() {
    assertEquals("0131 496 0178", alterego().phoneNumber().apply("020 7946 0958"));
    assertEquals("0116 496 0405", alterego().phoneNumber().apply("07123 456789"));
  }
}
