// 绕梁 Raoliang · :core:playback
// Media3 播放引擎（MediaSessionService + ExoPlayer）与 MediaController 桥。
// T1 拆线自 app 模块整包搬入（ADR-0004 D3：只搬包不改类）。
// M2 T6 预告：ResolvingDataSource 在此挂 SourceManager（D1 锚点，届时加 :core:source 依赖）。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.echomusic.app.core.playback"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))

    // Media3（ADR-0004 D1 播放内核）
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // 协程（桥层 StateFlow / serviceScope 直接 import，显式声明）
    implementation(libs.kotlinx.coroutines.android)

    // Koin（PlaybackModule 装配）
    implementation(libs.koin.android)

    // JVM 单测（桥层假体：MediaItem 工厂 / 模式策略 / 控制器逻辑）
    testImplementation(libs.junit)
}
