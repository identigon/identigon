package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.identigon.alterego.store.InMemoryMappingStore;
import org.identigon.alterego.store.MappingStore;
import org.junit.jupiter.api.Test;

/**
 * The section 2.5 decorator algebra: {@code unique()} subsumes {@code stored()}; both are
 * idempotent; {@code t.unique().stored()} ≡ {@code t.unique()}; {@code t.stored().unique()}
 * ≡ {@code t.unique()}. Proven by observing behaviour (an ever-incrementing strategy reveals
 * whether a given call actually reused a stored/unique value or re-ran the strategy), not by
 * inspecting internals.
 */
class DecoratorAlgebraTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo alterego(MappingStore store) {
    return AlterEgo.builder().salt(SALT).mappingStore(store).build();
  }

  private static Transformation<String> everIncrementing(AlterEgo eg, AtomicInteger counter) {
    Strategy<String> strategy = (in, ctx) -> "gen-" + counter.getAndIncrement();
    return eg.bind("test:algebra", strategy);
  }

  @Test
  void storedIsIdempotent() {
    AtomicInteger counter = new AtomicInteger();
    Transformation<String> t = everIncrementing(alterego(new InMemoryMappingStore()), counter).stored().stored();
    String first = t.apply("alice");
    assertEquals(first, t.apply("alice"));
  }

  @Test
  void uniqueIsIdempotent() {
    AtomicInteger counter = new AtomicInteger();
    Transformation<String> t = everIncrementing(alterego(new InMemoryMappingStore()), counter).unique().unique();
    String first = t.apply("alice");
    assertEquals(first, t.apply("alice"));
  }

  @Test
  void uniqueThenStoredBehavesLikeUniqueAlone() {
    AtomicInteger counterA = new AtomicInteger();
    AtomicInteger counterB = new AtomicInteger();
    AlterEgo egA = alterego(new InMemoryMappingStore());
    AlterEgo egB = alterego(new InMemoryMappingStore());

    Transformation<String> uniqueThenStored = everIncrementing(egA, counterA).unique().stored();
    Transformation<String> uniqueAlone = everIncrementing(egB, counterB).unique();

    // Same salt/domain/strategy behaviour: applying the same sequence of inputs to each produces
    // the same sequence of outputs, proving stored() added no further caching/behavioural layer
    // on top of unique() (both already cache internally, identically).
    for (String input : new String[] {"alice", "bob", "alice", "carol"}) {
      assertEquals(uniqueAlone.apply(input), uniqueThenStored.apply(input));
    }
  }

  @Test
  void storedThenUniqueBehavesLikeUniqueAlone() {
    AtomicInteger counterA = new AtomicInteger();
    AtomicInteger counterB = new AtomicInteger();
    AlterEgo egA = alterego(new InMemoryMappingStore());
    AlterEgo egB = alterego(new InMemoryMappingStore());

    Transformation<String> storedThenUnique = everIncrementing(egA, counterA).stored().unique();
    Transformation<String> uniqueAlone = everIncrementing(egB, counterB).unique();

    for (String input : new String[] {"alice", "bob", "alice", "carol"}) {
      assertEquals(uniqueAlone.apply(input), storedThenUnique.apply(input));
    }
  }
}
