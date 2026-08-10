package org.identigon.effigies;

import java.util.Optional;
import java.util.regex.Pattern;
import org.identigon.incognito.api.ColumnRole;

/**
 * Auto-infers baseline column roles based on column name heuristics and regex patterns.
 * Migrated from incognito to Effigies to preserve fail-closed engine execution.
 */
public class PolicyInferrer {

    // Anchored to the end of the column name (not ".*x.*") where the unanchored form would flag an
    // obviously-not-a-value column as if it held the identifier itself -- e.g. "email_verified" (a
    // boolean) or "phone_confirmed" would otherwise match as DIRECT_ID alongside "email"/"phone".
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i).*e[-_]?mail(_?address)?$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?i).*(phone|mobile|fax)(_?number)?$");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i).*(first_?name|last_?name|surname|full_?name).*");
    private static final Pattern DOB_PATTERN = Pattern.compile("(?i).*(dob|birth_?date|date_of_birth).*");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("(?i).*(ssn|social_?security|tax_?id|nhs_?num|nino|national_?insurance).*");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("(?i).*passport(_?number|_?no)?$");
    private static final Pattern DRIVING_LICENCE_PATTERN = Pattern.compile("(?i).*driv(ing|er)_?licen[cs]e(_?number|_?no)?$");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("(?i).*(credit_?card|card_?number|cc_?number)$");
    // Postcode is QUASI_ID, not DIRECT_ID, per ColumnRole's own javadoc example -- it's not
    // individually identifying the way an email or NHS number is.
    private static final Pattern POSTCODE_PATTERN = Pattern.compile("(?i).*(post_?code|postal_?code|zip_?code|zip)$");

    public record InferredRole(ColumnRole role, String heuristic) {}

    public Optional<InferredRole> inferRole(String columnName) {
        if (EMAIL_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "EMAIL_PATTERN"));
        if (PHONE_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "PHONE_PATTERN"));
        if (NAME_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "NAME_PATTERN"));
        if (NATIONAL_ID_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "NATIONAL_ID_PATTERN"));
        if (PASSPORT_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "PASSPORT_PATTERN"));
        if (DRIVING_LICENCE_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "DRIVING_LICENCE_PATTERN"));
        if (CREDIT_CARD_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "CREDIT_CARD_PATTERN"));
        if (POSTCODE_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.QUASI_ID, "POSTCODE_PATTERN"));
        if (DOB_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.QUASI_ID, "DOB_PATTERN"));
        return Optional.empty();
    }
}
