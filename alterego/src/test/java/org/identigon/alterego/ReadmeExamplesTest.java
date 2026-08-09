package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.identigon.alterego.store.FileMappingStore;
import org.identigon.alterego.store.InMemoryMappingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every code sample in {@code README.md}, compiled and run here so a breaking change to the
 * public API fails a test before it ever reaches a release, rather than silently rotting the
 * documentation. Each test method mirrors one README section, in order.
 */
class ReadmeExamplesTest {

  private static byte[] loadSecretSaltFromSomewhereSafe() {
    // Stands in for README's placeholder call; a real caller loads this from a secrets manager,
    // never a literal in source.
    return "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  }

  // --- Quick start -------------------------------------------------------------------------------

  @Test
  void quickStart() {
    byte[] salt = loadSecretSaltFromSomewhereSafe();

    AlterEgo alterego = AlterEgo.builder().salt(salt).build();

    List<String> originalFirstNames = List.of("Alice", "Bob", "Carol");
    List<LocalDate> originalBirthDates =
        List.of(LocalDate.of(1990, 1, 1), LocalDate.of(1985, 6, 15));

    var pseudonymisedFirstNames = originalFirstNames.stream().map(alterego.firstName()).toList();

    var pseudonymisedBirthDates = originalBirthDates.stream().map(alterego.shiftDate(30)).toList();

    assertEquals(3, pseudonymisedFirstNames.size());
    assertEquals(2, pseudonymisedBirthDates.size());
    pseudonymisedFirstNames.forEach(name -> assertTrue(Character.isUpperCase(name.charAt(0))));
  }

  // --- Fictional by default ----------------------------------------------------------------------

  @Test
  void fictionalByDefault() {
    AlterEgo alterego = AlterEgo.builder().salt(loadSecretSaltFromSomewhereSafe()).build();
    Transformation<String> cc = alterego.creditCardNumber();
    String ccOutput = cc.apply("input");
    assertNotNull(ccOutput);
    assertTrue(ccOutput.startsWith("0"));
  }

  // --- unique() and the order-independence caveat ------------------------------------------------

  @Test
  void uniqueCustomerId() {
    AlterEgo alterego =
        AlterEgo.builder().salt(loadSecretSaltFromSomewhereSafe()).mappingStore(new InMemoryMappingStore()).build();
    Strategy<String> customerIdStrategy = (in, ctx) -> ctx.random().pick(List.of("C1", "C2", "C3", "C4", "C5"));

    Transformation<String> uniqueCustomerId = alterego.bind("myapp:customer-id", customerIdStrategy).unique();

    String first = uniqueCustomerId.apply("alice@example.com");
    String second = uniqueCustomerId.apply("bob@example.com");
    assertNotNull(first);
    assertNotNull(second);
  }

  @Test
  void persistentUnique(@TempDir Path tempDir) {
    byte[] salt = loadSecretSaltFromSomewhereSafe();
    try (FileMappingStore store = FileMappingStore.open(tempDir.resolve("mappings.alterego"))) {
      AlterEgo alterego = AlterEgo.builder().salt(salt).mappingStore(store).build();
      Transformation<String> customerId = alterego.pattern("LLDDDDDD").unique();
      // ... mappings and collision resolutions now survive across runs
      assertNotNull(customerId.apply("test@example.com"));
    }
  }

  // --- Record coherence ----------------------------------------------------------------------------

  private static final class Row {
    String town;
    String postcode;
    String phone;
  }

  @Test
  void recordCoherence() {
    AlterEgo alterego = AlterEgo.builder().salt(loadSecretSaltFromSomewhereSafe()).build();
    Row inputRow = new Row();
    inputRow.town = "Manchester";
    inputRow.postcode = "M1 1AA";
    inputRow.phone = "0161 496 0123";
    Row outputRow = new Row();

    try (RecordScope rec = alterego.record()) {
      outputRow.town = rec.apply(alterego.city(), inputRow.town);
      outputRow.postcode = rec.apply(alterego.postcode(), inputRow.postcode);
      outputRow.phone = rec.apply(alterego.phoneNumber(), inputRow.phone);
    }

    assertNotNull(outputRow.town);
    assertTrue(outputRow.postcode.contains(" "), "expected an outward/inward code split: " + outputRow.postcode);
    assertNotNull(outputRow.phone);
  }

  // --- Extending with custom strategies --------------------------------------------------------

  private static String generateEmployeeId(Randomness random) {
    StringBuilder sb = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      sb.append(random.digit());
    }
    return sb.toString();
  }

  @Test
  void customStrategyExtension() {
    AlterEgo alterego =
        AlterEgo.builder().salt(loadSecretSaltFromSomewhereSafe()).mappingStore(new InMemoryMappingStore()).build();
    Strategy<String> employeeIdStrategy = (input, context) -> generateEmployeeId(context.random());

    Transformation<String> employeeId = alterego.bind("myapp:employee-id", employeeIdStrategy).unique();

    String result = employeeId.apply("1234567890");
    assertEquals(10, result.length());
    assertTrue(result.chars().allMatch(Character::isDigit));
  }

  // Sanity: the quick-start snippet's .map(alterego.firstName()) usage really does satisfy
  // Function<String, String>, matching the README's claim about Transformation<T>.
  @Test
  void transformationIsAPlainFunction() {
    AlterEgo alterego = AlterEgo.builder().salt(loadSecretSaltFromSomewhereSafe()).build();
    Function<String, String> asFunction = alterego.firstName();
    assertNotNull(asFunction.apply("Alice"));
  }
}
