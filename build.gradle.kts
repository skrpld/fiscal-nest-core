import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    `java-library`
    `maven-publish`
}

group = "io.github.skrpld"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar>().configureEach {
    from(rootDir) {
        include("LICENSE", "NOTICE")
        into("META-INF")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Fiscal Nest Core")
                description.set(
                    "Stateless Kotlin engine that distributes income across expenses, a safety cushion and savings."
                )
                url.set("https://github.com/skrpld/fiscal-nest-core")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("skrpld")
                        name.set("skrpld")
                    }
                }
                scm {
                    url.set("https://github.com/skrpld/fiscal-nest-core")
                    connection.set("scm:git:https://github.com/skrpld/fiscal-nest-core.git")
                }
            }
        }
    }
}
