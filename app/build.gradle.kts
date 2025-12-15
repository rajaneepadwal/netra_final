plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.level_1_app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.level_1_app"
        minSdk = 24
        targetSdk = 35
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
    // Add this to see deprecation warnings
    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true  // Enable ViewBinding for easier UI access
    }
}

dependencies {
        // Core Android
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.activity.compose)

        implementation("androidx.constraintlayout:constraintlayout:2.1.4")

        // Jetpack Compose
        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.ui)
        implementation(libs.androidx.ui.graphics)
        implementation(libs.androidx.ui.tooling.preview)
        implementation(libs.androidx.material3)

        // Material Design Components (for traditional XML layouts)
        implementation("com.google.android.material:material:1.11.0")

        // ===== OPENCV FOR COMPUTER VISION =====
        implementation("com.quickbirdstudios:opencv:4.5.3.0")

        // ===== ML KIT FOR FACE DETECTION (Pre-trained Google Model) =====
        implementation("com.google.mlkit:face-detection:16.1.6")

        // ===== CAMERAX FOR CAMERA ACCESS =====
        implementation("androidx.camera:camera-camera2:1.3.1")
        implementation("androidx.camera:camera-lifecycle:1.3.1")
        implementation("androidx.camera:camera-view:1.3.1")

        // ===== TENSORFLOW LITE (For Future Custom Models) =====
//        implementation("org.tensorflow:tensorflow-lite:2.14.0")
//        implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
//        implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
        //==for joystick==
    implementation("com.github.Krusshnaa:Joystick_Lib:1.0")

        // ===== TESTING =====
        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.ui.test.junit4)
        debugImplementation(libs.androidx.ui.tooling)
        debugImplementation(libs.androidx.ui.test.manifest)
        implementation("androidx.appcompat:appcompat:1.7.1")
    }


