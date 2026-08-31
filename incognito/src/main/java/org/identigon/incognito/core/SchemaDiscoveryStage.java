package org.identigon.incognito.core;

import java.util.List;
import java.util.Optional;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.PipelineContext;
import org.identigon.incognito.api.PipelineStage;
import org.identigon.incognito.api.QuasiIdStrategy;
import org.identigon.incognito.engine.SchemaInspector;
import org.identigon.incognito.engine.TableDependencyGraph;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.ColumnPolicy;
import org.identigon.incognito.policy.TablePolicy;

/**
 * Stage 1: Inspects the source database schema via JDBC metadata, validates the
 * {@link AnonymisationPolicy} against discovered columns (fail-closed), and builds a
 * topological execution plan for table processing. Results are stored in the pipeline
 * context's {@code attributes()} map for downstream stages.
 */
// Uses the deprecated (forRemoval) PolicyInferrer throughout, deliberately, for the fail-closed
// error-message hint until incognito's next major version removes it -- see PolicyInferrer's Javadoc.
@SuppressWarnings("removal")
public final class SchemaDiscoveryStage implements PipelineStage {

    /** Key used to store the discovered schema metadata in the pipeline context attributes. */
    public static final String ATTR_TABLE_METADATA = "incognito.schema.tableMetadata";

    /** Key used to store the topological execution plan in the pipeline context attributes. */
    public static final String ATTR_EXECUTION_PLAN = "incognito.schema.executionPlan";

    /**
     * Key used to store the auto-inference role suggestions in the pipeline context attributes.
     * <b>Note:</b> since an unclassified column always aborts the run ({@code ConfigException},
     * SPEC §7.2), this map is only ever stored on a fully-successful validation pass - where, by
     * definition, every column was already classified and there was nothing to suggest. Genuinely
     * populated suggestions currently only ever reach the thrown exception's message, listing
     * every unclassified column in a table at once, not a returned {@code AnonymisationReport}.
     */
    public static final String ATTR_INFER_SUGGESTIONS = "incognito.schema.inferSuggestions";

    private final SchemaInspector schemaInspector;
    private final TableDependencyGraph dependencyGraph;
    private final org.identigon.incognito.policy.PolicyInferrer inferrer = new org.identigon.incognito.policy.PolicyInferrer();

    /** Creates a schema-discovery stage with the default inspector and dependency graph. */
    public SchemaDiscoveryStage() {
        this(new SchemaInspector(), new TableDependencyGraph());
    }

    /**
     * Creates a schema-discovery stage with explicit collaborators (for testing).
     *
     * @param inspector the JDBC schema inspector
     * @param graph     the table dependency graph
     */
    public SchemaDiscoveryStage(SchemaInspector inspector, TableDependencyGraph graph) {
        this.schemaInspector = inspector;
        this.dependencyGraph = graph;
    }

    @Override
    public StageResult process(PipelineContext context) throws IncognitoException {
        // 1. Inspect the source database schema.
        List<SchemaInspector.TableMetadata> metadata = schemaInspector.inspect(context.source());

        java.util.Map<String, java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion>> suggestions = new java.util.HashMap<>();

        // 2. Validate policy: every discovered column in policy-declared tables must have a role,
        //    plus the role-specific requirements below (SENSITIVE/distinguishing, DIRECT_ID/
        //    directIdStrategy, QUASI_ID/SYNTHESISE-by-type). Accumulated across every table and
        //    every check, not thrown on the first hit, so one run reports everything wrong at once
        //    instead of the author fixing issues one table (or one column) at a time across
        //    repeated runs.
        List<String> failures = new java.util.ArrayList<>();
        AnonymisationPolicy policy = context.policy();
        for (SchemaInspector.TableMetadata table : metadata) {
            policy.table(table.tableName()).ifPresent(tablePolicy ->
                validateTablePolicy(table, tablePolicy, suggestions, failures)
            );
        }
        if (!failures.isEmpty()) {
            throw new IncognitoException.ConfigException(
                "Fail-closed: " + failures.size() + " issue(s) found - fix all at once, not one run"
                    + " at a time:\n  - " + String.join("\n  - ", failures));
        }

        // 3. Build the topological execution plan.
        TableDependencyGraph.TopologicalExecutionPlan plan =
            dependencyGraph.computeTopologicalOrder(metadata);

        // 4. Store results in context for downstream stages.
        context.attributes().put(ATTR_TABLE_METADATA, metadata);
        context.attributes().put(ATTR_EXECUTION_PLAN, plan);
        context.attributes().put(ATTR_INFER_SUGGESTIONS, suggestions);

        return new StageResult(
            "SchemaDiscoveryStage",
            true,
            metadata.size(),
            "Discovered " + metadata.size() + " tables, processing order: " + plan.sequentialTableOrder()
        );
    }

    /**
     * Validates that every column in the discovered table has a declared role in the policy, and
     * that role-specific requirements are met. Fail-closed: every issue found is appended to
     * {@code failures} rather than thrown immediately (SPEC §7.2) - the caller throws once, after
     * every table has been checked.
     */
    private void validateTablePolicy(
            SchemaInspector.TableMetadata table,
            TablePolicy tablePolicy,
            java.util.Map<String, java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion>> allSuggestions,
            List<String> failures) {

        java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion> tableSuggestions = new java.util.ArrayList<>();
        // Collected across the WHOLE table rather than thrown on the first hit, so one run reports
        // every unclassified column at once instead of the user fixing them one at a time across
        // repeated runs. (This still always aborts the run - auto-infer only suggests, never
        // assigns, SPEC §7.2; it does not make suggestions reach a *returned* report, since a
        // fail-closed run never returns one - see ATTR_INFER_SUGGESTIONS's Javadoc.)
        java.util.List<String> unclassifiedMessages = new java.util.ArrayList<>();

        for (String column : table.columns()) {
            // Skip generated columns - they are excluded from INSERT and need no classification.
            if (table.generatedColumns().contains(column)) {
                continue;
            }

            Optional<ColumnPolicy> declared = tablePolicy.column(column);
            // A column entirely absent from the policy, AND a column present but with no `role`
            // key (ColumnPolicy.role() == null - never defaulted, see ColumnPolicy.Builder) are
            // both "unclassified": both must fail closed identically. Checking Optional.isEmpty()
            // alone would silently miss the latter, since a ColumnPolicy still exists for it.
            if (declared.isEmpty() || declared.get().role() == null) {
                // Auto-inference only SUGGESTS a role; it never silently assigns one, so an
                // unclassified column ALWAYS fails-closed (SPEC §7.2) regardless of the policy's
                // autoInfer setting - it must never pass through as real data. SPEC §7.2's "opt-in"
                // language governs whether a suggestion reaches the REPORT (ATTR_INFER_SUGGESTIONS,
                // which - see its Javadoc - is moot here anyway: a fail-closed run never returns
                // one); it says nothing about this MESSAGE. The hint below is deliberately shown
                // unconditionally: it never assigns anything, so autoInfer gates nothing it needs
                // to gate, and suppressing it when autoInfer is off (the default) would make the
                // common case's error message less helpful, not more correct.
                var inferred = inferrer.inferRole(column);
                String hint = inferred
                    .map(r -> " (auto-infer suggests " + r.role() + " via " + r.heuristic() + ")")
                    .orElse("");
                unclassifiedMessages.add("'" + column + "'" + hint);
                inferred.ifPresent(r -> tableSuggestions.add(
                    new org.identigon.incognito.api.AnonymisationReport.InferSuggestion(column, r.role(), r.heuristic())));
            } else {
                ColumnPolicy colPol = declared.get();
                if (colPol.role() == ColumnRole.SENSITIVE) {
                    if (colPol.distinguishing() == null) {
                        failures.add("SENSITIVE column '" + column + "' in table '" + table.tableName()
                            + "' does not declare the 'distinguishing' flag. It must explicitly be distinguishing: true or false (SPEC §4.1).");
                    } else if (colPol.distinguishing() && colPol.quasiIdStrategy() == null && colPol.redactionStrategy() == null) {
                        failures.add("SENSITIVE column '" + column + "' in table '" + table.tableName()
                            + "' is distinguishing: true, but declares no RedactionStrategy or QuasiIdStrategy (SPEC §4.1).");
                    }
                } else if (colPol.role() == ColumnRole.DIRECT_ID) {
                    // No implicit ALTEREGO_GENERIC default (ADR 29): a DIRECT_ID with no declared
                    // strategy is an unmade decision, not a default, the same principle already
                    // applied to SENSITIVE/distinguishing above. UNIQUE_CANDIDATE_KEY shares
                    // buildDirectIdTransformer but is deliberately not covered here - it was never
                    // claimed to carry a fictionality guarantee, so its silent ALTEREGO_GENERIC
                    // default is unaffected.
                    if (colPol.directIdStrategy() == null) {
                        failures.add("DIRECT_ID column '" + column + "' in table '" + table.tableName()
                            + "' does not declare a directIdStrategy. It must be explicit - "
                            + "ALTEREGO_GENERIC is a valid choice, but not a silent one (SPEC §4.1).");
                    }
                } else if (colPol.role() == ColumnRole.QUASI_ID) {
                    validateSynthesiseType(table, column, colPol, failures);
                }
            }
        }
        allSuggestions.put(table.tableName(), tableSuggestions);

        if (!unclassifiedMessages.isEmpty()) {
            failures.add("table '" + table.tableName() + "' has " + unclassifiedMessages.size()
                + " unclassified column(s) with no declared ColumnRole in the policy: "
                + String.join(", ", unclassifiedMessages)
                + ". Classify each explicitly - auto-infer only suggests, never assigns.");
        }
    }

    /**
     * Fail-closed guard for {@code SYNTHESISE}-by-type (SPEC Appendix B): a {@code QUASI_ID} that is
     * synthesised (its strategy is {@code SYNTHESISE}, or absent - the default) from a source type
     * with no built-in generator, and with no typed {@code directIdStrategy} hint, would silently
     * shape-fabricate. That is forbidden; the issue is appended to {@code failures} rather than
     * thrown immediately - see {@link #validateTablePolicy}. Temporal and character types have a
     * mapping ({@link #isSynthesisableType}) and pass.
     */
    private void validateSynthesiseType(
            SchemaInspector.TableMetadata table, String column, ColumnPolicy colPol, List<String> failures) {
        QuasiIdStrategy strategy = colPol.quasiIdStrategy();
        boolean synthesise = strategy == null || strategy == QuasiIdStrategy.SYNTHESISE;
        if (!synthesise || colPol.directIdStrategy() != null) {
            return; // jitter modes, or an explicit typed hint, are always fine
        }
        Integer sqlType = table.columnTypes() == null ? null : table.columnTypes().get(column);
        if (sqlType != null && !isSynthesisableType(sqlType)) {
            failures.add("QUASI_ID column '" + column + "' in table '" + table.tableName()
                + "' uses SYNTHESISE but its type (" + jdbcTypeName(sqlType) + ") has no built-in"
                + " generator. Declare a directIdStrategy hint (e.g. ALTEREGO_POSTCODE) or a custom"
                + " strategy - SYNTHESISE never shape-fabricates an unmapped type (SPEC Appendix B).");
        }
    }

    /**
     * Whether a source SQL type has a built-in {@code SYNTHESISE} mapping (SPEC Appendix B): temporal
     * types shift; character types shape-preserve. Everything else needs a typed hint or fails closed.
     */
    private static boolean isSynthesisableType(int sqlType) {
        return switch (sqlType) {
            case java.sql.Types.DATE, java.sql.Types.TIMESTAMP, java.sql.Types.TIMESTAMP_WITH_TIMEZONE,
                 java.sql.Types.VARCHAR, java.sql.Types.CHAR, java.sql.Types.LONGVARCHAR,
                 java.sql.Types.NVARCHAR, java.sql.Types.NCHAR, java.sql.Types.LONGNVARCHAR -> true;
            default -> false;
        };
    }

    /** The JDBC type name for a {@link java.sql.Types} code, for a readable fail-closed message. */
    private static String jdbcTypeName(int sqlType) {
        try {
            return java.sql.JDBCType.valueOf(sqlType).getName();
        } catch (IllegalArgumentException e) {
            return "type " + sqlType;
        }
    }
}
