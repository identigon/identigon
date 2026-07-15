package io.github.dconneely.alterego.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dconneely.alterego.AlterEgoConfigException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DictionaryParserTest {

  private static final String VALID_HEADER =
      """
      # source: Test Org: Test Dataset
      # data-url: https://example.invalid/data
      # licence: TEST-LICENCE
      # licence-url: https://example.invalid/licence
      # retrieved: 2026-07-14
      """;

  @Test
  void parsesFlatEntries() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Alice\nBob\nCarol\n", "test");
    assertEquals(List.of("Alice", "Bob", "Carol"), dict.values());
  }

  @Test
  void parsesTaggedEntries() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Manchester\tM\tENGLAND\n", "test");
    DictionaryEntry entry = dict.entries().get(0);
    assertEquals("Manchester", entry.value());
    assertEquals(List.of("M", "ENGLAND"), entry.tags());
  }

  @Test
  void parsesHeaderFields() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "Alice\n", "test");
    assertEquals("Test Org: Test Dataset", dict.header().source());
    assertEquals("https://example.invalid/data", dict.header().dataUrl());
    assertEquals("TEST-LICENCE", dict.header().licence());
    assertEquals("https://example.invalid/licence", dict.header().licenceUrl());
    assertEquals("2026-07-14", dict.header().retrieved());
  }

  @Test
  void skipsBlankLines() {
    Dictionary dict = DictionaryParser.parse(VALID_HEADER + "\nAlice\n\nBob\n\n", "test");
    assertEquals(List.of("Alice", "Bob"), dict.values());
  }

  @Test
  void missingHeaderFieldThrows() {
    String incompleteHeader =
        """
        # source: Test Org
        # data-url: https://example.invalid/data
        # licence: TEST-LICENCE
        """;
    AlterEgoConfigException ex =
        assertThrows(
            AlterEgoConfigException.class, () -> DictionaryParser.parse(incompleteHeader + "Alice\n", "test"));
    assertTrue(ex.getMessage().contains("licence-url"));
  }

  @Test
  void malformedHeaderLineThrows() {
    String badHeader = "# this has no colon\n";
    assertThrows(
        AlterEgoConfigException.class, () -> DictionaryParser.parse(badHeader + "Alice\n", "test"));
  }

  @Test
  void emptyEntriesListIsAllowedByParserItself() {
    // Well-formedness (non-empty) is a separate check (DictionaryWellFormedness), not the
    // parser's job — the parser only enforces structural validity of what's present.
    Dictionary dict = DictionaryParser.parse(VALID_HEADER, "test");
    assertTrue(dict.entries().isEmpty());
  }
}
