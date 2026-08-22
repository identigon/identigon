package org.identigon.incognito.policy;

import java.util.regex.Pattern;
import org.identigon.incognito.api.ColumnRole;

/**
 * Auto-infers baseline column roles based on column name heuristics and regex patterns.
 *
 * @deprecated inference is authoring, not execution (fail-closed classification means it never
 *     affected engine output — see SPEC §7.2). The maintained version lives in {@code effigies}'
 *     own {@code PolicyInferrer}, which is what interviews users during authoring; this copy is
 *     retained only for {@code SchemaDiscoveryStage}'s fail-closed error-message hint and is
 *     scheduled for removal, together with {@code AnonymisationPolicy.Builder.autoInfer(boolean)},
 *     at incognito's next major version — see
 *     {@code docs/adr/0023-authoring-above-the-engine.md}.
 */
@Deprecated(forRemoval = true)
public class PolicyInferrer {

    /** Creates a policy inferrer with the built-in name heuristics. */
    public PolicyInferrer() {}

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i).*email.*");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?i).*(phone|mobile|fax).*");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i).*(first_?name|last_?name|surname|full_?name).*");
    private static final Pattern DOB_PATTERN = Pattern.compile("(?i).*(dob|birth_?date|date_of_birth).*");
    private static final Pattern SSN_PATTERN = Pattern.compile("(?i).*(ssn|social_?security|tax_?id|nhs_?num).*");

    /**
     * A suggested role and the heuristic that produced it.
     *
     * @param role      the suggested column role
     * @param heuristic the name of the heuristic that matched
     */
    public record InferredRole(ColumnRole role, String heuristic) {}

    /**
     * Infers a ColumnRole for a column based on its name.
     *
     * @param columnName Name of the database column.
     * @return Suggested ColumnRole and the heuristic matched, or empty if no heuristic matches.
     */
    public java.util.Optional<InferredRole> inferRole(String columnName) {
        if (EMAIL_PATTERN.matcher(columnName).matches()) return java.util.Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "EMAIL_PATTERN"));
        if (PHONE_PATTERN.matcher(columnName).matches()) return java.util.Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "PHONE_PATTERN"));
        if (NAME_PATTERN.matcher(columnName).matches()) return java.util.Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "NAME_PATTERN"));
        if (SSN_PATTERN.matcher(columnName).matches()) return java.util.Optional.of(new InferredRole(ColumnRole.DIRECT_ID, "SSN_PATTERN"));
        if (DOB_PATTERN.matcher(columnName).matches()) return java.util.Optional.of(new InferredRole(ColumnRole.QUASI_ID, "DOB_PATTERN"));
        return java.util.Optional.empty();
    }
}
