package io.github.dconneely.alterego;

import java.util.Locale;

/**
 * Everything a {@link Strategy} needs to transform one value. Created fresh for every input by
 * the {@code AlterEgo} binding machinery and passed to {@link Strategy#transform}; contexts are
 * single-use and must not be stored by a strategy.
 */
public interface TransformationContext {

  /** Deterministic randomness, derived from the salt, the domain, and this input value. */
  Randomness random();

  /** The locale the owning {@code AlterEgo} instance was configured with. */
  Locale locale();

  /** The namespace under which this transformation stores and looks up mappings. */
  String domain();

  /** Domain-scoped, key-hashing view of the configured mapping store. */
  Mappings mappings();

  /**
   * Attributes shared across the fields of the current record scope, if any. Outside any
   * {@code RecordScope}, this behaves as an empty, non-retaining view: {@code get} returns
   * empty, {@code set} is discarded, and {@code computeIfAbsent} resolves without retaining.
   */
  RecordAttributes record();

  /**
   * Returns a child context for composite strategies (e.g. {@code fullName} delegating to
   * {@code firstName}). {@code subInput} is the canonical text form of the sub-value. The
   * returned context is derived exactly as a top-level transformation of {@code subInput} under
   * {@code subDomain} would be, and shares this context's record attributes, so composite
   * consistency holds by construction.
   */
  TransformationContext derived(String subDomain, String subInput);
}
