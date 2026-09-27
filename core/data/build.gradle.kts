// 绕梁 Raoliang · :core:data
// Room3 曲库 + DataStore 设置 + MediaStore 同步管道。
// T1 拆线自 app 模块整包搬入（ADR-0004 D3：只搬包不改类；schemas 目录随 db 包跟走）。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.echomusic.app.core.data"
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

ksp {
    // Room3 schema 导出（升级校验与迁移测试的依据，路径随模块走）
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))

    // Room3（ADR-0004 D4）
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)

    // Preferences DataStore（ADR-0004 D4：PlaybackSettings）
    implementation(libs.androidx.datastore.preferences)

    // 协程（仓库层直接 import，显式声明）
    implementation(libs.kotlinx.coroutines.android)

    // Koin（DataModule 装配，androidContext() 需要 koin-android）
    implementation(libs.koin.android)

    // JVM 单测：Room3 内存库 + 内置 SQLite 驱动（BREAKDOWN §3.1）
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room3.runtime)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
}
