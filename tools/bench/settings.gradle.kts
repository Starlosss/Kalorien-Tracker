// Standalone build: the bench must not become part of the app build, but it compiles the app's
// own production sources so the measurement measures the shipped prompt and parser, not a copy.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kalorien-bench"
