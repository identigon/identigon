package io.github.dconneely.alterego;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Canonical text encoding and decoding for the fixed value-type set (section 2.6): {@code
 * String}, {@code Integer}, {@code Long}, {@code Boolean}, {@code LocalDate}, {@code
 * LocalDateTime}, {@code Instant}, {@code UUID}, and any enum. There is no public codec SPI
 * (ADR 0003); this registry is the whole of it.
 */
final class ValueCodecs {

  private ValueCodecs() {}

  static boolean isSupported(Class<?> type) {
    return type == String.class
        || type == Integer.class
        || type == Long.class
        || type == Boolean.class
        || type == LocalDate.class
        || type == LocalDateTime.class
        || type == Instant.class
        || type == UUID.class
        || type.isEnum();
  }

  /** Throws {@link AlterEgoConfigException} if {@code type} is not in the supported set. */
  static void requireSupported(Class<?> type) {
    if (!isSupported(type)) {
      throw new AlterEgoConfigException(
          "Unsupported value type: "
              + type.getName()
              + ". Supported types: String, Integer, Long, Boolean, LocalDate, LocalDateTime, "
              + "Instant, UUID, and any enum.");
    }
  }

  /** Encodes {@code value} to its canonical text form. Enums use {@code name()}, not {@code toString()}. */
  static <T> String encode(T value, Class<T> type) {
    requireSupported(type);
    if (type.isEnum()) {
      return ((Enum<?>) value).name();
    }
    return value.toString();
  }

  /**
   * Decodes {@code text} back into {@code type}. Throws {@link AlterEgoStoreException} if
   * {@code text} is not a valid canonical form for {@code type} (corrupted store, renamed enum
   * constant).
   */
  @SuppressWarnings("unchecked")
  static <T> T decode(String text, Class<T> type) {
    requireSupported(type);
    try {
      if (type == String.class) {
        return (T) text;
      } else if (type == Integer.class) {
        return (T) Integer.valueOf(text);
      } else if (type == Long.class) {
        return (T) Long.valueOf(text);
      } else if (type == Boolean.class) {
        return (T) decodeBoolean(text);
      } else if (type == LocalDate.class) {
        return (T) LocalDate.parse(text);
      } else if (type == LocalDateTime.class) {
        return (T) LocalDateTime.parse(text);
      } else if (type == Instant.class) {
        return (T) Instant.parse(text);
      } else if (type == UUID.class) {
        return (T) UUID.fromString(text);
      } else {
        return (T) decodeEnum(text, type);
      }
    } catch (RuntimeException e) {
      throw new AlterEgoStoreException(
          "Failed to decode value for type " + type.getName() + ": " + text, e);
    }
  }

  private static Boolean decodeBoolean(String text) {
    if ("true".equals(text)) {
      return Boolean.TRUE;
    }
    if ("false".equals(text)) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException("not a canonical boolean: " + text);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Enum<?> decodeEnum(String text, Class<?> type) {
    return Enum.valueOf((Class<Enum>) type, text);
  }
}
