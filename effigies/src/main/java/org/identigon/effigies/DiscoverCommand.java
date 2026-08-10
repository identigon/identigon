package org.identigon.effigies;

import java.io.PrintStream;
import java.util.List;
import javax.sql.DataSource;
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

        String password = System.getenv("IDENTIGON_SOURCE_PASSWORD");
        if (password == null) {
            err.println("Error: IDENTIGON_SOURCE_PASSWORD environment variable is not set.");
            return EffigiesCli.EXIT_USAGE;
        }

        return run(new SimpleDataSource(url, user, password), out, err);
    }

    /**
     * The testable core: given an already-resolved {@link DataSource}, inspects and prints its
     * schema. Split out from {@link #execute} so tests can exercise it directly against a real
     * database without needing to fake environment variables.
     */
    static int run(DataSource dataSource, PrintStream out, PrintStream err) {
        SchemaInspector inspector = new SchemaInspector();
        try {
            List<SchemaInspector.TableMetadata> tables = inspector.inspect(dataSource);
            for (SchemaInspector.TableMetadata table : tables) {
                out.println("Table: " + table.tableName());
                for (String col : table.columns()) {
                    out.println("  " + col + " (" + ColumnMetadataFormatter.format(table, col) + ")");
                }
                out.println();
            }
            return 0;
        } catch (Exception e) {
            err.println("Error: " + e);
            return 1;
        }
    }
}
