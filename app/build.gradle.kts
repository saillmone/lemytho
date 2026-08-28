import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

// URL du serveur multijoueur. Surchargeable au build :
//   ./gradlew assembleDebug -Plemytho.serverUrl=http://192.168.1.10:3000
val serverUrl = (project.findProperty("lemytho.serverUrl") as String?) ?: "https://lemytho.duckdns.org"

// Version de l'application, réutilisée pour le nom de l'APK généré.
val appVersion = "0.1.1"

android {
    namespace = "com.lemytho.app"
    compileSdk = 35

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.lemytho.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = appVersion
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Nom de l'APK généré : LeMytho-<version>-<debug|release>.apk
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl
                ?: return@forEach
            impl.outputFileName = "LeMytho-$appVersion-${variant.name}.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose (versions pilotées par le BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // AndroidX de base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Room (base de données locale) + KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Multijoueur temps réel (Socket.IO, licence MIT, compatible F-Droid)
    implementation(libs.socket.io.client) {
        // org.json est fourni par Android : on exclut la copie embarquée.
        exclude(group = "org.json", module = "json")
    }

    // Tests unitaires
    testImplementation(libs.junit)
    // org.json est fourni par Android à l'exécution, mais absent en test JVM.
    testImplementation(libs.json)

    // Tooling Compose (uniquement en debug)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
