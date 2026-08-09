package org.identigon.alterego;

import java.util.Locale;

/**
 * Everything a {@link Strategy} needs to transform one value. Created fresh for every input by
 * the {@code AlterEgo} binding machinery and passed to {@link Strategy#transform}; contexts are
 * single-use and must not be stored by a strategy.
 */
public interface TransformationContext {

  /**
   * Deterministic randomness, derived from the salt, the domain, and this input value.
   *
   * @return this context's randomness
   */
  Randomness random();

  /**
   * The locale the owning {@code AlterEgo} instance was configured with.
   *
   * @return the configured locale
   */
  Locale locale();

  /**
   * The namespace under which this transformation stores and looks up mappings.
   *
   * @return this transformation's domain
   */
  String domain();

  /**
   * Domain-scoped, key-hashing view of the configured mapping store.
   *
   * @return this context's mappings view
   */
  Mappings mappings();

  /**
   * Attributes shared across the fields of the current record scope, if any. Outside any
   * {@code RecordScope}, this behaves as an empty, non-retaining view: {@code get} returns
   * empty, {@code set} is discarded, and {@code computeIfAbsent} resolves without retaining.
   *
   * @return this context's record attributes view
   */
  RecordAttributes record();

  /**
   * Returns a child context for composite strategies (e.g. {@code fullName} delegating to
   * {@code firstName}). {@code subInput} is the canonical text form of the sub-value. The
   * returned context is derived exactly as a top-level transformation of {@code subInput} under
   * {@code subDomain} would be, and shares this context's record attributes, so composite
   * consistency holds by construction.
   *
   * @param subDomain the sub-component's own domain
   * @param subInput the canonical text form of the sub-value
   * @return the derived child context
   */
  TransformationContext derived(String subDomain, String subInput);
}
