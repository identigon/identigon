package org.identigon.alterego.strategy;

/**
 * The parsed provenance header of a dictionary file (SPECIFICATION.md section 9): source,
 * exact original data URL, licence name (must match a file under {@code dictionaries/LICENCES/}),
 * licence URL, and retrieval date.
 */
record DictionaryHeader(String source, String dataUrl, String licence, String licenceUrl, String retrieved) {}
