// 绕梁 Raoliang · 根构建脚本
// 版本统一在 gradle/libs.versions.toml（Version Catalog）里管理，这里只声明插件"不应用"。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
