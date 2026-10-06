val smart24KeystorePath = providers.environmentVariable("SMART24_KEYSTORE_PATH").orNull
val smart24KeystorePassword = providers.environmentVariable("SMART24_KEYSTORE_PASSWORD").orNull

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
        versionCode = 12
        versionName = "3.3.2-auto-update"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyDBFXRrgb7KwNVZArx_Du4DSLEOrKN5Vbw\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"https://smart24-fusion-default-rtdb.firebaseio.com\"")

        val yooseeAppId = providers.gradleProperty("YOOSEE_APP_ID").orElse("").get()
        val yooseeAppToken = providers.gradleProperty("YOOSEE_APP_TOKEN").orElse("").get()
        val yooseeAppVersion = providers.gradleProperty("YOOSEE_APP_VERSION").orElse("").get()
        buildConfigField("String", "YOOSEE_APP_ID", "\"${yooseeAppId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "YOOSEE_APP_TOKEN", "\"${yooseeAppToken.replace("\"", "\\\"")}\"")
        buildConfigField("String", "YOOSEE_APP_VERSION", "\"${yooseeAppVersion.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        if (!smart24KeystorePath.isNullOrBlank() && !smart24KeystorePassword.isNullOrBlank()) {
            create("smart24Release") {
                storeFile = file(smart24KeystorePath)
                storePassword = smart24KeystorePassword
                keyAlias = "smart24"
                keyPassword = smart24KeystorePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("smart24Release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
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

    // Player RTSP genérico. Não depende do aplicativo do fabricante da câmera.
    implementation("org.videolan.android:libvlc-all:3.7.0")
    implementation("com.p2p.core:p2p-core:0.4.4.9")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
