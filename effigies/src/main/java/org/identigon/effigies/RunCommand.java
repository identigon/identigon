package org.identigon.effigies;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.PipelineResult;
import org.identigon.incognito.core.DpiaArtefactEmitter;
import org.identigon.incognito.policy.YamlPolicyParser;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class RunCommand {
    static int execute(String[] args, PrintStream out, PrintStream err) {
        String policyFile = "./policy.yaml";
        String srcUrl = null;
        String srcUser = null;
        String tgtUrl = null;
        String tgtUser = null;

        for (int i = 0; i < args.length; i++) {
            if ("--policy".equals(args[i]) && i + 1 < args.length) {
                policyFile = args[++i];
            } else if ("--source-url".equals(args[i]) && i + 1 < args.length) {
                srcUrl = args[++i];
            } else if ("--source-user".equals(args[i]) && i + 1 < args.length) {
                srcUser = args[++i];
            } else if ("--target-url".equals(args[i]) && i + 1 < args.length) {
                tgtUrl = args[++i];
            } else if ("--target-user".equals(args[i]) && i + 1 < args.length) {
                tgtUser = args[++i];
            }
        }

        if (srcUrl == null || srcUser == null || tgtUrl == null || tgtUser == null) {
            err.println("Usage: run --source-url <url> --source-user <user> --target-url <url> --target-user <user> [--policy <file>]");
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

        // Parse salt mode directly from YAML since lib-incognito's YamlPolicyParser doesn't handle it
        String saltMode = "ephemeral";
        try (InputStream is = Files.newInputStream(policyPath)) {
            Yaml yaml = new Yaml(new SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            Map<String, Object> root = yaml.load(is);
            if (root != null && root.containsKey("saltMode")) {
                saltMode = String.valueOf(root.get("saltMode")).toLowerCase();
            }
        } catch (Exception e) {
            err.println("Warning: failed to peek saltMode from YAML, defaulting to ephemeral: " + e.getMessage());
        }

        try {
            SimpleDataSource sourceDs = new SimpleDataSource(srcUrl, srcUser, srcPass);
            SimpleDataSource targetDs = new SimpleDataSource(tgtUrl, tgtUser, tgtPass);

            IncognitoPipeline.Builder builder = IncognitoPipeline.builder()
                .source(sourceDs)
                .target(targetDs)
                .policy(new YamlPolicyParser().parse(policyPath));

            if ("persistent".equals(saltMode) || "reproducible".equals(saltMode)) {
                String saltStr = System.getenv("IDENTIGON_SALT");
                if (saltStr == null) {
                    err.println("Error: IDENTIGON_SALT environment variable is required for saltMode=" + saltMode);
                    return 1;
                }
                byte[] salt = saltStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if ("reproducible".equals(saltMode)) {
                    String seedStr = System.getenv("IDENTIGON_SEED");
                    long seed = seedStr != null ? Long.parseLong(seedStr) : 0L;
                    builder.reproducible(salt, seed);
                } else {
                    builder.persistentSalt(salt);
                }
            } else {
                builder.ephemeralSalt();
            }

            out.println("Starting anonymisation pipeline (Salt Mode: " + saltMode + ")...");
            PipelineResult result = builder.build().execute();

            out.println("Pipeline completed successfully.");
            out.println("Tables transformed: " + result.tablesProcessed());
            out.println("Rows processed: " + result.totalRowsLoaded());

            // The engine's own emitter produces the accountability artefact — salt-mode disclosure,
            // survival/lint/structural findings, illustrative sample rows. Dumping the raw
            // AnonymisationReport record graph would throw all of that away, so delegate.
            Path dpiaHtml = Paths.get("./dpia-report.html");
            Path dpiaJson = Paths.get("./dpia-report.json");
            Path dpiaMarkdown = Paths.get("./dpia-report.md");
            try {
                DpiaArtefactEmitter.emitHtml(result.report(), dpiaHtml);
                DpiaArtefactEmitter.emitJson(result.report(), dpiaJson);
                DpiaArtefactEmitter.emitMarkdown(result.report(), dpiaMarkdown);
                out.println("DPIA artefact written to " + dpiaHtml + ", " + dpiaJson + " and " + dpiaMarkdown);
            } catch (Exception e) {
                out.println("Failed to write DPIA artefact: " + e.getMessage());
            }

            return 0;
        } catch (Exception e) {
            err.println("Error executing pipeline: " + e.getMessage());
            return 1;
        }
    }
}
