import java.util.*

plugins {
    kotlin("jvm") version "1.7.20"
}

repositories {
    maven(url = "https://jitpack.io")
    mavenCentral()
}

dependencies {
    val arcHash = Properties(20).apply { load(file("../gradle.properties").inputStream()) }["archash"]
    implementation("com.github.mindustry-antigrief.arc:arc-core:$arcHash")
}