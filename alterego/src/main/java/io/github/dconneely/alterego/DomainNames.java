package io.github.dconneely.alterego;

import java.util.regex.Pattern;

/**
 * Validates the charset shared by transformation domains (section 2.6) and record attribute
 * names (section 6.2): {@code [A-Za-z0-9:._-]\{1,100\}}.
 */
final class DomainNames {

  private static final Pattern CHARSET = Pattern.compile("[A-Za-z0-9:._-]{1,100}");

  private DomainNames() {}

  static void requireValid(String name, String what) {
    if (name == null || !CHARSET.matcher(name).matches()) {
      throw new AlterEgoConfigException(
          what + " must match [A-Za-z0-9:._-]{1,100}, got: " + name);
    }
  }
}
