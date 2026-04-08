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
        versionCode = 12
        versionName = "1.3.7"

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

    // Mesin Utama Widget Kita
    implementation("com.batoulapps.adhan:adhan2:0.0.6")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
}