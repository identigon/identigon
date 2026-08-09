package org.identigon.effigies;

import java.util.Optional;
import java.util.regex.Pattern;
import org.identigon.incognito.api.ColumnRole;

/**
 * Auto-infers baseline column roles based on column name heuristics and regex patterns.
 * Migrated from lib-incognito to Effigies to preserve fail-closed engine execution.
 */
public class PolicyInferrer {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i).*email.*");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?i).*(phone|mobile|fax).*");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i).*(first_?name|last_?name|surname|full_?name).*");
    private static final Pattern DOB_PATTERN = Pattern.compile("(?i).*(dob|birth_?date|date_of_birth).*");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("(?i).*(ssn|social_?security|tax_?id|nhs_?num|nino|national_?insurance).*");

    public record InferredRole(ColumnRole role, String heuristic) {}

    public Optional<InferredRole> inferRole(String columnName) {
        if (EMAIL_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "EMAIL_PATTERN"));
        if (PHONE_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "PHONE_PATTERN"));
        if (NAME_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "NAME_PATTERN"));
        if (NATIONAL_ID_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "NATIONAL_ID_PATTERN"));
        if (DOB_PATTERN.matcher(columnName).matches()) return Optional.of(new InferredRole(ColumnRole.QUASI_ID, "DOB_PATTERN"));
        return Optional.empty();
    }
}
