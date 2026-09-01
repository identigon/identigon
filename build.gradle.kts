// Applied `false` here: this only resolves the plugin classpath once, at the monorepo root, so
// each subproject (alterego, incognito, effigies) can apply these without repeating a version and
// risking drift between them. Versions themselves live in gradle/libs.versions.toml.
//
// Root applies no JVM plugin of its own and produces no artifact - it is a pure aggregator. See
// docs/adr/0030-standalone-jar-assembly-back-in-effigies.md for why the standalone jar (once
// assembled here) moved back into effigies/build.gradle.kts instead.
plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.spotbugs) apply false
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
val baseVersion = "3.1.0"

val isExactlyTagged = providers.exec {
    commandLine("git", "describe", "--tags", "--exact-match")
    isIgnoreExitValue = true
}.result.get().exitValue == 0

version = if (isExactlyTagged) baseVersion else "$baseVersion-SNAPSHOT"

subprojects {
    version = rootProject.version

    // Spotless: identical tidy-only config for every subproject that applies it -- imports,
    // whitespace, EOF newline only, never a full reflow (which would fight the hand-maintained
    // style). Declared once here instead of copy-pasted per subproject.
    plugins.withId("com.diffplug.spotless") {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            java {
                importOrder()
                removeUnusedImports()
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }

    // SpotBugs: toolVersion, ignoreFailures, and report shape are identical everywhere. Only the
    // excludeFilter is genuinely per-subproject (config/spotbugs/exclude-<name>.xml) -- the
    // suppressions it encodes don't transfer between subprojects, so that stays declared in each
    // subproject's own build.gradle.kts.
    plugins.withId("com.github.spotbugs") {
        configure<com.github.spotbugs.snom.SpotBugsExtension> {
            toolVersion = libs.versions.spotbugsTool.get()
            ignoreFailures = false
        }
        tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
            reports {
                create("html") { required = true }
                create("xml") { required = false }
            }
        }
        // find-sec-bugs is identical across every subproject too.
        dependencies.add("spotbugsPlugins", libs.findsecbugs.plugin)
    }

    // PMD: fully identical everywhere -- one shared ruleset (config/pmd/ruleset.xml), nothing left
    // to differ per subproject.
    plugins.withId("pmd") {
        configure<org.gradle.api.plugins.quality.PmdExtension> {
            toolVersion = libs.versions.pmdTool.get()
            isConsoleOutput = true
            isIgnoreFailures = false
            ruleSets = emptyList()
            ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
        }
    }

    // Javadoc/doclint: unconditional, every subproject, whether or not it ships a javadoc jar.
    // Javadoc completeness on a public API is a code-quality question independent of whether that
    // API's docs are ever packaged and published - effigies has no withJavadocJar()/
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
        }
    }

    // JaCoCo: report shape and the check-task wiring are identical wherever the plugin is applied
    // (only alterego, historically -- adding it to incognito/effigies too is a separate, per-
    // subproject decision, not this block's job). toolVersion is left at the plugin's own default.
    plugins.withId("jacoco") {
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
    }
}
