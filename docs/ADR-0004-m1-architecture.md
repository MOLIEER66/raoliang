# ADR-0004 · M1 架构决策：播放引擎 / 音乐库 / 分层 / 持久化 / 图片 / 依赖注入

状态：**草案（Draft），待主控评审**（2026-09-05）
关联：[ADR-0001](ADR-0001-tech-stack.md)（技术栈）· [ADR-0002](ADR-0002-source-plugin.md)（音源协议方向）· [ADR-0003](research/ADR-0003-js-runtime.md)（JS 运行时草案，`LxRuntime` 接口已定义）· [PRD §7/§10](PRD-v0.1.md) · [ROADMAP](ROADMAP.md) M1 · [design/SCREENS.md](../design/SCREENS.md)
任务拆解：见 [M1-BREAKDOWN.md](M1-BREAKDOWN.md)

## 0. 核实方法与版本快照（2026-09-05，全部经 WebSearch/官方页面核实）

| 依赖 | 版本 | 核实来源 |
| --- | --- | --- |
| Media3 | **1.11.0** stable，官方发布页日期 2026-08-05（工程 toml 注释写 07-29，以官方页为准） | [androidx releases/media3](https://developer.android.com/jetpack/androidx/releases/media3) |
| Compose BOM | 2026.06.01（ui 1.11.4 / material3 1.4.0，最后支持 compileSdk 36 的稳定线） | 工程内 `gradle/libs.versions.toml`（M0 已核实） |
| Coil | **3.6.2** stable（2026-09-04） | [Coil changelog](https://coil-kt.github.io/coil/changelog/) |
| Glide | 4.16.0 为最后功能版，长期维护态（社区共识，近年仅补丁） | [r/androiddev 讨论](https://www.reddit.com/r/androiddev/comments/1sip9cg/glide/)、[Coil 官方迁移指南](https://coil-kt.github.io/coil/migrating/) |
| Hilt | **2.60.1**（2026-07-06；2.60 内置 Kotlin 2.3.21） | [google/dagger releases](https://github.com/google/dagger/releases) |
| Koin | **4.1.1** stable（2026 活跃） | [mvnrepository koin-compose-viewmodel-android](https://mvnrepository.com/artifact/io.insert-koin/koin-compose-viewmodel-android/4.1.1) |
| Room | 2.x 最后 stable 2.8.4（2025-11-19）；**新包 androidx.room3 3.0.2 stable（2026-08-26；3.0.0 stable 2026-07-01）** | [androidx releases/room](https://developer.android.com/jetpack/androidx/releases/room)、[releases/room3](https://developer.android.com/jetpack/androidx/releases/room3) |
| DataStore | **1.2.1** stable（2026-03-11；1.3.0 仅 alpha10） | [androidx releases/datastore](https://developer.android.com/jetpack/androidx/releases/datastore) |
| MaterialKolor（HCT） | **5.0.1**（2026-09 初），`com.materialkolor:material-color-utilities` 在 Maven Central，MIT | [jordond/MaterialKolor](https://github.com/jordond/materialkolor)、[Maven Central](https://central.sonatype.com/artifact/com.materialkolor/material-color-utilities) |
| Jetpack JavaScriptEngine | 1.1.0 stable（2026-05-06），独立沙箱进程（M2 用，本文只留接口位） | [ADR-0003 §2](research/ADR-0003-js-runtime.md) |
| Android 16（targetSdk 36） | 预测性返回默认开启、旧 opt-out 失效 | [behavior-changes-16](https://developer.android.com/about/versions/16/behavior-changes-16) |
| Android 14+ 前台服务 | FGS 必须声明类型；mediaPlayback 需 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限 | [fgs-types-required](https://developer.android.com/about/versions/14/changes/fgs-types-required) |
| 音频权限 | Android 13 起 `READ_MEDIA_AUDIO` 细分权限；14 的"部分访问"仅限照片/视频，音频无此态 | [shared/media 官方文档](https://developer.android.com/training/data-storage/shared/media) |

工程现状约束：单模块 `app`（`com.echomusic.app`），AGP 8.13.2 / Kotlin 2.4.10 / minSdk 26 / compileSdk 36，Media3 1.11.0 已在 `gradle/libs.versions.toml` 锁版。

---

## D1 · 播放引擎与后台架构

### 背景与硬约束

- M1 交付面：音频焦点、拔耳机暂停（becoming noisy）、单曲循环/列表循环、通知栏+锁屏控制、后台连续播放。
- Android 14+：前台服务必须声明 `foregroundServiceType="mediaPlayback"`，并声明 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限（[官方](https://developer.android.com/about/versions/14/changes/fgs-types-required)）。
- Media3 1.11.0 两个影响选型的事实（[releases 页](https://developer.android.com/jetpack/androidx/releases/media3)）：1.10.0 起 `MediaSessionService` 改为 `LifecycleService` 且**自定义 `MediaNotification.Provider` 接口出现破坏性变更**（新增必需方法）；1.9.0 起官方发布 `AudioFocusManager` / `AudioBecomingNoisyManager` 供复用、新增 `triggerNotificationUpdate()`。
- M2 衔接（ADR-0002/0003）：在线曲目要先经 `LxRuntime` 拿 `musicUrl`（含可能的 Referer/UA 头），再喂给播放器；多源失败要能自动降级重试。

### 候选方案

| # | 方案 | 一句话描述 |
| --- | --- | --- |
| A | **MediaSessionService + 默认 DefaultMediaNotificationProvider** | media3-session 标准路径：服务内建 `ExoPlayer` + `MediaSession`，通知用官方默认实现，UI 经 `MediaController` 连接 |
| B | MediaSessionService + 自定义 MediaNotification.Provider | 同 A，但完全自绘通知（自建 channel、按钮、MediaStyle 排版） |
| C | 裸写前台 Service + 手工 MediaSession + Notification | 不用 media3-session 的服务封装，自行处理 startForeground、session 回调、通知按钮 PendingIntent |
| D | MediaLibraryService | A 的超集，额外暴露 browsable 媒体树（Android Auto / Wear） |

### 对比矩阵（● 推荐 / ○ 可行 / ✗ 否决）

| 维度 | A：SessionService+默认通知 | B：自定义 Provider | C：裸 Service | D：MediaLibraryService |
| --- | --- | --- | --- | --- |
| 实现成本 | **低**：焦点/耳机/通知/锁屏全部开箱（官方指南：MediaStyle 通知自动带 title/artist/封面） | 中高：自建 channel/布局，且 1.10+ Provider 接口刚发生破坏性变更，网上资料多为旧 API | **极高**：焦点协商、通知按钮、锁屏、onTaskRemoved 全手写，等于重造 Media3 | 低（同 A）+ 需维护 browse tree |
| 面试叙事价值 | 高：能讲清"为什么通知不用我写"（官方默认已产出 MediaStyle + 自动 pendingIntent），以及焦点/becoming-noisy 三个开关背后的系统机制 | 中：多一句"我自定义了通知 Provider"，但容易被追问 1.10 breaking change 细节 | 低（看似深其实错）："我自己实现了通知"在 2026 年是反模式叙事 | 中：多一个"为什么现在不用"的诚实故事 |
| 踩坑风险 | 低：1.11.0 修复了 stopSelf 的 `ForegroundServiceStartNotAllowedException`（#3310）等关键 bug | 中：Provider 破坏性变更 + Android 16 通知行为叠加 | 高：FGS 类型/权限/stale intent（1.10 特意修）任何一步错都"后台被杀" | 低，但 browse tree 无消费者时是死代码 |
| 与 M2 衔接 | **好**：UI 走 `MediaController` 与实现解耦；换 DataSource 不影响 session 契约 | 好（同 A） | 差：在线流的解析点要自己再设计一遍 | 好（同 A） |

### 推荐：方案 A

`PlaybackService : MediaSessionService`，内部：

```kotlin
player = ExoPlayer.Builder(context)
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
        /* handleAudioFocus = */ true)      // 焦点获取/瞬态丢失/永久丢失全部托管
    .setHandleAudioBecomingNoisy(true)      // 拔耳机自动暂停
    .setWakeMode(C.WAKE_MODE_LOCAL)         // 播放期间持锁（1.9.0 起官方发布 WakeLockManager）
    .build()
// 循环模式 = Player.REPEAT_MODE_ONE / REPEAT_MODE_ALL / shuffle 用 setShuffleModeEnabled
```

UI 层用 `MediaController` 连接 session，再桥接为 Compose State（设计稿是深度定制的 Echo Palette 视觉，media3-ui-compose 的现成控件对不上 DESIGN-SYSTEM 规格，故只借其"Player→State"思路自写约 100 行的桥，不强引 media3-ui-compose）。

**M2 在线流衔接设计（本 ADR 锁定的关键接口）**：`MediaItem` 只承载"业务标识"（`mediaId = "{sourceId}:{songId}"` + 元数据），真实 URL 在 DataSource 层解析——`ResolvingDataSource`（包 `DefaultDataSource`）在 `open()` 时回调 `SourceManager.resolveMusicUrl(sourceId, songId)`，可按 DataSpec 注入 Referer/UA 头；单源失败在 `LoadErrorHandlingPolicy`/重试层触发 ADR-0002 的"自动切源"。这样 M1 本地 → M2 在线只换一个 DataSource 工厂，session/通知/队列零改动；P2 的下载缓存（SimpleCache + CacheDataSource）也插在同一位置。

### 被否方案一句话死因

- **B**：为了通知上的几十 dp 像素，接住 1.10.0 刚发生的 Provider 破坏性变更，M1 收益为零。
- **C**：把 Media3 已经解决并被官方指南明文托管的问题（焦点、通知、前台服务合规）全部手工重做一遍，踩坑面最大、面试叙事最差。
- **D**：没有 Auto/Wear 消费者的 browse tree 是死代码；MediaLibraryService 随时可平滑升级（API 超集），M1 不预支。

### 核实来源

- [后台播放官方指南（MediaSessionService/通知/onPlaybackResumption）](https://developer.android.com/media/media3/session/background-playback)
- [Media3 releases（1.9.0/1.10.0/1.11.0 行为）](https://developer.android.com/jetpack/androidx/releases/media3)
- [FGS 类型要求](https://developer.android.com/about/versions/14/changes/fgs-types-required)

---

## D2 · 本地音乐库：扫描与同步

### 背景与硬约束

- 权限：Android 13+ 用 `READ_MEDIA_AUDIO`（细分权限）；minSdk 26 需兼容 26–32 的 `READ_EXTERNAL_STORAGE`（`maxSdkVersion="32"`）。Android 14 的"部分媒体访问"仅针对照片/视频，**音频没有部分授权态**（[官方](https://developer.android.com/training/data-storage/shared/media)）——权限 UX 只有两态：授/不授。
- SCREENS §1 要求三个排序标签：**全部歌曲 / 最近播放 / 最常播放**——后两者需要播放历史与计数数据，纯 MediaStore 实时查询给不了（这是 M1 就必须落库的硬理由）。
- 扫描态/权限态/空态 UI（骨架 + 顶部 2dp 线性进度 + 实时计数）在 SCREENS §1 已定稿。

### 候选方案

| # | 方案 | 一句话描述 |
| --- | --- | --- |
| A | 纯 MediaStore 实时查询 | 每次进库页直接 `ContentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)`，无本地副本 |
| B | **MediaStore 查询 + Room 缓存 + 增量同步** | 首次全量入 Room；之后按 `DATE_MODIFIED > lastSyncAt` 增量 diff（增/改/删） |
| C | 手动文件扫描 | 自行递归存储目录找音频文件（支持 MediaStore 未收录的文件） |

### 对比矩阵

| 维度 | A：纯 MediaStore | B：MediaStore+Room 增量 | C：手动扫描 |
| --- | --- | --- | --- |
| 实现成本 | 低（一次查询） | 中（DAO + diff 逻辑，但 diff 是纯函数可单测） | 高（递归 + MIME 判定 + 自建索引，等于重写 MediaStore） |
| 面试叙事价值 | 低："我调了个 API" | 高：可讲增量同步策略、去重键、失败重试、大列表分页 | 中："我处理了脏数据"但方向与 scoped storage 潮流相反 |
| 踩坑风险 | 中：排序/筛选每次全量执行；封面逐条解码会卡 UI；MediaStore 变更（删除/新下载）不会自动反映 | 低：唯一风险是增量键选错——用 `DATE_MODIFIED`（秒级时间戳）+ `_id` 去重，语义直白 | 高：scoped storage 下无 `READ_MEDIA_AUDIO` 之外的漫游权限，Android 11+ 对非媒体目录受限；音质/标签解析全要自己来 |
| 与 M2 衔接 | 差：在线歌曲（M2）与本地曲目需要在同一列表/队列模型里共存，纯 MediaStore 没有承接在线条目的表 | **好**：`songs` 表用 `source=LOCAL/ONLINE` 统一建模，M2 在线曲直接复用队列与列表 UI | 差（同 A） |

### 推荐：方案 B

- 去重主键：MediaStore `_id`（稳定、系统维护）；`songKey = title|artist|duration` 仅用于跨设备/跨库匹配歌词的辅助键，不做主键。
- 增量同步：记录每卷 `lastSyncAt`，查询条件 `DATE_MODIFIED > lastSyncAt` 做增改；删除检测用"本轮见到的 `_id` 集合 − 库内现有 LOCAL `_id` 集合"。逻辑抽成纯函数 `syncDiff(local, incoming)` 单测覆盖（见 BREAKDOWN T10）。
- 封面：本地曲目优先读**内嵌封面**（ID3/APIC，经 `MediaMetadataRetriever` 下采样到 128px 供取色、44/334dp 供显示），缺失回退 MediaStore 专辑封面，再缺失走 DESIGN-SYSTEM §1.3 的 hash 渐变兜底。封面提取是 IO 密集，统一进 Coil 的自定义 Fetcher（见 D5），不在库页查询里逐条解码。
- Android 16 注意：预测性返回在 targetSdk 36 默认开启（[官方](https://developer.android.com/about/versions/16/behavior-changes-16)），权限页的返回路径要走 `OnBackPressedDispatcher` 系 API，别用已被忽略的旧 opt-out。

### 被否方案一句话死因

- **A**："最近播放/最常播放"两个标签页与 M2 在线曲列表无处安放，省下的两天在 M2 加倍还回。
- **C**：在 scoped storage 时代手工重写 MediaStore，拿到的是更差的数据面和更大的权限面。

### 核实来源

- [Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media)
- [Android 13 细分媒体权限（READ_MEDIA_AUDIO）](https://developer.android.com/about/versions/13/changes/13-behavior-changes-all)
- [Android 16 behavior changes（预测性返回）](https://developer.android.com/about/versions/16/behavior-changes-16)

---

## D3 · 应用分层：单模块 → 多模块的时机

### 背景与硬约束

- 单人 + AI 辅助开发、按周排期可顺延不可跳过（ROADMAP）；M1 只有两周，烂尾风险是 PRD §8 明示的风险。
- M2 引入 `androidx.javascriptengine`（独立沙箱进程，ADR-0003 已定）+ 可选 QuickJS 备选（NDK .so，涉及 16KB 内存页验证）+ OkHttp 桥 + 协议胶水——这是全项目第一个"重"子系统。
- 面试叙事需要"看得懂的架构"，不是"数得出的 module 数"。

### 候选方案

| # | 方案 | 一句话描述 |
| --- | --- | --- |
| A | 单模块分包到底 | 永远 `:app`，靠 package 纪律 |
| B | 现在就多模块（core/data/domain/designsystem + feature/*，对标 Now in Android） | M1 开工即 6–8 个 Gradle module |
| C | **渐进式：M1 单模块 + 硬分包纪律；M2 起按"信任边界"拆 module** | 包结构按未来的模块边界划线，M2 把 `core/source`、`core/js` 抽成独立 module |

### 对比矩阵

| 维度 | A：单模块到底 | B：开工多模块 | C：渐进式 |
| --- | --- | --- | --- |
| 实现成本 | 最低 | 高：单人一周内 30–50% 时间耗在 convention plugin/构建变体上 | 低（M1 与 A 等价）；M2 拆分是"搬包 + 建文件"级成本 |
| 面试叙事价值 | 低（无架构故事） | 中高（有故事但常被追问"为什么 split"答不出收益） | 高：能讲"拆分时机 = 依赖边界出现之时"，M2 正好演示一次真实的抽取 |
| 踩坑风险 | 中：JS 沙箱、协议胶水与 UI 编译耦合，全局单测跑全量；误依赖无编译期拦截 | 中：过早抽象的模块接口与真实需求错位，返工 | 低：唯一纪律成本是 M1 期间守住"包间单向依赖"（靠 CI 里的 ArchUnit/手工 review） |
| 与 M2 衔接 | 差：JS 引擎（WebView 可用性探测）、quickjs 备选的 NDK/16KB 验证、网络桥全挤在 app 包 | 好（隔离天然） | **好且精准**：M2 的模块拆分线恰好 = 信任边界（不可信脚本代码只活在 `:core:js`），16KB 页验证收敛到单模块 |

### 推荐：方案 C

M1 的包结构（即 M2 的模块边界预案，依赖严格单向）：

```
com.echomusic.app
├── core.designsystem      // Echo Palette 令牌、取色器、通用组件（SCREENS 全部复用件）
├── core.model             // Song/Album/Playlist/PlayMode 纯 Kotlin 模型（无 Android 依赖 → 可 JVM 单测）
├── core.data              // Room3 + MediaStore 同步 + 仓库接口实现
├── core.playback          // PlaybackService、PlayerHolder、队列/模式逻辑、DataSource 工厂（M2 换在线源的唯一改动点）
├── core.source            // M2：SourceManager / LxRuntime 接口（M1 只建包与接口，空实现）← ADR-0003 的接口位
├── core.js                // M2：JsEngineRuntime / QuickJsRuntime（独立 module，信任边界）
├── feature.library        // 音乐库屏
├── feature.player         // 正在播放 + 迷你条 + 状态桥
└── app                    // Application/DI 装配 + 导航
```

M1 只建 `core.model/data/playback/designsystem + feature.library/player + app`；`core.source` 在 M1 就放好接口（`SourceManager`、`resolveMusicUrl`），`core.playback` 只依赖接口——这是 D1 的 ResolvingDataSource 设计能"零改动换源"的结构保证。

**M2 拆 module 触发条件（写死，防摇摆）**：当 `core.js` 需要引入第一个非 AndroidX 的重依赖（JS 引擎或 NDK .so）时，即抽 `:core:js` 与 `:core:source` 两个 module——拆分动作只允许"搬包 + 建 build.gradle"，不允许改任何类。

### 被否方案一句话死因

- **A**：M2 的 JS 沙箱与协议胶水是全项目唯一的"重子系统"，让它和 UI 编译耦合等于把 16KB 页验证、依赖树审计、单测速度全部劣化。
- **B**：单人两周的 M1 交付不起 6–8 个 module 的构建基础设施税；Now in Android 的模块数是团队规模和 CI 规模的产物，不是圣经。

### 核实来源

- [Now in Android 架构（官方分模块参照）](https://github.com/android/nowinandroid)
- [Jetpack JavaScriptEngine（独立沙箱进程，ADR-0003 §2/§9 已核实）](https://developer.android.com/jetpack/androidx/releases/javascriptengine)

---

## D4 · 持久化：Room / DataStore 分工

### 背景与硬约束

- 数据面两 类：**关系型**（歌单↔歌曲多对多、播放历史、播放计数、D2 的库缓存）与**键值型**（主题模式、动态取色开关、默认播放模式——SCREENS §6 设置页）。
- Room3（新包 `androidx.room3`）2026-07-01 stable：coroutines-only、KSP-only、FTS5；Room 2.x 最后 stable 为 2.8.4（2025-11-19）（[room3 releases](https://developer.android.com/jetpack/androidx/releases/room3)）。

### 候选方案

| # | 方案 | 一句话描述 |
| --- | --- | --- |
| A | **Room（关系）+ Preferences DataStore（设置）** | 各司其职：SQL 管关系与聚合，DataStore 管偏好快照 |
| B | 纯 Room（设置也建表） | 一切皆表，单一技术栈 |
| C | 纯 DataStore | Preferences/Proto JSON 文件硬扛歌单与历史 |

### 对比矩阵

| 维度 | A：Room+DataStore | B：纯 Room | C：纯 DataStore |
| --- | --- | --- | --- |
| 实现成本 | 低（两者都是声明式，模板量相当） | 低-中：设置表要手写读写/迁移/类型转换 | 高：歌单多对多、计数聚合、事务全手搓 |
| 面试叙事价值 | 高：能讲"按数据形态选存储"的判断题 | 中：常被追问"为什么设置进 SQL"答不出强理由 | 低 |
| 踩坑风险 | 低：注意 DataStore 阻塞读要 `flowOn`/首次读取超时兜底 | 中：设置的原子性与 Flow 组合能力反而弱于 DataStore | 高：DataStore 无查询/无索引/全量重写，歌单上千首即灾难 |
| 与 M2 衔接 | 好：M2 音源注册表（脚本元数据、启用态、审计日志）正好是"关系+文档"混合，Room 存元数据、脚本文件落 filesDir | 中 | 差 |

### 推荐：方案 A，其中 Room 选 **androidx.room3 3.0.2 stable**

- 选 Room3 而非 2.8.4 的理由：新工程零迁移负担，晚选就是欠一次"改包名+全量回归"的债；coroutines-only 与本项目协程风格一致；**KMP 化让 DAO 可以在纯 JVM 上跑内存库单测**（配 `BundledSQLiteDriver`），直接服务 BREAKDOWN 的"无真机质量保障"。风险（stable 仅 2 个月、社区资料少、部分三方库仍是 `androidx.room` 2.x 依赖）登记为 R-D4-1，回退动作 = 换回 2.8.4（DAO 层 API 几乎同构，回退成本半天）。
- DataStore 选 **Preferences DataStore 1.2.1**（设置项 M1 ≤ 6 个，Proto 序列化属过度设计；M4 设置页扩容时再评估 Proto）。
- M1 落表：`songs`（D2 缓存 + source 字段）、`play_history`、`play_counts`（或并入 songs 冗余计数）、`albums` 缓存表（封面键）。歌单两张表 M3 再建（schema 演进走 Room3 migration，M1 末先锁 `schemas` 导出目录）。

### 被否方案一句话死因

- **B**：设置表省一个依赖，代价是每次讨论"偏好"都要过 SQL 这把牛刀，且失去 DataStore 的 corruption 兜底与事务外快照语义。
- **C**：用键值文件承载关系数据，是把 Room 的活儿手写一遍还写得更差。

### 核实来源

- [androidx.room3 releases（3.0.2，2026-08-26）](https://developer.android.com/jetpack/androidx/releases/room3)
- [androidx.room releases（2.8.4，2025-11-19）](https://developer.android.com/jetpack/androidx/releases/room)
- [DataStore releases（1.2.1，2026-03-11）](https://developer.android.com/jetpack/androidx/releases/datastore)

---

## D5 · 图片加载：专辑封面与网络封面

### 背景与硬约束

- 两个消费场景：列表行 44dp 封面 / 播放页 334dp 封面（本地内嵌 artwork）；M2 起音源 `pic` 返回的网络 URL 封面（SCREENS §2 在线态）。
- Echo Palette 取色流水线（DESIGN-SYSTEM §1.1）需要拿**位图**做 HCT 打分——取色是"额外消费一份解码结果"，不是图片库的职责。
- 封面缺失的兜底：按"歌手+曲名"hash 从 8 组回声渐变确定性选色（DESIGN-SYSTEM §1.3）。

### 候选方案

| # | 方案 | 一句话描述 |
| --- | --- | --- |
| A | **Coil 3**（coil-core 3.6.2 + coil-compose + coil-network-okhttp） | Kotlin/Compose 优先，自定义 Fetcher/Interceptor 一等公民 |
| B | Glide 4.16.0 | 老牌稳定，但 Kotlin/Compose 生态附挂（glide-compose 仍是独立 beta 系） |
| C | 自封装（BitmapFactory + LruCache + 手写异步） | 只针对封面，最小依赖 |

### 对比矩阵

| 维度 | A：Coil 3 | B：Glide | C：自封装 |
| --- | --- | --- | --- |
| 实现成本 | 低：`AsyncImage` 即用；自定义 `Fetcher`（内嵌封面按 albumId 提取）有官方扩展点，组件可服务加载自动注册 | 中：Compose 用法依赖第三方整合层；自定义解码链文档偏 Java 时代 | 高起步低维护：本地封面确实简单，但 M2 网络封面（缓存策略、并发去重、失败重试）全要手写 |
| 面试叙事价值 | 高：可讲"为什么 3.1.0 让 AsyncImage 快 25–40%"（[changelog](https://coil-kt.github.io/coil/changelog/)）、Fetcher 抽象如何把"内嵌封面"这个非 URL 数据源纳入统一管道 | 中：成熟稳定的故事，但"维护态"是扣分项 | 低：除非能讲出内存压力与生命周期取消的全套，否则像"不会用轮子" |
| 踩坑风险 | 低：3.x 已 2 年 stable 线，2026 年仍双月发版（3.6.2，2026-09-04） | 中：bumptech 维护态（社区共识），Compose 一等支持缺位 | 高：生命周期取消、内存抖动、OOOM 下采样，每个都是经典事故点 |
| 与 M2 衔接 | **好**：网络封面即 URL，换 `coil-network-okhttp`；与 LxBridge 共用 OkHttp 客户端（连接池/DNS 缓存复用） | 好（能力等价） | 差：M2 要补全套 HTTP |

### 推荐：方案 A + 明确的职责切分

- **Coil 只管"显示"**：`ImageRequest` 统一入口，列表行 `AsyncImage` + 播放页大图；内存缓存键 = `album:{albumId}`（本地）/ URL（M2 在线）。
- **内嵌封面走自定义 `AlbumArtFetcher`**：输入 `albumId` → MediaMetadataRetriever 提取 → 按 DESIGN-SYSTEM §1.1 下采样 128px 出双份（显示位图 + 取色位图）；取色位图交给 `EchoPaletteExtractor`（core.designsystem，K-means + HCT 用 `com.materialkolor:material-color-utilities` 5.0.1，MIT，[Maven Central](https://central.sonatype.com/artifact/com.materialkolor/material-color-utilities)）。取色器纯 JVM 可单测（喂固定色样表断言 seed/glow Hue）。
- 失败兜底链（Coil `error` + 自绘渐变）与 DESIGN-SYSTEM §1.3 一致：灰度封面 → 品牌回声青；无封面 → hash 渐变。

### 被否方案一句话死因

- **B**：选一个处于维护态的库去配一个 Compose-first 的新项目，等于主动放弃官方协同还背上迁移预期。
- **C**：本地封面能凑合，M2 网络封面的缓存/去重/重试让"自封装"变成没有测试的第二个图片库。

### 核实来源

- [Coil 官方 changelog（3.6.2，2026-09-04；3.1.0 性能数据）](https://coil-kt.github.io/coil/changelog/)
- [Coil 官方 Glide 迁移指南](https://coil-kt.github.io/coil/migrating/)
- [Glide 维护态社区证据](https://www.reddit.com/r/androiddev/comments/1sip9cg/glide/)
- [MaterialKolor](https://github.com/jordond/materialkolor)

---

## D6 · 依赖注入：Hilt / Koin / 手写 AppContainer

### 背景与硬约束

- Kotlin 2.4.10（很新）+ KSP 链路是本决策最大的不确定源：Hilt 2.60.1（2026-07-06）内置 Kotlin 2.3.21 / kotlinx-metadata 对 Kotlin 2.2+ 的支持（[dagger releases](https://github.com/google/dagger/releases)），**2.4.x 元数据兼容性必须编译实测**——这是 BREAKDOWN 里 T0 的门禁任务。
- 开发者零基础 + AI 辅助：DI 的"隐形魔法"越多，读懂与讲清的成本越高。
- 面试目标岗位为 Android 客户端校招：Hilt 是行业事实标准（Now in Android、官方文档默认路径）。

### 候选方案

| # | 方案 | 一句话描述 |
| --- | --- | --- |
| A | **Hilt 2.60.1（KSP）** | 编译期生成，@AndroidEntryPoint 覆盖 Application/Service/ViewModel |
| B | Koin 4.1.1 | 纯 Kotlin DSL service locator，无代码生成，可选 annotations |
| C | 手写 AppContainer | Application 里一个容器类 + 手动传递（[官方指南保留此模式](https://developer.android.com/topic/architecture/app-architecture)） |

### 对比矩阵

| 维度 | A：Hilt | B：Koin | C：AppContainer |
| --- | --- | --- | --- |
| 实现成本 | 中：注解集固定、样板少，但首次接通（KSP 版本矩阵、聚合任务）有一次性门槛 | 低：DSL 即写即用，无编译期集成 | 最低：M1 可 1 小时接通 |
| 面试叙事价值 | **高**：Android 岗位默认词表（@HiltViewModel/组件层级/编译期图校验），追问区成熟 | 中：KMP 叙事强，Android 岗叙事偏门；"ServiceLocator vs DI"可讲但属防守 | 低：讲"我懂 DI 本质"可以，但缺工程信号 |
| 踩坑风险 | 中：Kotlin 2.4.10 × KSP × Hilt 三方矩阵需 T0 实测（见上）；运行时一旦配错报错可读性一般 | 低-中：错误推迟到运行时（missing binding 崩在用户手里），测试需额外搭架子 | 低-中：无魔法，但作用域/生命周期纪律全靠自觉，M2 多音源多单例时易腐化 |
| 与 M2 衔接 | 好：`SourceManager`/`LxRuntime` 多实现绑定（@Binds @IntoSet）正是多源冗余需要的图结构 | 好（能力等价） | 中：手写多实现注入会迅速变成工厂大杂烩 |

### 推荐：方案 A（Hilt 2.60.1 + KSP），带明确降级开关

- T0（见 M1-BREAKDOWN）用最小工程验证 `Kotlin 2.4.10 + KSP + Hilt 2.60.1` 编译通过；**失败触发降级评审：直接落 Koin 4.1.1**（不纠结、不硬扛——DI 实现层被 D3 的包结构隔离在 `app` 装配层，替换成本 ≤ 1 天）。本推荐的自洽性依赖该门禁，不影响其余五个决策。
- 使用面收敛为四个注解套路：`@HiltAndroidApp`、`@AndroidEntryPoint`（含 PlaybackService）、`@HiltViewModel`、`@Module @InstallIn`（data/playback/source 三张 module 表）——零基础可读性最优的子集。
- 构建时间影响在单模块 + 小项目下可忽略（KSP 增量编译有效），不构成否决项。

### 被否方案一句话死因

- **B**：把绑定错误从编译期推迟到运行期，对"作品要现场被追问"的项目是防守型劣势；保留为 Hilt 门禁失败时的第一顺位备胎。
- **C**：M2 的多源多实现（LxRuntime × N 份 + 降级策略）会让手写容器长成一个没有编译期校验的 DIY DI 框架。

### 核实来源

- [google/dagger releases（2.60.1，2026-07-06；Kotlin 2.3.21 内置）](https://github.com/google/dagger/releases)
- [Koin 4.1.1（Maven）](https://mvnrepository.com/artifact/io.insert-koin/koin-compose-viewmodel-android/4.1.1) · [Koin 4.1 文档](https://insert-koin.io/docs/4.1/quickstart/android-annotations/)
- [官方应用架构指南（含手动 DI 模式）](https://developer.android.com/topic/architecture/app-architecture)

---

## 1. 推荐组合自洽性核对（六决策互不打架）

| 接缝 | 结论 |
| --- | --- |
| D1×D3 | `core.playback` 只依赖 `core.source` 的接口包（SourceManager/LxRuntime），ResolvingDataSource 是唯一换源点——模块边界与换源边界重合 |
| D2×D4 | 库缓存/最近播放/最常播放落在同一 Room3 库，songs.source 字段为 M2 在线曲目预留，同步 diff 为纯函数可 JVM 测 |
| D5×设计系统 | Coil Fetcher 产出"显示位图 + 128px 取色位图"，Echo Palette 取色器（HCT）纯 JVM，兜底链与 §1.3 一一对应 |
| D6×D1 | PlaybackService 走 `@AndroidEntryPoint`；MediaController 桥不依赖 DI，UI 侧经 ViewModel 消费 |
| D4×D5 | 封面缓存键按 albumId 与 Room 的 albums 表对齐；灰度/缺失兜底由取色器而非图片库实现 |
| D6×D3 | M2 拆 module 时 Hilt module 表随包迁移（@InstallIn 按层次），不产生结构返工 |

## 2. 遗留风险登记

| # | 风险 | 等级 | 缓解 | 触发动作 |
| --- | --- | --- | --- | --- |
| R1 | Kotlin 2.4.10 × KSP × Hilt 2.60.1 矩阵未实测 | 中 | BREAKDOWN T0 半天门禁 | 失败 → Koin 4.1.1，≤1 天替换 |
| R2 | Room3 stable 仅 2 个月，三方生态仍挂 androidx.room 2.x | 中 | M1 仅用基础 DAO/Flow | 失败 → 2.8.4 回退，≤半天 |
| R3 | media3-ui-compose 现成控件与设计稿视觉不匹配，状态桥需自写 | 低 | 桥层 ≤100 行并单测 | 无 |
| R4 | Media3 1.10.0 起自定义 Provider 接口破坏性变更，若 M1 后仍想改通知 | 低 | M1 用默认通知，M4 打磨期再评估 | 届时按 1.11 文档适配 |
| R5 | toml 注释的 Media3 发布日期（07-29）与官方页（08-05）不一致 | 信息 | 本 ADR 以官方页为准；toml 修订归 M1-BREAKDOWN T0（版本目录动工处） | 无 |

---

## 主控评审记录（2026-09-05）

结论：**整体接受，按推荐组合执行**。
- D1 Media3 MediaSessionService + DefaultMediaNotificationProvider + ResolvingDataSource 预留 M2 在线流：接受
- D2 MediaStore + Room 缓存 + DATE_MODIFIED 增量同步：接受
- D3 渐进式单模块硬分包，M2 触发条件"只搬包不改类"：接受
- D4 Room3 + Preferences DataStore 组合：接受
- D5 Coil 3 + 解耦取色器（纯 JVM 可测）：接受
- D6 Hilt **附条件接受**：以 T0 编译门禁实测为准（Kotlin 2.4.10 × KSP × Hilt 2.60.1 矩阵），失败即按既定回退线切 Koin 4.1.1，当天完成不恋战
