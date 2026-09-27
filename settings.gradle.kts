// 绕梁 Raoliang · 工程设置
// 仓库唯一入口：声明插件与依赖的仓库来源，子模块在这里 include。
pluginManagement {
    repositories {
        // google() 只放 Android/Google 相关构件，加 content 过滤可加快解析、避免误匹配
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS：禁止子模块私自加仓库，依赖来源全局可控
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Raoliang"
include(":app")

// T1 模块拆线（ADR-0004 D3 触发条件兑现：JS 引擎重依赖进 :core:js）
// 依赖方向（编译期单向，首尾不接环）：
//   app → :core:playback / :core:js（装配层）
//   :core:playback → :core:data / :core:source（T6 接入）/ :core:model
//   :core:js → :core:source（实现其 LxRuntime 接口，ADR-0004 D3 接口位）
//   :core:data / :core:source → :core:model
// core.designsystem 与 feature/* 留在 app（UI 层，非信任边界）。
include(":core:model")     // 纯 Kotlin 领域模型（JVM 可单测）
include(":core:data")      // Room3 + DataStore + MediaStore 同步
include(":core:playback")  // Media3 播放引擎与控制器桥
include(":core:source")    // M2：音源协议解析 + SourceManager + LxRuntime/LxBridge 接口（纯 JVM）
include(":core:js")        // M2：JS Engine 沙箱运行时实现（信任边界，ADR-0003）
