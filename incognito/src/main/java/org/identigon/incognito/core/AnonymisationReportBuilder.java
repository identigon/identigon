package org.identigon.incognito.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.identigon.incognito.api.AnonymisationReport;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.DirectIdStrategy;
import org.identigon.incognito.api.PipelineContext;
import org.identigon.incognito.api.PipelineStage;
import org.identigon.incognito.api.SurrogateStrategy;
import org.identigon.incognito.engine.SchemaInspector;
import org.identigon.incognito.engine.TableDependencyGraph;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.ColumnPolicy;
import org.identigon.incognito.policy.TablePolicy;

/**
 * Assembles the typed {@link AnonymisationReport} (DPIA evidence) from the pipeline context and the
 * per-stage results.
 */
public final class AnonymisationReportBuilder {

    private AnonymisationReportBuilder() {}

    /** Context attribute key: the {@link org.identigon.incognito.api.SaltMode} the run was keyed with. */
    public static final String ATTR_SALT_MODE = "incognito.saltMode";

    /** Context attribute key: the list of {@link AnonymisationReport.SurvivalFinding} from verification. */
    public static final String ATTR_SURVIVAL_FINDINGS = "incognito.verification.survivalFindings";

    /** Context attribute key: the list of {@link AnonymisationReport.LintFinding} from verification. */
    public static final String ATTR_LINT_FINDINGS = "incognito.verification.lintFindings";

    /** Context attribute key: the list of {@link AnonymisationReport.StructuralUniquenessFinding} from verification. */
    public static final String ATTR_STRUCTURAL_FINDINGS = "incognito.verification.structuralFindings";

    /**
     * Builds the anonymisation report from the run's context and stage results.
     *
     * @param context      the pipeline context (holds the plan, table metadata, and inference suggestions)
     * @param stageResults the per-stage results
     * @return the assembled report
     */
    @SuppressWarnings("unchecked")
    public static AnonymisationReport build(PipelineContext context, List<PipelineStage.StageResult> stageResults) {
        Object planObj = context.attributes().get("incognito.schema.executionPlan");
        Object metaObj = context.attributes().get("incognito.schema.tableMetadata");
        Object inferObj = context.attributes().get("incognito.schema.inferSuggestions");
        Object rowsObj = context.attributes().get("incognito.metrics.rowsPerTable");
        Object verifiedTablesObj = context.attributes().get("incognito.verification.verifiedTables"); // Optional

        org.identigon.incognito.api.SaltMode saltMode =
            (org.identigon.incognito.api.SaltMode) context.attributes().get(ATTR_SALT_MODE);
        List<AnonymisationReport.SurvivalFinding> survivalFindings =
            (List<AnonymisationReport.SurvivalFinding>) context.attributes().getOrDefault(
                ATTR_SURVIVAL_FINDINGS, Collections.emptyList());
        List<AnonymisationReport.LintFinding> lintFindings =
            (List<AnonymisationReport.LintFinding>) context.attributes().getOrDefault(
                ATTR_LINT_FINDINGS, Collections.emptyList());
        List<AnonymisationReport.StructuralUniquenessFinding> structuralFindings =
            (List<AnonymisationReport.StructuralUniquenessFinding>) context.attributes().getOrDefault(
                ATTR_STRUCTURAL_FINDINGS, Collections.emptyList());

        if (planObj == null || metaObj == null) {
            return new AnonymisationReport(saltMode, Collections.emptyList(), survivalFindings,
                lintFindings, structuralFindings, stageResults);
        }

        TableDependencyGraph.TopologicalExecutionPlan plan = (TableDependencyGraph.TopologicalExecutionPlan) planObj;
        List<SchemaInspector.TableMetadata> metadataList = (List<SchemaInspector.TableMetadata>) metaObj;
        Map<String, List<AnonymisationReport.InferSuggestion>> suggestions = inferObj != null ?
            (Map<String, List<AnonymisationReport.InferSuggestion>>) inferObj : Collections.emptyMap();
        Map<String, Long> rowsPerTable = rowsObj != null ?
            (Map<String, Long>) rowsObj : Collections.emptyMap();
        List<String> verifiedTables = verifiedTablesObj != null ?
            (List<String>) verifiedTablesObj : Collections.emptyList();

        AnonymisationPolicy policy = context.policy();
        List<AnonymisationReport.TableReport> tableReports = new ArrayList<>();

        // A throwaway AlterEgo, keyed with a fixed non-secret salt, used only to generate the
        // illustrative per-column sample values (SPEC §7). It is unrelated to the run's real salt, so
        // the samples are synthetic and reproducible and correspond to no real subject. Effectively
        // final so the per-column lambda below can capture it; closed after the loop.
        org.identigon.alterego.AlterEgo exampleAlterEgo = exampleAlterEgo();

        for (String tableName : plan.sequentialTableOrder()) {
            SchemaInspector.TableMetadata tableMeta = metadataList.stream()
                .filter(m -> m.tableName().equals(tableName)).findFirst().orElse(null);

            if (tableMeta == null) continue;

            Optional<TablePolicy> tablePolicyOpt = policy.table(tableName);
            if (tablePolicyOpt.isEmpty()) continue;
            TablePolicy tablePolicy = tablePolicyOpt.get();

            List<AnonymisationReport.ColumnAction> columnActions = new ArrayList<>();
            List<AnonymisationReport.PassthroughFlag> passthroughFlags = new ArrayList<>();

            for (String colName : tableMeta.columns()) {
                if (tableMeta.generatedColumns().contains(colName)) continue;

                tablePolicy.column(colName).ifPresent(colPol -> {
                    // Does the column keep its real value (a passthrough), or is it transformed?
                    boolean keptReal = colPol.role() == ColumnRole.PAYLOAD
                        || (colPol.role() == ColumnRole.SENSITIVE && Boolean.FALSE.equals(colPol.distinguishing()));

                    String transformation;
                    if (colPol.role() == ColumnRole.SENSITIVE) {
                        if (Boolean.FALSE.equals(colPol.distinguishing())) {
                            transformation = "KEEP";
                        } else if (colPol.redactionStrategy() != null) {
                            transformation = colPol.redactionStrategy().toString();
                        } else if (colPol.quasiIdStrategy() != null) {
                            transformation = colPol.quasiIdStrategy().toString();
                        } else {
                            transformation = "REDACT";
                        }
                    } else if (colPol.role() == ColumnRole.INHERITED_ATTRIBUTE) {
                        transformation = "INHERIT from " + colPol.derivedFromTable() + "." + colPol.derivedFromColumn();
                    } else if (colPol.role() == ColumnRole.FOREIGN_KEY) {
                        transformation = "LINK to " + colPol.referencedTable();
                    } else {
                        transformation = switch (colPol.role()) {
                            case PRIMARY_KEY -> colPol.surrogateStrategy() != null ? colPol.surrogateStrategy().toString() : "SURROGATE";
                            case DIRECT_ID -> colPol.directIdStrategy() != null ? colPol.directIdStrategy().toString() : "FABRICATE";
                            case UNIQUE_CANDIDATE_KEY -> (colPol.directIdStrategy() != null ? colPol.directIdStrategy().toString() : "FABRICATE") + " (unique)";
                            case QUASI_ID -> colPol.quasiIdStrategy() != null ? colPol.quasiIdStrategy().toString() : "SYNTHESISE";
                            default -> "KEEP"; // PAYLOAD, GENERATED_COLUMN
                        };
                    }
                    Integer columnSqlType = tableMeta.columnTypes() == null
                        ? null : tableMeta.columnTypes().get(colName);
                    List<String> examples = new ArrayList<>();
                    for (int i = 0; i < SEEDS.size(); i++) {
                        examples.add(exampleCell(exampleAlterEgo, colPol, columnSqlType, SEEDS.get(i), i));
                    }
                    columnActions.add(new AnonymisationReport.ColumnAction(
                        colName, colPol.role(), transformation, examples));

                    // Opaque-type audit (SPEC §7.2): a KEPT column of a complex/untransformable JDBC
                    // type is surfaced in the DPIA report so a retained potentially-identifying value
                    // (JSONB, array, geometry, INET, BLOB, …) is visible, never silently passed through.
                    if (keptReal) {
                        Integer sqlType = tableMeta.columnTypes() == null ? null : tableMeta.columnTypes().get(colName);
                        String opaque = opaqueTypeName(sqlType);
                        if (opaque != null) {
                            passthroughFlags.add(new AnonymisationReport.PassthroughFlag(
                                colName, opaque, "untransformed potentially-identifying type kept as-is (SPEC §7.2)"));
                        }
                    }
                });
            }

            long rowsProcessed = rowsPerTable.getOrDefault(tableName, 0L);
            List<AnonymisationReport.InferSuggestion> tableSuggestions = suggestions.getOrDefault(tableName, Collections.emptyList());
            boolean verified = verifiedTables.contains(tableName);

            tableReports.add(new AnonymisationReport.TableReport(
                tableName,
                columnActions,
                rowsProcessed,
                passthroughFlags,
                tableSuggestions,
                verified
            ));
        }

        exampleAlterEgo.close(); // zero the (non-secret) example salt; no external resources to release

        return new AnonymisationReport(saltMode, tableReports, survivalFindings, lintFindings,
            structuralFindings, stageResults);
    }

    /** Fixed dummy seeds — one per illustrative sample row. Different seeds give varied sample values. */
    private static final List<String> SEEDS = List.of("sample-a", "sample-b", "sample-c");

    /**
     * A fixed, <b>non-secret</b> salt used only to key the illustrative example generator. It protects
     * nothing and is deliberately hardcoded so the samples are deterministic and reproducible, and it
     * is unrelated to the run's real salt — the samples therefore have zero linkage to any real or run
     * data.
     */
    private static final byte[] EXAMPLE_SALT =
        "incognito-illustrative-examples".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /** Builds the throwaway AlterEgo used for illustrative sample values; the caller closes it. */
    private static org.identigon.alterego.AlterEgo exampleAlterEgo() {
        return org.identigon.alterego.AlterEgo.builder()
            .salt(EXAMPLE_SALT.clone())
            .locale(java.util.Locale.UK)
            .rawMappingKeys(false)
            .mappingStore(new org.identigon.alterego.store.InMemoryMappingStore())
            .build();
    }

    /**
     * One illustrative sample value for a column at sample-row {@code i}. Synthetic (never touches real
     * data); mirrors the branch order of the {@code transformation} label above so the two stay in
     * step, using {@code sqlType} to distinguish a temporal QUASI_ID (shifted date) from a
     * character/other one. Any generation failure degrades to a placeholder rather than breaking the
     * report. Uses the literal guillemets {@code ‹ ›} for placeholders deliberately: they contain no
     * HTML-special character, so the emitter's {@code htmlEscape} leaves them intact.
     */
    private static String exampleCell(
            org.identigon.alterego.AlterEgo ex, ColumnPolicy colPol, Integer sqlType, String seed, int i) {
        try {
            ColumnRole role = colPol.role();
            if (role == ColumnRole.PAYLOAD) return "‹kept›";
            if (role == ColumnRole.FOREIGN_KEY) return "‹link›";
            if (role == ColumnRole.INHERITED_ATTRIBUTE) return "‹inherited›";
            if (role == ColumnRole.SENSITIVE) {
                if (Boolean.FALSE.equals(colPol.distinguishing())) return "‹kept›";
                if (colPol.redactionStrategy() != null) {
                    return switch (colPol.redactionStrategy()) {
                        case MASK -> ex.mask('*', 0).apply(seed);
                        case CLEAR -> "(cleared)";
                        case CONSTANT -> "(fixed value)";
                    };
                }
                if (colPol.quasiIdStrategy() != null) return quasiIdExample(ex, colPol, sqlType, seed, i);
                return "(redacted)";
            }
            if (role == ColumnRole.PRIMARY_KEY) {
                if (colPol.surrogateStrategy() == SurrogateStrategy.UUID_V4) {
                    return java.util.UUID.nameUUIDFromBytes(
                        seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                }
                if (colPol.surrogateStrategy() == SurrogateStrategy.PASSTHROUGH_SURROGATE) return "‹kept›";
                return String.valueOf(1001 + i); // SEQUENTIAL_LONG or null
            }
            if (role == ColumnRole.QUASI_ID) return quasiIdExample(ex, colPol, sqlType, seed, i);
            if (role == ColumnRole.DIRECT_ID || role == ColumnRole.UNIQUE_CANDIDATE_KEY) {
                return directIdExample(ex, colPol.directIdStrategy(), seed);
            }
            return "‹kept›";
        } catch (RuntimeException e) {
            return "‹example unavailable›";
        }
    }

    /**
     * A sample value for a QUASI_ID (or SENSITIVE-jittered) column, matching what the run actually
     * does: a typed {@code directIdStrategy} hint routes to that generator; otherwise a temporal type
     * shifts, and a non-temporal one is synthesised (a shape-preserving fabrication not reproduced
     * concretely here — shown as a placeholder so the sample never misrepresents a text column as a
     * date).
     */
    private static String quasiIdExample(
            org.identigon.alterego.AlterEgo ex, ColumnPolicy colPol, Integer sqlType, String seed, int i) {
        if (colPol.directIdStrategy() != null) return directIdExample(ex, colPol.directIdStrategy(), seed);
        if (isTemporalType(sqlType)) return shiftedDate(ex, i);
        return "‹synthesised›";
    }

    /** A sample value for a typed direct identifier; a null strategy is a generic shape-preserving one. */
    private static String directIdExample(
            org.identigon.alterego.AlterEgo ex, DirectIdStrategy s, String seed) {
        if (s == null) return "Example-" + seed;
        return switch (s) {
            case ALTEREGO_NAME -> ex.fullName().apply(seed);
            case ALTEREGO_FIRST_NAME -> ex.firstName().apply(seed);
            case ALTEREGO_LAST_NAME -> ex.lastName().apply(seed);
            case ALTEREGO_ORGANISATION -> ex.organisationName().apply(seed);
            case ALTEREGO_CITY -> ex.city().apply(seed);
            case ALTEREGO_STREET_ADDRESS -> ex.streetAddress().apply(seed);
            case ALTEREGO_POSTCODE -> ex.postcode().apply(seed);
            case ALTEREGO_EMAIL -> ex.emailAddress().apply(seed);
            case ALTEREGO_PHONE -> ex.phoneNumber().apply(seed);
            case ALTEREGO_DOMAIN -> ex.domainName().apply(seed);
            case ALTEREGO_URL -> ex.url().apply(seed);
            case ALTEREGO_GENERIC -> "Example-" + seed;
        };
    }

    /** Whether a JDBC type is temporal (so a QUASI_ID sample is a shifted date, not a synthesised string). */
    private static boolean isTemporalType(Integer sqlType) {
        return sqlType != null && (sqlType == java.sql.Types.DATE || sqlType == java.sql.Types.TIMESTAMP
            || sqlType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
    }

    /** A representative shifted date for a temporal QUASI_ID sample, varied by sample-row {@code i}. */
    private static String shiftedDate(org.identigon.alterego.AlterEgo ex, int i) {
        return ex.shiftDate(org.identigon.alterego.AlterEgo.DateField.MONTH)
            .apply(java.time.LocalDate.of(1984, 1 + i, 15)).toString();
    }

    /**
     * If {@code sqlType} is a complex/opaque JDBC type that v1.0 does not transform (JSONB, arrays,
     * geometry, INET, XML, LOBs, …), returns its JDBC type name; otherwise {@code null}. PostgreSQL
     * maps JSONB/JSON/INET/geometry to {@link java.sql.Types#OTHER}, and SQL arrays to
     * {@link java.sql.Types#ARRAY}.
     */
    private static String opaqueTypeName(Integer sqlType) {
        if (sqlType == null) return null;
        return switch (sqlType) {
            case java.sql.Types.ARRAY, java.sql.Types.OTHER, java.sql.Types.STRUCT, java.sql.Types.REF,
                 java.sql.Types.JAVA_OBJECT, java.sql.Types.SQLXML, java.sql.Types.DATALINK,
                 java.sql.Types.BLOB, java.sql.Types.CLOB, java.sql.Types.NCLOB,
                 java.sql.Types.BINARY, java.sql.Types.VARBINARY, java.sql.Types.LONGVARBINARY -> {
                try {
                    yield java.sql.JDBCType.valueOf(sqlType).getName();
                } catch (IllegalArgumentException e) {
                    yield "TYPE_" + sqlType;
                }
            }
            default -> null;
        };
    }
}
