# 回声音乐 EchoMusic（暂定名）

> 一个开源的安卓本地音乐播放器：本体零曲库，支持导入用户自备的「洛雪(LX)自定义音源」脚本获得在线播放能力。工程规范对标大厂开源项目。

🚧 当前状态：**脚手架阶段** —— PRD 与关键决策已定稿，代码骨架待 M0 开工。

- 技术栈：Kotlin + Jetpack Compose + Media3（决策见 [ADR-0001](docs/ADR-0001-tech-stack.md)）
- 目标系统：Android 10+，重点适配 Android 16
- 文档：[产品需求文档 PRD v0.1](docs/PRD-v0.1.md) · [路线图](docs/ROADMAP.md) · [音源插件设计 ADR-0002](docs/ADR-0002-source-plugin.md)

## 它是什么 / 它不是什么

| 它是 | 它不是 |
| --- | --- |
| 播放器：播放本地音乐 + 用户自备音源 | 不内置曲库、不内置任何音源服务器地址 |
| 插件宿主：兼容洛雪音源脚本协议 | 不分发任何音源脚本 |
| 工程示范：CI / 决策记录 / 规范发版 | 不是网易云仿品（无评论 / 无社交 / 无推荐） |

## 计划功能（v1.0）

本地音乐扫描播放 · 后台播放 + 通知栏控制 · 歌词逐行滚动 · 洛雪音源导入与切换 · 歌单管理 · 深色模式 · Android 16 专项适配

## License

MIT

## 开发

### 本地构建

```bash
./gradlew assembleDebug   # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew lint            # Android Lint 静态检查
```

> 零基础环境搭建请从 [docs/dev-setup.md](docs/dev-setup.md) 开始（含真机 USB 调试与常见报错对照表）。

### CI 与下载

- 每次 push 到 `main` / 每个 PR：CI 自动 `assembleDebug` + `lint`，APK 在 [Actions](../../actions) 对应 run 页底部的 **Artifacts**（`EchoMusic-debug-apk`）
- 打 `v*` tag（如 `v0.1.0`）：自动额外构建 release APK 并创建 **GitHub Release** 附上 APK——群友直接去 [Releases](../../releases) 下载，无需登录
- 工作流定义：[.github/workflows/android-ci.yml](.github/workflows/android-ci.yml)

### 文档索引

- 产品与技术决策：[docs/](docs/) —— PRD · 路线图 · ADR · 开发环境指南
- 设计：[design/](design/) —— 设计系统 · 页面清单 · 高保真 mockup

### 许可证

MIT，全文见 [LICENSE](LICENSE)。
