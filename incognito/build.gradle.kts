plugins {
    `java-library`
    `maven-publish`
    // Code hygiene, kept in step with the sibling subprojects (alterego, effigies) -- see the
    // root build.gradle.kts's `subprojects { }` block for the shared Spotless/SpotBugs/PMD config.
    alias(libs.plugins.spotless) // version pinned at the root
    alias(libs.plugins.spotbugs) // version pinned at the root
    id("pmd")
    id("jacoco")
}

group = "org.identigon"
// version comes from the root project -- lockstep versioning across the monorepo, see docs/adr/.

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    // Maven Central requires both alongside the binary jar.
    withSourcesJar()
    withJavadocJar()
}

repositories {
    // alterego is now a sibling subproject (see dependencies below), not fetched from Maven --
    // this only resolves incognito's other, genuinely external dependencies (snakeyaml, JUnit, etc.).
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Javadoc/doclint config (Xdoclint:all + Xwerror) is shared with every subproject that publishes
// a javadoc jar, in the root build.gradle.kts's `subprojects { }` block.

dependencies {
    // alterego is exposed through Incognito's public API (e.g. PipelineContext.alterEgo()), so it
    // is `api`, not `implementation` — consumers writing custom stages compile against its types.
    api(project(":alterego"))

    // Declarative YAML policy parser — an internal detail. TODO: move to a separate incognito-yaml
    // module so the core stays dependency-lean (docs/spec/incognito.md §1); currently bundled in
    // core.
    implementation(libs.snakeyaml)

    // Testing dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher) // required by the Gradle 9.x test runner
    testImplementation(libs.h2)

    // Testcontainers for PostgreSQL integration testing (v1.0 Tier-1 engine).
    // 2.x is required for Docker Engine 29.x (older docker-java probes API 1.32, which the daemon
    // rejects; needs ≥1.40). NOTE 2.x renamed the module artifacts (testcontainers-* prefix) and
    // moved PostgreSQLContainer to package org.testcontainers.postgresql.
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    // PostgreSQL JDBC driver — the integration tests connect via raw DriverManager.
    testRuntimeOnly(libs.postgresql)

}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The LICENCE must travel inside the built artifact: most consumers receive only the jar, never the
// repository, so packaging it at the repo root alone is not enough (Maven Central also expects it).
// No NOTICE is packaged — Incognito bundles no third-party data in the jar (the benchmark fixtures
// under src/test/resources are test-only); their attribution lives with them, not in the artifact.
tasks.named<Jar>("jar") {
    from(rootProject.file("LICENCE")) {
        into("META-INF")
    }
}

// No repository/credentials are configured here — that's environment-specific and not this project's
// job to commit. This produces a correct, complete POM plus the three artifact jars (binary, sources,
// javadoc) for `./gradlew publishToMavenLocal`; wiring an actual remote is a separate, later decision.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "incognito"

            pom {
                name = "Incognito"
                description = "A Java library that clones a production database into a schema-identical " +
                    "test database with all PII replaced by clearly fictional data."
                url = "https://github.com/identigon/identigon/tree/main/incognito"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/identigon/identigon/blob/main/LICENCE"
                    }
                }
                developers {
                    developer {
                        id = "identigon"
                        name = "Identigon"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/identigon/identigon.git"
                    developerConnection = "scm:git:https://github.com/identigon/identigon.git"
                    url = "https://github.com/identigon/identigon/tree/main/incognito"
                }
            }
        }
    }
    // Publish to this repository's GitHub Packages Maven registry. `./gradlew publish` pushes here;
    // credentials come from the environment only (GITHUB_ACTOR/GITHUB_TOKEN), never committed — so
    // locally `publish` has nowhere authenticated to push unless those are set. Mirrors alterego.
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/identigon/identigon")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

spotbugs {
    excludeFilter = rootProject.file("config/spotbugs/exclude-incognito.xml")
}
