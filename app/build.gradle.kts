plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.willian.mp3pro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.willian.mp3pro"
        minSdk = 33
        targetSdk = 34
        // No GitHub, o número da execução sobe a cada compilação: o Android só atualiza
        // por cima quando o versionCode é maior ou igual ao instalado.
        versionCode = maxOf(2, System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2)
        versionName = "1.1.0"
    }

    signingConfigs {
        // Chave fixa e guardada no projeto: sem ela, cada compilação no GitHub gera uma
        // assinatura nova e o Android recusa instalar por cima da versão anterior.
        create("fixa") {
            storeFile = file("mp3pro.jks")
            storePassword = "mp3pro-app-2026"
            keyAlias = "mp3pro"
            keyPassword = "mp3pro-app-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("fixa")
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
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
