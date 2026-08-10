package org.identigon.effigies;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.identigon.incognito.engine.SchemaInspector;

class ScaffoldCommand {
    static int execute(String[] args, PrintStream out, PrintStream err) {
        String url = null;
        String user = null;
        String outFile = "./policy.scaffold.yaml";
        for (int i = 0; i < args.length; i++) {
            if ("--source-url".equals(args[i]) && i + 1 < args.length) {
                url = args[++i];
            } else if ("--source-user".equals(args[i]) && i + 1 < args.length) {
                user = args[++i];
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                outFile = args[++i];
            }
        }
        if (url == null || user == null) {
            err.println("Usage: scaffold --source-url <url> --source-user <user> [--out <file>]");
            return EffigiesCli.EXIT_USAGE;
        }

        String password = System.getenv("IDENTIGON_SOURCE_PASSWORD");
        if (password == null) {
            err.println("Error: IDENTIGON_SOURCE_PASSWORD environment variable is not set.");
            return EffigiesCli.EXIT_USAGE;
        }

        File file = new File(outFile);
        if (file.exists()) {
            err.println("Error: output file already exists: " + outFile);
            return 1;
        }

        SimpleDataSource dataSource = new SimpleDataSource(url, user, password);
        SchemaInspector inspector = new SchemaInspector();
        try {
            List<SchemaInspector.TableMetadata> tables = inspector.inspect(dataSource);
            writeScaffold(file, tables);
            out.println("Scaffold written to " + outFile);
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

                    Optional<PolicyInferrer.InferredRole> inferred = inferrer.inferRole(col);
                    if (inferred.isPresent()) {
                        writer.write("        role:              # TODO classify (Suggestion: " + inferred.get().role() + " based on " + inferred.get().heuristic() + ")\n");
                    } else {
                        writer.write("        role:              # TODO classify — see the role vocabulary; run fails closed until filled\n");
                    }
                }
            }
        }
    }
}
