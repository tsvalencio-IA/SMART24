plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.thiaguinhosolucoes.smart24vision"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.thiaguinhosolucoes.smart24vision"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.0.0-live-ia-pilot"

        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyDBFXRrgb7KwNVZArx_Du4DSLEOrKN5Vbw\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"https://smart24-fusion-default-rtdb.firebaseio.com\"")
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
}
