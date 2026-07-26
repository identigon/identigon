package io.github.dconneely.alterego;

import java.util.Objects;

/** Options for {@code emailAddress()} (SPECIFICATION.md section 4.1, section 4.4). */
public final class EmailOptions {

  private static final EmailOptions DEFAULTS = new EmailOptions(false, null);

  private final boolean preserveDomain;
  private final String mappedDomain;

  private EmailOptions(boolean preserveDomain, String mappedDomain) {
    this.preserveDomain = preserveDomain;
    this.mappedDomain = mappedDomain;
  }

  /**
   * The domain is drawn from the RFC 2606 reserved set (the fictionality guarantee applies).
   *
   * @return the default options
   */
  public static EmailOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Keeps the input's own domain unchanged instead of drawing a reserved one — opts out of the
   * fictionality guarantee. If the input has no {@code @}, there is no domain to preserve, and
   * this falls back to a reserved-set draw (documented, still deterministic).
   *
   * @return options that preserve the input's own domain
   */
  public static EmailOptions preserveDomain() {
    return new EmailOptions(true, null);
  }

  /**
   * Maps every output to {@code domain} instead of drawing a reserved one.
   *
   * @param domain the fixed domain to map every output to
   * @return options that map every output to {@code domain}
   */
  public static EmailOptions mapDomain(String domain) {
    Objects.requireNonNull(domain, "domain");
    return new EmailOptions(false, domain);
  }

  boolean isPreserveDomain() {
    return preserveDomain;
  }

  String mappedDomain() {
    return mappedDomain;
  }
}
