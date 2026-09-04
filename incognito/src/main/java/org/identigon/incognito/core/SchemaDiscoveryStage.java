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
 * Stage 1: Inspects the source database schema via JDBC metadata, validates the {@link
 * AnonymisationPolicy} against discovered columns (fail-closed), and builds a topological execution
 * plan for table processing. Results are stored in the pipeline context's {@code attributes()} map
 * for downstream stages.
 */
public final class SchemaDiscoveryStage implements PipelineStage {

  /** Key used to store the discovered schema metadata in the pipeline context attributes. */
  public static final String ATTR_TABLE_METADATA = "incognito.schema.tableMetadata";

  /** Key used to store the topological execution plan in the pipeline context attributes. */
  public static final String ATTR_EXECUTION_PLAN = "incognito.schema.executionPlan";

  /**
   * Key used to store the auto-inference role suggestions in the pipeline context attributes.
   * <b>Always empty.</b> incognito's own inference (the {@code autoInfer} flag and {@code
   * PolicyInferrer}) was removed at v2.0.0 - inference now lives entirely in {@code effigies}' own
   * {@code PolicyInferrer} (ADR 23), which incognito cannot reach (it would invert the dependency
   * direction). Retained only so {@link
   * org.identigon.incognito.api.AnonymisationReport.TableReport#inferSuggestions()} still has
   * something to read; nothing populates it any more.
   */
  public static final String ATTR_INFER_SUGGESTIONS = "incognito.schema.inferSuggestions";

  private final SchemaInspector schemaInspector;
  private final TableDependencyGraph dependencyGraph;

  /** Creates a schema-discovery stage with the default inspector and dependency graph. */
  public SchemaDiscoveryStage() {
    this(new SchemaInspector(), new TableDependencyGraph());
  }

  /**
   * Creates a schema-discovery stage with explicit collaborators (for testing).
   *
   * @param inspector the JDBC schema inspector
   * @param graph the table dependency graph
   */
  public SchemaDiscoveryStage(SchemaInspector inspector, TableDependencyGraph graph) {
    this.schemaInspector = inspector;
    this.dependencyGraph = graph;
  }

  @Override
  public StageResult process(PipelineContext context) throws IncognitoException {
    // 1. Inspect the source database schema.
    List<SchemaInspector.TableMetadata> metadata = schemaInspector.inspect(context.source());

    // 2. Validate policy against the discovered schema (fail-closed; SPEC §7.2).
    java.util.Map<
            String, java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion>>
        suggestions = validate(metadata, context.policy());

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
        "Discovered "
            + metadata.size()
            + " tables, processing order: "
            + plan.sequentialTableOrder());
  }

  /**
   * Validates {@code policy} against already-discovered {@code metadata} - the same fail-closed
   * check {@link #process} runs (SPEC §7.2), callable directly against just a schema inspection,
   * with no target connection, no dependency-graph computation, and no {@link PipelineContext}
   * needed. This is what makes {@code effigies}' {@code validate} command possible: the engine's
   * best diagnostics, reachable without committing to a full {@code run}.
   *
   * @param metadata the discovered source schema (e.g. from {@link SchemaInspector#inspect})
   * @param policy the policy to validate against it
   * @return per-table auto-infer suggestions - always empty (see {@link #ATTR_INFER_SUGGESTIONS})
   * @throws IncognitoException.ConfigException if any table fails the check
   */
  public java.util.Map<
          String, java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion>>
      validate(List<SchemaInspector.TableMetadata> metadata, AnonymisationPolicy policy) {
    java.util.Map<
            String, java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion>>
        suggestions = new java.util.HashMap<>();

    // Accumulated across every table and every check, not thrown on the first hit, so one run
    // reports everything wrong at once instead of the author fixing issues one table (or one
    // column) at a time across repeated runs.
    List<String> failures = new java.util.ArrayList<>();
    java.util.Map<String, SchemaInspector.TableMetadata> metadataByName = new java.util.HashMap<>();
    for (SchemaInspector.TableMetadata table : metadata) {
      metadataByName.put(table.tableName(), table);
    }
    for (SchemaInspector.TableMetadata table : metadata) {
      policy
          .table(table.tableName())
          .ifPresent(
              tablePolicy ->
                  validateTablePolicy(table, tablePolicy, metadataByName, suggestions, failures));
    }
    if (!failures.isEmpty()) {
      throw new IncognitoException.ConfigException(
          "Fail-closed: "
              + failures.size()
              + " issue(s) found - fix all at once, not one run"
              + " at a time:\n  - "
              + String.join("\n  - ", failures));
    }
    return suggestions;
  }

  /**
   * Validates that every column in the discovered table has a declared role in the policy, and that
   * role-specific requirements are met. Fail-closed: every issue found is appended to {@code
   * failures} rather than thrown immediately (SPEC §7.2) - the caller throws once, after every
   * table has been checked.
   */
  private void validateTablePolicy(
      SchemaInspector.TableMetadata table,
      TablePolicy tablePolicy,
      java.util.Map<String, SchemaInspector.TableMetadata> metadataByName,
      java.util.Map<
              String,
              java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion>>
          allSuggestions,
      List<String> failures) {

    // Always empty - see ATTR_INFER_SUGGESTIONS's Javadoc. Kept only because the map this feeds
    // still needs an (empty) entry per table.
    java.util.List<org.identigon.incognito.api.AnonymisationReport.InferSuggestion>
        tableSuggestions = java.util.List.of();
    // Collected across the WHOLE table rather than thrown on the first hit, so one run reports
    // every unclassified column at once instead of the user fixing them one at a time across
    // repeated runs.
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
        // An unclassified column ALWAYS fails closed (SPEC §7.2) - it must never pass
        // through as real data. incognito itself makes no guess at what it might be;
        // effigies' scaffold/validate commands are where a suggestion comes from (ADR 23).
        unclassifiedMessages.add("'" + column + "'");
      } else {
        ColumnPolicy colPol = declared.get();
        if (colPol.role() == ColumnRole.SENSITIVE) {
          if (colPol.distinguishing() == null) {
            failures.add(
                "SENSITIVE column '"
                    + column
                    + "' in table '"
                    + table.tableName()
                    + "' does not declare the 'distinguishing' flag. It must explicitly be "
                    + "distinguishing: true or false (SPEC §4.1).");
          } else if (colPol.distinguishing()
              && colPol.quasiIdStrategy() == null
              && colPol.redactionStrategy() == null) {
            failures.add(
                "SENSITIVE column '"
                    + column
                    + "' in table '"
                    + table.tableName()
                    + "' is distinguishing: true, but declares no RedactionStrategy or "
                    + "QuasiIdStrategy (SPEC §4.1).");
          }
        } else if (colPol.role() == ColumnRole.DIRECT_ID) {
          // No implicit ALTEREGO_GENERIC default (ADR 29): a DIRECT_ID with no declared
          // strategy is an unmade decision, not a default, the same principle already
          // applied to SENSITIVE/distinguishing above. UNIQUE_CANDIDATE_KEY shares
          // buildDirectIdTransformer but is deliberately not covered here - it was never
          // claimed to carry a fictionality guarantee, so its silent ALTEREGO_GENERIC
          // default is unaffected.
          if (colPol.directIdStrategy() == null) {
            failures.add(
                "DIRECT_ID column '"
                    + column
                    + "' in table '"
                    + table.tableName()
                    + "' does not declare a directIdStrategy. It must be explicit - "
                    + "ALTEREGO_GENERIC is a valid choice, but not a silent one (SPEC §4.1).");
          }
        } else if (colPol.role() == ColumnRole.QUASI_ID) {
          validateSynthesiseType(table, column, colPol, failures);
        } else if (colPol.role() == ColumnRole.FOREIGN_KEY) {
          validateForeignKeyReferences(table, column, colPol, metadataByName, failures);
        }
      }
    }
    allSuggestions.put(table.tableName(), tableSuggestions);

    if (!unclassifiedMessages.isEmpty()) {
      failures.add(
          "table '"
              + table.tableName()
              + "' has "
              + unclassifiedMessages.size()
              + " unclassified column(s) with no declared ColumnRole in the policy: "
              + String.join(", ", unclassifiedMessages)
              + ". Classify each explicitly - see effigies' scaffold/validate commands for"
              + " suggested roles.");
    }
  }

  /**
   * Fail-closed guard for a {@code FOREIGN_KEY} with no declared {@code references} (SPEC §7.2,
   * §4.1). A <em>single-column</em> FK is resolved at load time via the policy-declared {@code
   * referencedTable}/{@code referencedColumn} ({@code TableTransformLoadStage.buildFkTransformer}),
   * not structurally - a missing block used to reach {@code run} as a raw {@code
   * NullPointerException} from the key-translation store instead of failing here. A composite FK is
   * resolved purely structurally from the discovered constraint and consults no policy field, so it
   * is exempt - checking {@link SchemaInspector.ForeignKeyConstraint#isComposite} the same way
   * {@code buildFkTransformer} does keeps this check and that resolution logic in lock-step.
   */
  private void validateForeignKeyReferences(
      SchemaInspector.TableMetadata table,
      String column,
      ColumnPolicy colPol,
      java.util.Map<String, SchemaInspector.TableMetadata> metadataByName,
      List<String> failures) {

    boolean composite =
        table.foreignKeyConstraints().stream()
            .anyMatch(fk -> fk.isComposite() && fk.childColumns().contains(column));
    if (composite) {
      return;
    }
    if (colPol.referencedTable() != null && colPol.referencedColumn() != null) {
      return;
    }

    // Best-effort suggestion, exactly as ScaffoldCommand.writeRoleStub already offers: the
    // parent table is structurally known either way; the parent column is only offered when
    // the parent has a single-column PK to point at unambiguously.
    String suggestion = "";
    String parentTable = table.foreignKeys().get(column);
    if (parentTable != null) {
      SchemaInspector.TableMetadata parent = metadataByName.get(parentTable);
      if (parent != null && parent.primaryKeyColumns().size() == 1) {
        suggestion =
            " (Suggestion: references: { table: "
                + parentTable
                + ", column: "
                + parent.primaryKeyColumns().get(0)
                + " })";
      } else {
        suggestion =
            " (Suggestion: target table is "
                + parentTable
                + "; its column isn't determined here - composite or unknown PK)";
      }
    }
    failures.add(
        "FOREIGN_KEY column '"
            + column
            + "' in table '"
            + table.tableName()
            + "' does not declare a references block. It must name the parent table and column"
            + " explicitly, e.g. references: { table: ..., column: ... }"
            + suggestion
            + " (SPEC §4.1).");
  }

  /**
   * Fail-closed guard for {@code SYNTHESISE}-by-type (SPEC Appendix B, ADR 31): a {@code QUASI_ID}
   * that is synthesised (its strategy is {@code SYNTHESISE}, or absent - the default) from a source
   * type with no built-in <em>fictional</em> generator, and with no typed {@code directIdStrategy}
   * hint, would silently fall back to shape-preserving fabrication with no fictionality guarantee -
   * the same unmade-decision problem ADR 29 already closed for {@code DIRECT_ID}. That is
   * forbidden; the issue is appended to {@code failures} rather than thrown immediately - see
   * {@link #validateTablePolicy}. A temporal type ({@link #isTemporalType}) has a type-matched
   * shift primitive and needs no hint; a character type ({@link #isCharacterType}) shape-preserves
   * only, so it always needs one; anything else has no mapping at all and always needs one.
   */
  private void validateSynthesiseType(
      SchemaInspector.TableMetadata table,
      String column,
      ColumnPolicy colPol,
      List<String> failures) {
    QuasiIdStrategy strategy = colPol.quasiIdStrategy();
    boolean synthesise = strategy == null || strategy == QuasiIdStrategy.SYNTHESISE;
    if (!synthesise || colPol.directIdStrategy() != null) {
      return; // jitter modes, or an explicit typed hint, are always fine
    }
    Integer sqlType = table.columnTypes() == null ? null : table.columnTypes().get(column);
    if (sqlType == null || isTemporalType(sqlType)) {
      return;
    }
    if (isCharacterType(sqlType)) {
      failures.add(
          "QUASI_ID column '"
              + column
              + "' in table '"
              + table.tableName()
              + "' uses SYNTHESISE on a character type ("
              + jdbcTypeName(sqlType)
              + ") with no"
              + " directIdStrategy hint. Shape-preserving fabrication carries no fictionality"
              + " guarantee (SPEC Appendix B, ADR 31) - declare a directIdStrategy hint (e.g."
              + " ALTEREGO_POSTCODE), or ALTEREGO_GENERIC if that lack of guarantee is a"
              + " deliberate choice.");
    } else {
      failures.add(
          "QUASI_ID column '"
              + column
              + "' in table '"
              + table.tableName()
              + "' uses SYNTHESISE but its type ("
              + jdbcTypeName(sqlType)
              + ") has no built-in"
              + " generator. Declare a directIdStrategy hint (e.g. ALTEREGO_POSTCODE) or a custom"
              + " strategy - SYNTHESISE never shape-fabricates an unmapped type (SPEC Appendix"
              + " B).");
    }
  }

  /**
   * Whether a source SQL type has a type-matched {@code SYNTHESISE} shift primitive (SPEC Appendix
   * B) that needs no {@code directIdStrategy} hint.
   */
  private static boolean isTemporalType(int sqlType) {
    return switch (sqlType) {
      case java.sql.Types.DATE, java.sql.Types.TIMESTAMP, java.sql.Types.TIMESTAMP_WITH_TIMEZONE ->
          true;
      default -> false;
    };
  }

  /**
   * Whether a source SQL type only has shape-preserving (format-preserving, not fictional) {@code
   * SYNTHESISE} fabrication (SPEC Appendix B) absent a {@code directIdStrategy} hint - so a hint is
   * always required (ADR 31).
   */
  private static boolean isCharacterType(int sqlType) {
    return switch (sqlType) {
      case java.sql.Types.VARCHAR,
          java.sql.Types.CHAR,
          java.sql.Types.LONGVARCHAR,
          java.sql.Types.NVARCHAR,
          java.sql.Types.NCHAR,
          java.sql.Types.LONGNVARCHAR ->
          true;
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
