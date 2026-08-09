// Applied `false` here: this only resolves the plugin classpath once, at the monorepo root, so
// each subproject (alterego, incognito, effigies) can `id(...)` these without repeating a version
// and risking drift between them.
plugins {
    id("com.diffplug.spotless") version "8.8.0" apply false
    id("com.github.spotbugs") version "6.5.9" apply false
}
