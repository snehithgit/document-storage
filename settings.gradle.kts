pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required: cz.adaptech.tesseract4android:tesseract4android:4.9.0
        // is not available on Maven Central and was previously fetched from JitPack.
        maven("https://jitpack.io")
    }
}
rootProject.name = "DocumentVault"
include(":app")
