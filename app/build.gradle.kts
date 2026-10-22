plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mondiro.monkey"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mondiro.monkey"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3.0"
    }

    // Khoá ký lấy từ GitHub Secrets (MONKEY_KEYSTORE, MONKEY_KEY_PASSWORD) để mọi bản build
    // cùng chữ ký -> cài đè / tự cập nhật được. Không có secrets thì ký bằng khoá debug.
    val keystorePath = System.getenv("MONKEY_KEYSTORE_FILE")
    val signPassword = System.getenv("MONKEY_KEY_PASSWORD_CLEAN") ?: System.getenv("MONKEY_KEY_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrEmpty() && !signPassword.isNullOrEmpty() && file(keystorePath).exists()

    signingConfigs {
        if (hasReleaseKey) {
            create("mondiro") {
                storeFile = file(keystorePath!!)
                storePassword = signPassword
                keyAlias = "monkey"
                keyPassword = signPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("mondiro") ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.window:window:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
