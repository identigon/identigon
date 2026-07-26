package io.github.dconneely.alterego;

/**
 * Bounds one record's transformation, so its fields can cohere via shared
 * {@link RecordAttributes}. Created per record with {@code AlterEgo.record()} or
 * {@code AlterEgo.record(String)}, and closed when the record is done, at which point its
 * attributes are discarded.
 *
 * <p><b>Use one instance from a single thread only.</b> This is not an arbitrary restriction:
 * record attributes resolve first-touch-wins, which only has one deterministic winner if
 * "first" is well-defined — and it is not across threads, which race. A parallel stream of
 * records is fine and cheap: give each element its own scope. What must never happen is sharing
 * one {@code RecordScope} instance across threads.
 */
public interface RecordScope extends AutoCloseable {

  /**
   * Applies {@code transformation} to {@code value} with this record's attributes visible.
   *
   * @param <T> the value type transformed
   * @param transformation the transformation to apply
   * @param value the input value
   * @return the transformed value
   */
  <T> T apply(Transformation<T> transformation, T value);

  /**
   * Pre-seeds an attribute (e.g. a known region) before any field is transformed.
   *
   * @param <A> the attribute's value type
   * @param key the attribute key
   * @param value the value to fix {@code key} to
   * @return this scope
   */
  <A> RecordScope with(AttributeKey<A> key, A value);

  /** Discards this record's attributes. Does not close the owning {@code AlterEgo} instance. */
  @Override
  void close();
}
