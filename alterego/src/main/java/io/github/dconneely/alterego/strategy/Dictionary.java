package io.github.dconneely.alterego.strategy;

import java.util.List;

/** A parsed dictionary: its provenance header plus every entry, in file order. */
final class Dictionary {

  private final DictionaryHeader header;
  private final List<DictionaryEntry> entries;
  private final List<String> values;

  Dictionary(DictionaryHeader header, List<DictionaryEntry> entries) {
    this.header = header;
    this.entries = List.copyOf(entries);
    this.values = this.entries.stream().map(DictionaryEntry::value).toList();
  }

  DictionaryHeader header() {
    return header;
  }

  /** Every entry, with tags, in file order. */
  List<DictionaryEntry> entries() {
    return entries;
  }

  /** Just the value column, for flat (untagged) dictionaries. */
  List<String> values() {
    return values;
  }
}
