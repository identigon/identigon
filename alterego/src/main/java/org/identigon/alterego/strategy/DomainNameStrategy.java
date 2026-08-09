package org.identigon.alterego.strategy;

import java.util.List;
import org.identigon.alterego.Randomness;
import org.identigon.alterego.Strategy;
import org.identigon.alterego.TransformationContext;

/**
 * Generates a fictional domain name drawn from RFC 2606 reserved domains or subdomains of RFC 2606 reserved TLDs.
 */
public final class DomainNameStrategy implements Strategy<String> {

  /** Singleton instance. */
  public static final DomainNameStrategy INSTANCE = new DomainNameStrategy();

  private static final List<String> RESERVED_TLDS = List.of("test", "example", "invalid");
  private static final List<String> RESERVED_DOMAINS = List.of("example.com", "example.net", "example.org");

  private DomainNameStrategy() {}

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    int choice = random.nextInt(10);
    if (choice < 3) {
      return random.pick(RESERVED_DOMAINS);
    } else {
      String tld = random.pick(RESERVED_TLDS);
      int len = 4 + random.nextInt(5);
      StringBuilder sb = new StringBuilder(len);
      for (int i = 0; i < len; i++) {
        sb.append(random.letterLower());
      }
      return sb.toString() + "." + tld;
    }
  }
}
