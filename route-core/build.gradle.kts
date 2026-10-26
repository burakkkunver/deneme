import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Saf Kotlin rota algoritması: Android'e bağımlı değil, aynı kod
// hem telefonda (çevrimdışı) hem de sunucuda (JVM) çalışabilir.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
