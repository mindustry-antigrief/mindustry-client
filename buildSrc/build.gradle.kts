plugins {
    kotlin("jvm") version "1.7.0"
}

repositories {
    maven(url = "https://jitpack.io")
    mavenCentral()
}

dependencies {
    implementation("com.github.mindustry-antigrief.arc:arc-core:840192b7f4")
}