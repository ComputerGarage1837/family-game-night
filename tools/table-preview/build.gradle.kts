// Renders the castle table to PNGs on a desktop JVM (no phone needed), so every CI build
// comes with screenshots. It compiles the app's table/theme code directly against Compose Desktop.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    application
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val appUi = rootProject.file("app/src/main/kotlin/com/familygamenight/app/ui")
val shared = layout.buildDirectory.dir("shared-src")
val copyShared by tasks.registering(Sync::class) {
    from(appUi) {
        include(
            "TableModel.kt", "Components.kt", "theme/Theme.kt",
            "table/Camera.kt", "table/CastleScene.kt", "table/TableScene.kt", "table/Cards.kt", "table/GoFishTable.kt",
            "table/CrazyEightsTable.kt", "table/TableWidgets.kt",
        )
    }
    into(shared.map { it.dir("com/familygamenight/app/ui") })
}
sourceSets.main { kotlin.srcDir(copyShared) }

dependencies {
    implementation(project(":core"))
    val compose = "1.7.3"
    implementation("org.jetbrains.compose.ui:ui-desktop:$compose")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$compose")
    implementation("org.jetbrains.compose.material3:material3-desktop:$compose")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.8.18")
}

application { mainClass.set("PreviewKt") }
