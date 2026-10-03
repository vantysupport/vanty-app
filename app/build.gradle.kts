import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Firebase Cloud Messaging (avisos al instante). Lee app/google-services.json
    alias(libs.plugins.google.services)
}

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun cfg(key: String, default: String = ""): String = local.getProperty(key) ?: System.getenv(key) ?: default

// Firma para Google Play: crea keystore.properties en la raíz del proyecto (ver README.md).
val keystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "xyz.vanty.aba"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.vanty.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // Valores públicos de Vanty (la clave "anon" es pública por diseño: los datos los protege RLS en Supabase).
        // Se pueden cambiar en local.properties; nunca pongas aquí la service_role ni otras claves secretas.
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("SUPABASE_URL", "https://ylcnfqkhivqwjeifuhbl.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${cfg("SUPABASE_KEY", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlsY25mcWtoaXZxd2plaWZ1aGJsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAxMTQyMDksImV4cCI6MjEwNTY5MDIwOX0.zyhHMD2G0Rb-cmpldp6TO1fhrkRsfTLW2txMythLMQ8")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${cfg("API_BASE_URL", "https://vanty.xyz")}\"")
    }

    signingConfigs {
        if (!keystore.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!keystore.isEmpty) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)

    // Solo Cloud Messaging: sin Analytics ni otros servicios de Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
