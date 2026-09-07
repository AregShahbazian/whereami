import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing config — loaded from key.properties (gitignored).
// Absent on a fresh clone: release builds fall back to debug signing so the project still builds.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.mby4m.whereami"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mby4m.whereami"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Lets the demo build (used for store screenshots) sit alongside the
            // real one. Without it, installing debug over a sideloaded release
            // fails on the signature mismatch.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // play-services-base drags in androidx.fragment 1.1.0, which lint rejects as
    // unsafe for the ActivityResult APIs (older FragmentActivity skipped
    // super.onRequestPermissionsResult). This app has no Fragments at all —
    // MainActivity is a ComponentActivity — but the stale version on the classpath
    // is enough to fail the release lint, so constrain it upward. A constraint
    // rather than a dependency: it only raises the version if something else
    // actually pulls fragment in.
    constraints {
        implementation("androidx.fragment:fragment:1.8.9")
    }

    // Chosen over the platform LocationManager: getCurrentLocation(PRIORITY_HIGH_ACCURACY)
    // is one reliable call on every supported API level, and this artifact pulls in no
    // advertising ID. Rationale recorded in README.md.
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
