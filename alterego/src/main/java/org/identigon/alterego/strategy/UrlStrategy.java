package org.identigon.alterego.strategy;

import java.util.List;
import org.identigon.alterego.Randomness;
import org.identigon.alterego.Strategy;
import org.identigon.alterego.TransformationContext;

/**
 * Generates a fictional URL using a domain name drawn from {@link DomainNameStrategy}.
 */
public final class UrlStrategy implements Strategy<String> {

  /** Singleton instance. */
  public static final UrlStrategy INSTANCE = new UrlStrategy();

  private static final List<String> SCHEMES = List.of("http", "https");

  private UrlStrategy() {}

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    String scheme = random.pick(SCHEMES);
    String domain = DomainNameStrategy.INSTANCE.transform(input, context);
    int pathChoice = random.nextInt(3);
    if (pathChoice == 0) {
      return scheme + "://" + domain;
    } else {
      int len = 4 + random.nextInt(6);
      StringBuilder path = new StringBuilder(len);
      for (int i = 0; i < len; i++) {
        path.append(random.letterLower());
      }
      return scheme + "://" + domain + "/" + path.toString();
    }
  }
}
