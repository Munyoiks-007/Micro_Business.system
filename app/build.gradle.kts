// App module build.gradle.kts (:app)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val darajaSandboxConsumerKey = providers.gradleProperty("DARAJA_SANDBOX_CONSUMER_KEY")
    .orElse("").get()

val darajaSandboxConsumerSecret = providers.gradleProperty("DARAJA_SANDBOX_CONSUMER_SECRET")
    .orElse("").get()

val darajaSandboxShortCode = providers.gradleProperty("DARAJA_SANDBOX_SHORT_CODE")
    .orElse("").get()

val darajaSandboxPassKey = providers.gradleProperty("DARAJA_SANDBOX_PASS_KEY")
    .orElse("").get()

val darajaSandboxCallbackUrl = providers.gradleProperty("DARAJA_SANDBOX_CALLBACK_URL")
    .orElse("").get()
val appBuildId = System.currentTimeMillis().toString()

android {
    namespace = "com.example.microbusinesssystem"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.microbusinesssystem"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DARAJA_SANDBOX_CONSUMER_KEY", "\"$darajaSandboxConsumerKey\"")
        buildConfigField("String", "DARAJA_SANDBOX_CONSUMER_SECRET", "\"$darajaSandboxConsumerSecret\"")
        buildConfigField("String", "DARAJA_SANDBOX_SHORT_CODE", "\"$darajaSandboxShortCode\"")
        buildConfigField("String", "DARAJA_SANDBOX_PASS_KEY", "\"$darajaSandboxPassKey\"")
        buildConfigField("String", "DARAJA_SANDBOX_CALLBACK_URL", "\"$darajaSandboxCallbackUrl\"")
        buildConfigField("String", "APP_BUILD_ID", "\"$appBuildId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        dataBinding = false
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Firebase BoM & Auth & Firestore
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
