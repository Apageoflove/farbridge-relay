// Phone Mirror Android 应用模块的编译、测试与稳定依赖配置。
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.zj.phonemirror"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zj.phonemirror"
        minSdk = 23
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ALLOW_CLEARTEXT_SERVER", "false")
    }

    val keystoreFile = rootProject.file("keystore/keystore.properties")
    val keystoreProps = if (keystoreFile.exists()) keystoreFile.readLines()
        .filter { it.contains("=") }
        .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    else emptyMap<String, String>()
    signingConfigs {
        create("release") {
            keystoreProps["storeFile"]?.let { storeFile = rootProject.file("keystore/$it") }
            storePassword = keystoreProps["storePassword"]
            keyAlias = keystoreProps["keyAlias"]
            keyPassword = keystoreProps["keyPassword"]
        }
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_CLEARTEXT_SERVER", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    // Pin the cached production client explicitly so offline builds do not
    // fall back to Retrofit's older transitive OkHttp version.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.moshi:moshi:1.15.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.work:work-testing:2.11.2")
}

kapt {
    correctErrorTypes = true
    // Keep the Room schema in source control so future upgrades can ship
    // explicit, testable migrations instead of destructive rebuilds.
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}
