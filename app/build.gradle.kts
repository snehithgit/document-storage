plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val vaultKeystorePath = System.getenv("VAULT_KEYSTORE_PATH")
val vaultStorePassword = System.getenv("VAULT_STORE_PASSWORD")
val vaultKeyAlias = System.getenv("VAULT_KEY_ALIAS")
val vaultKeyPassword = System.getenv("VAULT_KEY_PASSWORD")
val releaseKeysAvailable = listOf(vaultKeystorePath, vaultStorePassword, vaultKeyAlias, vaultKeyPassword).all { !it.isNullOrBlank() }
android {
    namespace = "com.snehith.documentvault"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.snehith.documentvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "2.3.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseKeysAvailable) {
            create("vaultRelease") {
                storeFile = file(vaultKeystorePath!!)
                storePassword = vaultStorePassword
                keyAlias = vaultKeyAlias
                keyPassword = vaultKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = providers.gradleProperty("vaultDebugSuffix").getOrElse(".v2")
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (releaseKeysAvailable) signingConfig = signingConfigs.getByName("vaultRelease")
        }
    }
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/*.kotlin_module")
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.work:work-testing:2.10.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.opencv:opencv:4.10.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")
}
