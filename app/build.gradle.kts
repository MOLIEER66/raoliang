// 绕梁 Raoliang · app 模块
// M0：空壳工程，只负责"能编译、能安装、显示一行字"。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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

    // Lifecycle（2.10.0：最后一个兼容 compileSdk 36 的稳定版，2.11.0 要求 compileSdk 37）
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Koin（ADR-0004 D6 回退落点，门禁结论见 toml 注释）
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Room3（ADR-0004 D4）：新包 androidx.room3，KSP 生成 DAO 实现
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)

    // Preferences DataStore（ADR-0004 D4：设置键值存储，T4 接入）
    implementation(libs.androidx.datastore.preferences)

    // Coil（ADR-0004 D5）：封面加载；3.5.0 回退说明见 toml 注释
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // MaterialKolor HCT 数学库（ADR-0004 D5 / T9 取色器）
    implementation(libs.materialkolor.color.utilities)

    // 仅调试包需要：IDE 预览渲染
    debugImplementation(libs.androidx.compose.ui.tooling)

    // JVM 单测：Room3 内存库 + 内置 SQLite 驱动（免模拟器跑 DAO 用例，BREAKDOWN §3.1）
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room3.runtime)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
}
