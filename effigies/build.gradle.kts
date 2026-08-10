plugins {
    application
    // Minimal code hygiene, kept in step with the sibling subprojects (incognito, alterego).
    // Tidy-only (no googleJavaFormat) so it does not reflow the hand-maintained style.
    id("com.diffplug.spotless") // version pinned at the root
    id("com.github.spotbugs") // version pinned at the root
}

group = "org.identigon"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    // The CLI entry point. Effigies is a thin authoring/orchestration front-end above incognito;
    // see ADR 0001 and SPECIFICATION.md for the boundary.
    mainClass = "org.identigon.effigies.EffigiesCli"
}

// Light, non-reflowing hygiene: tidy imports/whitespace only, never a full reformat (which would
// fight the hand-maintained style). `spotlessCheck` runs as part of `check`; `spotlessApply` fixes.
spotless {
    java {
        target("src/**/*.java")
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
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
    // inference that migrates here — ADR 0001).
    implementation(project(":incognito"))

    // Reads/writes the declarative policy YAML that incognito consumes.
    implementation("org.yaml:snakeyaml:2.2")

    // Testing dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // required by the Gradle 9.x test runner

    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.13.0")
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

// A single runnable ("fat") jar so the tool runs with a bare `java -jar build/libs/effigies.jar`
// — the runtime classpath (incognito, alterego, snakeyaml, JDBC drivers) is bundled in. No
// shadow plugin needed; plain Gradle assembles it. Signature files from signed dependency jars are
// dropped, as they would otherwise invalidate the merged jar.
tasks.jar {
    // A stable, unversioned filename so `java -jar build/libs/effigies.jar` always works; the
    // version travels in the manifest (Implementation-Version) instead.
    archiveFileName = "effigies.jar"
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Effigies"
        attributes["Implementation-Version"] = project.version.toString()
    }
    // Now that incognito/alterego are sibling project() dependencies rather than external Maven
    // coordinates, Gradle can't infer from the `from({ ... })` closure alone that this task's output
    // depends on their jar tasks -- declare it explicitly so build ordering/up-to-date checks are correct.
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.kotlin_module")
    // The LICENCE travels inside the artifact — most consumers receive only the jar, never the repo.
    from(rootProject.file("LICENCE")) {
        into("META-INF")
    }
}

spotbugs {
    toolVersion = "4.9.8"
    ignoreFailures = false
    excludeFilter = file("config/spotbugs/exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports {
        create("html") {
            required = true
        }
        create("xml") {
            required = false
        }
    }
}
