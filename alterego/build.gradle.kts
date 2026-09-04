plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.spotless) // version pinned at the root
    alias(libs.plugins.spotbugs) // version pinned at the root
    id("pmd")
    id("checkstyle")
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

// Spotless, SpotBugs (toolVersion/ignoreFailures/report shape), PMD, and Checkstyle are configured
// for every subproject from the root build.gradle.kts's `subprojects { }` block. Only the SpotBugs
// excludeFilter is genuinely per-subproject.
spotbugs {
    excludeFilter.set(rootProject.file("config/spotbugs/exclude-alterego.xml"))
}

// JaCoCo's report shape and check-task wiring are shared with every subproject that applies the
// plugin, in the root build.gradle.kts's `subprojects { }` block.

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Javadoc/doclint config (Xdoclint:all + Xwerror) is shared with every subproject that publishes
// a javadoc jar, in the root build.gradle.kts's `subprojects { }` block.

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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
// docs/research/0001-alterego-dictionaries.md, "Attribution placement"). These are alterego's own
// copies (its LICENCE
// carries an extra Open Government Licence clause for the dictionaries/ data, and NOTICE is the
// attribution it points to) -- not the monorepo root's, which is deliberately the plainer, common text.
tasks.named<Jar>("jar") {
    from(file("LICENCE")) {
        into("META-INF")
    }
    from(file("NOTICE")) {
        into("META-INF")
    }
}

// Maven Central itself is not wired up yet (a separate, later decision - see the signing block
// below), but GitHub Packages is: CI publishes every push to main there as a snapshot feed. The
// credentials are read from the environment only, never committed; locally, `./gradlew publish`
// simply has nowhere authenticated to push unless GITHUB_ACTOR/GITHUB_TOKEN are set.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "alterego"

            pom {
                name = "AlterEgo"
                description = "A zero-dependency Java library for deterministic pseudonymisation."
                url = "https://github.com/identigon/identigon/tree/main/alterego"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/identigon/identigon/blob/main/alterego/LICENCE"
                    }
                }
                developers {
                    developer {
                        id = "dconneely"
                        name = "David Conneely"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/identigon/identigon.git"
                    developerConnection = "scm:git:https://github.com/identigon/identigon.git"
                    url = "https://github.com/identigon/identigon/tree/main/alterego"
                }
            }
        }
    }
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

// Maven Central requires every artifact to be PGP-signed. Signing activates only when a key is
// supplied (an ASCII-armored key in SIGNING_KEY, optional passphrase in SIGNING_PASSWORD), so
// local `build` and CI `build` runs - which have no key - are unaffected; a release job sets the
// env vars from secrets. The remaining Central step (which staging endpoint/plugin to publish
// through) is a deliberate, still-open decision, kept out of the build until it is made.
signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
