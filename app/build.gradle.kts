plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.dentrain"
    compileSdk = 34

    defaultConfig {
        val verificationEndpoint = providers.gradleProperty("denTrainVerificationEndpoint")
            .orElse("https://dentrain-email-verification.dorov-thletics.workers.dev/sendVerificationEmail")
            .get()
        require(verificationEndpoint.isEmpty() || verificationEndpoint.matches(
            Regex("https://[a-z0-9][a-z0-9.-]+/sendVerificationEmail")
        )) { "denTrainVerificationEndpoint must be an HTTPS /sendVerificationEmail URL." }
        buildConfigField("String", "VERIFICATION_ENDPOINT", "\"$verificationEndpoint\"")
        val resetEndpoint = providers.gradleProperty("denTrainPasswordResetEndpoint")
            .orElse(
                verificationEndpoint.ifEmpty {
                    "https://dentrain-email-verification.dorov-thletics.workers.dev/sendVerificationEmail"
                }.replaceAfterLast('/', "sendPasswordResetEmail")
            ).get()
        require(resetEndpoint.matches(Regex("https://[a-z0-9][a-z0-9.-]+/sendPasswordResetEmail"))) {
            "denTrainPasswordResetEndpoint must be an HTTPS /sendPasswordResetEmail URL."
        }
        buildConfigField("String", "PASSWORD_RESET_ENDPOINT", "\"$resetEndpoint\"")
        applicationId = "com.dentrain"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.dentrain.data.DiaryTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    implementation("com.airbnb.android:lottie-compose:6.5.2")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-process:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    // Firebase Hosting links for email actions require a newer Auth SDK than this BoM provides.
    implementation("com.google.firebase:firebase-auth:24.0.1")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Coil для загрузки аватарок
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

ksp {
    arg("room.schemaLocation", file("schemas").path)
}

