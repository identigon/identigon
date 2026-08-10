// Applied `false` here: this only resolves the plugin classpath once, at the monorepo root, so
// each subproject (alterego, incognito, effigies) can `id(...)` these without repeating a version
// and risking drift between them.
plugins {
    id("com.diffplug.spotless") version "8.8.0" apply false
    id("com.github.spotbugs") version "6.5.9" apply false
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
val baseVersion = "1.0.0"

val isExactlyTagged = providers.exec {
    commandLine("git", "describe", "--tags", "--exact-match")
    isIgnoreExitValue = true
}.result.get().exitValue == 0

version = if (isExactlyTagged) baseVersion else "$baseVersion-SNAPSHOT"

subprojects {
    version = rootProject.version
}
