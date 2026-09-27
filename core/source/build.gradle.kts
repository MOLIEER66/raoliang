// 绕梁 Raoliang · :core:source
// M2 音源系统中枢（ADR-0002 协议方向 / ADR-0003 接口位）：洛雪协议解析纯函数、
// SourceManager 多源降级、LxRuntime/LxBridge 接口定义。
// 纯 JVM 模块纪律：协议与降级逻辑零 Android 依赖，全部 JVM 秒级单测。
// T1 建空骨架，T2-T5 填内容。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))

    // JVM 单测（T2 起：标本脚本驱动的协议用例）
    testImplementation(libs.junit)
}
