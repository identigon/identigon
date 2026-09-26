package org.identigon.incognito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The single PostgreSQL Docker image every Testcontainers-based test in this module runs against.
 * The tag lives in {@code quickstart/postgres/Dockerfile}, shared with the quickstart scripts,
 * where dependabot can see and bump it.
 */
public final class TestPostgres {

  private TestPostgres() {}

  /**
   * The shared Dockerfile. The Gradle test task passes its path; the fallback suits an IDE run from
   * this module's directory.
   */
  static final Path DOCKERFILE =
      Path.of(
          System.getProperty("identigon.postgresDockerfile", "../quickstart/postgres/Dockerfile"));

  /** The Testcontainers {@code PostgreSQLContainer} image tag used by every integration test. */
  public static final String IMAGE = imageFromDockerfile();

  private static String imageFromDockerfile() {
    try {
      return Files.readAllLines(DOCKERFILE).stream()
          .filter(line -> line.startsWith("FROM "))
          .map(line -> line.substring("FROM ".length()).trim())
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("No FROM line in " + DOCKERFILE));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the PostgreSQL image from " + DOCKERFILE, e);
    }
  }
}
