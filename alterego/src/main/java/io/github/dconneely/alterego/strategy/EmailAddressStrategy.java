package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.List;

/**
 * Generates a fictional email address (SPECIFICATION.md section 4.1, section 4.4): splits the
 * input at the <strong>last</strong> {@code @} (input with no {@code @} is treated as a bare
 * local part); replaces the local part class-wise in place (each ASCII letter by a letter of the
 * same case, each ASCII digit by a digit, every other character — dots, hyphens, plus tags,
 * non-ASCII — left untouched); and by default draws the domain from RFC 2606's reserved set.
 *
 * <p>RFC 2606 reserves seven names in total: four whole TLDs ({@code test}, {@code example},
 * {@code invalid}, {@code localhost}) and three second-level domains ({@code example.com},
 * {@code example.net}, {@code example.org}). A bare reserved TLD used alone as an email domain
 * (e.g. {@code user@invalid}) does not read as a realistic email domain, so v1 draws only from
 * the three two-label reserved domains.
 */
public final class EmailAddressStrategy implements Strategy<String> {

  static final List<String> RESERVED_DOMAINS = List.of("example.com", "example.net", "example.org");

  private final boolean preserveDomain;
  private final String mappedDomain;

  private EmailAddressStrategy(boolean preserveDomain, String mappedDomain) {
    this.preserveDomain = preserveDomain;
    this.mappedDomain = mappedDomain;
  }

  /** {@code mappedDomain} null means no mapping is configured. */
  public static EmailAddressStrategy create(boolean preserveDomain, String mappedDomain) {
    return new EmailAddressStrategy(preserveDomain, mappedDomain);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    int at = input.lastIndexOf('@');
    String localPart = at < 0 ? input : input.substring(0, at);
    String inputDomain = at < 0 ? null : input.substring(at + 1);
    Randomness random = context.random();
    String newLocalPart = replaceClassWise(localPart, random);
    String domain = resolveDomain(inputDomain, random);
    return newLocalPart + "@" + domain;
  }

  private String resolveDomain(String inputDomain, Randomness random) {
    if (mappedDomain != null) {
      return mappedDomain;
    }
    if (preserveDomain && inputDomain != null) {
      return inputDomain;
    }
    return random.pick(RESERVED_DOMAINS);
  }

  private static String replaceClassWise(String s, Randomness random) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= 'a' && c <= 'z') {
        sb.append(random.letterLower());
      } else if (c >= 'A' && c <= 'Z') {
        sb.append(random.letterUpper());
      } else if (c >= '0' && c <= '9') {
        sb.append(random.digit());
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
