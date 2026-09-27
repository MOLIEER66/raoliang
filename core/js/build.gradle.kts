// 绕梁 Raoliang · :core:js
// JS Engine 沙箱运行时（ADR-0003 主选：androidx.javascriptengine 1.1.0）。
// 信任边界模块：洛雪脚本 = 完全不可信第三方代码，只允许在本模块的沙箱 isolate 内执行；
// 唯一出口 = LxBridge 白名单网络桥（实现在 :core:source，域名全量审计）。
// T1 建空骨架（依赖先接通验证打包），T2 填 JsEngineRuntime + lx-prelude.js。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.echomusic.app.core.js"
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
    // 实现 :core:source 的 LxRuntime 接口（ADR-0004 D3 接口位）
    api(project(":core:source"))

    // JS Engine（ADR-0003 主选：沙箱在 WebView Provider 进程，APK 零增重，16KB 页无风险）
    implementation(libs.androidx.javascriptengine)

    // JVM 单测（T2 起：预置胶水与端口分发器的假体用例）
    testImplementation(libs.junit)
}
