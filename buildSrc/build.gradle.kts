import java.util.*

plugins {
    kotlin("jvm") version "1.8.20"
}

repositories {
    maven(url = "https://jitpack.io")
    mavenCentral()
}

dependencies {
    val arcHash = Properties(20).apply { load(file("../gradle.properties").inputStream()) }["archash"]
    val localArc = File(rootDir.parentFile.parent, "Arc").exists() && !project.hasProperty("noLocalArc")
    implementation("com.github.mindustry-antigrief${if (localArc) "" else ".Arc"}:arc-core:$arcHash")
}

// I swear gradle is the worst
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    kotlinOptions.jvmTarget = "16"
}

tasks.withType(org.gradle.api.tasks.compile.JavaCompile::class.java){
    targetCompatibility = "16"
}