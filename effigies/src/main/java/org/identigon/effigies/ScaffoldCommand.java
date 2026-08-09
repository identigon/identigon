package org.identigon.effigies;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.JDBCType;
import java.util.List;
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

        String password = System.getenv("EFFIGIES_SOURCE_PASSWORD");
        if (password == null) {
            err.println("Error: EFFIGIES_SOURCE_PASSWORD environment variable is not set.");
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
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    static void writeScaffold(File file, List<SchemaInspector.TableMetadata> tables) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("autoInfer: false\n");
            writer.write("tables:\n");
            for (SchemaInspector.TableMetadata table : tables) {
                writer.write("  " + table.tableName() + ":\n");
                writer.write("    columns:\n");
                for (String col : table.columns()) {
                    if (table.generatedColumns().contains(col)) {
                        continue;
                    }
                    StringBuilder md = new StringBuilder();
                    Integer typeCode = table.columnTypes().get(col);
                    if (typeCode != null) {
                        try {
                            md.append("type: ").append(JDBCType.valueOf(typeCode).getName());
                        } catch (IllegalArgumentException e) {
                            md.append("type: ").append(typeCode);
                        }
                    }
                    if (table.primaryKeyColumns().contains(col)) {
                        md.append(", pk");
                    }
                    if (table.foreignKeys().containsKey(col)) {
                        md.append(", fk -> ").append(table.foreignKeys().get(col));
                    }
                    writer.write("      " + col + ":            # " + md + "\n");
                    writer.write("        role:              # TODO classify — see the role vocabulary; run fails closed until filled\n");
                }
            }
        }
    }
}
