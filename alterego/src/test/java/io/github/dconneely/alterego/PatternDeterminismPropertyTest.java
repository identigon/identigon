package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Property-style tests backing the PLAN.md M1 "done when" criterion: {@code t.apply(x)} is stable
 * across repeated calls, input-order permutations, and sequential vs parallel streams
 * (section 3.1's order-independence guarantee). Inputs are enumerated deterministically so a
 * failure always reproduces.
 */
class PatternDeterminismPropertyTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  /** A deliberately varied fixed input set: lengths, casing, punctuation, whitespace, non-ASCII. */
  private static final List<String> VARIED_INPUTS =
      List.of(
          "a", "Z", "7", "!", "  ", "abc", "Hello, World", "1234567890",
          "the-quick-brown-fox", "MiXeDcAsE", "café", "naïve", "東京", "🙂emoji",
          "line\nbreak", "tab\there", "xxxxxxxxxxxxxxxxxxxx");

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  @Test
  void stableAcrossRepeatedCalls() {
    Transformation<String> t = alterego().pattern("DLDDDL");
    for (String input : VARIED_INPUTS) {
      String first = t.apply(input);
      String second = t.apply(input);
      String third = alterego().pattern("DLDDDL").apply(input);
      assertEquals(first, second, "input=" + input);
      assertEquals(first, third, "input=" + input);
    }
  }

  @Test
  void stableAcrossInputOrderPermutations() {
    List<String> inputs = IntStream.range(0, 12).mapToObj(i -> "perm-" + i).toList();

    Transformation<String> forwardTransformation = alterego().pattern("DLDDDL");
    Map<String, String> forward = new LinkedHashMap<>();
    for (String in : inputs) {
      forward.put(in, forwardTransformation.apply(in));
    }

    List<String> reversed = new ArrayList<>(inputs);
    Collections.reverse(reversed);
    Transformation<String> reverseTransformation = alterego().pattern("DLDDDL");
    for (String in : reversed) {
      assertEquals(forward.get(in), reverseTransformation.apply(in));
    }
  }

  @Test
  void stableBetweenSequentialAndParallelStreams() {
    Transformation<String> t = alterego().pattern("DLDDDL");
    List<String> inputs = IntStream.range(0, 500).mapToObj(i -> "input-" + i).toList();

    Map<String, String> sequential = inputs.stream().collect(Collectors.toMap(in -> in, t));
    Map<String, String> parallel = inputs.parallelStream().collect(Collectors.toConcurrentMap(in -> in, t));

    assertEquals(sequential, parallel);
  }

  @Test
  void stableBetweenSequentialAndParallelStreamsSharedTransformation() {
    // The same Transformation instance, reused, must behave identically under parallel use:
    // no shared mutable generator state (section 3.1).
    AlterEgo shared = alterego();
    Transformation<String> t = shared.pattern("DLDDDL");
    List<String> inputs = IntStream.range(0, 500).mapToObj(i -> "shared-" + i).toList();

    Map<String, String> sequential = inputs.stream().collect(Collectors.toMap(in -> in, t));
    Map<String, String> parallel = inputs.parallelStream().collect(Collectors.toConcurrentMap(in -> in, t));

    assertEquals(sequential, parallel);
  }
}
