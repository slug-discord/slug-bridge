plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "bot.slug.bridge"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("slug-bridge.jar")
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
