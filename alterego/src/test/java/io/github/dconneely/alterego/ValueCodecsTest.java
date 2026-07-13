package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValueCodecsTest {

  private static <T> void assertRoundTrip(T value, Class<T> type) {
    String encoded = ValueCodecs.encode(value, type);
    T decoded = ValueCodecs.decode(encoded, type);
    assertEquals(value, decoded);
  }

  @Test
  void stringRoundTrips() {
    assertRoundTrip("hello, café 🔑", String.class);
    assertRoundTrip("", String.class);
  }

  @Test
  void integerRoundTrips() {
    assertRoundTrip(42, Integer.class);
    assertRoundTrip(-7, Integer.class);
    assertRoundTrip(0, Integer.class);
  }

  @Test
  void longRoundTrips() {
    assertRoundTrip(9_000_000_000L, Long.class);
    assertRoundTrip(-1L, Long.class);
  }

  @Test
  void booleanRoundTrips() {
    assertRoundTrip(Boolean.TRUE, Boolean.class);
    assertRoundTrip(Boolean.FALSE, Boolean.class);
    assertEquals("true", ValueCodecs.encode(Boolean.TRUE, Boolean.class));
    assertEquals("false", ValueCodecs.encode(Boolean.FALSE, Boolean.class));
  }

  @Test
  void booleanDecodeRejectsNonCanonicalText() {
    assertThrows(AlterEgoStoreException.class, () -> ValueCodecs.decode("yes", Boolean.class));
    assertThrows(AlterEgoStoreException.class, () -> ValueCodecs.decode("True", Boolean.class));
  }

  @Test
  void localDateRoundTrips() {
    assertRoundTrip(LocalDate.of(2026, 7, 13), LocalDate.class);
  }

  @Test
  void localDateTimeRoundTripsWithZeroSeconds() {
    LocalDateTime value = LocalDateTime.of(2026, 7, 13, 14, 30);
    assertEquals("2026-07-13T14:30", value.toString());
    assertRoundTrip(value, LocalDateTime.class);
  }

  @Test
  void localDateTimeRoundTripsWithNanoseconds() {
    LocalDateTime value = LocalDateTime.of(2026, 7, 13, 14, 30, 0, 123_000_000);
    assertTrue(value.toString().contains("."));
    assertRoundTrip(value, LocalDateTime.class);
  }

  @Test
  void instantRoundTrips() {
    assertRoundTrip(Instant.parse("2026-07-13T14:30:00Z"), Instant.class);
  }

  @Test
  void uuidRoundTripsAndIsLowerCase() {
    UUID value = UUID.randomUUID();
    String encoded = ValueCodecs.encode(value, UUID.class);
    assertEquals(encoded, encoded.toLowerCase());
    assertRoundTrip(value, UUID.class);
  }

  @Test
  void enumRoundTripsUsingNameNotToString() {
    assertRoundTrip(GbCountry.NORTHERN_IRELAND, GbCountry.class);
    assertEquals("NORTHERN_IRELAND", ValueCodecs.encode(GbCountry.NORTHERN_IRELAND, GbCountry.class));
  }

  @Test
  void unsupportedTypeThrowsNamingTypeAndSupportedSet() {
    AlterEgoConfigException ex =
        assertThrows(AlterEgoConfigException.class, () -> ValueCodecs.requireSupported(Double.class));
    assertTrue(ex.getMessage().contains("Double"));
    assertTrue(ex.getMessage().contains("String"));
    assertTrue(ex.getMessage().contains("enum"));
  }

  @Test
  void encodeRejectsUnsupportedType() {
    assertThrows(AlterEgoConfigException.class, () -> ValueCodecs.encode(3.14, Double.class));
  }

  @Test
  void decodeRejectsUnsupportedType() {
    assertThrows(AlterEgoConfigException.class, () -> ValueCodecs.decode("3.14", Double.class));
  }

  @Test
  void decodeRejectsCorruptedEnumConstant() {
    AlterEgoStoreException ex =
        assertThrows(AlterEgoStoreException.class, () -> ValueCodecs.decode("ATLANTIS", GbCountry.class));
    assertFalse(ex.getMessage().isBlank());
  }

  @Test
  void decodeRejectsCorruptedNumber() {
    assertThrows(AlterEgoStoreException.class, () -> ValueCodecs.decode("not-a-number", Integer.class));
  }

  @Test
  void isSupportedCoversTheFullSet() {
    assertTrue(ValueCodecs.isSupported(String.class));
    assertTrue(ValueCodecs.isSupported(Integer.class));
    assertTrue(ValueCodecs.isSupported(Long.class));
    assertTrue(ValueCodecs.isSupported(Boolean.class));
    assertTrue(ValueCodecs.isSupported(LocalDate.class));
    assertTrue(ValueCodecs.isSupported(LocalDateTime.class));
    assertTrue(ValueCodecs.isSupported(Instant.class));
    assertTrue(ValueCodecs.isSupported(UUID.class));
    assertTrue(ValueCodecs.isSupported(GbCountry.class));
    assertFalse(ValueCodecs.isSupported(Double.class));
    assertFalse(ValueCodecs.isSupported(Object.class));
  }
}
