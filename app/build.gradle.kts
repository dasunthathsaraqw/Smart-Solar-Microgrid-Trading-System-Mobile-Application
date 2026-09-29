import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)

}

// ===== API base URL =====
// The C# Web API the app talks to, compiled into BuildConfig.API_BASE_URL (read by Constants).
// Default: http://10.0.2.2:5151/ — the Android emulator's alias for this PC's localhost.
// For a physical phone, override it with the PC's LAN IP without touching Kotlin:
//   API_BASE_URL=http://192.168.1.5:5151/
// in ~/.gradle/gradle.properties (keeps your IP out of git), this project's gradle.properties,
// or on the command line: gradlew installDebug -PAPI_BASE_URL=http://192.168.1.5:5151/
val apiBaseUrl: String = providers.gradleProperty("API_BASE_URL")
    .getOrElse("http://10.0.2.2:5151/")
    .trim()
    // Retrofit only accepts a base URL ending in '/', so add it here rather than crash at runtime.
    .let { if (it.endsWith("/")) it else "$it/" }

// A malformed URL fails the build now instead of crashing on the first API call.
require(apiBaseUrl.startsWith("http://") || apiBaseUrl.startsWith("https://")) {
    "API_BASE_URL must start with http:// or https:// (got \"$apiBaseUrl\")"
}

// ===== Google Maps API key =====
// Read from local.properties, which is gitignored, so a real key never lands in git. Copy
// local.properties.example to local.properties and set MAPS_API_KEY (see README, section 9).
// Injected into AndroidManifest.xml as ${MAPS_API_KEY} through manifestPlaceholders.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "").trim()

// Fallback when no key is set: a deliberately fake but non-empty value. The app still builds and
// runs, and the map area just shows no tiles. It is not left empty because the Maps SDK treats an
// empty key as "API key not found" and can crash the map screen.
val mapsApiKeyOrPlaceholder: String = mapsApiKey.ifEmpty { "MAPS_API_KEY_NOT_SET" }
if (mapsApiKey.isEmpty()) {
    logger.warn(
        "MAPS_API_KEY is not set in local.properties: the Nearby Stations map will show no " +
            "tiles. See README, section 9, \"Google Maps API key\"."
    )
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

        // Set in defaultConfig so debug and release builds both get it (see apiBaseUrl above).
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        // Fills ${MAPS_API_KEY} in AndroidManifest.xml (see mapsApiKey above).
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKeyOrPlaceholder
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