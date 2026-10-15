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
        versionCode = 2
        versionName = "1.1.0"
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
            applicationIdSuffix = ".audit"
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (releaseKeysAvailable) signingConfig = signingConfigs.getByName("vaultRelease")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/NOTICE", "META-INF/LICENSE", "META-INF/*.kotlin_module")
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
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
