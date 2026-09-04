package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.DirectIdStrategy;
import org.junit.jupiter.api.Test;

class PolicyInferrerTest {

  private final PolicyInferrer inferrer = new PolicyInferrer();

  @Test
  void suggestsDirectIdForEmailColumns() {
    for (String col :
        new String[] {"email", "user_email", "contact_email", "email_address", "e_mail"}) {
      Optional<PolicyInferrer.InferredRole> role = inferrer.inferRole(col);
      assertTrue(role.isPresent(), col + " should be suggested");
      assertEquals(ColumnRole.DIRECT_ID, role.get().role(), col);
      assertEquals("EMAIL_PATTERN", role.get().heuristic(), col);
    }
  }

  @Test
  void doesNotFlagBooleanEmailSuffixFlagsAsDirectId() {
    // A regression case: the old unanchored ".*email.*" pattern matched these too, suggesting
    // DIRECT_ID for a boolean/status column that holds no email value at all. Anchoring to the
    // end of the name only helps for a boolean-ish *suffix* on "email" (e.g. "_verified") --
    // a prefix like "has_email" still matches, since the name genuinely does end in "email".
    // That narrower remaining gap is accepted, not fixed here.
    for (String col : new String[] {"email_verified", "email_opt_in"}) {
      assertEquals(Optional.empty(), inferrer.inferRole(col), col + " holds no email value");
    }
  }

  @Test
  void suggestsDirectIdForPhoneColumns() {
    for (String col : new String[] {"phone", "mobile", "fax", "phone_number", "contact_mobile"}) {
      Optional<PolicyInferrer.InferredRole> role = inferrer.inferRole(col);
      assertTrue(role.isPresent(), col + " should be suggested");
      assertEquals(ColumnRole.DIRECT_ID, role.get().role(), col);
    }
  }

  @Test
  void suggestsDirectIdForNameColumns() {
    for (String col : new String[] {"first_name", "last_name", "surname", "full_name"}) {
      Optional<PolicyInferrer.InferredRole> role = inferrer.inferRole(col);
      assertTrue(role.isPresent(), col + " should be suggested");
      assertEquals(ColumnRole.DIRECT_ID, role.get().role(), col);
    }
  }

  @Test
  void suggestsDirectIdForNationalIdColumns() {
    for (String col : new String[] {"ssn", "nhs_number", "nino", "national_insurance_number"}) {
      assertEquals(ColumnRole.DIRECT_ID, inferrer.inferRole(col).orElseThrow().role(), col);
    }
  }

  @Test
  void suggestsDirectIdForPassportColumns() {
    for (String col : new String[] {"passport", "passport_number", "passport_no"}) {
      Optional<PolicyInferrer.InferredRole> role = inferrer.inferRole(col);
      assertTrue(role.isPresent(), col + " should be suggested");
      assertEquals(ColumnRole.DIRECT_ID, role.get().role(), col);
      assertEquals("PASSPORT_PATTERN", role.get().heuristic(), col);
    }
  }

  @Test
  void suggestsDirectIdForDrivingLicenceColumns() {
    for (String col :
        new String[] {"driving_licence", "driving_license_number", "driver_license_no"}) {
      Optional<PolicyInferrer.InferredRole> role = inferrer.inferRole(col);
      assertTrue(role.isPresent(), col + " should be suggested");
      assertEquals(ColumnRole.DIRECT_ID, role.get().role(), col);
      assertEquals("DRIVING_LICENCE_PATTERN", role.get().heuristic(), col);
    }
  }

  @Test
  void suggestsSensitiveForCreditCardColumns() {
    // SENSITIVE, not DIRECT_ID: no typed fictional-card-number generator exists, and a card
    // number is conventionally redacted (RedactionStrategy.CONSTANT), the same as a bank account.
    for (String col :
        new String[] {"credit_card", "credit_card_number", "card_number", "cc_number"}) {
      Optional<PolicyInferrer.InferredRole> role = inferrer.inferRole(col);
      assertTrue(role.isPresent(), col + " should be suggested");
      assertEquals(ColumnRole.SENSITIVE, role.get().role(), col);
      assertEquals("CREDIT_CARD_PATTERN", role.get().heuristic(), col);
    }
  }

  @Test
  void suggestsQuasiIdForPostcodeColumns() {
    // QUASI_ID, not DIRECT_ID -- per ColumnRole's own javadoc, a postcode alone isn't
    // individually identifying the way an email or NHS number is.
    for (String col : new String[] {"postcode", "postal_code", "zip_code", "zip"}) {
      Optional<PolicyInferrer.InferredRole> role = inferrer.inferRole(col);
      assertTrue(role.isPresent(), col + " should be suggested");
      assertEquals(ColumnRole.QUASI_ID, role.get().role(), col);
      assertEquals("POSTCODE_PATTERN", role.get().heuristic(), col);
    }
  }

  @Test
  void suggestsQuasiIdForDobColumns() {
    for (String col : new String[] {"dob", "birth_date", "date_of_birth"}) {
      assertEquals(ColumnRole.QUASI_ID, inferrer.inferRole(col).orElseThrow().role(), col);
    }
  }

  @Test
  void suggestsNothingForAnUnrecognisedColumnName() {
    assertEquals(Optional.empty(), inferrer.inferRole("status"));
    assertEquals(Optional.empty(), inferrer.inferRole("quantity"));
  }

  @Test
  void suggestsAnUnambiguousDirectIdStrategyPerHeuristic() {
    assertEquals(
        DirectIdStrategy.ALTEREGO_EMAIL,
        inferrer.suggestedDirectIdStrategy("EMAIL_PATTERN").orElseThrow());
    assertEquals(
        DirectIdStrategy.ALTEREGO_PHONE,
        inferrer.suggestedDirectIdStrategy("PHONE_PATTERN").orElseThrow());
    assertEquals(
        DirectIdStrategy.ALTEREGO_NAME,
        inferrer.suggestedDirectIdStrategy("NAME_PATTERN").orElseThrow());
    assertEquals(
        DirectIdStrategy.ALTEREGO_PASSPORT_NUMBER,
        inferrer.suggestedDirectIdStrategy("PASSPORT_PATTERN").orElseThrow());
    assertEquals(
        DirectIdStrategy.ALTEREGO_DRIVING_LICENCE_NUMBER,
        inferrer.suggestedDirectIdStrategy("DRIVING_LICENCE_PATTERN").orElseThrow());
  }

  @Test
  void suggestsNoDirectIdStrategyForAnAmbiguousHeuristic() {
    // NATIONAL_ID_PATTERN spans ssn/tax_id (no typed generator exists for either) and
    // nino/nhs_number (two different generators) - no single strategy is a safe suggestion.
    assertEquals(Optional.empty(), inferrer.suggestedDirectIdStrategy("NATIONAL_ID_PATTERN"));
  }
}
