package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CliErrorsTest {

  @Test
  void causeChainStartsWithTheTopExceptionsOwnToString() {
    Exception e = new IllegalStateException("boom");
    assertEquals(e.toString(), CliErrors.causeChain(e));
  }

  @Test
  void causeChainAppendsEveryCauseBeneathIt() {
    Exception root = new java.sql.SQLException("No suitable driver found");
    Exception wrapper = new RuntimeException("Failed to inspect schema", root);

    String chain = CliErrors.causeChain(wrapper);
    assertTrue(chain.startsWith(wrapper.toString()), chain);
    assertTrue(chain.contains("Caused by: " + root), chain);
  }

  @Test
  void causeChainStopsAtACycleRatherThanLoopingForever() {
    Throwable a = new RuntimeException("a");
    Throwable b = new RuntimeException("b", a);
    a.initCause(b); // a -> b -> a, a genuine cycle a real Throwable would normally forbid

    // Must terminate at all - the real assertion is that this call returns.
    String chain = CliErrors.causeChain(a);
    assertTrue(chain.contains(a.toString()), chain);
    assertTrue(chain.contains(b.toString()), chain);
  }

  @Test
  void causesOnlyIsEmptyWhenThereIsNoCause() {
    assertEquals("", CliErrors.causesOnly(new IllegalStateException("boom")));
  }

  @Test
  void causesOnlyOmitsTheTopExceptionButKeepsEveryCauseBeneathIt() {
    Exception root = new java.io.IOException("permission denied");
    Exception wrapper = new RuntimeException("Failed to read YAML from path: policy.yaml", root);

    String causes = CliErrors.causesOnly(wrapper);
    assertFalse(causes.contains(wrapper.getMessage()), causes);
    assertTrue(causes.contains("Caused by: " + root), causes);
  }
}
