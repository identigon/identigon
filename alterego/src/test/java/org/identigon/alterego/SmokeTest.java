package org.identigon.alterego;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SmokeTest {

  @Test
  void modulePackageIsPresent() {
    assertNotNull(getClass().getPackage());
  }
}
