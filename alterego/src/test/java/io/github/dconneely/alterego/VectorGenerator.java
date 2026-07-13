package io.github.dconneely.alterego;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the frozen conformance vector fixtures under {@code src/test/resources/vectors/}
 * from this milestone's implementation of Appendix A. Run once via {@code main}; the output is
 * reviewed by hand (see the human review gate in {@code docs/tasks/M1.md}) and then never
 * regenerated (CLAUDE.md invariant 2).
 */
final class VectorGenerator {

  /** The reference salt for every golden/vector test; never changes (docs/tasks/M1.md). */
  static final byte[] REFERENCE_SALT = "alterego-reference-salt!".getBytes(StandardCharsets.UTF_8);

  private static final Path OUTPUT_DIR = Path.of("src/test/resources/vectors");
  private static final java.util.HexFormat HEX = java.util.HexFormat.of();

  private VectorGenerator() {}

  public static void main(String[] args) throws IOException {
    Files.createDirectories(OUTPUT_DIR);
    List<Map<String, Object>> derivationCases = derivationCases();
    writeVectorFile("derivation.json", derivationCases);
    writeVectorFile("stream.json", streamCases(derivationCases));
    writeVectorFile("sampling.json", samplingCases());
    writeVectorFile("mapkey.json", mapkeyCases());
    System.out.println("Wrote vector files to " + OUTPUT_DIR.toAbsolutePath());
  }

  private static void writeVectorFile(String fileName, List<Map<String, Object>> cases) throws IOException {
    Files.writeString(OUTPUT_DIR.resolve(fileName), MinimalJson.write(cases));
  }

  // --- derivation.json ---------------------------------------------------------------------

  private static List<Map<String, Object>> derivationCases() {
    List<Map<String, Object>> cases = new ArrayList<>();
    cases.add(derivationCase("random-purpose-basic", Derivation.PURPOSE_RANDOM, "alterego:first-name", "Alice", 0));
    cases.add(derivationCase("mapkey-purpose-basic", Derivation.PURPOSE_MAPKEY, "alterego:first-name", "Alice", 0));
    cases.add(derivationCase("record-purpose-basic", Derivation.PURPOSE_RECORD, "alterego:gb-postcode-area", "case-12345", 0));
    cases.add(derivationCase("counter-nonzero", Derivation.PURPOSE_RANDOM, "alterego:first-name", "Alice", 5));
    cases.add(derivationCase("counter-max-uint32", Derivation.PURPOSE_RANDOM, "alterego:first-name", "Alice", -1));
    cases.add(derivationCase("empty-canonical", Derivation.PURPOSE_RANDOM, "alterego:first-name", "", 0));
    cases.add(derivationCase("nul-embedded-canonical", Derivation.PURPOSE_RANDOM, "alterego:first-name", "a\u0000b", 0));
    cases.add(derivationCase("non-ascii-canonical", Derivation.PURPOSE_RANDOM, "alterego:first-name", "café", 0));
    cases.add(derivationCase("emoji-canonical", Derivation.PURPOSE_RANDOM, "alterego:first-name", "🔑", 0));
    cases.add(derivationCase("long-canonical", Derivation.PURPOSE_RANDOM, "alterego:first-name",
        "a-fairly-long-canonical-value-used-to-exercise-multi-block-messages", 0));
    cases.add(derivationCase("max-length-domain", Derivation.PURPOSE_RANDOM, "a".repeat(100), "x", 0));
    cases.add(derivationCase("domain-charset-variety", Derivation.PURPOSE_RANDOM, "myapp:case_ref-1.v2", "x", 0));
    cases.add(derivationCase("nul-boundary-ab-c", Derivation.PURPOSE_RANDOM, "ab", "c", 0));
    cases.add(derivationCase("nul-boundary-a-bc", Derivation.PURPOSE_RANDOM, "a", "bc", 0));
    return cases;
  }

  private static Map<String, Object> derivationCase(
      String name, String purpose, String domain, String canonical, int counter) {
    byte[] key = Derivation.deriveKey(REFERENCE_SALT, purpose, domain, canonical, counter);
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", name);
    entry.put("saltHex", HEX.formatHex(REFERENCE_SALT));
    entry.put("purpose", purpose);
    entry.put("domain", domain);
    entry.put("canonical", canonical);
    entry.put("counter", (long) counter);
    entry.put("keyHex", HEX.formatHex(key));
    return entry;
  }

  // --- stream.json ---------------------------------------------------------------------------

  private static List<Map<String, Object>> streamCases(List<Map<String, Object>> derivationCases) {
    List<Map<String, Object>> cases = new ArrayList<>();
    for (String name : List.of("random-purpose-basic", "counter-nonzero", "non-ascii-canonical", "long-canonical")) {
      Map<String, Object> derivationCase = findByName(derivationCases, name);
      byte[] key = HEX.parseHex((String) derivationCase.get("keyHex"));
      byte[] stream = RawStream.firstBytes(key, 96);
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("name", name);
      entry.put("keyHex", derivationCase.get("keyHex"));
      entry.put("streamHex", HEX.formatHex(stream));
      cases.add(entry);
    }
    return cases;
  }

  private static Map<String, Object> findByName(List<Map<String, Object>> cases, String name) {
    return cases.stream()
        .filter(c -> c.get("name").equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No case named " + name));
  }

  // --- sampling.json ---------------------------------------------------------------------------

  private static List<Map<String, Object>> samplingCases() {
    List<Map<String, Object>> cases = new ArrayList<>();

    cases.add(samplingCase("mixed-basic", keyFor("sampling-1"), rand -> List.of(
        call("nextInt", Map.of("bound", 10L), (long) rand.nextInt(10)),
        call("nextInt", Map.of("bound", 100L), (long) rand.nextInt(100)),
        call("digit", Map.of(), String.valueOf(rand.digit())),
        call("letterUpper", Map.of(), String.valueOf(rand.letterUpper())),
        call("letterLower", Map.of(), String.valueOf(rand.letterLower())))));

    cases.add(samplingCase("boolean-sequence", keyFor("sampling-2"), rand -> List.of(
        call("nextBoolean", Map.of(), rand.nextBoolean()),
        call("nextBoolean", Map.of(), rand.nextBoolean()),
        call("nextBoolean", Map.of(), rand.nextBoolean()),
        call("nextBoolean", Map.of(), rand.nextBoolean()))));

    cases.add(samplingCase("pick-sequence", keyFor("sampling-3"), rand -> List.of(
        call("pick", Map.of("size", 5L), (long) rand.nextInt(5)))));
    // pick(choices) == choices.get(nextInt(choices.size())); recorded as the chosen index since
    // the algorithm depends only on list size, not content (Appendix A.3).

    Map<String, Object> rejectionCase = nextLongRejectionCase();
    cases.add(rejectionCase);

    cases.add(samplingCase("bound-two-repeated", keyFor("sampling-5"), rand -> {
      List<Map<String, Object>> calls = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        calls.add(call("nextInt", Map.of("bound", 2L), (long) rand.nextInt(2)));
      }
      return calls;
    }));

    cases.add(samplingCase("bound-one-collapses-to-zero", keyFor("sampling-6"), rand -> List.of(
        call("nextLong", Map.of("bound", 1L), rand.nextLong(1)),
        call("nextLong", Map.of("bound", 1L), rand.nextLong(1)))));

    cases.add(samplingCase("interleaved-mixed-ops", keyFor("sampling-7"), rand -> List.of(
        call("nextInt", Map.of("bound", 26L), (long) rand.nextInt(26)),
        call("pick", Map.of("size", 3L), (long) rand.nextInt(3)),
        call("digit", Map.of(), String.valueOf(rand.digit())),
        call("nextBoolean", Map.of(), rand.nextBoolean()),
        call("nextInt", Map.of("bound", 1000L), (long) rand.nextInt(1000)))));

    cases.add(samplingCase("long-char-sequence", keyFor("sampling-8"), rand -> {
      List<Map<String, Object>> calls = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        calls.add(call("digit", Map.of(), String.valueOf(rand.digit())));
        calls.add(call("letterUpper", Map.of(), String.valueOf(rand.letterUpper())));
        calls.add(call("letterLower", Map.of(), String.valueOf(rand.letterLower())));
      }
      return calls;
    }));

    return cases;
  }

  private static byte[] keyFor(String seed) {
    return Derivation.deriveKey(REFERENCE_SALT, Derivation.PURPOSE_RANDOM, "vector-generator", seed, 0);
  }

  private interface CallSequence {
    List<Map<String, Object>> generate(Randomness rand);
  }

  private static Map<String, Object> samplingCase(String name, byte[] key, CallSequence sequence) {
    Randomness rand = new HmacRandomness(key);
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", name);
    entry.put("keyHex", HEX.formatHex(key));
    entry.put("calls", sequence.generate(rand));
    return entry;
  }

  private static Map<String, Object> call(String op, Map<String, Object> args, Object result) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("op", op);
    c.putAll(args);
    c.put("result", result);
    return c;
  }

  /**
   * Finds a key whose first stream draw for {@code bound = floor(MAX/2)+1} is already out of
   * range, so {@code nextLong(bound)} is provably forced to reject and redraw at least once.
   * This checks Appendix A.2's block(0) formula directly, independent of {@link HmacRandomness},
   * to confirm the case genuinely exercises the redraw path before recording the real result.
   */
  private static Map<String, Object> nextLongRejectionCase() {
    long bound = (Long.MAX_VALUE / 2) + 1;
    long limit = (Long.MAX_VALUE / bound) * bound;
    for (int i = 0; ; i++) {
      byte[] key = keyFor("reject-search-" + i);
      byte[] block0 = Derivation.hmac(key, ByteBuffer.allocate(4).putInt(0).array());
      long v0 = ByteBuffer.wrap(block0, 0, 8).getLong() & Long.MAX_VALUE;
      if (v0 >= limit) {
        Randomness rand = new HmacRandomness(key);
        long result = rand.nextLong(bound);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", "nextLong-forces-rejection");
        entry.put("keyHex", HEX.formatHex(key));
        entry.put("calls", List.of(call("nextLong", Map.of("bound", bound), result)));
        return entry;
      }
    }
  }

  // --- mapkey.json -----------------------------------------------------------------------------

  private static List<Map<String, Object>> mapkeyCases() {
    List<Map<String, Object>> cases = new ArrayList<>();
    cases.add(mapkeyCase("basic", "alterego:first-name", "Alice"));
    cases.add(mapkeyCase("different-domain", "alterego:last-name", "Alice"));
    cases.add(mapkeyCase("empty-canonical", "alterego:first-name", ""));
    cases.add(mapkeyCase("non-ascii-canonical", "alterego:first-name", "Zoë"));
    cases.add(mapkeyCase("nul-embedded-canonical", "alterego:first-name", "a\u0000b"));
    cases.add(mapkeyCase("long-domain", "myapp:some-fairly-long-domain-name-for-testing", "x"));
    return cases;
  }

  private static Map<String, Object> mapkeyCase(String name, String domain, String canonical) {
    byte[] key = Derivation.deriveKey(REFERENCE_SALT, Derivation.PURPOSE_MAPKEY, domain, canonical, 0);
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", name);
    entry.put("saltHex", HEX.formatHex(REFERENCE_SALT));
    entry.put("domain", domain);
    entry.put("canonical", canonical);
    entry.put("keyHex", HEX.formatHex(key));
    return entry;
  }
}
