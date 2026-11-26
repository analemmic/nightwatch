import java.util.Properties

// Companion: ComplicationDataSourceService(s) that publish sun/moon dial angles for the face. No UI.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.analemmic.nightwatch.astro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.analemmic.nightwatch.astro"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release signing: Play upload key, read from keystore.properties at the project root (git-ignored; never commit it).
    // Without that file, release builds come out unsigned. See RELEASE.md.
    val ks = rootProject.file("keystore.properties")
    if (ks.exists()) {
        val p = Properties().apply { ks.inputStream().use { load(it) } }
        signingConfigs.create("upload") {
            storeFile = rootProject.file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        // Owner 2026-11-24: the debug APK is the daily driver; debuggable code runs ~5x slower on the watch (day scrubber
        // ~100 ms/day vs ~20). RefreshReceiver (src/debug) still works; you lose only the debugger and run-as.
        debug { isDebuggable = false }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }
}

dependencies {
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.suncalc)
    testImplementation(libs.junit)
}
