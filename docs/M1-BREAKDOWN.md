# M1 任务拆解 · 保底播放器（第 2-3 周）

状态：**草案（待主控评审）** · 依据：[ROADMAP M1](ROADMAP.md) · [ADR-0004](ADR-0004-m1-architecture.md)（六决策推荐组合）· [design/SCREENS.md](../design/SCREENS.md) · [design/DESIGN-SYSTEM.md](../design/DESIGN-SYSTEM.md)
推荐组合速查：MediaSessionService+默认通知（D1）· MediaStore+Room3 增量同步（D2）· 单模块硬分包+M2 拆线（D3）· Room3 3.0.2 + Preferences DataStore 1.2.1（D4）· Coil 3.6.2（D5）· Hilt 2.60.1 带 Koin 降级开关（D6）

## 0. M1 范围（先划清楚不做什么）

**做**：本地音乐扫描（MediaStore）→ 音乐库列表（全部/最近播放/最常播放）→ 点播出声 → 正在播放页（封面/进度/上下首/播放模式）→ 后台播放 + 通知栏/锁屏控制 → Echo Palette 动态取色 v1。

**不做（防蔓延）**：歌单管理（M3）、搜索（M3）、歌词滚动（M2，播放页歌词预览行只显示「纯音乐，请欣赏」占位态，SCREENS §3 已定义该态）、音源脚本（M2）、下载缓存（P2）。

**折中**：底部导航 4 项全部可见，搜索/歌单/设置三屏显示设计稿定义的空态（SCREENS §5.8），既忠实视觉稿又避免导航返工。

## 1. 任务列表（按依赖顺序）

预估含自测。✅ 行为验收标准，全部可勾选。

### T0 · 门禁 spike：版本矩阵验证（0.5 天，卡后续一切）—— ✅ 已完成（2026-09-05）

产出：`gradle/libs.versions.toml` 增补 ksp 2.3.10 / koin 4.1.1 / room3 3.0.2 / datastore 1.2.1 / coil 3.5.0 / materialkolor 4.1.1 / lifecycle 2.10.0 / sqlite-bundled 2.7.0 / junit 4.13.2，最小代码接通 Koin Application + 一个 Room3 DAO（内存库 JVM 单测）+ 一个 Coil `AsyncImage`。

- ✅ `compileDebugKotlin`（KSP 处理器：Room3）在 Kotlin 2.4.10 下零错误通过（原验收的「Room3 + Hilt 双处理器」被门禁改写：Hilt 插件在应用阶段即被 AGP 版本检查拦截，见结论）
- ✅ Coil + coil-network-okhttp 依赖解析通过（3.5.0）；Room3 注解生成物在 IDE 可跳转
- ✅ 顺手：把 toml 注释里的 Media3 发布日期修正为官方页的 2026-08-05（现写 07-29，见 ADR-0004 §2 R5）
- 🔀 降级开关：KSP/Hilt 编译失败 → 当天评审切 Koin 4.1.1（ADR-0004 D6），Room3 失败 → 回退 androidx.room 2.8.4（ADR-0004 D4）
- 📌 **实测结论**：Hilt 门禁失败（2.59+ Gradle 插件强制 AGP ≥ 9.0.0，与锁定的 AGP 8.13.2 冲突，实测报错；回退 Hilt ≤ 2.58 又无法安全消费 Kotlin 2.4.10 元数据），按 D6 既定回退线落 **Koin 4.1.1**，Kotlin 2.4.10 × KSP 2.3.10 × Room3 3.0.2 全链绿。Coil 3.6.x 传递 compose 1.12.0（AAR 实测 minCompileSdk=37/minAGP=9.1）→ 回退 **3.5.0**；materialkolor 5.x（AAR 实测 minCompileSdk=37）→ 回退 **4.1.1**；lifecycle 锁 **2.10.0**（2.11.0 需 compileSdk 37）。以上版本均待 compileSdk 37 + AGP 9.2+ 升级时同批回升。本地 `assembleDebug` + `testDebugUnitTest`（Room3 内存库 + BundledSQLiteDriver，2/2 绿）+ `lint` 全绿；CI 于同一提交触发。

### T1 · 权限与清单（0.5 天，依赖 T0）

产出：manifest 权限与 service 声明齐全。

- ✅ 声明 `READ_MEDIA_AUDIO`（33+）与 `READ_EXTERNAL_STORAGE`（`maxSdkVersion="32"`）
- ✅ 声明 `POST_NOTIFICATIONS`（13+ 运行时权限）、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- ✅ `PlaybackService` 注册：`android:foregroundServiceType="mediaPlayback"`、`exported="true"`、intent-filter `androidx.media3.session.MediaSessionService`（官方后台播放指南要求项）

### T2 · 数据层：Room3 schema 与 DAO（1 天，依赖 T0）

产出：`core.model`（纯 Kotlin）+ `core.data`。

- ✅ 表：`songs`（含 `source` 字段，M1 全为 LOCAL，M2 复用）、`albums`（封面缓存键）、`play_history`、`play_counts`（或并入 songs 冗余字段，二选一并在代码注释说明）
- ✅ DAO 全部返回 `Flow`；`schemas` 导出目录在 gradle 里配好并提交首个 json
- ✅ JVM 内存库测试通过（Room3 + `BundledSQLiteDriver`，不需要模拟器）：插入/查询/计数聚合三条用例

### T3 · MediaStore 同步器（1 天，依赖 T2）

产出：全量 + 增量同步管道。

- ✅ 首次全量：查询 `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`，按 `_id` 去重入库
- ✅ 增量：`DATE_MODIFIED > lastSyncAt` 做增改；删除 = 本轮 `_id` 集合差集；核心 diff 逻辑为纯函数 `syncDiff(current, incoming)`
- ✅ `syncDiff` 纯 JVM 单测：新增/修改/删除/重复 `_id` 四类用例
- ✅ 同步进度可观测（供 SCREENS §1 扫描态的顶部 2dp 线性进度 + 实时计数）

### T4 · 权限流与扫描态（1 天，依赖 T3）

产出：首次启动权限页 + 库页四态（SCREENS §1 状态定义）。

- ✅ 权限弹窗文案与 SCREENS §1 一致（「权限仅用于扫描音乐文件，不会上传」）
- ✅ 四态可用：未授权 / 扫描中（骨架 + 进度 + 计数增长）/ 空（回声波纹插画 + 「扫描本地音乐」主按钮）/ 正常
- ✅ 返回键走 `OnBackPressedDispatcher` 系 API（targetSdk 36 预测性返回默认开启，旧 opt-out 已失效）

### T5 · PlaybackService（1.5 天，依赖 T0，可与 T2-T4 并行）

产出：`core.playback.PlaybackService : MediaSessionService`。

- ✅ `setAudioAttributes(_, handleAudioFocus = true)`：被其他应用抢占→暂停，抢占结束→按系统信号恢复；音频焦点丢失期间不自动抢回
- ✅ `setHandleAudioBecomingNoisy(true)`：拔耳机自动暂停
- ✅ 通知：默认 DefaultMediaNotificationProvider，MediaStyle，13+ 无通知权限时服务不死（内容仍可播，仅通知不显示）
- ✅ `onTaskRemoved`：播放中保持、空闲时 `pauseAllPlayersAndStopSelf`
- ✅ 进程内可播本地 uri（用临时测试入口验证，不等 UI）

### T6 · MediaController 桥 + 迷你播放条（1 天，依赖 T5）

产出：UI↔session 解耦层。

- ✅ `MediaController` 异步连接、断线重连；桥层输出 Compose State（当前曲/播放态/进度/队列位置）
- ✅ 迷你条符合 DESIGN-SYSTEM §5.2：44 封面 r10、播放/暂停 40 圆形、下一首、顶部 2dp 进度线、无播放整条隐藏
- ✅ 点击主体展开播放页（共享元素转场 M1 可先降级为普通导航，签名 morph 转场记入 M4 打磨，SCREENS §7）

### T7 · 音乐库屏（1.5 天，依赖 T4、T6）

产出：SCREENS §1 全量实现。

- ✅ 大标题 + 统计行（「N 首 · 本地 N」）+ 三个标签页（全部/最近播放/最常播放，数据来自 T2 表）
- ✅ 「随机播放全部」药丸按钮 → 进队并开 shuffle
- ✅ 歌曲行 64dp 规格（§5.3）：44 封面、正在播放行标题转 primary + 12dp 声波指示器、时长 tabular
- ✅ 大标题滚动收缩吸顶 + 底部导航激活态声波指示器（spring，§5.1）
- ✅ 列表滚动 60fps 无可见卡顿（Galaxy 分类机，Developer Profiling 粗测）

### T8 · 正在播放页（1.5 天，依赖 T6、T9）

产出：SCREENS §2 全量实现（歌词页除外）。

- ✅ 334 封面 r26 + shadow-3、标题块/操作行/进度滑杆（§5.5 拖拽态规格）/控制行 74dp 播放大键
- ✅ 进度拖动 seek 生效；时间 11sp tabular；静默隐藏 thumb
- ✅ 双击封面收藏的入口 M1 只留位（收藏落 M3）
- ✅ Edge-to-edge：顶部控件避开状态栏 inset，底部距手势条 12
- ✅ 顶栏「队列」入口 → M1 显示当前队列列表（读取 session queue）

### T9 · Echo Palette 取色管道 v1（1 天，依赖 T0、T5 封面可用）

产出：`core.designsystem` 取色器。

- ✅ 内嵌封面提取（经 Coil AlbumArtFetcher）→ 128px 下采样 → HCT 打分（materialkolor 的 material-color-utilities）→ `darkColorScheme()/lightColorScheme()` 运行时构建 + `glow`/`coverShadow` 扩展 token（CompositionLocal）
- ✅ 映射遵守 DESIGN-SYSTEM §1.2 数值表（T 值硬编码对照）
- ✅ 兜底链生效：灰度封面（C<8）→ 回声青基准板；无封面 → 「歌手+曲名」hash 选 8 组渐变之一（确定性，同曲同色）
- ✅ 纯 JVM 单测：固定色样表 → 断言 seed/glow Hue（DESIGN-SYSTEM §1.1 五步流水线各步可独立断言）
- ✅ 取色切换 800ms crossfade（§6.3，M1 允许降为 600ms，M4 回调）

### T10 · 播放模式与收尾联调（0.5 天，依赖 T8）

- ✅ 单曲循环（角标 1）/列表循环/随机三态循环切换，图标随 DESIGN-SYSTEM §4.4（填充态语义）
- ✅ 上一首/下一首在三种模式下行为正确；队首点上一首 → 回到队尾（列表循环语义）
- ✅ 队列/模式决策抽成纯函数 `NextPicker`，纯 JVM 单测覆盖 3 模式 × 边界（队首/队尾/单曲）用例

### T11 · 质量保障（持续，随 T2-T10 推进，见 §3）

### T12 · 发版（0.5 天，依赖全部）

- ✅ versionName `0.2.0-m1` / versionCode 2；debug 签名兜底不变（正式 keystore 归 M4）
- ✅ CHANGELOG-M1.md（发群文案用）；CI 产出 release APK artifact
- ✅ 真机验收清单（§2）P0 全绿后发群

依赖图：`T0 → T1/T2/T5 → (T3→T4→T7) + (T9→T8) → T6 → T10 → T12`，T11 全程。总计 ≈ 10.5 人日，适配两周排期（可顺延不可跳过）。

## 2. 用户真机验收清单（零基础可执行）

前置：手机为 Android 10+（Android 16 优先），已连接 Wi-Fi；从 CI 构建产物下载 `app-release.apk`，传到手机点击安装（未知来源按系统提示允许）。

### P0（有一条不过就不发群）

| # | 你做什么 | 你该看到什么 |
| --- | --- | --- |
| 1 | 点开 APP 图标 | 进入音乐库页，看到「还没有歌曲」空态插画与「扫描本地音乐」按钮（前提：先拒绝权限） |
| 2 | 点「扫描本地音乐」/或首次直接触发 | 系统权限弹窗出现，文案含「读取本地音乐」；选择「允许」 |
| 3 | 授权后观察列表区 | 出现 3-5 行骨架 + 顶部细进度线，几秒内变成完整歌曲列表；标题下统计行数字 = 你手机里的歌曲数 |
| 4 | 点任意一首歌 | 立即出声；该行标题变青色 + 出现声波指示器；屏幕底部出现迷你播放条 |
| 5 | 按手机 Home 键回桌面 | 音乐不中断；下拉通知栏出现媒体通知：封面+歌名+歌手，播放/暂停/上下首/进度条全部可点 |
| 6 | 锁屏 | 锁屏界面出现同样的媒体控制卡片，暂停/继续/切歌可用 |
| 7 | 播放中拔掉有线耳机（或断开蓝牙） | 音乐立即自动暂停，不外放 |
| 8 | 在迷你条上点「下一首」 | 切到下一首且封面/标题/配色随之变化 |
| 9 | 点迷你条主体进入播放页 | 全屏沉浸页：大封面、进度可拖动、74dp 大播放键、左右切换上下首；背景颜色与封面同色系 |
| 10 | 播放页点循环图标切到「单曲循环」（角标 1） | 同一首歌播完自动重播，连续听 2 遍验证 |
| 11 | 播放中从最近任务划掉 APP | （允许行为：继续播放或停止，M1 以「通知仍在、可从通知恢复控制」为通过线；重启自动恢复队列是 M4 playback resumption 项，不算失败） |
| 12 | 播放中打开另一个视频 APP 放视频 | 绕梁自动暂停（音频焦点让位）；关掉视频回到绕梁，点播放能继续 |

### P1（应过，不过记 issue 不阻塞发版）

| # | 你做什么 | 你该看到什么 |
| --- | --- | --- |
| 13 | 播放页双击封面 | 无崩溃（收藏 M3 才有功能，M1 允许无反应） |
| 14 | 系统切深色模式 | 全部页面深色优先、无刺眼白块；播放页配色仍随封面 |
| 15 | 从屏幕边缘做返回手势（预测性返回） | 有跟手的返回预览动画，不闪退不白屏 |
| 16 | 底部导航切「搜索/歌单/设置」 | 显示各自的空态屏（回声波纹插画 + 说明 + 按钮），无崩溃 |
| 17 | 播放一张无封面/灰色封面的歌 | 背景回落到回声青基准色或 hash 渐变，文字可读性正常 |
| 18 | 在设置里（M1 仅有占位行）检查版本号 | 显示 v0.2.0-m1 |
| 19 | 连续播放 30 分钟后台挂机 | 不被系统杀（通知仍在、进度正常走）；若被杀记录手机型号与通知设置状态 |
| 20 | 手机上用其他音乐 APP 播放后再切回 | 通知栏不出现两个媒体通知（旧的 session 已释放） |

## 3. 无真机阶段的质量保障（CI 上可跑）

**原则：把「系统交互」与「业务决策」拆开。系统交互（MediaStore 查询、权限弹窗、session 绑定）用最小验证面；业务决策（同步 diff、取色打分、队列逻辑、DAO）全部下沉为纯 JVM 测试。**

### 3.1 纯 JVM 单测矩阵（不需要模拟器/Robolectric）

| 被测对象 | 怎么做到纯 JVM | 用例要点 |
| --- | --- | --- |
| `syncDiff`（T3） | 纯函数：输入现库快照与 MediaStore 查询结果的模型列表 | 增/改/删/重复 `_id`/时间戳相等不误判 |
| Room3 DAO（T2） | Room3 的 JVM target + `BundledSQLiteDriver` 内存库（KMP 化的直接红利） | CRUD、`play_counts` 聚合排序、Flow 发射 |
| `EchoPaletteExtractor`（T9） | HCT/K-means 是纯数学，输入固定色样表 | 五步流水线各步断言：聚类数、L* 过滤、打分序、seed/glow 选定、护栏兜底（灰度→品牌色） |
| `NextPicker`（T10） | 纯函数：模式 × 当前位置 × 队列 | 3 模式 × 队首/队尾/单曲/空队列 |
| 设置存取（T4+） | `datastore-preferences-core`（无 Android 依赖的构件） | 读写、默认值、并发写不丢 |
| 模型映射 | `core.model` 零 Android 依赖（D3 的结构保证） | MediaStore 行 → Song 字段映射 |

### 3.2 Robolectric（少量，只测系统胶水）

- 权限封装：`READ_MEDIA_AUDIO` 授予/拒绝/永久拒绝三分支的路由（Robolectric `ShadowContext` 授予权限，断言 UI 路由目标）。
- MediaStore 查询包装层：`MediaStoreSource` 接口以 Robolectric 的 `ShadowContentResolver` 喂 fixture 游标，验证查询参数（projection/selection/排序）拼装正确；业务断言仍落在 `syncDiff` 纯函数上。
- 目标：Robolectric 用例 ≤ 5 个。它启动慢、API 版本矩阵脆，不承载主业务覆盖。

### 3.3 Compose UI（轻量）

- 四态（权限/扫描/空/正常）各 1 个 `createComposeRule` + Robolectric 渲染冒烟（断言关键节点存在与文案），不追求像素级。
- 像素级还原交给 SCREENS/mockups 人工比对（真机验收 §2 P1-14/16 顺带完成）。

### 3.4 CI 门禁（M0 已有 CI，本里程碑补齐）

- `compileReleaseKotlin` + 全部 JVM 单测绿 → 才产出 APK artifact；T0 的 KSP 矩阵失败会在这里第一时间暴露。
- Artifacts 命名带 `versionName`（`echomusic-0.2.0-m1.apk`），对应 §2 清单的下载入口。

## 4. 风险与回退（继承 ADR-0004 §2）

| 风险 | 挂在哪 | 回退 |
| --- | --- | --- |
| Kotlin 2.4.10 × KSP × Hilt 编译失败 | T0 | 切 Koin 4.1.1（≤1 天，影响面限 `app` 装配层） |
| Room3 生态摩擦（三方库依赖旧 androidx.room） | T2 | 回退 androidx.room 2.8.4（≤半天，DAO API 近同构） |
| 真机媒体通知不出现（厂商 ROM 差异） | §2-P0-5/6 | 记录机型 + 检查通知权限引导路径；P2 做厂商白名单文档 |
| 取色在低端机掉帧 | T9 | 下采样已限 128px；仍卡则取色缓存按 albumId 落 Room（预留字段已留） |
