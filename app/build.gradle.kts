// 回声音乐 EchoMusic · app 模块
// M0：空壳工程，只负责"能编译、能安装、显示一行字"。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.echomusic.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.echomusic.app"
        minSdk = 26       // Android 8.0，PRD 目标为 Android 10+，26 留出统计余量
        targetSdk = 36    // Android 16（PRD §7 适配清单）
        versionCode = 1
        versionName = "0.1.0-m0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 签名兜底：项目还没有正式签名密钥，先沿用 debug 签名让 release APK 可直接安装。
            // 正式发版前换成本地/CI 注入的正式 keystore（见 docs/dev-setup.md 已知问题）。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // MainActivity 要显示 BuildConfig.VERSION_NAME
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose：版本全部由 BOM 仲裁，这里不写版本号
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Media3 播放内核（ADR-0001）：M0 仅锁版本，M1 起真正接入
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // 仅调试包需要：IDE 预览渲染
    debugImplementation(libs.androidx.compose.ui.tooling)
}
