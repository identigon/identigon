package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultTransformationContextTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static TransformationContext topLevel(String domain, String canonical) {
    return DefaultTransformationContext.topLevel(SALT, Locale.UK, domain, canonical, null, false);
  }

  // --- section 2.2 invariant: derived(...) matches an equivalent top-level context -----------

  @Test
  void derivedContextMatchesEquivalentTopLevelContext() {
    TransformationContext parent = topLevel("parent-domain", "parent-input");
    TransformationContext derived = parent.derived("sub-domain", "sub-input");
    TransformationContext equivalentTopLevel = topLevel("sub-domain", "sub-input");

    for (int i = 0; i < 50; i++) {
      assertEquals(equivalentTopLevel.random().nextInt(1_000_000), derived.random().nextInt(1_000_000));
    }
  }

  @Test
  void derivedContextIgnoresParentsDomainAndInput() {
    // Two different parents deriving the same (subDomain, subInput) must agree, proving the
    // child's derivation depends only on subDomain/subInput, not on the parent's own domain/input.
    TransformationContext parentA = topLevel("parent-a", "input-a");
    TransformationContext parentB = topLevel("parent-b", "input-b");
    TransformationContext derivedFromA = parentA.derived("shared-sub", "shared-input");
    TransformationContext derivedFromB = parentB.derived("shared-sub", "shared-input");

    for (int i = 0; i < 20; i++) {
      assertEquals(derivedFromA.random().nextInt(1000), derivedFromB.random().nextInt(1000));
    }
  }

  @Test
  void derivedContextRejectsInvalidSubDomain() {
    TransformationContext parent = topLevel("parent-domain", "parent-input");
    assertThrows(AlterEgoConfigException.class, () -> parent.derived("bad domain!", "x"));
  }

  @Test
  void derivedContextCarriesLocaleForward() {
    TransformationContext parent = DefaultTransformationContext.topLevel(SALT, Locale.UK, "d", "x", null, false);
    TransformationContext derived = parent.derived("sub", "y");
    assertEquals(Locale.UK, derived.locale());
  }

  // --- outside-scope record() no-op (section 6.2) ---------------------------------------------

  private static final AttributeKey<String> KEY = AttributeKey.of("test:attr", String.class);
  private static final AttributeKey<Integer> INT_KEY = AttributeKey.of("test:int-attr", Integer.class);

  @Test
  void recordGetIsEmptyOutsideScope() {
    TransformationContext ctx = topLevel("d", "x");
    assertEquals(Optional.empty(), ctx.record().get(KEY));
  }

  @Test
  void recordSetIsDiscardedOutsideScope() {
    TransformationContext ctx = topLevel("d", "x");
    ctx.record().set(KEY, "value");
    assertEquals(Optional.empty(), ctx.record().get(KEY));
  }

  @Test
  void recordComputeIfAbsentResolvesWithoutRetaining() {
    TransformationContext ctx = topLevel("d", "x");
    String first = ctx.record().computeIfAbsent(KEY, r -> r.pick(List.of("a", "b", "c")));
    assertTrue(List.of("a", "b", "c").contains(first));
    // Not retained: a second call may resolve again rather than returning the first value.
    assertEquals(Optional.empty(), ctx.record().get(KEY));
  }

  @Test
  void recordComputeIfAbsentUsesThisContextsOwnRandomness() {
    // The resolver's draw must come from *this* context's stream, not some other context's:
    // draw the same amount directly from random() first, then confirm computeIfAbsent's draw
    // continues that same stream rather than starting a fresh/unrelated one.
    TransformationContext ctx = topLevel("d", "x");
    TransformationContext independent = topLevel("d", "x");

    int direct = independent.random().nextInt(1000);
    int viaComputeIfAbsent = ctx.record().computeIfAbsent(INT_KEY, r -> r.nextInt(1000));
    int nextDirectDraw = ctx.random().nextInt(1000);

    assertEquals(direct, viaComputeIfAbsent);
    assertFalse(nextDirectDraw == direct && nextDirectDraw == viaComputeIfAbsent);
  }

  // --- Mappings (section 2.3) ------------------------------------------------------------------

  @Test
  void mappingsThrowsWithoutConfiguredStore() {
    TransformationContext ctx = topLevel("d", "x");
    assertThrows(AlterEgoStoreException.class, () -> ctx.mappings().get("key"));
  }
}
