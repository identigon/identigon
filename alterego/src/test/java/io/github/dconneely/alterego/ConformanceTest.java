package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Loads the frozen conformance vectors under {@code src/test/resources/vectors/} and asserts
 * this implementation reproduces every case exactly. These vectors were independently
 * recomputed in Python during the M1 independent review gate (docs/tasks/M1.md) before being
 * frozen; this test is what keeps them enforced on every future build.
 */
class ConformanceTest {

  private static final HexFormat HEX = HexFormat.of();

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> loadVectorFile(String fileName) {
    try {
      String json = Files.readString(Path.of("src/test/resources/vectors", fileName));
      return (List<Map<String, Object>>) (List<?>) MinimalJson.parse(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] hex(Map<String, Object> entry, String field) {
    return HEX.parseHex((String) entry.get(field));
  }

  private static int asInt(Object value) {
    return ((Long) value).intValue();
  }

  @TestFactory
  Stream<DynamicTest> derivationVectors() {
    return loadVectorFile("derivation.json").stream()
        .map(v -> dynamicTest((String) v.get("name"), () -> {
          byte[] salt = hex(v, "saltHex");
          byte[] expected = hex(v, "keyHex");
          byte[] actual = Derivation.deriveKey(
              salt,
              (String) v.get("purpose"),
              (String) v.get("domain"),
              (String) v.get("canonical"),
              asInt(v.get("counter")));
          assertEquals(HEX.formatHex(expected), HEX.formatHex(actual));
        }));
  }

  @TestFactory
  Stream<DynamicTest> streamVectors() {
    return loadVectorFile("stream.json").stream()
        .map(v -> dynamicTest((String) v.get("name"), () -> {
          byte[] key = hex(v, "keyHex");
          byte[] expected = hex(v, "streamHex");
          byte[] actual = RawStream.firstBytes(key, expected.length);
          assertEquals(HEX.formatHex(expected), HEX.formatHex(actual));
        }));
  }

  @TestFactory
  Stream<DynamicTest> mapkeyVectors() {
    return loadVectorFile("mapkey.json").stream()
        .map(v -> dynamicTest((String) v.get("name"), () -> {
          byte[] salt = hex(v, "saltHex");
          byte[] expected = hex(v, "keyHex");
          byte[] actual = Derivation.deriveKey(
              salt, Derivation.PURPOSE_MAPKEY, (String) v.get("domain"), (String) v.get("canonical"), 0);
          assertEquals(HEX.formatHex(expected), HEX.formatHex(actual));
        }));
  }

  @TestFactory
  Stream<DynamicTest> samplingVectors() {
    return loadVectorFile("sampling.json").stream()
        .map(v -> dynamicTest((String) v.get("name"), () -> {
          Randomness rand = new HmacRandomness(hex(v, "keyHex"));
          @SuppressWarnings("unchecked")
          List<Map<String, Object>> calls = (List<Map<String, Object>>) (List<?>) v.get("calls");
          for (Map<String, Object> call : calls) {
            assertCall(rand, call);
          }
        }));
  }

  private static void assertCall(Randomness rand, Map<String, Object> call) {
    String op = (String) call.get("op");
    Object expected = call.get("result");
    switch (op) {
      case "nextInt" -> assertEquals(expected, (long) rand.nextInt(asInt(call.get("bound"))));
      case "nextLong" -> assertEquals(expected, rand.nextLong((Long) call.get("bound")));
      case "nextBoolean" -> assertEquals(expected, rand.nextBoolean());
      case "digit" -> assertEquals(expected, String.valueOf(rand.digit()));
      case "letterUpper" -> assertEquals(expected, String.valueOf(rand.letterUpper()));
      case "letterLower" -> assertEquals(expected, String.valueOf(rand.letterLower()));
      case "pick" -> {
        int size = asInt(call.get("size"));
        List<Integer> choices = IntStream.range(0, size).boxed().toList();
        assertEquals(expected, (long) rand.pick(choices));
      }
      default -> throw new IllegalArgumentException("Unknown sampling op: " + op);
    }
  }
}
