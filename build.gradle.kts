// Plugin versions live in settings.gradle.kts; each module applies what it needs.
// (Gradle warns that the Kotlin plugin is loaded per module. That's expected: declaring it here
// would also need the Android plugin here, which the Android-free CORE_ONLY build can't resolve.)
