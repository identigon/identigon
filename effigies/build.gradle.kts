plugins {
    application
    // Minimal code hygiene, kept in step with the sibling repos (../lib-incognito, ../lib-alterego).
    // Tidy-only (no googleJavaFormat) so it does not reflow the hand-maintained style.
    id("com.diffplug.spotless") version "8.8.0"
    id("com.github.spotbugs") version "6.5.9"
}

group = "org.identigon"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    // The CLI entry point. Effigies is a thin authoring/orchestration front-end above lib-incognito;
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
    mavenCentral()
    // lib-incognito (and, transitively, lib-alterego) are consumed as local -SNAPSHOTs until they are
    mavenLocal()
    // lib-incognito and lib-alterego are published to GitHub Packages. GitHub Packages requires
    // authentication even to READ, so a token with `read:packages` must be on the environment as
    // GITHUB_ACTOR/GITHUB_TOKEN — in CI the automatic token, locally a PAT. Credentials resolve to
    // null when unset, so this repo is simply skipped when the artifact is already available above.
    maven {
        name = "IncognitoGitHubPackages"
        url = uri("https://maven.pkg.github.com/identigon/lib-incognito")
        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").orNull
            password = providers.environmentVariable("GITHUB_TOKEN").orNull
        }
    }
    maven {
        name = "AlterEgoGitHubPackages"
        url = uri("https://maven.pkg.github.com/identigon/lib-alterego")
        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").orNull
            password = providers.environmentVariable("GITHUB_TOKEN").orNull
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // The orchestration engine. Effigies depends ONLY on lib-incognito (lib-alterego arrives
    // transitively and is not called directly). Pinned to the current published version; it moves to
    // 2.0.x once lib-incognito 2.0 lands (which removes the inference that migrates here — ADR 0001).
    implementation("org.identigon:incognito:1.1.0-SNAPSHOT")

    // Reads/writes the declarative policy YAML that lib-incognito consumes.
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
// — the runtime classpath (lib-incognito, lib-alterego, snakeyaml, JDBC drivers) is bundled in. No
// shadow plugin needed; plain Gradle assembles it. Signature files from signed dependency jars are
// dropped, as they would otherwise invalidate the merged jar.
tasks.jar {
    // A stable, unversioned filename so `java -jar build/libs/effigies.jar` always works; the
    // version travels in the manifest (Implementation-Version) instead.
    // The published/runnable name drops the repo's "app-" prefix, mirroring how lib-alterego and
    // lib-incognito publish as `alterego`/`incognito` rather than their repo names.
    archiveFileName = "effigies.jar"
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Effigies"
        attributes["Implementation-Version"] = project.version.toString()
    }
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
