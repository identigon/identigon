package org.identigon.alterego.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.identigon.alterego.AlterEgoConfigException;

/**
 * Parses the dictionary file format (SPECIFICATION.md section 9): a provenance header of
 * {@code # key: value} comment lines, followed by one entry per line — a value optionally
 * followed by tab-separated tag fields. Blank lines are skipped. Pure text in, {@link Dictionary}
 * out; classpath loading is {@link DictionaryLoader}'s job, so this is independently testable.
 */
final class DictionaryParser {

  private static final List<String> REQUIRED_HEADER_KEYS =
      List.of("source", "data-url", "licence", "licence-url", "retrieved");

  private DictionaryParser() {}

  static Dictionary parse(String text, String resourceName) {
    Map<String, String> headerFields = new HashMap<>();
    List<DictionaryEntry> entries = new ArrayList<>();

    for (String line : text.split("\n", -1)) {
      String trimmed = line.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.startsWith("#")) {
        parseHeaderLine(trimmed, headerFields, resourceName);
      } else {
        entries.add(parseEntryLine(line));
      }
    }

    for (String key : REQUIRED_HEADER_KEYS) {
      if (!headerFields.containsKey(key)) {
        throw new AlterEgoConfigException(
            resourceName + ": missing required provenance header field '" + key + "'");
      }
    }

    DictionaryHeader header =
        new DictionaryHeader(
            headerFields.get("source"),
            headerFields.get("data-url"),
            headerFields.get("licence"),
            headerFields.get("licence-url"),
            headerFields.get("retrieved"));

    return new Dictionary(header, entries);
  }

  private static void parseHeaderLine(String line, Map<String, String> headerFields, String resourceName) {
    String content = line.substring(1).strip();
    int colon = content.indexOf(':');
    if (colon < 0) {
      throw new AlterEgoConfigException(
          resourceName + ": malformed header line (expected '# key: value'): " + line);
    }
    String key = content.substring(0, colon).strip();
    String value = content.substring(colon + 1).strip();
    headerFields.put(key, value);
  }

  private static DictionaryEntry parseEntryLine(String line) {
    String[] fields = line.split("\t", -1);
    List<String> tags = fields.length > 1 ? List.of(fields).subList(1, fields.length) : List.of();
    return new DictionaryEntry(fields[0], tags);
  }
}
