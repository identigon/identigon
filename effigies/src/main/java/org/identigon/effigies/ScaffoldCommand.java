package org.identigon.effigies;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.DirectIdStrategy;
import org.identigon.incognito.engine.SchemaInspector;

class ScaffoldCommand {
    private static final String USAGE =
        "Usage: scaffold --source-url <url> --source-user <user> [--out <file>] [--force]";

    static int execute(String[] args, PrintStream out, PrintStream err) {
        if (CliArgs.hasHelpFlag(args)) {
            out.println(USAGE);
            return 0;
        }

        String url = null;
        String user = null;
        String outFile = "./policy.scaffold.yaml";
        boolean force = false;
        for (int i = 0; i < args.length; i++) {
            if ("--source-url".equals(args[i]) && i + 1 < args.length) {
                url = args[++i];
            } else if ("--source-user".equals(args[i]) && i + 1 < args.length) {
                user = args[++i];
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                outFile = args[++i];
            } else if ("--force".equals(args[i])) {
                force = true;
            }
        }
        if (url == null || user == null) {
            err.println(USAGE);
            return EffigiesCli.EXIT_USAGE;
        }

        String password = System.getenv("IDENTIGON_SOURCE_PASSWORD");
        if (password == null) {
            err.println("Error: IDENTIGON_SOURCE_PASSWORD environment variable is not set.");
            return EffigiesCli.EXIT_USAGE;
        }

        return run(new SimpleDataSource(url, user, password), new File(outFile), force, out, err);
    }

    /**
     * The testable core: given an already-resolved {@link DataSource} and output file, inspects the
     * schema and writes the scaffold. Split out from {@link #execute} so tests can exercise it
     * directly against a real database without needing to fake environment variables.
     */
    static int run(DataSource dataSource, File file, boolean force, PrintStream out, PrintStream err) {
        if (file.exists() && !force) {
            err.println("Error: output file already exists: " + file + " (use --force to overwrite)");
            return 1;
        }

        SchemaInspector inspector = new SchemaInspector();
        try {
            List<SchemaInspector.TableMetadata> tables = inspector.inspect(dataSource);
            writeScaffold(file, tables);
            out.println("Scaffold written to " + file);
            return 0;
        } catch (Exception e) {
            err.println("Error: " + e);
            return 1;
        }
    }

    static void writeScaffold(File file, List<SchemaInspector.TableMetadata> tables) throws IOException {
        // Explicit UTF-8, not the platform-default charset a bare FileWriter would use -- a schema
        // with non-ASCII table/column names must round-trip correctly regardless of the OS this
        // runs on (the platform default is not UTF-8 on Windows).
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            PolicyInferrer inferrer = new PolicyInferrer();
            Map<String, SchemaInspector.TableMetadata> tablesByName = new HashMap<>();
            for (SchemaInspector.TableMetadata t : tables) {
                tablesByName.put(t.tableName(), t);
            }

            writer.write("autoInfer: false\n");
            writer.write("tables:\n");
            for (SchemaInspector.TableMetadata table : tables) {
                writer.write("  " + table.tableName() + ":\n");
                writer.write("    columns:\n");
                for (String col : table.columns()) {
                    if (table.generatedColumns().contains(col)) {
                        continue;
                    }
                    writer.write("      " + col + ":            # "
                        + ColumnMetadataFormatter.format(table, col) + "\n");
                    writeRoleStub(writer, table, col, tablesByName, inferrer);
                }
            }
        }
    }

    /**
     * Writes the {@code role:} TODO stub for one column, plus - where the choice determining
     * output quality is itself known or inferable - a second stub for it ({@code
     * directIdStrategy}, {@code distinguishing}, {@code references}, {@code surrogateStrategy}):
     * never assigned, only suggested, the same "suggest, never assign" pattern as {@code role:}
     * itself. Structural facts from {@link SchemaInspector} (a column IS the primary key, or IS a
     * foreign key) take priority over {@link PolicyInferrer}'s name-based heuristics - a known
     * constraint outranks a guess.
     */
    private static void writeRoleStub(
            BufferedWriter writer, SchemaInspector.TableMetadata table, String col,
            Map<String, SchemaInspector.TableMetadata> tablesByName, PolicyInferrer inferrer) throws IOException {

        if (table.foreignKeys().containsKey(col)) {
            String parentTable = table.foreignKeys().get(col);
            writer.write("        role:              # TODO classify (Suggestion: FOREIGN_KEY -> "
                + parentTable + ", structurally discovered - not a guess)\n");
            SchemaInspector.TableMetadata parent = tablesByName.get(parentTable);
            if (parent != null && parent.primaryKeyColumns().size() == 1) {
                writer.write("        references:        # TODO if FOREIGN_KEY (Suggestion: {table: "
                    + parentTable + ", column: " + parent.primaryKeyColumns().get(0) + "})\n");
            } else {
                writer.write("        references:        # TODO if FOREIGN_KEY - target table is "
                    + parentTable + "; its column isn't determined here (composite or unknown PK)\n");
            }
            return;
        }

        if (table.primaryKeyColumns().contains(col)) {
            writer.write("        role:              # TODO classify (Suggestion: PRIMARY_KEY,"
                + " structurally discovered - not a guess)\n");
            writer.write("        surrogateStrategy: # TODO if PRIMARY_KEY (Suggestion: SEQUENTIAL_LONG)\n");
            return;
        }

        Optional<PolicyInferrer.InferredRole> inferred = inferrer.inferRole(col);
        if (inferred.isEmpty()) {
            writer.write("        role:              # TODO classify - see docs/spec/incognito.md"
                + " §4.1 for the full ColumnRole vocabulary; run fails closed until filled\n");
            return;
        }

        PolicyInferrer.InferredRole role = inferred.get();
        writer.write("        role:              # TODO classify (Suggestion: " + role.role()
            + " based on " + role.heuristic() + ")\n");
        if (role.role() == ColumnRole.DIRECT_ID) {
            Optional<DirectIdStrategy> strategy = inferrer.suggestedDirectIdStrategy(role.heuristic());
            if (strategy.isPresent()) {
                writer.write("        directIdStrategy:  # TODO if DIRECT_ID (Suggestion: " + strategy.get() + ")\n");
            } else {
                writer.write("        directIdStrategy:  # TODO if DIRECT_ID - see DirectIdStrategy's"
                    + " Javadoc for the right typed generator\n");
            }
        } else if (role.role() == ColumnRole.SENSITIVE) {
            writer.write("        distinguishing:    # TODO if SENSITIVE (true|false - does this alone identify someone?)\n");
        }
    }
}
