plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    application
}

/**
 * The production sources are compiled in directly instead of being copied. Only files that are
 * free of Android classes can be listed here; if a file ever gains an Android import the build
 * breaks loudly, which is the point.
 */
val appSources = file("../../app/src/main/java")

sourceSets {
    main {
        kotlin {
            srcDir(appSources)
            include(
                "com/kalorientracker/app/domain/model/Enums.kt",
                "com/kalorientracker/app/domain/model/Models.kt",
                "com/kalorientracker/app/domain/model/Nutrients.kt",
                "com/kalorientracker/app/domain/usecase/MealUseCases.kt",
                "com/kalorientracker/app/data/analyzer/FoodAnalyzer.kt",
                "com/kalorientracker/app/data/analyzer/GemmaPrompt.kt",
                "com/kalorientracker/app/data/analyzer/DescriptionAnchors.kt",
                "com/kalorientracker/app/data/analyzer/StubFoodAnalyzer.kt",
                "bench/**",
            )
            srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-jvm:0.17.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("bench.BenchKt")
    applicationDefaultJvmArgs = listOf("-Xmx8g")
}
