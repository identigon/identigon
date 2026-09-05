// Applied here (not `apply false`) and re-applied to every subproject by `subprojects { }` below
// via `apply(plugin = "...")`. A subproject then has nothing to declare for any of these: it
// cannot end up missing Checkstyle/PMD/JaCoCo/Spotless/SpotBugs by forgetting a plugin id, the way
// an earlier version of this file required. Versions themselves live in gradle/libs.versions.toml.
//
// Root produces no artifact of its own - it is a pure aggregator; applying these to it too is
// harmless (none of them have any sourceSet here to act on). See
// docs/adr/0030-standalone-jar-assembly-back-in-effigies.md for why the standalone jar (once
// assembled here) moved back into effigies/build.gradle.kts instead.
plugins {
    id("checkstyle")
    id("pmd")
    id("jacoco")
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
}

// Lockstep versioning: one version for the whole monorepo, not one per subproject (see each
// subproject's own "lockstep versioning" ADR). Every subproject inherits this rather than
// declaring its own -- there is exactly one place to bump for a release.
//
// The base number below is a deliberate, human decision (bump it by hand for a real release, and
// tag the commit -- see docs/adr/ in any subproject for what "major" means here). Whether a given
// build is that release or a SNAPSHOT of it is NOT a human decision: it's derived from whether HEAD
// is exactly a tagged commit, so nobody has to remember to flip a suffix, and an ordinary push to
// main can never accidentally overwrite an immutable release coordinate.
val baseVersion = "3.2.0"

val isExactlyTagged = providers.exec {
    commandLine("git", "describe", "--tags", "--exact-match")
    isIgnoreExitValue = true
}.result.get().exitValue == 0

version = if (isExactlyTagged) baseVersion else "$baseVersion-SNAPSHOT"

// Whether a Docker daemon that can actually run incognito's Testcontainers PostgreSQL E2Es is
// reachable here - not the same question as "is this windows-latest CI" (the first, wrong version
// of this check: a dev machine running Docker Desktop on Windows gets full coverage same as Linux
// does), and not just "does a daemon answer" either (the second, still-wrong version: confirmed
// against a real failing windows-latest run that `docker info` exits 0 there too - GitHub's hosted
// Windows runners ship Docker preinstalled for Windows containers, so a daemon genuinely answers,
// it just can't run the Linux `postgres` image Testcontainers needs). `docker info`'s own OSType
// field is what actually distinguishes those two "a daemon answered" cases; both "docker isn't
// installed" and "the daemon isn't running" are caught by the same try/catch and treated as
// unavailable too - computed once here, not once per subproject, since it shells out.
val dockerAvailable = try {
    val info = providers.exec {
        commandLine("docker", "info", "--format", "{{.OSType}}")
        isIgnoreExitValue = true
    }
    info.result.get().exitValue == 0
        && info.standardOutput.asText.get().trim().equals("linux", ignoreCase = true)
} catch (e: Exception) {
    false
}

subprojects {
    version = rootProject.version

    // Deferred to afterEvaluate: applying these plugins/reading the version catalog eagerly here
    // would run before this subproject's own build.gradle.kts has been evaluated at all (Gradle
    // evaluates a root `subprojects { }` block's actions before each subproject's own script), at
    // which point the generated `libs` accessor isn't wired up on this project yet. afterEvaluate
    // guarantees the subproject's own script -- and everything it needs -- has already run.
    afterEvaluate {
        // Applied here, not in each subproject's own build.gradle.kts: alterego/incognito/effigies
        // declare only the JVM plugin flavour their own artifact needs (java-library vs
        // application); every quality plugin below is guaranteed on all three by this one block,
        // so a subproject cannot silently end up missing one by omitting a plugin id.
        apply(plugin = "checkstyle")
        apply(plugin = "pmd")
        apply(plugin = "jacoco")
        apply(plugin = "com.diffplug.spotless")
        apply(plugin = "com.github.spotbugs")

        // Spotless: identical full-reflow config for every subproject -- googleJavaFormat().
        // Paired with Checkstyle below, which enforces the Google style rules a formatter alone
        // doesn't (naming, unused imports, brace placement, etc.) rather than just its layout.
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            java {
                googleJavaFormat()
            }
        }

        // Checkstyle: fully identical everywhere -- one shared ruleset
        // (config/checkstyle/checkstyle.xml), nothing left to differ per subproject. No Javadoc
        // module in that ruleset: javac's own doclint
        // (-Xdoclint:all -Xwerror, configured below) already enforces doc-comment completeness on
        // every public element, so a Checkstyle Javadoc check would be redundant.
        configure<org.gradle.api.plugins.quality.CheckstyleExtension> {
            toolVersion = libs.versions.checkstyleTool.get()
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
            isIgnoreFailures = false
        }

        // SpotBugs: toolVersion, ignoreFailures, and report shape are identical everywhere. Only
        // the excludeFilter is genuinely per-subproject (config/spotbugs/exclude-<name>.xml) --
        // the suppressions it encodes don't transfer between subprojects, so that stays declared
        // in each subproject's own build.gradle.kts.
        configure<com.github.spotbugs.snom.SpotBugsExtension> {
            toolVersion = libs.versions.spotbugsTool.get()
            ignoreFailures = false
        }
        tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
            reports {
                create("html") { required = true }
                // xml left unrequired - nothing in this repo consumes it.
                create("xml") { required = false }
            }
        }
        // find-sec-bugs is identical across every subproject too.
        dependencies.add("spotbugsPlugins", libs.findsecbugs.plugin)

        // PMD: fully identical everywhere -- one shared ruleset (config/pmd/ruleset.xml), nothing
        // left to differ per subproject.
        configure<org.gradle.api.plugins.quality.PmdExtension> {
            toolVersion = libs.versions.pmdTool.get()
            isConsoleOutput = true
            isIgnoreFailures = false
            ruleSets = emptyList()
            ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
        }

        // Javadoc/doclint: unconditional, every subproject, whether or not it ships a javadoc jar.
        // Javadoc completeness on a public API is a code-quality question independent of whether
        // that API's docs are ever packaged and published - effigies has no withJavadocJar()/
        // withSourcesJar() (a CLI, not a published library) but still has a public API surface
        // (EffigiesCli.main, SimpleDataSource, PolicyInferrer) worth holding to the same standard.
        // `./gradlew javadoc` at the root is the one command that exercises this everywhere.
        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).apply {
                // The full `all` group (javadoc's own default doclint level -- stated explicitly
                // rather than relied upon) including `missing`: a doc comment / @param / @return /
                // @throws is required on every public element.
                addBooleanOption("Xdoclint:all", true)
                // A doclint warning fails the build instead of a backlog quietly accumulating.
                addBooleanOption("Xwerror", true)
                // javac/javadoc's default -Xmaxwarns is 100: with Xwerror active that silently
                // truncates the reported list rather than the actual violation count. Set high
                // enough that a real regression is never hidden by the cap.
                addStringOption("Xmaxwarns", "1000")
            }
        }

        // JaCoCo: report shape and the check-task wiring are identical for every subproject --
        // applied above, alongside Checkstyle/PMD/Spotless/SpotBugs.
        configure<org.gradle.testing.jacoco.plugins.JacocoPluginExtension> {
            // Pinned to what was already the plugin's own default at the time of pinning
            // (0.8.14) -- this changes nothing about today's behaviour, it just moves the version
            // into the same shared catalog spotbugsTool/pmdTool already use, instead of trusting
            // an unpinned plugin default that could silently change on a Gradle upgrade.
            toolVersion = libs.versions.jacocoTool.get()
        }
        // jacocoTestReport isn't wired into `check` by default; every other quality plugin here
        // (SpotBugs, PMD) already is.
        tasks.named("check") {
            dependsOn(tasks.withType<JacocoReport>())
        }
        tasks.withType<JacocoReport>().configureEach {
            dependsOn(tasks.withType<Test>())
            reports {
                xml.required.set(true)
                html.required.set(true)
                csv.required.set(true)
            }
        }

        // JaCoCo coverage verification - one minimum per module, so a real regression fails
        // `check` instead of a report nobody is required to look at. Each threshold sits a few
        // points below that module's actual instruction coverage when this was added (alterego
        // ~94.9%, incognito ~90.4%, effigies ~77.9%, measured via `./gradlew jacocoTestReport` and
        // summing INSTRUCTION_MISSED/INSTRUCTION_COVERED from each module's jacocoTestReport.csv),
        // so normal fluctuation doesn't fail a build - the point is to catch a regression, not to
        // ratchet coverage upward automatically.
        //
        // incognito is the one module this can't be a single cross-platform number for: its
        // Testcontainers-backed PostgreSQL E2E tests need a Docker daemon, and skip gracefully
        // (JUnit's Testcontainers extension does this on its own) rather than fail when there
        // isn't one - a real, deterministic difference in what's achievable, not flakiness to
        // paper over. Gating on `dockerAvailable` (not OS - see its own comment above) is what
        // keeps this meaningful on a Windows dev machine running Docker Desktop, which gets full
        // coverage same as Linux CI does. A single minimum could only ever be as strict as the
        // no-Docker case allows, which would make it toothless everywhere Docker IS available.
        val minInstructionCoverage =
            when (project.name) {
                "alterego" -> 0.92
                "incognito" -> if (dockerAvailable) 0.88 else 0.55
                else -> 0.75 // effigies
            }

        tasks.withType<JacocoCoverageVerification>().configureEach {
            dependsOn(tasks.withType<Test>())
            violationRules {
                rule {
                    limit {
                        counter = "INSTRUCTION"
                        minimum = minInstructionCoverage.toBigDecimal()
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(tasks.withType<JacocoCoverageVerification>())
        }
    }
}
