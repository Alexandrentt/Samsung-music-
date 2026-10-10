plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        // applicationId estable para toda la historia de la app: NO cambiarlo,
        // o Android lo trataría como una app distinta (biblioteca y descargas
        // invisibles para la nueva instalación).
        applicationId = "com.example"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("debugConfig") {
            // Keystore fijo comprometido en el repo para que TODOS los builds
            // (locales y de CI) firmen con el mismo certificado. Sin esto,
            // Android rechazaría el APK nuevo como actualización de la app
            // ya instalada (firma distinta → "App not installed").
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager (background updates)
    implementation(libs.androidx.work.runtime.ktx)

    // OkHttp & Coil
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    // NewPipeExtractor: resolución del stream de audio
    implementation(libs.newpipe.extractor)

    // Conversión real a MP3 (encoder LAME); no basta con cambiar la extensión.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.9")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
