// 绕梁 Raoliang · :core:model
// 纯 Kotlin 领域模型（Song/Album/PlayMode），零 Android 依赖 → 整包 JVM 单测。
// T1 拆线自 app 模块整包搬入（ADR-0004 D3：只搬包不改类）。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        // 与 app / 各 android 模块的 jvmTarget 17 保持一致（CI JDK 17 toolchain）
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 零第三方依赖是本模块的纪律（model 层不许 import 任何框架）
}
