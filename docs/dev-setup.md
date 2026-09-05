# 开发环境搭建（零基础照做版）

> 目标：30 分钟内让项目在你电脑上编译通过、在你手机上装上。
> 全程照抄命令与点击路径即可；卡住了先查 [§6 常见报错对照表](#6-常见报错对照表)，再查不了就提 issue。

## 0. 需要准备什么

| 东西 | 说明 |
| --- | --- |
| 一台电脑 | Windows 10+ 或 macOS，磁盘剩余 ≥ 20GB（SDK + 依赖很能吃） |
| 一部安卓真机 | Android 8.0+（本项目 minSdk 26；重点适配 Android 16） |
| 一根数据线 | 注意：很多线只能充电不能传数据，报错先换线 |
| 网络 | 能访问 GitHub（代码与 CI 出包都在这） |

不需要单独装 JDK —— Android Studio 自带，见下节。

## 1. 安装 Android Studio（唯一必装软件）

1. 下载：<https://developer.android.com/studio>（官方直连，一般无需加速）
2. 安装：一路默认下一步。「组件选择」页保持默认即可（模拟器组件可选，本文用真机）
3. 首次启动向导：选 Standard，让它自动下载 Android SDK（几个 GB，慢慢等）
4. **JDK 说明（记住这条就不用到处问了）**：
   - Android Studio 自带 JetBrains Runtime，并内置多版本 JDK 供 Gradle 使用，**无需自己安装 JDK**
   - 本项目要求 JDK 17+（Gradle 9.5 / AGP 8.13 的硬性要求），Studio 默认配置即满足
   - 自查入口：`Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK`，显示 17 或更高即可
   - 只有「打死不装 Studio、纯命令行」的人需要自装：去 <https://adoptium.net> 装 Temurin 17+，并设置 `JAVA_HOME`

## 2. 打开工程

1. 获取代码：

   ```bash
   git clone https://github.com/<你的用户名>/echomusic.git
   ```

2. Android Studio → `File → Open` → 选 **仓库根目录**（认准有 `settings.gradle.kts` 的那一层，**不要**选 `app` 子目录）
3. 等右下角 Gradle Sync 跑完。**首次会下载 Gradle 9.5 与全部依赖，10~30 分钟属于正常**，之后有缓存就快了
4. Sync 成功的标志：左侧项目树能展开，`app` 目录无红色报错

> `local.properties`（记录你本机 SDK 路径）会在打开工程时自动生成，且已被 `.gitignore` 忽略——它不进库是刻意设计，因为每个人路径不同。

## 3. 真机 USB 调试跑起来

1. **解锁开发者模式**：手机 `设置 → 关于手机` → 连点「版本号」7 次 → 提示「已进入开发者模式」
2. **打开 USB 调试**：`设置 → 系统 → 开发者选项`（小米在「更多设置」里，OPPO/一加/真我在「关于设备-版本信息」后于「其他设置」）→ 打开「USB 调试」
   - 小米/红米：还需打开「USB 调试（安全设置）」，否则装不上
3. **连线授权**：数据线连电脑 → 手机弹窗「允许 USB 调试吗？」→ 勾选「一律允许使用这台计算机调试」→ 确定
4. **运行**：Studio 顶部工具栏出现你的手机型号 → 点绿色 ▶（`Run 'app'`）
5. 手机上出现「绕梁」页面 = 环境体检通过 ✅

没弹授权弹窗？下拉通知栏把 USB 用途从「仅充电」改成「传输文件」，或换线。

## 4. 没有 Android Studio？靠 CI 出 APK

仓库每次 push 都会自动编译，你不需要任何本地环境：

1. 把代码 push 到 GitHub（main 分支或任意 PR 都会触发）
2. 打开仓库页 → **Actions** 标签 → 点最新一次「Android CI」
3. 页面底部 **Artifacts** 区 → 下载 `Raoliang-debug-apk`（是个 zip，解压得到 `app-debug.apk`）
   - 注意：下载 artifact 需要登录 GitHub
4. 把 APK 传到手机：微信「文件传输助手」/ 网盘 / 数据线拷贝，任选
5. 手机上点开 APK 安装 → 提示「未知来源应用」时允许即可

**给群友发版**：打 `v*` tag（如 `v0.1.0`）push 后，CI 会自动在 **Releases** 页创建正式发布并附上 APK——Release 附件无需登录 GitHub，直接把链接甩群里。

## 5. 命令行速查（在仓库根目录执行）

| 命令 | 作用 |
| --- | --- |
| `./gradlew assembleDebug` | 编译 debug 包 → `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew assembleRelease` | 编译 release 包（当前 debug 签名兜底，可直接安装） |
| `./gradlew lint` | Android Lint 静态检查（CI 每次都会跑） |
| `./gradlew clean` | 清理全部构建产物 |

Windows 用 `.\gradlew.bat`，macOS/Linux 用 `./gradlew`。

## 6. 常见报错对照表

| 症状 | 原因 | 解法 |
| --- | --- | --- |
| `SDK location not found` | 本机没有 SDK 或路径未配置 | 用 Studio 打开工程一次会自动生成 `local.properties`；手动方式：在该文件里写 `sdk.dir=C:/Users/你/AppData/Local/Android/Sdk`（用正斜杠） |
| 下载 `gradle-9.5.0-bin.zip` 超时/失败 | 国内网络访问 services.gradle.org 慢 | 编辑 `gradle/wrapper/gradle-wrapper.properties`，把 `distributionUrl` 换成腾讯镜像：`https://mirrors.cloud.tencent.com/gradle/gradle-9.5.0-bin.zip`（**仅本机临时用，别提交**） |
| 依赖下载失败 / `Could not resolve ...` | Maven 仓库连接不稳 | 首选开代理；命令行代理写进全局 `~/.gradle/gradle.properties`（在用户主目录，不在仓库里）：`systemProp.https.proxyHost=127.0.0.1`、`systemProp.https.proxyPort=7890`（端口换成你的代理端口） |
| `Unsupported class file major version` / JDK 版本不对 | Gradle JDK 低于 17 | Studio：`Settings → Gradle → Gradle JDK` 切到 17+；命令行：`JAVA_HOME` 指向 Temurin 17 |
| `./gradlew: Permission denied`（macOS/Linux/CI） | gradlew 丢失可执行位 | `git update-index --chmod=+x gradlew` 然后提交（**本项目首次 git init 后必做，见 §7**） |
| CI 报 `/usr/bin/env: 'sh\r': No such file or directory` | Windows 的 CRLF 换行污染了 gradlew | 首次提交前执行 `git config --global core.autocrlf input`；已污染则用编辑器把 gradlew 保存回 LF 再提交 |
| Run 按钮 / 设备列表看不到手机 | 驱动或授权问题 | 换数据线 → 手机改「传输文件」模式 → 撤销 USB 调试授权后重插重授权 → Windows 装厂商驱动 |
| 安装时「解析包失败 / 无法安装」 | APK 传输不完整或手机版本过低 | 重新完整传输；确认手机 Android ≥ 8.0（minSdk 26） |
| 想发正式签名的包 | 目前 release 沿用 debug 签名 | 见 `app/build.gradle.kts` 内注释与 CI 工作流头部 TODO：后续接 secrets 注入正式 keystore |

## 7. 第一次 git 提交检查单

- [ ] `git init` 后立即执行 `git update-index --chmod=+x gradlew`（否则 CI 和 Linux 用户无法运行 gradlew）
- [ ] 提交前设置 `git config --global core.autocrlf input`（防止 gradlew 被 CRLF 污染）
- [ ] `git status` 检查：`local.properties`、`build/`、`.gradle/`、`.idea/`、`.kotlin/` **不在**待提交列表（`.gitignore` 已兜底，出现即说明被改动过）
- [ ] 任何 `*.jks` / `*.keystore` / 密码文件一律不进库
- [ ] 首个 commit + push → 到 Actions 页确认绿灯 → Artifacts 里有 APK = M0 体检完成 ✅
