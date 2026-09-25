plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)

}

android {
    namespace = "com.example.smartmicrogrid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartmicrogrid"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    // ===== Core (already there) =====
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    // ===== Lifecycle + ViewModel =====
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)

    // ===== Navigation =====
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // ===== Retrofit + OkHttp + Gson (networking) =====
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // ===== Room (SQLite) =====
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ===== Google Maps =====
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // ===== QR (ZXing) =====
    implementation(libs.zxing.embedded)
    implementation(libs.zxing.core)

    // ===== Glide (image loading) =====
    implementation(libs.glide)

    // ===== Coroutines =====
    implementation(libs.coroutines.android)

    // ===== Security Crypto (encrypted JWT storage) =====
    implementation(libs.security.crypto)

    // ===== Testing (already there) =====
    testImplementation(libs.junit)

    // ===== Unit testing (JVM tests in app/src/test — no emulator needed) =====
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.arch.core.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}