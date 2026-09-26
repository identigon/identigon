plugins {
    `java-library`
    `maven-publish`
}

// Quality plugins, jar LICENCE packaging, and the shared publishing setup (sources/javadoc jars,
// POM, repository, signing) come from the root build.gradle.kts. Version is lockstep, also from the
// root (docs/adr/0024-lockstep-versioning.md).

group = "org.identigon"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // alterego is exposed through Incognito's public API (e.g. PipelineContext.alterEgo()), so it
    // is `api`, not `implementation` - consumers writing custom stages compile against its types.
    api(project(":alterego"))

    // Declarative YAML policy parser - an internal detail (see PLAN.md for splitting it out).
    implementation(libs.snakeyaml)

    // Testing dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher) // required by the Gradle 9.x test runner
    testImplementation(libs.h2)

    // Testcontainers for PostgreSQL integration testing (v1.0 Tier-1 engine).
    // 2.x is required for Docker Engine 29.x (older docker-java probes API 1.32, which the daemon
    // rejects; needs >=1.40). NOTE 2.x renamed the module artifacts (testcontainers-* prefix) and
    // moved PostgreSQLContainer to package org.testcontainers.postgresql.
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    // PostgreSQL JDBC driver - the integration tests connect via raw DriverManager.
    testRuntimeOnly(libs.postgresql)

}

tasks.test {
    // The PostgreSQL image tag, shared with the quickstart and bumped there by dependabot.
    val postgresDockerfile = rootProject.file("quickstart/postgres/Dockerfile")
    inputs.file(postgresDockerfile)
    systemProperty("identigon.postgresDockerfile", postgresDockerfile.absolutePath)
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "incognito"
            pom {
                name = "Incognito"
                description = "A Java library that clones a production database into a schema-identical " +
                    "test database with all PII replaced by clearly fictional data."
            }
        }
    }
}
