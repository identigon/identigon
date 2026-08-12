plugins {
    application
    // Code hygiene, kept in step with the sibling subprojects (incognito, alterego) -- see the
    // root build.gradle.kts's `subprojects { }` block for the shared Spotless/SpotBugs/PMD config.
    id("com.diffplug.spotless") // version pinned at the root
    id("com.github.spotbugs") // version pinned at the root
    id("pmd")
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
    // see ADR 0001 and SPECIFICATION.md for the boundary.
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
    // inference that migrates here — ADR 0001).
    implementation(project(":incognito"))

    // Reads/writes the declarative policy YAML that incognito consumes.
    implementation("org.yaml:snakeyaml:2.2")

    // Testing dependencies
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // required by the Gradle 9.x test runner
    // A real, in-process JDBC target for discover/run command tests -- exercises SchemaInspector
    // and IncognitoPipeline against genuine metadata instead of hand-mocking JDBC. Same version as
    // incognito's own test-scope usage.
    testImplementation("com.h2database:h2:2.2.224")

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

// A single runnable ("fat") jar so the tool runs with a bare `java -jar build/libs/identigon.jar`
// — the runtime classpath (incognito, alterego, snakeyaml, JDBC drivers) is bundled in. No
// shadow plugin needed; plain Gradle assembles it. Signature files from signed dependency jars are
// dropped, as they would otherwise invalidate the merged jar.
//
// Named "identigon.jar", not "effigies.jar": consumers run this one artifact and never touch
// incognito/alterego directly, so the jar (and the CLI's own --version/--help banner, see
// EffigiesCli) present the project's public name. "effigies" stays as the internal module/package
// name only -- see the module's own Javadoc and ADR 0001 for why the three-subproject split exists.
tasks.jar {
    // A stable, unversioned filename so `java -jar build/libs/identigon.jar` always works; the
    // version travels in the manifest (Implementation-Version) instead.
    archiveFileName = "identigon.jar"
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Identigon"
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
    // Both alterego's and incognito's own jars carry a META-INF/LICENCE of their own (different
    // content -- see below), zipTree'd in above along with everything else on the runtime
    // classpath. Drop whichever one the merge would otherwise pick (order/precedence between a
    // zipTree'd entry and an explicit from() is not something to rely on) and add our own
    // deliberately, so which LICENCE/NOTICE end up in this jar is unambiguous.
    exclude("META-INF/LICENCE", "META-INF/NOTICE")
    // This is alterego's LICENCE, not the root's, deliberately: this fat jar physically bundles
    // alterego's classes and its OGL-derived dictionary data (zipTree'd in above from the runtime
    // classpath), so the plain root LICENCE alone would omit the OGL attribution clause that data
    // requires. alterego's LICENCE is a superset -- the same plain MIT text, plus that clause -- so
    // it correctly covers effigies' and incognito's own MIT-only code too.
    from(rootProject.file("alterego/LICENCE")) {
        into("META-INF")
    }
    from(rootProject.file("alterego/NOTICE")) {
        into("META-INF")
    }
}

spotbugs {
    excludeFilter = rootProject.file("config/spotbugs/exclude-effigies.xml")
}
