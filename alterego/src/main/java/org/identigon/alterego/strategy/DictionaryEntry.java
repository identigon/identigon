package org.identigon.alterego.strategy;

import java.util.List;

/** One parsed dictionary line: the value plus any tab-separated tag fields. */
record DictionaryEntry(String value, List<String> tags) {}
