plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "xyz.mulin.tvauto"
    compileSdk = 35

    defaultConfig {
        applicationId = "mulin.tvauto.pro.x5"
        minSdk = 21
        targetSdk = 35
        versionCode = 60
        versionName = "6.0-x5"
        multiDexEnabled = true

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
dependencies {
    implementation(libs.material.v190)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.tencent.tbs:tbssdk:44286")
    implementation("androidx.multidex:multidex:2.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
