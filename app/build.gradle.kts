import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.celularsaudavel"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.celularsaudavel"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.0"
    }

    // Chave de TESTE fixa: permite atualizar o app instalado sem desinstalar.
    // Para publicar na Play Store, gere uma chave própria e NÃO a deixe no repositório.
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("keystore/dev.jks")
            storePassword = "celularsaudavel"
            keyAlias = "dev"
            keyPassword = "celularsaudavel"
        }
    }

    // Chave de ENVIO para a Play Store: fica fora do repositório (segredos do GitHub).
    val uploadStore = System.getenv("UPLOAD_KEYSTORE_PATH")
    if (uploadStore != null && file(uploadStore).exists()) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore)
            storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
        }
    }

    flavorDimensions += "loja"
    productFlavors {
        // Versão completa para instalar direto (APK): inclui pastas ocultas e tudo liberado.
        create("full") {
            dimension = "loja"
            buildConfigField("boolean", "FOLDERS_ENABLED", "true")
            buildConfigField("boolean", "PREMIUM_FREE", "true")
        }
        // Versão da Google Play: sem permissões restritas e com assinatura Premium.
        create("play") {
            dimension = "loja"
            buildConfigField("boolean", "FOLDERS_ENABLED", "false")
            buildConfigField("boolean", "PREMIUM_FREE", "false")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("dev")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("dev")
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
