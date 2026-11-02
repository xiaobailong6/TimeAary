import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
// 缺少完整 Secrets 时不创建签名配置，debug CI 仍可独立运行。
val signingValues = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .associateWith { System.getenv(it).orEmpty() }
val hasSigning = signingValues.values.all { it.isNotBlank() }
// 受控签名作业显式要求密钥，缺失时停止而非静默生成 unsigned 包。
val requireSigning = providers.gradleProperty("requireSigning").orNull == "true"
check(!requireSigning || hasSigning) { "Signing credentials are incomplete; refusing unsigned distribution." }
val expectedCertificate = rootProject.file(".github/signing-certificate.sha256").readText().trim()
check(expectedCertificate.matches(Regex("[0-9a-f]{64}"))) { "Invalid pinned signing certificate." }
android {
    namespace = "local.daybook"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "local.daybook"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName") + "-dev"
        manifestPlaceholders["appLabel"] = "@string/app_label"
        buildConfigField("String", "SIGNING_CERTIFICATE_SHA256", "\"$expectedCertificate\"")
        ndk { abiFilters += "arm64-v8a" }
    }
    // 使用显式目录，避免默认 main 目录和多余包路径层级。
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/code"))
            res.setSrcDirs(listOf("src/resources"))
        }
        getByName("test") { java.setSrcDirs(listOf("tests")) }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (hasSigning) create("distribution") {
            storeFile = file(signingValues.getValue("ANDROID_KEYSTORE_PATH"))
            storeType = "PKCS12"
            storePassword = signingValues.getValue("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = signingValues.getValue("ANDROID_KEY_ALIAS")
            keyPassword = signingValues.getValue("ANDROID_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "@string/app_label_debug"
        }
        release {
            if (hasSigning) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "rules.pro")
        }
    }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    implementation(libs.paging.compose)
    implementation(libs.coroutines.android)
    ksp(libs.room.compiler)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
}
