package org.identigon.effigies;

import java.io.PrintStream;
import java.sql.JDBCType;
import java.util.List;
import org.identigon.incognito.engine.SchemaInspector;

class DiscoverCommand {
    static int execute(String[] args, PrintStream out, PrintStream err) {
        String url = null;
        String user = null;
        for (int i = 0; i < args.length; i++) {
            if ("--source-url".equals(args[i]) && i + 1 < args.length) {
                url = args[++i];
            } else if ("--source-user".equals(args[i]) && i + 1 < args.length) {
                user = args[++i];
            }
        }
        if (url == null || user == null) {
            err.println("Usage: discover --source-url <url> --source-user <user>");
            return EffigiesCli.EXIT_USAGE;
        }

        String password = System.getenv("EFFIGIES_SOURCE_PASSWORD");
        if (password == null) {
            err.println("Error: EFFIGIES_SOURCE_PASSWORD environment variable is not set.");
            return EffigiesCli.EXIT_USAGE;
        }

        SimpleDataSource dataSource = new SimpleDataSource(url, user, password);
        SchemaInspector inspector = new SchemaInspector();
        try {
            List<SchemaInspector.TableMetadata> tables = inspector.inspect(dataSource);
            for (SchemaInspector.TableMetadata table : tables) {
                out.println("Table: " + table.tableName());
                for (String col : table.columns()) {
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
                    out.println("  " + col + " (" + md + ")");
                }
                out.println();
            }
            return 0;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
