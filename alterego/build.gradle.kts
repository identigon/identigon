plugins {
    `java-library`
    `maven-publish`
    id("com.diffplug.spotless") version "8.8.0"
    id("com.github.spotbugs") version "6.5.9"
    id("pmd")
    id("jacoco")
}

group = "org.identigon"
version = "0.4.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    // Maven Central requires both alongside the binary jar.
    withSourcesJar()
    withJavadocJar()
}

spotless {
    java {
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs {
    toolVersion = "4.9.8"
    ignoreFailures = false
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    reports {
        create("html") { required.set(true) }
        create("xml") { required.set(true) }
    }
}

tasks.named("check") {
    dependsOn(tasks.withType<com.github.spotbugs.snom.SpotBugsTask>())
    dependsOn(tasks.withType<JacocoReport>())
}

// PMD
pmd {
    toolVersion = "7.22.0"
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSets = emptyList()
    ruleSetFiles = rootProject.files("config/pmd/ruleset.xml")
}

// JaCoCo
tasks.withType<JacocoReport>().configureEach {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        // Enforced from M5 (CLAUDE.md): a doclint warning fails the build instead of the M1-M4
        // backlog quietly accumulating (100 warnings had, by M5, gone unnoticed).
        addBooleanOption("Xwerror", true)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

// LICENCE and NOTICE must travel inside the built artifact: most consumers receive only the
// jar, never the repository, so packaging them at the repo root alone is not enough (see
// docs/dictionaries.md, "Attribution placement").
tasks.named<Jar>("jar") {
    from(rootProject.file("LICENCE")) {
        into("META-INF")
    }
    from(rootProject.file("NOTICE")) {
        into("META-INF")
    }
}

// No repository/credentials are configured here — that's environment-specific and not this
// project's job to commit. This produces a correct, complete POM plus the three artifact jars
// (binary, sources, javadoc) for `./gradlew publishToMavenLocal`; wiring an actual remote
// (Central, GitHub Packages) is a separate, later decision.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "alterego" // the library's own name, distinct from the lib-alterego repo/directory name

            pom {
                name = "AlterEgo"
                description = "A zero-dependency Java library for deterministic pseudonymisation."
                url = "https://github.com/identigon/lib-alterego"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/identigon/lib-alterego/blob/main/LICENCE"
                    }
                }
                developers {
                    developer {
                        id = "dconneely"
                        name = "David Conneely"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/identigon/lib-alterego.git"
                    developerConnection = "scm:git:https://github.com/identigon/lib-alterego.git"
                    url = "https://github.com/identigon/lib-alterego"
                }
            }
        }
    }
}
