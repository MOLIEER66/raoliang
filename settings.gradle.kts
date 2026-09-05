// 回声音乐 EchoMusic · 工程设置
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

rootProject.name = "EchoMusic"
include(":app")
