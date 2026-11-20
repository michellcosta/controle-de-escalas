
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
}

// Achado 15 da auditoria (infra): versao.json é a FONTE ÚNICA da versão —
// versionCode/versionName aqui e o aviso de atualização/OTA no GitHub saem
// do mesmo arquivo. Antes eram mantidos à mão em dois lugares; esquecer um
// quebrava o gate de versão (claim) ou não disparava a atualização.
val versaoArquivo = rootProject.file("../versao.json")
val versaoInfo: Map<String, Any> = if (versaoArquivo.exists()) {
    try {
        val json = JsonSlurper().parseText(versaoArquivo.readText()) as Map<*, *>
        val codigo = (json["versaoCodigo"] as? Number)?.toInt() ?: 1
        val nome = json["versaoNome"] as? String ?: "dev"
        mapOf("codigo" to codigo, "nome" to nome)
    } catch (e: Exception) {
        println("⚠️ versao.json inválido (${e.message}); usando fallback")
        mapOf("codigo" to 1, "nome" to "dev")
    }
} else {
    println("⚠️ versao.json não encontrado em ${versaoArquivo.path}; usando fallback")
    mapOf("codigo" to 1, "nome" to "dev")
}

android {
    namespace = "com.controleescalas.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.controleescalas.app"
        minSdk = 24
        targetSdk = 35
        versionCode = versaoInfo["codigo"] as Int
        versionName = versaoInfo["nome"] as String

        vectorDrawables {
            useSupportLibrary = true
        }

        // Achado 3 da auditoria: só os idiomas do app (resources enxutos)
        resourceConfigurations += listOf("pt", "en")
    }

    buildTypes {
        release {
            // Achados 2/3 da auditoria (APK 78MB): R8 + shrink reduzem o dex de
            // ~18MB para ~8MB; ABIs restritas ao hardware real (x86/x86_64 são
            // só emulador — debug mantém todas as ABIs para desenvolvimento)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        encoding = "UTF-8"
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material3
    implementation("androidx.compose.material3:material3")

    // Material Icons Extended (para ícones como Help)
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle / ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore for preferences (session persistence)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Firebase services (versões específicas para evitar problemas de resolução)
    implementation("com.google.firebase:firebase-firestore-ktx:24.9.1")
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.1")
    implementation("com.google.firebase:firebase-functions-ktx:20.3.1")

    // Coroutines para Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // OpenStreetMap (Free alternative to Google Maps)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // ML Kit Text Recognition (OCR para escala por foto)
    // Achado 2 da auditoria (APK 78MB): variante BUNDLED embutia o modelo
    // OCR nativo em 4 ABIs (~38MB). A variante Play Services baixa o modelo
    // via GMS quando necessário — aparelhos reais têm Play Services.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:18.0.0")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.2")
    // journeyapps:zxing-android-embedded REMOVIDA (achado 9): nenhum uso no
    // código — só com.google.zxing:core é usado (QRCodeGenerator/Reader)

    // OkHttp para chamadas HTTP à API Python
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // CameraX for barcode scanner
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    // HorizontalPager for swipe between pages (Accompanist)
    implementation("com.google.accompanist:accompanist-pager:0.32.0")

    // Image Cropper - usando uCrop (mais simples e confiável)
    implementation("com.github.yalantis:ucrop:2.2.8")

    // Google Play Billing para assinaturas
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
