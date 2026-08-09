package org.identigon.incognito.core;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.identigon.incognito.api.AnonymisationReport;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.PipelineStage;

/**
 * Serialises the {@link AnonymisationReport} to a DPIA artefact. Three formats are offered
 * (SPEC §7 / PLAN Phase 6): machine-readable {@link #emitJson JSON}, presentation-ready
 * {@link #emitHtml HTML}, and human-diffable {@link #emitMarkdown Markdown}. All are
 * zero-dependency (no JSON/HTML library) so the core stays dependency-lean.
 *
 * <p>This is <b>opt-in</b>: the pipeline always builds the {@link AnonymisationReport} (available
 * from {@code PipelineResult.report()}), but it never writes a file automatically. A caller that
 * wants a persisted DPIA artefact invokes one of these methods with that report — e.g.
 * {@code DpiaArtefactEmitter.emitJson(result.report(), path)}.
 */
public final class DpiaArtefactEmitter {

    private DpiaArtefactEmitter() {}

    /** Static HTML scaffolding: document head, CSS, and title, up to the opening {@code <body>}. */
    private static final String HTML_HEAD = """
        <!doctype html>
        <html lang="en"><head><meta charset="utf-8">
        <title>Incognito Anonymisation Report (DPIA Artifact)</title>
        <style>body{font-family:system-ui,sans-serif;margin:2rem;max-width:70rem}table{border-collapse:collapse;margin:.5rem 0 1.5rem}th,td{border:1px solid #ccc;padding:.3rem .6rem;text-align:left}th{background:#f2f2f2}.fail{color:#b00}.ok{color:#080}caption{font-weight:bold;text-align:left;padding:.3rem 0}</style>
        </head><body>
        <h1>Incognito Anonymisation Report (DPIA Artifact)</h1>
        """;

    // --- JSON ---------------------------------------------------------------------------------

    /**
     * Emits the report as a JSON document — the machine-readable DPIA artifact for ingestion into a
     * governance system.
     * @param report the anonymisation report to emit
     * @param outputPath the path where the JSON file will be written
     * @throws IncognitoException if writing fails
     */
    public static void emitJson(AnonymisationReport report, Path outputPath) throws IncognitoException {
        JsonWriter jw = new JsonWriter();
        jw.beginObject();
        jw.field("saltMode", report.saltMode() == null ? null : report.saltMode().name());

        jw.name("survivalFindings").beginArray();
        for (AnonymisationReport.SurvivalFinding sf : report.survivalFindings()) {
            jw.beginObject()
                .field("table", sf.table())
                .field("column", sf.column())
                .field("sampledDistinct", sf.sampledDistinct())
                .field("survived", sf.survived())
                .field("hardFailure", sf.hardFailure())
                .endObject();
        }
        jw.endArray();

        jw.name("lintFindings").beginArray();
        for (AnonymisationReport.LintFinding lf : report.lintFindings()) {
            jw.beginObject()
                .field("table", lf.table())
                .field("column", lf.column())
                .field("distinctValues", lf.distinctValues())
                .field("threshold", lf.threshold())
                .endObject();
        }
        jw.endArray();

        jw.name("structuralFindings").beginArray();
        for (AnonymisationReport.StructuralUniquenessFinding suf : report.structuralFindings()) {
            jw.beginObject()
                .field("parentTable", suf.parentTable())
                .field("childTable", suf.childTable());
            jw.name("childColumns").beginArray();
            for (String col : suf.childColumns()) jw.value(col);
            jw.endArray();
            jw.field("distinctParents", suf.distinctParents())
                .field("maxChildCount", suf.maxChildCount())
                .field("uniqueFingerprintCount", suf.uniqueFingerprintCount())
                .field("rareFingerprintCount", suf.rareFingerprintCount())
                .field("k", suf.k())
                .endObject();
        }
        jw.endArray();

        jw.name("stages").beginArray();
        for (PipelineStage.StageResult sr : report.stageResults()) {
            jw.beginObject()
                .field("stage", sr.stageName())
                .field("success", sr.success())
                .field("processed", sr.processedCount())
                .field("message", sr.message())
                .endObject();
        }
        jw.endArray();

        jw.name("tables").beginArray();
        for (AnonymisationReport.TableReport tr : report.tables()) {
            jw.beginObject()
                .field("table", tr.table())
                .field("rowsProcessed", tr.rowsProcessed())
                .field("fictionalityVerified", tr.fictionalityVerified());
            jw.name("columns").beginArray();
            for (AnonymisationReport.ColumnAction ca : tr.columns()) {
                jw.beginObject()
                    .field("column", ca.column())
                    .field("role", ca.role().name())
                    .field("transformation", ca.transformation())
                    .endObject();
            }
            jw.endArray();
            jw.name("passthroughFlags").beginArray();
            for (AnonymisationReport.PassthroughFlag pf : tr.passthroughFlags()) {
                jw.beginObject()
                    .field("column", pf.column())
                    .field("jdbcType", pf.jdbcType())
                    .field("reason", pf.reason())
                    .endObject();
            }
            jw.endArray();
            jw.name("inferSuggestions").beginArray();
            for (AnonymisationReport.InferSuggestion is : tr.inferSuggestions()) {
                jw.beginObject()
                    .field("column", is.column())
                    .field("suggestedRole", is.suggestedRole().name())
                    .field("matchedHeuristic", is.matchedHeuristic())
                    .endObject();
            }
            jw.endArray();
            jw.endObject();
        }
        jw.endArray();

        jw.endObject();

        try {
            Files.writeString(outputPath, jw.toJson());
        } catch (IOException e) {
            throw new IncognitoException("Failed to write DPIA JSON report to " + outputPath, e);
        }
    }

    // --- HTML ---------------------------------------------------------------------------------

    /**
     * Emits the report as a self-contained HTML document — the presentation-ready DPIA artifact.
     * @param report the anonymisation report to emit
     * @param outputPath the path where the HTML file will be written
     * @throws IncognitoException if writing fails
     */
    public static void emitHtml(AnonymisationReport report, Path outputPath) throws IncognitoException {
        try (Writer w = Files.newBufferedWriter(outputPath)) {
            w.write(HTML_HEAD);

            w.write("<p><b>Salt mode:</b> <code>"
                + htmlEscape(report.saltMode() == null ? "unknown" : report.saltMode().name())
                + "</code> &mdash; " + saltModeNote(report.saltMode()) + "</p>\n");

            w.write("<h2>Residual re-identification risk</h2>\n");
            if (report.survivalFindings().isEmpty() && report.lintFindings().isEmpty()
                    && report.structuralFindings().isEmpty()) {
                w.write("<p class=\"ok\">No source-value survival, misdeclaration, or structural"
                    + " findings.</p>\n");
            }
            if (!report.survivalFindings().isEmpty()) {
                w.write("<table><caption>Source-value survival (SPEC &sect;4.3 &mdash; singling-out evidence)"
                    + "</caption><tr><th>Table</th><th>Column</th><th>Sampled</th><th>Survived</th>"
                    + "<th>Verdict</th></tr>\n");
                for (AnonymisationReport.SurvivalFinding sf : report.survivalFindings()) {
                    w.write("<tr><td>" + htmlEscape(sf.table()) + "</td><td>" + htmlEscape(sf.column())
                        + "</td><td>" + sf.sampledDistinct() + "</td><td>" + sf.survived()
                        + "</td><td class=\"" + (sf.hardFailure() ? "fail\">LEAK" : "ok\">coincidental")
                        + "</td></tr>\n");
                }
                w.write("</table>\n");
            }
            if (!report.lintFindings().isEmpty()) {
                w.write("<table><caption>Misdeclaration lint (SPEC &sect;4.1 &mdash; distinguishing:false"
                    + " kept opaque)</caption><tr><th>Table</th><th>Column</th><th>Distinct values</th>"
                    + "<th>Threshold</th></tr>\n");
                for (AnonymisationReport.LintFinding lf : report.lintFindings()) {
                    w.write("<tr><td>" + htmlEscape(lf.table()) + "</td><td>" + htmlEscape(lf.column())
                        + "</td><td>" + lf.distinctValues() + "</td><td>" + lf.threshold() + "</td></tr>\n");
                }
                w.write("</table>\n");
            }
            if (!report.structuralFindings().isEmpty()) {
                w.write("<table><caption>Structural re-identification risk (SPEC &sect;2.4 &mdash;"
                    + " relational fingerprints)</caption><tr><th>Parent table</th><th>Child table</th>"
                    + "<th>FK column(s)</th><th>Distinct parents</th><th>Max child count</th>"
                    + "<th>Unique fingerprints</th><th>Rare fingerprints (&lt;k)</th><th>k</th></tr>\n");
                for (AnonymisationReport.StructuralUniquenessFinding suf : report.structuralFindings()) {
                    w.write("<tr><td>" + htmlEscape(suf.parentTable()) + "</td><td>"
                        + htmlEscape(suf.childTable()) + "</td><td>" + htmlEscape(String.join(", ", suf.childColumns()))
                        + "</td><td>" + suf.distinctParents()
                        + "</td><td>" + suf.maxChildCount() + "</td><td>" + suf.uniqueFingerprintCount()
                        + "</td><td>" + suf.rareFingerprintCount() + "</td><td>" + suf.k() + "</td></tr>\n");
                }
                w.write("</table>\n");
            }

            w.write("<h2>Pipeline stages</h2>\n<table><tr><th>Stage</th><th>Result</th>"
                + "<th>Processed</th><th>Message</th></tr>\n");
            for (PipelineStage.StageResult sr : report.stageResults()) {
                w.write("<tr><td>" + htmlEscape(sr.stageName()) + "</td><td class=\""
                    + (sr.success() ? "ok\">OK" : "fail\">FAILED") + "</td><td>" + sr.processedCount()
                    + "</td><td>" + htmlEscape(sr.message()) + "</td></tr>\n");
            }
            w.write("</table>\n<h2>Tables</h2>\n");
            if (report.tables().isEmpty()) w.write("<p>No tables processed.</p>\n");

            for (AnonymisationReport.TableReport tr : report.tables()) {
                w.write("<h3><code>" + htmlEscape(tr.table()) + "</code></h3>\n"
                    + "<p>Rows processed: " + tr.rowsProcessed() + " &middot; Fictionality verified: "
                    + tr.fictionalityVerified() + "</p>\n");
                w.write("<table><caption>Column actions</caption><tr><th>Column</th><th>Role</th>"
                    + "<th>Transformation</th></tr>\n");
                for (AnonymisationReport.ColumnAction ca : tr.columns()) {
                    w.write("<tr><td>" + htmlEscape(ca.column()) + "</td><td>" + ca.role()
                        + "</td><td>" + htmlEscape(ca.transformation()) + "</td></tr>\n");
                }
                w.write("</table>\n");
                if (!tr.passthroughFlags().isEmpty()) {
                    w.write("<table><caption>Passthrough flags (opaque types kept as-is)</caption>"
                        + "<tr><th>Column</th><th>JDBC type</th><th>Reason</th></tr>\n");
                    for (AnonymisationReport.PassthroughFlag pf : tr.passthroughFlags()) {
                        w.write("<tr><td>" + htmlEscape(pf.column()) + "</td><td>" + htmlEscape(pf.jdbcType())
                            + "</td><td>" + htmlEscape(pf.reason()) + "</td></tr>\n");
                    }
                    w.write("</table>\n");
                }
                if (!tr.inferSuggestions().isEmpty()) {
                    w.write("<table><caption>Inference suggestions (surfaced only, never auto-applied)"
                        + "</caption><tr><th>Column</th><th>Suggested role</th><th>Heuristic</th></tr>\n");
                    for (AnonymisationReport.InferSuggestion is : tr.inferSuggestions()) {
                        w.write("<tr><td>" + htmlEscape(is.column()) + "</td><td>" + is.suggestedRole()
                            + "</td><td>" + htmlEscape(is.matchedHeuristic()) + "</td></tr>\n");
                    }
                    w.write("</table>\n");
                }
            }
            w.write("</body></html>\n");
        } catch (IOException e) {
            throw new IncognitoException("Failed to write DPIA HTML report to " + outputPath, e);
        }
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * A one-line plain-language gloss of a salt mode's anonymity implication (SPEC §5.1/§5.2), for
     * the DPIA reader who should not have to know the API to understand the run's re-identification
     * posture. Contains no markup, so it is safe in both HTML and Markdown output.
     */
    private static String saltModeNote(org.identigon.incognito.api.SaltMode mode) {
        if (mode == null) return "salt mode not recorded";
        return switch (mode) {
            case EPHEMERAL -> "fresh per-run salt, destroyed on completion; output unlinkable and irreversible";
            case PERSISTENT -> "fixed reused salt; output is linkable across runs and forfeits irreversibility (SPEC §5.2)";
            case REPRODUCIBLE -> "fixed salt + seed for reproducible fixtures; linkable and not for production clones (SPEC §5.2)";
        };
    }

    /**
     * Emits the report as a Markdown file.
     * @param report the anonymisation report to emit
     * @param outputPath the path where the Markdown file will be written
     * @throws IncognitoException if writing fails
     */
    public static void emitMarkdown(AnonymisationReport report, Path outputPath) throws IncognitoException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            writer.write("# Incognito Anonymisation Report (DPIA Artifact)\n\n");

            writer.write(String.format("**Salt mode:** `%s` — %s%n%n",
                report.saltMode() == null ? "unknown" : report.saltMode().name(),
                saltModeNote(report.saltMode())));

            writer.write("## Residual Re-identification Risk\n\n");
            if (report.survivalFindings().isEmpty() && report.lintFindings().isEmpty()
                    && report.structuralFindings().isEmpty()) {
                writer.write("No source-value survival, misdeclaration, or structural findings.\n\n");
            }
            if (!report.survivalFindings().isEmpty()) {
                writer.write("""
                    ### Source-Value Survival (SPEC §4.3 — singling-out evidence)

                    | Table | Column | Sampled | Survived | Verdict |
                    |---|---|---|---|---|
                    """);
                for (AnonymisationReport.SurvivalFinding sf : report.survivalFindings()) {
                    writer.write(String.format("| %s | %s | %d | %d | %s |%n",
                        sf.table(), sf.column(), sf.sampledDistinct(), sf.survived(),
                        sf.hardFailure() ? "LEAK" : "coincidental"));
                }
                writer.write("\n");
            }
            if (!report.lintFindings().isEmpty()) {
                writer.write("""
                    ### Misdeclaration Lint (SPEC §4.1 — distinguishing:false kept opaque)

                    | Table | Column | Distinct Values | Threshold |
                    |---|---|---|---|
                    """);
                for (AnonymisationReport.LintFinding lf : report.lintFindings()) {
                    writer.write(String.format("| %s | %s | %d | %d |%n",
                        lf.table(), lf.column(), lf.distinctValues(), lf.threshold()));
                }
                writer.write("\n");
            }
            if (!report.structuralFindings().isEmpty()) {
                writer.write("""
                    ### Structural Re-identification Risk (SPEC §2.4 — relational fingerprints)

                    | Parent Table | Child Table | FK Column(s) | Distinct Parents | Max Child Count | Unique Fingerprints | Rare Fingerprints (<k) | k |
                    |---|---|---|---|---|---|---|---|
                    """);
                for (AnonymisationReport.StructuralUniquenessFinding suf : report.structuralFindings()) {
                    writer.write(String.format("| %s | %s | %s | %d | %d | %d | %d | %d |%n",
                        suf.parentTable(), suf.childTable(), String.join(", ", suf.childColumns()),
                        suf.distinctParents(), suf.maxChildCount(),
                        suf.uniqueFingerprintCount(), suf.rareFingerprintCount(), suf.k()));
                }
                writer.write("\n");
            }

            writer.write("## Pipeline Stages Summary\n\n");
            for (PipelineStage.StageResult sr : report.stageResults()) {
                writer.write(String.format("- **%s**: %s (Processed: %d, Success: %b)\n",
                    sr.stageName(), sr.message(), sr.processedCount(), sr.success()));
            }
            writer.write("\n");

            writer.write("## Table Reports\n\n");
            if (report.tables().isEmpty()) {
                writer.write("No tables processed.\n");
            }

            for (AnonymisationReport.TableReport tr : report.tables()) {
                writer.write(String.format("### Table: `%s`\n\n", tr.table()));
                writer.write(String.format("- Rows Processed: %d\n", tr.rowsProcessed()));
                writer.write(String.format("- Fictionality Verified: %b\n\n", tr.fictionalityVerified()));

                writer.write("""
                    #### Column Actions

                    | Column | Role | Transformation |
                    |---|---|---|
                    """);
                for (AnonymisationReport.ColumnAction ca : tr.columns()) {
                    writer.write(String.format("| %s | %s | %s |\n", ca.column(), ca.role(), ca.transformation()));
                }
                writer.write("\n");

                if (!tr.inferSuggestions().isEmpty()) {
                    writer.write("""
                        #### Inference Suggestions (Not Auto-Applied)

                        | Column | Suggested Role | Heuristic |
                        |---|---|---|
                        """);
                    for (AnonymisationReport.InferSuggestion is : tr.inferSuggestions()) {
                        writer.write(String.format("| %s | %s | %s |\n", is.column(), is.suggestedRole(), is.matchedHeuristic()));
                    }
                    writer.write("\n");
                }

                if (!tr.passthroughFlags().isEmpty()) {
                    writer.write("""
                        #### Passthrough Flags (Opaque Data Types)

                        | Column | JDBC Type | Reason |
                        |---|---|---|
                        """);
                    for (AnonymisationReport.PassthroughFlag pf : tr.passthroughFlags()) {
                        writer.write(String.format("| %s | %s | %s |\n", pf.column(), pf.jdbcType(), pf.reason()));
                    }
                    writer.write("\n");
                }
            }

        } catch (IOException e) {
            throw new IncognitoException("Failed to write DPIA report to " + outputPath, e);
        }
    }
}
