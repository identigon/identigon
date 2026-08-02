package org.identigon.alterego;

import java.util.Objects;

/**
 * A typed key for a {@link RecordAttributes} value. Two keys are equal iff their name and type
 * are equal.
 *
 * @param <A> the attribute's value type
 */
public final class AttributeKey<A> {

  private final String name;
  private final Class<A> type;

  private AttributeKey(String name, Class<A> type) {
    DomainNames.requireValid(name, "attribute name");
    this.name = name;
    this.type = Objects.requireNonNull(type, "type");
  }

  /**
   * Creates an attribute key. {@code name} must match {@code [A-Za-z0-9:._-]\{1,100\}}.
   *
   * @param <A> the attribute's value type
   * @param name the key's name
   * @param type the attribute's value type
   * @return the attribute key
   */
  public static <A> AttributeKey<A> of(String name, Class<A> type) {
    return new AttributeKey<>(name, type);
  }

  /**
   * The key's name.
   *
   * @return the key's name
   */
  public String name() {
    return name;
  }

  /**
   * The attribute's value type.
   *
   * @return the attribute's value type
   */
  public Class<A> type() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof AttributeKey<?> other
        && name.equals(other.name)
        && type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  @Override
  public String toString() {
    return "AttributeKey[" + name + ", " + type.getSimpleName() + "]";
  }
}
