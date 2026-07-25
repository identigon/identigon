package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.AlterEgoConfigException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads and caches dictionaries from {@code dictionaries/<country>/<name>.txt} classpath
 * resources (SPECIFICATION.md section 9). Resolution is by country only — no language fallback,
 * no borrowing from another country (section 4): {@code en-GB} and {@code cy-GB} resolve to the
 * exact same resource, and an unshipped country fails fast.
 */
public final class DictionaryLoader {

  private static final ConcurrentMap<String, Dictionary> CACHE = new ConcurrentHashMap<>();

  private DictionaryLoader() {}

  /** Loads {@code dictionaries/<country>/<name>.txt}, cached after the first call. */
  static Dictionary load(String country, String name) {
    return CACHE.computeIfAbsent(country + "/" + name, key -> loadFromClasspath(country, name));
  }

  /**
   * Returns the ISO 3166-1 alpha-2 country of {@code locale}, or throws
   * {@link AlterEgoConfigException} if the locale has none (section 4: country-scoped
   * transformations require a country).
   */
  public static String requireCountry(Locale locale) {
    String country = locale.getCountry();
    if (country.isEmpty()) {
      throw new AlterEgoConfigException(
          "Locale has no country, but this transformation is country-scoped: " + locale);
    }
    return country;
  }

  /** Whether {@code dictionaries/LICENCES/<licenceName>.txt} exists on the classpath. */
  static boolean licenceTextExists(String licenceName) {
    try (InputStream in =
        DictionaryLoader.class.getResourceAsStream("/dictionaries/LICENCES/" + licenceName + ".txt")) {
      return in != null;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to check licence resource: " + licenceName, e);
    }
  }

  /**
   * Whether {@code dictionaries/<country>/<name>.txt} exists on the classpath, without throwing
   * if it doesn't — for built-ins like {@code phoneNumber()} where a missing resource is a
   * documented lesser category (no fictionality guarantee), not a configuration failure.
   */
  static boolean exists(String country, String name) {
    try (InputStream in =
        DictionaryLoader.class.getResourceAsStream("/dictionaries/" + country + "/" + name + ".txt")) {
      return in != null;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to check dictionary resource: " + country + "/" + name, e);
    }
  }

  private static Dictionary loadFromClasspath(String country, String name) {
    String resourcePath = "/dictionaries/" + country + "/" + name + ".txt";
    try (InputStream in = DictionaryLoader.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new AlterEgoConfigException(
            "No '" + name + "' dictionary for country '" + country + "' (" + resourcePath + ")");
      }
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return DictionaryParser.parse(text, resourcePath);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read dictionary resource: " + resourcePath, e);
    }
  }
}
