package org.identigon.incognito.policy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.DirectIdStrategy;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.QuasiIdStrategy;
import org.identigon.incognito.api.RedactionStrategy;
import org.identigon.incognito.api.SurrogateStrategy;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses declarative YAML configuration files into AnonymisationPolicy instances.
 */
public class YamlPolicyParser {

    // Every key this parser reads at each nesting level, plus RETIRED_ROOT_KEYS below - anything
    // else is a fail-closed error (SPEC §6), not a silent no-op: an unrecognised *required* key's
    // absence is already caught downstream (e.g. a column with no `role`), but an unrecognised
    // *optional* key (a typo like `jitterdays` for `jitterDays`) previously vanished with no
    // signal, changing the run's behaviour without changing what the author wrote or was told.
    private static final Set<String> KNOWN_ROOT_KEYS = Set.of(
        "maxCategoricalCardinality", "distinguishingLint", "structuralUniqueness",
        "structuralRarenessK", "tables");

    // Keys tolerated for back-compat, meaningless today but never rejected - the deliberate
    // exception to the rule above. Extend this set when a key retires; never remove an entry once
    // added, or a policy file that was silently fine before starts failing.
    private static final Set<String> RETIRED_ROOT_KEYS = Set.of("autoInfer");

    private static final Set<String> KNOWN_TABLE_KEYS = Set.of("columns");

    private static final Set<String> KNOWN_COLUMN_KEYS = Set.of(
        "role", "surrogateStrategy", "directIdStrategy", "quasiIdStrategy", "redactionStrategy",
        "redactionConstant", "distinguishing", "jitterDays", "coherenceGroup", "references",
        "derivedFrom");

    /** Creates a YAML policy parser. */
    public YamlPolicyParser() {}

    /**
     * Parses an anonymisation policy from a YAML file path.
     *
     * @param yamlPath Path to the incognito-policy.yaml file.
     * @return Parsed AnonymisationPolicy object.
     * @throws IncognitoException.ConfigException if parsing fails or YAML is invalid.
     */
    public AnonymisationPolicy parse(Path yamlPath) throws IncognitoException.ConfigException {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            return parse(is);
        } catch (IncognitoException.ConfigException e) {
            // parse(InputStream) already reports a precise, actionable diagnostic (e.g. every
            // unrecognised key, in one pass) - rethrow it unchanged rather than burying it behind
            // a generic "Failed to read YAML" message the CLI would print instead of this one.
            throw e;
        } catch (Exception e) {
            throw new IncognitoException.ConfigException("Failed to read YAML from path: " + yamlPath, e);
        }
    }

    /**
     * Parses an anonymisation policy from an InputStream.
     *
     * @param inputStream InputStream containing YAML content.
     * @return Parsed AnonymisationPolicy object.
     * @throws IncognitoException.ConfigException if parsing fails or YAML is invalid.
     */
    @SuppressWarnings("unchecked")
    public AnonymisationPolicy parse(InputStream inputStream) throws IncognitoException.ConfigException {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            Map<String, Object> root = yaml.load(inputStream);
            if (root == null) {
                return AnonymisationPolicy.builder().build(); // Empty config
            }

            AnonymisationPolicy.Builder builder = AnonymisationPolicy.builder();

            // Every unrecognised key is collected across the whole file before anything is thrown,
            // matching SchemaDiscoveryStage's "report every issue in one run" fail-closed
            // convention (SPEC §7.2) rather than the author fixing one typo per run.
            List<String> unrecognisedKeys = new ArrayList<>();
            for (String key : root.keySet()) {
                if (!KNOWN_ROOT_KEYS.contains(key) && !RETIRED_ROOT_KEYS.contains(key)) {
                    unrecognisedKeys.add("'" + key + "' at the policy root");
                }
            }

            // A policy.yaml written before v2.0.0 may still carry `autoInfer: false` (or `true`) -
            // silently ignored (RETIRED_ROOT_KEYS above), unlike any other unrecognised key: the key
            // no longer means anything (AnonymisationPolicy.Builder#autoInfer was removed), but a
            // leftover no-op key in an otherwise-valid file shouldn't fail the parse.
            if (root.containsKey("maxCategoricalCardinality")) {
                builder.maxCategoricalCardinality((Integer) root.get("maxCategoricalCardinality"));
            }
            if (root.containsKey("distinguishingLint")) {
                builder.distinguishingLint(org.identigon.incognito.api.DistinguishingLint.valueOf(String.valueOf(root.get("distinguishingLint")).toUpperCase()));
            }
            if (root.containsKey("structuralUniqueness")) {
                builder.structuralUniqueness(org.identigon.incognito.api.StructuralUniquenessMode.valueOf(
                    String.valueOf(root.get("structuralUniqueness")).toUpperCase()));
            }
            if (root.containsKey("structuralRarenessK")) {
                builder.structuralRarenessK((Integer) root.get("structuralRarenessK"));
            }

            if (root.containsKey("tables")) {
                Map<String, Map<String, Object>> tables = (Map<String, Map<String, Object>>) root.get("tables");
                for (Map.Entry<String, Map<String, Object>> tableEntry : tables.entrySet()) {
                    String tableName = tableEntry.getKey();
                    Map<String, Object> tableNode = tableEntry.getValue();

                    if (tableNode != null) {
                        for (String key : tableNode.keySet()) {
                            if (!KNOWN_TABLE_KEYS.contains(key)) {
                                unrecognisedKeys.add("'" + key + "' in table '" + tableName + "'");
                            }
                        }
                    }

                    TablePolicy.Builder tableBuilder = TablePolicy.builder(tableName);
                    if (tableNode != null && tableNode.containsKey("columns")) {
                        Map<String, Map<String, Object>> columns = (Map<String, Map<String, Object>>) tableNode.get("columns");
                        for (Map.Entry<String, Map<String, Object>> colEntry : columns.entrySet()) {
                            String colName = colEntry.getKey();
                            Map<String, Object> colNode = colEntry.getValue();

                            ColumnPolicy.Builder colBuilder = ColumnPolicy.builder(colName);
                            if (colNode != null) {
                                for (String key : colNode.keySet()) {
                                    if (!KNOWN_COLUMN_KEYS.contains(key)) {
                                        unrecognisedKeys.add("'" + key + "' on column '" + colName
                                            + "' in table '" + tableName + "'");
                                    }
                                }
                                // colNode.get("X") != null, not containsKey("X"): `scaffold` always emits every
                                // key with a blank value (e.g. "role:" with nothing after the colon - a YAML
                                // null, present but unset), specifically so a human/agent fills it in. containsKey
                                // is true for that blank entry too, so ColumnRole.valueOf(String.valueOf(null))
                                // used to evaluate ColumnRole.valueOf("NULL") and throw a cryptic
                                // IllegalArgumentException instead of leaving the field null for the existing
                                // fail-closed validation (SPEC §7.2) to report clearly.
                                if (colNode.get("role") != null) {
                                    colBuilder.role(ColumnRole.valueOf(String.valueOf(colNode.get("role")).toUpperCase()));
                                }
                                if (colNode.get("surrogateStrategy") != null) {
                                    colBuilder.surrogateStrategy(SurrogateStrategy.valueOf(String.valueOf(colNode.get("surrogateStrategy")).toUpperCase()));
                                }
                                if (colNode.get("directIdStrategy") != null) {
                                    colBuilder.directIdStrategy(DirectIdStrategy.valueOf(String.valueOf(colNode.get("directIdStrategy")).toUpperCase()));
                                }
                                if (colNode.get("quasiIdStrategy") != null) {
                                    colBuilder.quasiIdStrategy(QuasiIdStrategy.valueOf(String.valueOf(colNode.get("quasiIdStrategy")).toUpperCase()));
                                }
                                if (colNode.get("redactionStrategy") != null) {
                                    colBuilder.redactionStrategy(RedactionStrategy.valueOf(String.valueOf(colNode.get("redactionStrategy")).toUpperCase()));
                                }
                                if (colNode.get("redactionConstant") != null) {
                                    colBuilder.redactionConstant(String.valueOf(colNode.get("redactionConstant")));
                                }
                                if (colNode.containsKey("distinguishing")) {
                                    colBuilder.distinguishing((Boolean) colNode.get("distinguishing"));
                                }
                                if (colNode.containsKey("jitterDays")) {
                                    colBuilder.jitterDays((Integer) colNode.get("jitterDays"));
                                }
                                if (colNode.containsKey("coherenceGroup")) {
                                    colBuilder.coherenceGroup(String.valueOf(colNode.get("coherenceGroup")));
                                }
                                if (colNode.containsKey("references")) {
                                    Map<String, String> ref = (Map<String, String>) colNode.get("references");
                                    colBuilder.references(ref.get("table"), ref.get("column"));
                                }
                                if (colNode.containsKey("derivedFrom")) {
                                    Map<String, String> df = (Map<String, String>) colNode.get("derivedFrom");
                                    colBuilder.derivedFrom(df.get("table"), df.get("column"));
                                }
                            }
                            tableBuilder.column(colBuilder.build());
                        }
                    }
                    builder.table(tableBuilder.build());
                }
            }

            if (!unrecognisedKeys.isEmpty()) {
                throw new IncognitoException.ConfigException(
                    "Unrecognised policy key(s): " + unrecognisedKeys.size() + " found - fix all at"
                    + " once, not one run at a time:\n  - " + String.join("\n  - ", unrecognisedKeys));
            }

            return builder.build();
        } catch (IncognitoException.ConfigException e) {
            throw e; // already the right shape/message - don't re-wrap it below
        } catch (Exception e) {
            throw new IncognitoException.ConfigException("Failed to parse YAML policy", e);
        }
    }
}
