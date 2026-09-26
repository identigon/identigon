// Plugin versions live in gradle/libs.versions.toml. `apply false` only puts the third-party
// plugins on the build classpath; the `subprojects` block below applies every quality plugin to
// each subproject exactly once, so no subproject declares (or can forget) any of them. The root
// itself is a pure aggregator and applies none.
plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.spotbugs) apply false
}

// Lockstep versioning (docs/adr/0024-lockstep-versioning.md): one version for the whole monorepo,
// bumped by hand here for a release. Whether a build is that release or a SNAPSHOT of it is derived
// from whether HEAD is exactly a tagged commit, so an ordinary push to main can never overwrite an
// immutable release coordinate.
val baseVersion = "3.2.0"

val isExactlyTagged = providers.exec {
    commandLine("git", "describe", "--tags", "--exact-match")
    isIgnoreExitValue = true
}.result.get().exitValue == 0

version = if (isExactlyTagged) baseVersion else "$baseVersion-SNAPSHOT"

// Whether a Docker daemon that can run incognito's Linux `postgres` Testcontainers image is
// reachable. A daemon answering is not enough: GitHub's hosted Windows runners answer `docker info`
// but only run Windows containers, so the OSType must be linux. A missing binary or stopped daemon
// throws or exits non-zero and counts as unavailable.
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

// The generated `libs` accessor resolves against whichever project is in scope, and inside
// `subprojects` that is a subproject whose own accessor isn't wired up yet - so capture the root's.
val catalog = libs

subprojects {
    version = rootProject.version

    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.spotbugs")

    // Spotless formats; Checkstyle enforces the Google style rules a formatter doesn't (naming,
    // unused imports and the like).
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat()
        }
    }

    // No Javadoc module in the Checkstyle ruleset: doclint (configured below) already enforces
    // doc-comment completeness on every public element.
    configure<org.gradle.api.plugins.quality.CheckstyleExtension> {
        toolVersion = catalog.versions.checkstyleTool.get()
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
    }

    // The exclude filter is the one per-subproject setting: its suppressions don't transfer
    // between subprojects.
    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        toolVersion = catalog.versions.spotbugsTool.get()
        ignoreFailures = false
        excludeFilter = rootProject.file("config/spotbugs/exclude-${project.name}.xml")
    }
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
        reports {
            create("html") { required = true }
            create("xml") { required = false }
        }
    }
    dependencies.add("spotbugsPlugins", catalog.findsecbugs.plugin)

    configure<org.gradle.api.plugins.quality.PmdExtension> {
        toolVersion = catalog.versions.pmdTool.get()
        isConsoleOutput = true
        isIgnoreFailures = false
        ruleSets = emptyList()
        ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    }

    // Doclint on every subproject, whether or not it publishes a javadoc jar: public-API
    // documentation completeness is a code-quality question in its own right.
    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).apply {
            // The full `all` group including `missing`: a doc comment / @param / @return /
            // @throws is required on every public element.
            addBooleanOption("Xdoclint:all", true)
            // A doclint warning fails the build instead of a backlog quietly accumulating.
            addBooleanOption("Xwerror", true)
            // The default cap of 100 would truncate the reported list under Xwerror.
            addStringOption("Xmaxwarns", "1000")
        }
    }

    configure<org.gradle.testing.jacoco.plugins.JacocoPluginExtension> {
        toolVersion = catalog.versions.jacocoTool.get()
    }
    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(true)
        }
    }

    // One minimum per module, a few points below its measured instruction coverage, so a real
    // regression fails `check` without normal fluctuation doing so. incognito's PostgreSQL E2Es
    // skip without a usable Docker daemon, so its minimum depends on `dockerAvailable`: a single
    // number could only be as strict as the no-Docker case allows.
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

    // `check` exists only once the subproject's own script applies its Java plugin.
    pluginManager.withPlugin("java") {
        tasks.named("check") {
            dependsOn(tasks.withType<JacocoReport>())
            dependsOn(tasks.withType<JacocoCoverageVerification>())
        }
    }
}
