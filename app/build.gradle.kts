plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.cyberzilla.islamicwidget"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.cyberzilla.islamicwidget"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "1.5.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Mengaktifkan R8 untuk membuang kode Kotlin/Java yang tidak terpakai
            isMinifyEnabled = true

            // Membuang file XML, Layout, atau Drawable yang tidak terpakai
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Mesin Astronomi Kustom (VSOP87 via astronomy.kt + IslamicAstronomy.kt)

    implementation("com.google.android.gms:play-services-location:21.2.0")


    implementation("androidx.media:media:1.7.1")
}
