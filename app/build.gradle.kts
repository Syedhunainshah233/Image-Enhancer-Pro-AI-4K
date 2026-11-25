plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.syedtechdevelopers.imageenhancerpro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.syedtechdevelopers.imageenhancerpro"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
        // Crash reports in Play Console with readable native stack traces.
        ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    assetPacks += listOf(":models")

    // Play Store upload key comes from GitHub Actions secrets; it is never stored in the repo.
    val releaseKeystore = System.getenv("IEP_KEYSTORE")

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("IEP_KEYSTORE_PASSWORD")
                keyAlias = "imageenhancer"
                keyPassword = System.getenv("IEP_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Google's official TEST ads: safe to click, never earn money.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "AD_APP_OPEN", "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("String", "AD_BANNER", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "AD_REWARDED", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "AD_REWARDED_INTERSTITIAL", "\"ca-app-pub-3940256099942544/5354046379\"")
            buildConfigField("String", "AD_NATIVE", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
        release {
            // Real Image Enhancer Pro ad units (AdMob account pub-8064134362670148).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-8064134362670148~2100035717"
            buildConfigField("String", "AD_APP_OPEN", "\"ca-app-pub-8064134362670148/9985304130\"")
            buildConfigField("String", "AD_BANNER", "\"ca-app-pub-8064134362670148/7220053344\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"ca-app-pub-8064134362670148/2099075258\"")
            buildConfigField("String", "AD_REWARDED", "\"ca-app-pub-8064134362670148/3280808338\"")
            buildConfigField("String", "AD_REWARDED_INTERSTITIAL", "\"ca-app-pub-8064134362670148/4534627368\"")
            buildConfigField("String", "AD_NATIVE", "\"ca-app-pub-8064134362670148/7272024234\"")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            // R8: shrink + obfuscate; mapping.txt goes inside the AAB for Play Console.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }

    packaging {
        // Native libraries stay uncompressed and 16 KB page aligned (Play requirement for Android 15+).
        jniLibs { useLegacyPackaging = false }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
                it.maxHeapSize = "3g"
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // On-device AI: ONNX Runtime runs Real-ESRGAN + GFPGAN; ML Kit finds faces (works offline).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("com.google.mlkit:face-detection:16.1.7")

    // AdMob + consent (GDPR) form
    implementation("com.google.android.gms:play-services-ads:23.5.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Real-device tests (run on an Android emulator in GitHub Actions)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
}
