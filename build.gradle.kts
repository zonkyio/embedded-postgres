import enforcer.rules.DependencyConvergence
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    `java-library`
    `maven-publish`
    signing
    pmd
    id("dev.yumi.gradle.licenser")
    id("com.gradleup.nmcp")
    id("de.aaschmid.cpd")
    id("org.kordamp.gradle.project-enforcer")
}

val projectName = providers.gradleProperty("name")
val projectGroup = providers.gradleProperty("group")
val projectVersion = providers.gradleProperty("version")

val javaVersion = providers.gradleProperty("java_version")

val embeddedPostgresBinariesVersion = providers.gradleProperty("embedded_postgres_binaries_version")
val commonsCodecVersion = providers.gradleProperty("commons_codec_version")
val commonsCompressVersion = providers.gradleProperty("commons_compress_version")
val commonsIoVersion = providers.gradleProperty("commons_io_version")
val commonsLang3Version = providers.gradleProperty("commons_lang3_version")
val flywayVersion = providers.gradleProperty("flyway_version")
val junit4Version = providers.gradleProperty("junit4_version")
val junit5Version = providers.gradleProperty("junit5_version")
val liquibaseVersion = providers.gradleProperty("liquibase_version")
val mockitoVersion = providers.gradleProperty("mockito_version")
val pmdVersion = providers.gradleProperty("pmd_version")
val postgresqlVersion = providers.gradleProperty("postgresql_version")
val slf4jVersion = providers.gradleProperty("slf4j_version")
val xzVersion = providers.gradleProperty("xz_version")

description = "Embedded PostgreSQL Server"
base.archivesName = projectName.get()
group = projectGroup.get()
version = projectVersion.get()

repositories { mavenCentral() }

dependencies {
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64:${embeddedPostgresBinariesVersion.get()}")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-amd64:${embeddedPostgresBinariesVersion.get()}")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64:${embeddedPostgresBinariesVersion.get()}")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64-alpine:${embeddedPostgresBinariesVersion.get()}")

    implementation("org.slf4j:slf4j-api:${slf4jVersion.get()}")
    implementation("org.apache.commons:commons-lang3:${commonsLang3Version.get()}")
    implementation("org.apache.commons:commons-compress:${commonsCompressVersion.get()}")
    implementation("org.tukaani:xz:${xzVersion.get()}")
    implementation("commons-io:commons-io:${commonsIoVersion.get()}")
    implementation("commons-codec:commons-codec:${commonsCodecVersion.get()}")
    implementation("org.postgresql:postgresql:${postgresqlVersion.get()}")

    compileOnly("org.flywaydb:flyway-core:${flywayVersion.get()}")
    compileOnly("org.liquibase:liquibase-core:${liquibaseVersion.get()}")
    compileOnly("junit:junit:${junit4Version.get()}")
    compileOnly("org.junit.jupiter:junit-jupiter-api:${junit5Version.get()}")

    testImplementation("org.flywaydb:flyway-core:${flywayVersion.get()}")
    testImplementation("org.liquibase:liquibase-core:${liquibaseVersion.get()}")
    testImplementation(platform("org.junit:junit-bom:${junit5Version.get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:${junit4Version.get()}")
    testImplementation("org.mockito:mockito-core:${mockitoVersion.get()}")

    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:${slf4jVersion.get()}")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion.get())
    sourceCompatibility = JavaVersion.toVersion(javaVersion.get().toInt())
    targetCompatibility = JavaVersion.toVersion(javaVersion.get().toInt())
    withSourcesJar()
    withJavadocJar()
}
tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
        sourceCompatibility = javaVersion.get()
        targetCompatibility = javaVersion.get()
        if (javaVersion.get().toInt() > 8) options.release = javaVersion.get().toInt()
    }
    withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        val standardOptions = options as StandardJavadocDocletOptions
        standardOptions.addStringOption("Xdoclint:none", "-quiet")
    }
    withType<JavaExec>().configureEach { defaultCharacterEncoding = "UTF-8" }
    withType<Test>().configureEach {
        defaultCharacterEncoding = "UTF-8"
        useJUnitPlatform()
        if (!Os.isFamily(Os.FAMILY_WINDOWS)) environment("LC_ALL", System.getenv("LC_ALL") ?: "en_US.UTF-8")
    }
    named("check") { dependsOn("cpdCheck") }
}
pmd {
    toolVersion = pmdVersion.get()
    isConsoleOutput = true
    isIgnoreFailures = false
    rulesMinimumPriority = 4
}
cpd {
    toolVersion = pmdVersion.get()
    isIgnoreFailures = true
    minimumTokenCount = 100
    encoding = "UTF-8"
}
enforce {
    rule(DependencyConvergence::class.java) {
        failOnDynamicVersions = true
        failOnChangingVersions = true
        failOnNonReproducibleResolution = true
    }
}
license {
    rule(file("./HEADER"))
    include("**/*.java")
    exclude("**/*.properties")
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = base.archivesName.get()
            version = project.version.toString()
            pom {
                name = projectName
                description = project.description
                url = "https://github.com/zonkyio/embedded-postgres"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        name = "Tomas Vanek"
                        email = "tomix26@gmail.com"
                    }
                    developer {
                        name = "Zonky Developers"
                        email = "developers@zonky.cz"
                    }
                }
                scm {
                    url = "https://github.com/zonkyio/embedded-postgres"
                    connection = "scm:git:https://github.com/zonkyio/embedded-postgres.git"
                    developerConnection = "scm:git:https://github.com/zonkyio/embedded-postgres.git"
                    tag = "HEAD"
                }
            }
        }
    }
}
signing {
    val signingKey = System.getenv("MAVEN_SIGNING_KEY")
    val signingPassphrase = System.getenv("MAVEN_SIGNING_PASSPHRASE")
    if (!signingKey.isNullOrBlank()) {
        isRequired = true
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        sign(publishing.publications)
    } else {
        isRequired = false
    }
}
nmcp {
    publishAllPublicationsToCentralPortal {
        username = System.getenv("USERNAME_TOKEN") ?: ""
        password = System.getenv("PASSWORD_TOKEN") ?: ""
    }
}