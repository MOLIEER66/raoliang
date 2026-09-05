// 绕梁 Raoliang · 根构建脚本
// 版本统一在 gradle/libs.versions.toml（Version Catalog）里管理，这里只声明插件"不应用"。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // T0 门禁接入（ADR-0004 D4）：Room3 注解处理走 KSP。
    // 注：D6 的 Hilt 插件随门禁失败移除（要求 AGP 9+，见 toml 注释），Koin 为纯 DSL 无插件。
    alias(libs.plugins.ksp) apply false
}
