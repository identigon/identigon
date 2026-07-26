package io.github.dconneely.alterego;

/**
 * The unit of transformation logic bound by {@code AlterEgo.bind(...)}. Stateless: all
 * variability comes from the supplied {@link TransformationContext}.
 *
 * @param <T> the value type transformed
 */
@FunctionalInterface
public interface Strategy<T> {

  /**
   * Transforms one input value. Called once per element; {@code context} is fresh for this
   * call and must not be retained.
   *
   * @param input the input value
   * @param context this call's fresh transformation context
   * @return the transformed value
   */
  T transform(T input, TransformationContext context);
}
