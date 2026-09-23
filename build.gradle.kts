// Plugin versions live in settings.gradle.kts. Declaring the Kotlin plugins here (not applied)
// makes every module share one copy of them.
plugins {
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}
