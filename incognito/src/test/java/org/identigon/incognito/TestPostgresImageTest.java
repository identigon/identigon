package org.identigon.incognito;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The quickstart README spells out the PostgreSQL image in a copy-paste command, so it cannot read
 * {@code quickstart/postgres/Dockerfile} the way the scripts and {@link TestPostgres} do. This
 * fails a dependabot bump of the Dockerfile until the README is updated to match.
 */
class TestPostgresImageTest {

  @Test
  void imageIsReadFromTheSharedDockerfile() {
    assertTrue(TestPostgres.IMAGE.startsWith("postgres:"), TestPostgres.IMAGE);
  }

  @Test
  void quickstartReadmeNamesTheSameImage() throws Exception {
    Path readme = TestPostgres.DOCKERFILE.resolveSibling("../README.md");
    assertTrue(
        Files.readString(readme).contains(" " + TestPostgres.IMAGE + "\n"),
        "quickstart/README.md must name " + TestPostgres.IMAGE + " to match the Dockerfile");
  }
}
