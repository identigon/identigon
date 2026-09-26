package org.identigon.effigies;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.PipelineResult;
import org.identigon.incognito.core.DpiaArtefactEmitter;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.YamlPolicyParser;

class RunCommand {
  private static final String USAGE =
      "Usage: run --source-url <url> --source-user <user> "
          + "--target-url <url> --target-user <user> [--policy <file>] [--force]";

  static int execute(String[] args, PrintStream out, PrintStream err) {
    if (CliArgs.hasHelpFlag(args)) {
      out.println(USAGE);
      return 0;
    }

    CliArgs.Parsed parsed;
    try {
      parsed =
          CliArgs.parse(
              args,
              Set.of("--source-url", "--source-user", "--target-url", "--target-user", "--policy"),
              Set.of("--force"));
    } catch (CliArgs.UsageException e) {
      err.println("Error: " + e.getMessage());
      err.println(USAGE);
      return EffigiesCli.EXIT_USAGE;
    }
    String srcUrl = parsed.value("--source-url").orElse(null);
    String srcUser = parsed.value("--source-user").orElse(null);
    String tgtUrl = parsed.value("--target-url").orElse(null);
    String tgtUser = parsed.value("--target-user").orElse(null);
    String policyFile = parsed.value("--policy").orElse("./policy.yaml");
    boolean force = parsed.has("--force");

    if (srcUrl == null || srcUser == null || tgtUrl == null || tgtUser == null) {
      err.println(USAGE);
      return EffigiesCli.EXIT_USAGE;
    }

    String srcPass = System.getenv("IDENTIGON_SOURCE_PASSWORD");
    if (srcPass == null) {
      err.println("Error: IDENTIGON_SOURCE_PASSWORD environment variable is not set.");
      return EffigiesCli.EXIT_USAGE;
    }

    String tgtPass = System.getenv("IDENTIGON_TARGET_PASSWORD");
    if (tgtPass == null) {
      err.println("Error: IDENTIGON_TARGET_PASSWORD environment variable is not set.");
      return EffigiesCli.EXIT_USAGE;
    }

    Path policyPath = Paths.get(policyFile);
    if (!Files.exists(policyPath)) {
      err.println("Error: policy file not found: " + policyFile);
      return 1;
    }

    // Parsed in full here, not just to read saltMode, so a malformed or schema-invalid policy
    // fails before either database connection is opened. run() parses it again through the same
    // YamlPolicyParser, so the two parses cannot disagree.
    String saltMode;
    try {
      AnonymisationPolicy policy = new YamlPolicyParser().parse(policyPath);
      saltMode = policy.saltMode().name().toLowerCase(Locale.ROOT);
    } catch (Exception e) {
      err.println("Error: failed to parse policy file: " + CliErrors.causeChain(e));
      return 1;
    }

    byte[] salt = null;
    Long seed = null;
    if ("persistent".equals(saltMode) || "reproducible".equals(saltMode)) {
      String saltStr = System.getenv("IDENTIGON_SALT");
      if (saltStr == null) {
        err.println(
            "Error: IDENTIGON_SALT environment variable is required for saltMode=" + saltMode);
        return 1;
      }
      salt = saltStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      if ("reproducible".equals(saltMode)) {
        String seedStr = System.getenv("IDENTIGON_SEED");
        if (seedStr != null) {
          try {
            seed = Long.parseLong(seedStr);
          } catch (NumberFormatException e) {
            err.println("Error: IDENTIGON_SEED must be a valid long, got: " + seedStr);
            return 1;
          }
        } else {
          seed = 0L;
        }
      }
    }

    SimpleDataSource sourceDs = new SimpleDataSource(srcUrl, srcUser, srcPass);
    SimpleDataSource targetDs = new SimpleDataSource(tgtUrl, tgtUser, tgtPass);
    return run(sourceDs, targetDs, policyPath, saltMode, salt, seed, force, out, err);
  }

  /**
   * The testable core: given already-resolved connections, a policy file, and salt configuration,
   * builds and executes the pipeline and emits the DPIA artefacts. Split out from {@link #execute}
   * so tests can exercise it directly against real databases without needing to fake environment
   * variables.
   */
  static int run(
      DataSource sourceDs,
      DataSource targetDs,
      Path policyPath,
      String saltMode,
      byte[] salt,
      Long seed,
      boolean force,
      PrintStream out,
      PrintStream err) {
    return run(
        sourceDs, targetDs, policyPath, saltMode, salt, seed, force, Paths.get("."), out, err);
  }

  /**
   * As {@link #run(DataSource, DataSource, Path, String, byte[], Long, boolean, PrintStream,
   * PrintStream)}, writing the DPIA artefacts into {@code reportDir} instead of the working
   * directory.
   */
  static int run(
      DataSource sourceDs,
      DataSource targetDs,
      Path policyPath,
      String saltMode,
      byte[] salt,
      Long seed,
      boolean force,
      Path reportDir,
      PrintStream out,
      PrintStream err) {
    // AlterEgo's builder enforces this same minimum, but only once construction reaches it -
    // deep inside IncognitoPipeline.Builder#build(), after both connections have already been
    // opened. Catching a too-short salt here, before either DataSource is touched, gives the
    // same fail-fast treatment the missing-salt check (in execute(), before this method is even
    // called) already gets.
    if (("persistent".equals(saltMode) || "reproducible".equals(saltMode))
        && salt != null
        && salt.length < IncognitoPipeline.MIN_SALT_BYTES) {
      err.println(
          "Error: IDENTIGON_SALT must be at least "
              + IncognitoPipeline.MIN_SALT_BYTES
              + " bytes, got: "
              + salt.length
              + " bytes");
      return 1;
    }
    try {
      IncognitoPipeline.Builder builder =
          IncognitoPipeline.builder()
              .source(sourceDs)
              .target(targetDs)
              .policy(new YamlPolicyParser().parse(policyPath));

      if (force) {
        builder.allowNonEmptyTarget();
      }

      if ("persistent".equals(saltMode)) {
        builder.persistentSalt(salt);
      } else if ("reproducible".equals(saltMode)) {
        builder.reproducible(salt, seed != null ? seed : 0L);
      } else {
        builder.ephemeralSalt();
      }

      out.println("Starting anonymisation pipeline (Salt Mode: " + saltMode + ")...");
      PipelineResult result = builder.build().execute();

      out.println("Pipeline completed successfully.");
      out.println("Tables transformed: " + result.tablesProcessed());
      out.println("Rows processed: " + result.totalRowsLoaded());

      // The engine's own emitter produces the accountability artefact - salt-mode disclosure,
      // survival/lint/structural findings, illustrative sample rows. Dumping the raw
      // AnonymisationReport record graph would throw all of that away, so delegate.
      Path dpiaHtml = reportDir.resolve("dpia-report.html");
      Path dpiaJson = reportDir.resolve("dpia-report.json");
      Path dpiaMarkdown = reportDir.resolve("dpia-report.md");
      try {
        DpiaArtefactEmitter.emitHtml(result.report(), dpiaHtml);
        DpiaArtefactEmitter.emitJson(result.report(), dpiaJson);
        DpiaArtefactEmitter.emitMarkdown(result.report(), dpiaMarkdown);
      } catch (Exception e) {
        // The clone exists, but without its accountability record the run is not a success.
        err.println(
            "Error: the clone completed but the DPIA artefact could not be written: "
                + CliErrors.causeChain(e));
        return 1;
      }
      out.println(
          "DPIA artefact written to " + dpiaHtml + ", " + dpiaJson + " and " + dpiaMarkdown);
      return 0;
    } catch (Exception e) {
      err.println("Error executing pipeline: " + CliErrors.causeChain(e));
      return 1;
    }
  }
}
