import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

// Applied `false` here: this only resolves the plugin classpath once, at the monorepo root, so
// each subproject (alterego, incognito, effigies) can apply these without repeating a version and
// risking drift between them. Versions themselves live in gradle/libs.versions.toml.
//
// `java-base` (not `apply false`) is applied directly to the root project itself, not offered to
// subprojects: it registers the JVM ecosystem's attribute-matching rules so the root can resolve
// another project's runtime classpath below, without adding a `main` source set or a `jar` task of
// its own the way the full `java`/`application` plugins would - see the `identigonJar` task.
plugins {
    id("java-base")
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
val baseVersion = "1.1.0"

val isExactlyTagged = providers.exec {
    commandLine("git", "describe", "--tags", "--exact-match")
    isIgnoreExitValue = true
}.result.get().exitValue == 0

version = if (isExactlyTagged) baseVersion else "$baseVersion-SNAPSHOT"

// The standalone-runnable fat jar for the whole monorepo (docs/adr/0028-publish-effigies-runnable
// -jar.md). Lives here, not inside effigies/build.gradle.kts: "identigon.jar" is named for
// rootProject.name (this file's own project), not for the effigies subproject, because it bundles
// incognito's and alterego's classes too - no single subproject's build file should be the one
// claiming to speak for the whole product. effigies' own `jar` task stays a normal thin jar (see
// effigies/build.gradle.kts) so it can be published as a real, dependency-safe Maven artifact.
//
// Resolves :effigies's full runtime classpath from outside that project - which transitively
// includes incognito, alterego, and their own external dependencies (SnakeYAML, the Postgres
// driver), since effigies already depends on incognito and alterego arrives transitively - so
// nothing here needs to name incognito/alterego directly. Repositories aren't inherited between
// projects, so this project needs its own even though every subproject already declares the same
// one for its own resolution.
repositories {
    mavenCentral()
}

val identigonJarRuntime = configurations.create("identigonJarRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements::class.java, LibraryElements.JAR),
        )
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
    }
}

dependencies {
    identigonJarRuntime(project(":effigies"))
}

tasks.register<Jar>("identigonJar") {
    // A stable, unversioned filename so `java -jar build/libs/identigon.jar` always works; the
    // version travels in the manifest (Implementation-Version) instead.
    archiveFileName = "identigon.jar"
    manifest {
        attributes["Main-Class"] = "org.identigon.effigies.EffigiesCli"
        attributes["Implementation-Title"] = "Identigon"
        attributes["Implementation-Version"] = project.version.toString()
    }
    // As in effigies' own former override of this task: Gradle can't infer from the from({ ... })
    // closure alone that this task's output depends on :effigies:jar (and, transitively,
    // :incognito:jar/:alterego:jar) having already run -- declare it explicitly so build
    // ordering/up-to-date checks are correct.
    dependsOn(identigonJarRuntime)
    from({
        identigonJarRuntime.map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Signature files from signed dependency jars would otherwise invalidate the merged jar.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.kotlin_module")
    // Both alterego's and incognito's own jars carry a META-INF/LICENCE of their own (different
    // content -- see below), zipTree'd in above along with everything else on the runtime
    // classpath. Drop whichever one the merge would otherwise pick (order/precedence between a
    // zipTree'd entry and an explicit from() is not something to rely on) and add our own
    // deliberately, so which LICENCE/NOTICE end up in this jar is unambiguous.
    exclude("META-INF/LICENCE", "META-INF/NOTICE")
    // alterego's LICENCE, not this root project's plain one, deliberately: this fat jar physically
    // bundles alterego's classes and its OGL-derived dictionary data, so the plain root LICENCE
    // alone would omit the OGL attribution clause that data requires. alterego's LICENCE is a
    // superset -- the same plain MIT text, plus that clause -- so it correctly covers effigies' and
    // incognito's own MIT-only code too.
    from(file("alterego/LICENCE")) {
        into("META-INF")
    }
    from(file("alterego/NOTICE")) {
        into("META-INF")
    }
}

// So a plain top-level `./gradlew build` produces identigon.jar too, the same way it already
// produces every subproject's own jar - nobody has to remember a separate invocation for it.
tasks.named("assemble") {
    dependsOn("identigonJar")
}

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
