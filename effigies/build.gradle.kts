plugins {
    application
    `maven-publish`
    // Code hygiene, kept in step with the sibling subprojects (incognito, alterego) -- see the
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
}

application {
    // The CLI entry point. Effigies is a thin authoring/orchestration front-end above incognito;
    // see ADR 23 and docs/spec/effigies.md for the boundary.
    mainClass = "org.identigon.effigies.EffigiesCli"
}

repositories {
    // incognito (and transitively alterego) are now sibling subprojects (see dependencies below),
    // not fetched from Maven -- this only resolves effigies's other, genuinely external dependencies
    // (snakeyaml, JUnit, etc.).
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // The orchestration engine. Effigies depends ONLY on incognito (alterego arrives transitively
    // and is not called directly). It moves to a 2.0.x incognito once that lands (which removes the
    // inference that migrates here - ADR 23).
    implementation(project(":incognito"))

    // Reads/writes the declarative policy YAML that incognito consumes.
    implementation(libs.snakeyaml)

    // incognito is driver-agnostic (works against any caller-supplied javax.sql.DataSource; see its
    // testRuntimeOnly-scoped use of this same artifact for its own Testcontainers tests) -- but
    // effigies' SimpleDataSource concretely resolves a `jdbc:postgresql://...` URL via
    // DriverManager, which needs the driver's own ServiceLoader registration on the runtime
    // classpath to find a driver for that URL at all. runtimeOnly: nothing in effigies' own source
    // references org.postgresql.* directly (SPEC §1: PostgreSQL is what's actually supported today).
    runtimeOnly(libs.postgresql)

    // Testing dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher) // required by the Gradle 9.x test runner
    // A real, in-process JDBC target for discover/run command tests -- exercises SchemaInspector
    // and IncognitoPipeline against genuine metadata instead of hand-mocking JDBC. Same version as
    // incognito's own test-scope usage.
    testImplementation(libs.h2)

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

// effigies' own `jar` task is deliberately left at its plain default here (no override): it
// produces a normal thin jar containing only effigies' own classes, which is what gets published
// below as the real Maven artifact `org.identigon:effigies`. The standalone-runnable *fat* jar
// (`identigon.jar` - incognito, alterego, SnakeYAML, the Postgres driver and effigies' own classes
// all merged) is assembled at the monorepo root instead, not here - see the `identigonJar` task in
// the root build.gradle.kts and docs/adr/0028-publish-effigies-runnable-jar.md for why. Publishing
// the fat jar under this project's own plain Maven coordinate would be wrong: a consumer resolving
// `org.identigon:effigies` transitively would get incognito/alterego/SnakeYAML/the Postgres driver
// twice - once embedded in the jar, once again from this POM's own resolved dependencies.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "effigies"
            // The standalone-runnable fat jar, also published here under this same coordinate
            // (a `-standalone` classifier, not the primary artifact) rather than as its own
            // separate GAV - the usual way to offer a shaded jar alongside a real one without a
            // dependency resolver ever selecting it by accident, since a classified artifact is
            // never chosen unless asked for by name. Built at the root, not here - see the
            // `identigonJar` task in the root build.gradle.kts.
            artifact(rootProject.tasks.named("identigonJar")) {
                classifier = "standalone"
            }

            pom {
                name = "Effigies"
                description = "A Java CLI that discovers database schemas, scaffolds declarative " +
                    "policy.yaml files, and drives Incognito to anonymise databases."
                url = "https://github.com/identigon/identigon/tree/main/effigies"
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
                    url = "https://github.com/identigon/identigon/tree/main/effigies"
                }
            }
        }
    }
    // Publish to this repository's GitHub Packages Maven registry. `./gradlew publish` pushes here;
    // credentials come from the environment only (GITHUB_ACTOR/GITHUB_TOKEN), never committed - so
    // locally `publish` has nowhere authenticated to push unless those are set. Mirrors
    // alterego/incognito. Unlike them, no `withSourcesJar()`/`withJavadocJar()`: effigies is a CLI,
    // not a library anyone is meant to browse the source/API docs of via a dependency manager.
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
    excludeFilter = rootProject.file("config/spotbugs/exclude-effigies.xml")
}
