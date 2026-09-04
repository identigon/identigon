package org.identigon.effigies;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.sql.DataSource;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.core.SchemaDiscoveryStage;
import org.identigon.incognito.engine.SchemaInspector;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.YamlPolicyParser;

/**
 * Checks a policy against a source schema with no target connection and no data movement - {@code
 * SchemaDiscoveryStage}'s fail-closed diagnostics (the tool's best, per SPEC §7.2) reachable
 * without committing to a full {@code run}. Useful while authoring, and as a CI pre-flight check
 * for a policy going stale after a schema migration.
 */
class ValidateCommand {
  private static final String USAGE =
      "Usage: validate --source-url <url> --source-user <user> [--policy <file>]";

  static int execute(String[] args, PrintStream out, PrintStream err) {
    if (CliArgs.hasHelpFlag(args)) {
      out.println(USAGE);
      return 0;
    }

    String policyFile = "./policy.yaml";
    String url = null;
    String user = null;
    for (int i = 0; i < args.length; i++) {
      if ("--source-url".equals(args[i]) && i + 1 < args.length) {
        url = args[++i];
      } else if ("--source-user".equals(args[i]) && i + 1 < args.length) {
        user = args[++i];
      } else if ("--policy".equals(args[i]) && i + 1 < args.length) {
        policyFile = args[++i];
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

    Path policyPath = Paths.get(policyFile);
    if (!Files.exists(policyPath)) {
      err.println("Error: policy file not found: " + policyFile);
      return 1;
    }

    return run(new SimpleDataSource(url, user, password), policyPath, out, err);
  }

  /**
   * The testable core: given an already-resolved {@link DataSource} and policy file, inspects the
   * source schema and validates the policy against it - no target connection, no data read. Split
   * out from {@link #execute} so tests can exercise it directly without needing to fake environment
   * variables.
   */
  static int run(DataSource sourceDs, Path policyPath, PrintStream out, PrintStream err) {
    try {
      AnonymisationPolicy policy = new YamlPolicyParser().parse(policyPath);
      List<SchemaInspector.TableMetadata> tables = new SchemaInspector().inspect(sourceDs);
      // Return value is per-table auto-infer suggestions, always empty on success (SPEC §7.2:
      // an unclassified column always fails closed) - nothing to do with it here but let the
      // ConfigException through on failure.
      new SchemaDiscoveryStage().validate(tables, policy);

      out.println("Policy is valid against " + tables.size() + " discovered table(s).");
      return 0;
    } catch (IncognitoException.ConfigException e) {
      err.println("Error: " + e.getMessage() + CliErrors.causesOnly(e));
      return 1;
    } catch (Exception e) {
      err.println("Error: " + CliErrors.causeChain(e));
      return 1;
    }
  }
}
