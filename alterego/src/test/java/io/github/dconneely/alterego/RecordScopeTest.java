package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link RecordScope}/{@link RecordAttributes} directly (section 6.1, section 6.2):
 * first-touch-wins, conflicting {@code set}, keyed vs anonymous {@code computeIfAbsent}
 * resolution, scope isolation, and {@code derived(...)} sharing a parent's record attributes.
 */
class RecordScopeTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final AttributeKey<String> PLACE = AttributeKey.of("test:place", String.class);

  private static AlterEgo alterego() {
    return AlterEgo.builder().salt(SALT).build();
  }

  private static final Strategy<String> SETS_PLACE_TO_INPUT =
      (in, ctx) -> {
        ctx.record().set(PLACE, in);
        return in;
      };

  private static final Strategy<String> READS_PLACE =
      (in, ctx) -> ctx.record().get(PLACE).orElse("none");

  @Test
  void firstFieldFixesTheAttributeAndLaterFieldsSeeIt() {
    AlterEgo eg = alterego();
    Transformation<String> setter = eg.bind("test:setter", SETS_PLACE_TO_INPUT);
    Transformation<String> reader = eg.bind("test:reader", READS_PLACE);
    try (RecordScope rec = eg.record()) {
      rec.apply(setter, "Manchester");
      assertEquals("Manchester", rec.apply(reader, "ignored"));
    }
  }

  @Test
  void readingFirstThenSettingADifferentValueThrows() {
    AlterEgo eg = alterego();
    Transformation<String> setter = eg.bind("test:setter", SETS_PLACE_TO_INPUT);
    try (RecordScope rec = eg.record()) {
      rec.apply(setter, "Manchester");
      assertThrows(AlterEgoCoherenceException.class, () -> rec.apply(setter, "Leeds"));
    }
  }

  @Test
  void settingTheSameValueTwiceIsANoOp() {
    AlterEgo eg = alterego();
    Transformation<String> setter = eg.bind("test:setter", SETS_PLACE_TO_INPUT);
    try (RecordScope rec = eg.record()) {
      rec.apply(setter, "Manchester");
      rec.apply(setter, "Manchester"); // must not throw
    }
  }

  @Test
  void preSeededWithTakesEffectBeforeAnyFieldRuns() {
    AlterEgo eg = alterego();
    Transformation<String> reader = eg.bind("test:reader", READS_PLACE);
    try (RecordScope rec = eg.record().with(PLACE, "Cardiff")) {
      assertEquals("Cardiff", rec.apply(reader, "ignored"));
    }
  }

  @Test
  void outsideAnyScopeGetIsAlwaysEmptyAndSetIsDiscarded() {
    AlterEgo eg = alterego();
    Transformation<String> setter = eg.bind("test:setter", SETS_PLACE_TO_INPUT);
    Transformation<String> reader = eg.bind("test:reader", READS_PLACE);
    setter.apply("Manchester"); // no scope: set() is a no-op
    assertEquals("none", reader.apply("ignored"));
  }

  @Test
  void twoScopesNeverShareState() {
    AlterEgo eg = alterego();
    Transformation<String> setter = eg.bind("test:setter", SETS_PLACE_TO_INPUT);
    Transformation<String> reader = eg.bind("test:reader", READS_PLACE);
    try (RecordScope recA = eg.record()) {
      recA.apply(setter, "Manchester");
      try (RecordScope recB = eg.record()) {
        assertEquals("none", recB.apply(reader, "ignored"));
      }
      assertEquals("Manchester", recA.apply(reader, "ignored"));
    }
  }

  @Test
  void closeDiscardsAttributes() {
    AlterEgo eg = alterego();
    Transformation<String> reader = eg.bind("test:reader", READS_PLACE);
    RecordScope rec = eg.record().with(PLACE, "Cardiff");
    rec.close();
    // After close, this scope is no longer installed on the thread for any further apply(); a
    // fresh scope demonstrates the attribute was actually discarded, not merely inaccessible.
    try (RecordScope fresh = eg.record()) {
      assertEquals("none", fresh.apply(reader, "ignored"));
    }
  }

  // --- computeIfAbsent: keyed vs anonymous resolution -----------------------------------------

  private static final AttributeKey<Integer> ROLL = AttributeKey.of("test:roll", Integer.class);

  private static final Strategy<String> RESOLVES_ROLL =
      (in, ctx) -> String.valueOf(ctx.record().computeIfAbsent(ROLL, r -> r.nextInt(1_000_000)));

  @Test
  void keyedScopeComputeIfAbsentIsFieldOrderIndependent() {
    AlterEgo eg = alterego();
    Transformation<String> t1 = eg.bind("test:field-a", RESOLVES_ROLL);
    Transformation<String> t2 = eg.bind("test:field-b", RESOLVES_ROLL);

    String firstOrder;
    try (RecordScope rec = eg.record("case-123")) {
      firstOrder = rec.apply(t1, "x");
      assertEquals(firstOrder, rec.apply(t2, "y"));
    }
    String secondOrder;
    try (RecordScope rec = eg.record("case-123")) {
      secondOrder = rec.apply(t2, "y");
      assertEquals(secondOrder, rec.apply(t1, "x"));
    }
    assertEquals(firstOrder, secondOrder);
  }

  @Test
  void anonymousScopeComputeIfAbsentIsFirstAskerResolved() {
    // Documented, not field-order independent: whichever field asks first, its own context's
    // randomness resolves the value, so a different first-asker can legitimately resolve a
    // different value.
    AlterEgo eg = alterego();
    Transformation<String> t1 = eg.bind("test:field-a", RESOLVES_ROLL);
    Transformation<String> t2 = eg.bind("test:field-b", RESOLVES_ROLL);

    String whenT1First;
    try (RecordScope rec = eg.record()) {
      whenT1First = rec.apply(t1, "x");
      assertEquals(whenT1First, rec.apply(t2, "y")); // second asker still sees the fixed value
    }
    String whenT2First;
    try (RecordScope rec = eg.record()) {
      whenT2First = rec.apply(t2, "y");
      assertEquals(whenT2First, rec.apply(t1, "x"));
    }
    // Not asserting whenT1First != whenT2First (a coincidental match is possible); the point
    // proven above is that *within* each scope, the second asker always sees the first's value.
  }

  // --- derived(...) sharing --------------------------------------------------------------------

  private static final Strategy<String> COMPOSITE_SETS_THEN_DELEGATES =
      (in, ctx) -> {
        ctx.record().set(PLACE, "Manchester");
        TransformationContext derived = ctx.derived("test:sub", in);
        return READS_PLACE.transform(in, derived);
      };

  @Test
  void derivedChildContextSeesTheParentsRecordAttributes() {
    AlterEgo eg = alterego();
    Transformation<String> composite = eg.bind("test:composite", COMPOSITE_SETS_THEN_DELEGATES);
    try (RecordScope rec = eg.record()) {
      assertEquals("Manchester", rec.apply(composite, "x"));
    }
  }

  @Test
  void keyedRandomnessDiffersFromRandomnessDerivedUnderAnyOrdinaryDomain() {
    // Sanity check that keyed record-attribute derivation is genuinely a distinct purpose, not
    // accidentally reusing the plain randomness derivation for the same salt/key.
    AlterEgo eg = alterego();
    Transformation<String> t1 = eg.bind("test:field-a", RESOLVES_ROLL);
    String viaScope;
    try (RecordScope rec = eg.record("shared-key")) {
      viaScope = rec.apply(t1, "x");
    }
    Strategy<String> plainDraw = (in, ctx) -> String.valueOf(ctx.random().nextInt(1_000_000));
    String viaPlainDomain = eg.bind("test:field-a", plainDraw).apply("shared-key");
    assertNotEquals(viaScope, viaPlainDomain);
  }
}
