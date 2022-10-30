plugins {
    kotlin("jvm") version "1.7.10"
}

repositories {
    maven(url = "https://jitpack.io")
    mavenCentral()
}

dependencies {
    if(!project.hasProperty("release") && File(rootDir.parent, "Arc").exists()) // if localArc
        implementation("com.github.zxtej:arc-core")
    else implementation("com.github.mindustry-antigrief.arc:arc-core:840192b7f4")
}