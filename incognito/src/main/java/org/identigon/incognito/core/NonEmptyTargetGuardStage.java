package org.identigon.incognito.core;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.PipelineContext;
import org.identigon.incognito.api.PipelineStage;
import org.identigon.incognito.engine.SchemaInspector;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.TablePolicy;

/**
 * Fail-closed guard: refuses to run against a target where any policy-covered table already has
 * rows, unless the caller has explicitly opted out. {@code run} only ever loads into an empty
 * target - a mid-run failure's compensation ({@link IncognitoCleanUpHandler}) issues an
 * unconditional {@code DELETE FROM} every table it touched, which would destroy pre-existing data
 * that was never Incognito's to delete (a mistyped {@code --target-url} pointed at a populated
 * database, most plausibly). Checked once, before any row is written, rather than discovered only
 * if a run happens to fail - the tutorial-feedback finding this closes found the only warning on
 * this living in a documentation aside, not the tool itself.
 */
public final class NonEmptyTargetGuardStage implements PipelineStage {

    /**
     * Context attribute: when {@link Boolean#TRUE}, skips this guard entirely - the CLI's
     * {@code --force}, for a caller who has weighed the risk and wants it anyway.
     */
    public static final String ATTR_ALLOW_NON_EMPTY_TARGET = "incognito.allowNonEmptyTarget";

    /** Creates a non-empty-target guard stage. */
    public NonEmptyTargetGuardStage() {}

    @Override
    @SuppressWarnings("unchecked")
    public StageResult process(PipelineContext context) throws IncognitoException {
        if (Boolean.TRUE.equals(context.attributes().get(ATTR_ALLOW_NON_EMPTY_TARGET))) {
            return new StageResult("NonEmptyTargetGuardStage", true, 0, "skipped (--force)");
        }

        Object metaObj = context.attributes().get(SchemaDiscoveryStage.ATTR_TABLE_METADATA);
        if (!(metaObj instanceof List<?> rawMetaList)) {
            throw new IncognitoException.ConfigException(
                "SchemaDiscoveryStage must run before NonEmptyTargetGuardStage");
        }
        List<SchemaInspector.TableMetadata> metadata = (List<SchemaInspector.TableMetadata>) rawMetaList;
        AnonymisationPolicy policy = context.policy();

        List<String> nonEmpty = new ArrayList<>();
        try (Connection targetConn = context.target().getConnection()) {
            for (SchemaInspector.TableMetadata table : metadata) {
                Optional<TablePolicy> tablePolicyOpt = policy.table(table.tableName());
                if (tablePolicyOpt.isEmpty()) continue; // not loaded by this run

                try (Statement stmt = targetConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table.tableName())) {
                    if (rs.next() && rs.getLong(1) > 0) {
                        nonEmpty.add("'" + table.tableName() + "' has " + rs.getLong(1) + " row(s)");
                    }
                } catch (SQLException e) {
                    // The target table doesn't exist yet (e.g. Postgres "undefined_table", 42P01) -
                    // nothing there to protect, so this isn't this guard's concern; a missing target
                    // table surfaces its own clear failure later, at the actual load attempt. Any
                    // other failure (permissions, a dropped connection) is a genuine problem worth
                    // surfacing here rather than silently treating the table as empty.
                    if (!"42P01".equals(e.getSQLState())) {
                        throw e;
                    }
                }
            }
        } catch (SQLException e) {
            throw new IncognitoException.SchemaException("Failed to check target table row counts", e);
        }

        if (!nonEmpty.isEmpty()) {
            throw new IncognitoException.ConfigException(
                "Fail-closed: " + nonEmpty.size() + " target table(s) already have data - run only"
                    + " loads into an empty target, and a failed run deletes existing rows during"
                    + " compensation:\n  - " + String.join("\n  - ", nonEmpty)
                    + "\nPoint at an empty target, or pass --force if you accept that risk.");
        }

        return new StageResult("NonEmptyTargetGuardStage", true, metadata.size(), "target tables empty");
    }
}
