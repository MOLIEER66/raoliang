# M2 任务拆解 · 音源系统（第 4-5 周）

状态：草案（2026-09-27，AI 代拟 · 主控=用户可随时否决单条） · 依据：[ROADMAP M2](ROADMAP.md) · [ADR-0002](ADR-0002-source-plugin.md)（协议方向） · [ADR-0003](research/ADR-0003-js-runtime.md)（运行时选型：javascriptengine 1.1.0 主选 + quickjs-kt 备选） · [协议剖析](research/lx-script-protocol.md)（P0×16 清单） · [SCREENS §3/§6](../design/SCREENS.md)
推荐组合速查：JS Engine 沙箱 isolate/脚本（ADR-0003 §4）· lx-prelude.js 胶水 ~200 行 · OkHttp 桥（UA 注入 + header 黑名单 + 域名审计）· ResolvingDataSource 在线流（M1 预留锚点）· 歌词 = LRC 行滚动 v1

## 0. M2 范围（先划清楚不做什么）

**做**：洛雪脚本导入（SAF + 头注释解析）→ 受限沙箱执行（javascriptengine，每脚本一 isolate）→ request 网络桥（OkHttp + 域名审计）→ 多源管理与自动降级 → 在线播放接入（ResolvingDataSource）→ 设置页音源组（SCREENS §6 组2：切换/详情/沙箱声明）→ 歌词页（SCREENS §3 逐行滚动）→ 播放页歌词预览三行（M1 TODO 锚点兑现）。

**不做（防蔓延）**：搜索/歌单（M3）· 双语/罗马音歌词列（SCREENS §3 未定义，记 M4）· `utils.zlib`/`.lxmc` 加密脚本/歌单源（协议 P1-8/10，M4 兴趣项）· QuickJsRuntime 备选实现（仅 spike no-go 时启用，ADR-0003 §7）· 下载缓存（P2）· 音质选择 UI（SCREENS §6 组3，M2 只打通数据链路，默认最高可用档）。

**折中**：spike 验收矩阵（ADR-0003 §8 原文要求 Android 8/12/16 三台）裁剪为**用户真机 1 台全流程 + CI JVM 全量**；CI emulator 矩阵成本高（无 KVM 慢），记 M4 可选增强。对抗样本（死循环/内存巨兽）在真机做。

## 1. 任务列表（按依赖顺序）

预估含自测。✅ 行为验收标准，全部可勾选。

### T0 · 门禁 spike：本地环境 + 基线构建 + 还债（0.5 天，卡后续一切）—— ✅ 已完成（2026-09-27）

产出：云端开发环境（Android SDK platform-36 + build-tools 36.0.0）、基线构建验证、阶段 0 三件还债。

- ✅ `assembleDebug` + `testDebugUnitTest` 本地全绿（88 用例）
- ✅ 还债：README 状态更新（原文仍写"脚手架阶段"）、`CHANGELOG-M1.md` 补写（T12 勾了但文件缺失）、版本号 `0.2.0-m2`/versionCode 2 起步
- ✅ `androidx.javascriptengine:1.1.0` 进 toml 并依赖解析通过
- 🔀 降级开关：JS Engine 依赖解析失败 → 当天评审（quickjs-kt，ADR-0003 §7 触发条件）

### T1 · 模块拆线（1 天，依赖 T0，只搬不改类）

产出：D3 触发条件兑现——`core.*` 从 app 拆出，M2 新代码落新模块。

- [ ] 拆 `:core:model`（core.model 整包搬）、`:core:data`（core.data 整搬，schemas 目录跟走）
- [ ] 新建 `:core:js`（LxRuntime + 沙箱实现 + lx-prelude.js 资产）、`:core:source`（SourceManager + 协议解析）空模块，依赖图：`app → :core:source/:core:playback → :core:js/:core:data → :core:model`
- [ ] 每搬一个包 CI 绿一次；测试文件随包走
- [ ] 验收：`assembleDebug` + 全部测试绿，APK 功能与 M1 无差异（不重装验收，编译等价即可）

### T2 · :core:js 沙箱运行时（2 天，依赖 T1）

产出：`LxRuntime` 接口 + `JsEngineRuntime`（ADR-0003 §4 架构落点）。

📌 实测结论（2026-09-27）：① API 全部经 AAR 反编译核实（javap），官方文档无一处凭记忆——关键发现：`TerminationInfo` 构造器包私有、JS 侧端口获取是 `await android.getNamedPort(name)`（AOSP MessagePortTest 一手证据）；② lx-prelude.js 227 行，Node vm 沙箱契约自测 18 用例 + HYW 标本真实握手 22 用例全绿（tools/test-prelude.mjs，五平台 sources/actions 全开/hires 扩展与协议研究记录一致）；③ LxWire 信道编解码纯函数 17 JVM 用例；④ 开发沙箱 4GB 内存两次 OOM（gradle.properties 降至 1536m + kotlin.daemon 1024m 后稳定）。

- ✅ `JavaScriptSandbox.createConnectedInstanceAsync` + `isSupported()` 探测（R1：不可用给可读提示，不崩）
- ✅ 每脚本一个 `JavaScriptIsolate`（多实例并存、故障隔离——P0-15/16 的地基）
- ✅ `lx-prelude.js`（~200 行）：构造 `globalThis.lx`、`EVENT_NAMES`、`on/send` 握手、`request()` Promise 化 + callId 关联、双签名 callback 派发、`setTimeout/clearTimeout`（P0-10）、`console.log/warn/error` 捕获 1024 截断（P0-11）
- ✅ MessagePort 信道；特性不可用 → evaluate 字符串信道降级（R3，功能等价）
- ✅ isolate 生命周期：超时熔断、崩溃回调、销毁时终止在途请求（P1-4 真实生效）
- ✅ 胶水协议自测：Node 环境跑 prelude + 假宿主（握手/分发/双签名/Promise/异常转发全用例），不依赖 Android
- ✅ JVM 单测：上层逻辑用 `LxRuntime` 假体（沙箱真身隔离在 JsEngineRuntime 单文件，真机归 T9）

### T3 · request 网络桥 LxBridge（1 天，依赖 T2）

产出：OkHttp 代发的宿主桥（P0-8/P0-9）。

- [ ] options 全量：method（默认 GET）/headers/body/form/formData/timeout（毫秒，官方唯一超时手段）
- [ ] UA 默认注入 `lx-music-mobile/{协议版本}`，脚本显式 UA 优先（P0-9）
- [ ] header 透传制 + 黑名单（Host/Cookie 等敏感头）
- [ ] callback 恒 3 参 `(err, resp, body)` 且 `resp.body === body`（双签名裁决）
- [ ] body JSON 语义：content-type 为 JSON → 对象，否则 string（needle 行为）
- [ ] 取消函数返回（哪怕 no-op）；isolate 销毁联动取消
- [ ] 域名审计：桥接 URL 全量记录（内存环形缓冲 + DataStore 落盘，供 §6 脚本详情页）
- [ ] MockWebServer 单测：双签名/JSON 语义/UA 优先/黑名单/超时/取消

### T4 · 协议层纯函数（1.5 天，依赖 T1，可与 T2/T3 并行）

产出：`core.source.protocol` 包，标本驱动的容错解析。

- [ ] 头注释解析 `@name/@description/@version/@author/@homepage`（P0-13：≤24/≤36 校验，超限截断不拒收）
- [ ] inited 握手状态机：`status/sources` 解析、未知字段忽略（openDevTools 等桌面版语义，协议 §4）
- [ ] sources 容错：qualitys 未知值能映射就映射、不能就隐藏不崩（协议 §4）；actions 声明驱动分发 + 双签名兜底（§8-1 分歧）
- [ ] musicUrl/lyric/pic 出入参编解码；lyric 四字段 `lryic/tlryic` typo 容错（P1-11）
- [ ] updateAlert 接收（≤1 次，P0-14）→ 事件流给 UI
- [ ] musicInfo 透传通道：`Map<String, Any?>` 型 `sourceSpecific`（协议 §5.4"宁多勿少"）
- [ ] 未声明 action 组合快速失败（协议 §5.5）
- [ ] JVM 单测：标本脚本 `reference/HYWmusic_free_v1.0.0.js` 真实数据驱动的用例 ≥ 20 个

### T5 · SourceManager + 脚本仓库（1 天，依赖 T2/T3/T4）

产出：多源管理与降级（ADR-0002 §技术要点）。

- [ ] 脚本导入管线：SAF 读 `.js` → 头注释解析 → 存内部存储（`filesDir/sources/`）+ DataStore 登记元数据
- [ ] 多源列表：启用/禁用/排序/删除；"当前源"偏好持久化
- [ ] 冗余降级（P0-16）：handler reject / 网络失败 / handler 超时（10-15s 可配置）/ isolate 崩溃 → 自动切下一启用源；reject 文案原样透出（"鉴权失败"/"请求过速"等可读错误）
- [ ] 降级策略纯函数抽出（`resolveWithFallback(sources, action, info)`）JVM 单测：全挂/部分挂/首选挂/单源
- [ ] 首次无源状态：库页空态幽灵按钮「导入洛雪音源脚本」接导入流（SCREENS §1 空态定义）

### T6 · 播放接入（1 天，依赖 T5）

产出：M1 预留锚点兑现（ADR-0004 D1）。

- [ ] `ResolvingDataSource.Factory` 挂进 `PlaybackService` 的 player 构建：在线曲目播放前经 SourceManager 解析 musicUrl
- [ ] 在线曲目 mediaId 编码：`Song.id` 负数合成（-hash，M1 模型注释预留）
- [ ] 解析失败 → 降级链 → 全挂时 UI 可读错误（播放页 §2 音源失效横幅：自动切换提示 3s）
- [ ] 来源行徽章数据：「经洛雪音源播放 · 音质档」/「本地文件」（SCREENS §2 来源行）
- [ ] 回归：本地播放路径零变化（M1 全部测试仍绿）

### T7 · 音源设置 UI（1 天，依赖 T5/T6）

产出：SCREENS §6 组 2 全量 + 沙箱声明页。

- [ ] 当前源行 + 多源切换列表 + 「导入脚本」主按钮（SAF `application/javascript` + `.js`）
- [ ] 脚本详情页：头注释元数据 + **域名审计面板**（桥接 URL 列表，ADR-0002 安全承诺的 UI 落点）
- [ ] 沙箱声明页（PRD §6 立场：本体零曲库/脚本用户自备/网络仅经审计桥）
- [ ] 导入失败态：组 2 内联错误横幅「脚本解析失败：不兼容的协议头」+ 重试（SCREENS §6 状态）
- [ ] updateAlert 更新弹窗 → updateUrl 下载重导入（URL 过域名审计）

### T8 · 歌词系统（2 天，依赖 T4/T6）

产出：SCREENS §3 歌词页 + 播放页预览行兑现。

- [ ] 解析器纯函数：LRC（多时间标签/偏移容错）、lxlyric（`[mm:ss.ms]<start,dur>text` 逐字）→ 行模型；tlyric/rlyric 解析进模型（渲染 v1 单列原文，双语列记 M4）
- [ ] 解析器 JVM 单测：边界样本（空行/超长行/乱序时间戳/BOM/typo 字段）≥ 15 用例
- [ ] 歌词页 §3：逐行滚动（当前行 25/700 + 3.5dp 声标 14dp 泛光 + 左内边距 15）、三档透明度 0.26/0.42/0.60、间奏三点行、点击行 seek（涟漪 + 时间气泡）、拖动时列表 scale 0.96 冻结
- [ ] 三态：无歌词「纯音乐，请欣赏」+ 封面 120 r16 / 加载 4 行骨架 / 未匹配「搜索歌词」幽灵按钮（联动 M3 预填）
- [ ] 播放页歌词预览三行（M1 信息区锚点替换：上一行 14/500 sub、当前 22/740、下一行 14/500 sub）+ 点击/上滑进歌词页（垂直共享轴 300ms）
- [ ] 本地曲目歌词：优先 MediaStore 内嵌，无则走在线 lyric action（songKey 辅助匹配，ADR-0004 D2）

### T9 · spike 真机验收 + 对抗测试（1 天，依赖 T2-T8，需用户真机）

产出：ADR-0003 §8 go/no-go（裁剪版）。

- [ ] 用户真机全流程：导入 HYW 标本 → inited（sources 含 hires）→ musicUrl（128k/320k/flac）→ lyric 四字段 → pic（协议全流程）
- [ ] 对抗样本：死循环脚本、`while(1) malloc` 内存巨兽 → isolate 被终止/超时，主进程与 UI 无感，其他 isolate 不受影响
- [ ] 特性矩阵记录：MESSAGE_PORT / PROMISE_RETURN / ISOLATE_TERMINATION 实际支持情况（记入本文档实测结论）
- [ ] 桥接往返粗测 p95 < 50ms（不含网络）；冷启动（建沙箱 + 注入 + inited）< 1.5s
- [ ] 任一项失败且降级通道不可用 → 触发备选切换评审（quickjs-kt），当天决策

### T10 · 发版（0.5 天，依赖全部）

- [ ] `CHANGELOG-M2.md`（发群文案）；tag `v0.2.0-m2`；CI Release 产物
- [ ] 真机验收清单（§2）P0 全绿后发群

依赖图：`T0 → T1 → (T2 → T3) + T4 → T5 → T6 → (T7 + T8) → T9 → T10`，总计 ≈ 12 人日当量，适配两周排期（可顺延不可跳过）。

## 2. 用户真机验收清单（零基础可执行）

前置：从 CI Artifacts 下载 `Raoliang-debug-apk` 安装；准备一个洛雪 `.js` 音源脚本文件（群里拿或 HYW 公益版）。

### P0（有一条不过就不发群）

| # | 你做什么 | 你该看到什么 |
| --- | --- | --- |
| 1 | 设置 → 音源 → 导入脚本 → 文件管理器选中 `.js` | 解析出脚本名/版本/作者；出现在源列表；无报错 |
| 2 | 导入一个改名 `.txt` 的乱码文件 | 内联错误横幅「脚本解析失败」，App 不崩 |
| 3 | 库页空态点「导入洛雪音源脚本」 | 跳到设置音源组（同导入流） |
| 4 | 在线搜索暂无（M3）——用脚本源直接放：设置里选刚导入的源为当前源，播放页播放任一有 musicInfo 的歌（经 T6 演示路径） | 出声；来源行显示「经洛雪音源播放 · 音质档」 |
| 5 | 播放中进歌词页 | 歌词逐行滚动，当前行高亮有声标；点任意行跳转进度 |
| 6 | 无歌词的歌（纯音乐） | 「纯音乐，请欣赏」+ 居中封面 |
| 7 | 脚本详情页 | 看得到脚本名/版本 + 桥接过的 URL 列表（域名审计） |
| 8 | 杀掉后台重开 App | 当前源偏好还在；再播在线歌仍出声 |
| 9 | 断网后播在线歌 | 可读错误提示（非闪退非静默失败） |
| 10 | 设置里关掉该源再播在线歌 | 提示无可用源，不崩 |

### P1（体验项，不阻塞发版）

飞行模式下本地播放照常 · 导入第二个源并切换 · 删除源后列表干净 · updateAlert 弹窗（若标本脚本带）· 深色模式下歌词页对比度。

## 3. 测试策略

### 3.1 纯函数层（主力，全 JVM）

- 协议解析（T4）、歌词解析（T8）、降级策略（T5）、头注释解析：标本数据驱动，边界样本全覆盖。目标：新增用例 ≥ 60，全 JVM 秒级跑完。

### 3.2 桥与运行时层

- LxBridge：MockWebServer（新增 testImplementation，版本进 toml）
- JsEngineRuntime 沙箱真身：**不在 JVM 测**（Android 框架 API），隔离在单文件；上层用 LxRuntime 假体测；真机归 T9
- lx-prelude.js：Node 直跑（胶水 = 协议的可执行规格，TDD 顺序：先写用例再写胶水）

### 3.3 回归门禁

- M1 的 88 个测试随包搬迁后必须全绿（拆线 T1 的验收线）
- 本地播放路径零改动（T6 后跑全量回归）

## 4. 风险与回退（继承 ADR-0003 §6 + 新增）

| 风险 | 挂在哪 | 回退 |
| --- | --- | --- |
| R1 沙箱在用户真机不可用（WebView 过旧/被禁用） | T9 | `isSupported()` 探测 + 可读提示；触发备选评审切 quickjs-kt（ADR-0003 §7，≤2 天） |
| R2 MessagePort 特性门控 | T2 | evaluate 字符串信道（功能等价，性能降）——已内建 |
| R3 HYW 公益后端随时失效（PRD 风险表） | T9 | 测试用 fixture 不依赖后端；真机验收后端挂 → 换任一生态脚本验协议，验收标准不变 |
| R4 模块拆线搬崩 M1 测试 | T1 | 只搬不改，每包一步 CI；出问题单独 revert 该包 |
| R5 OkHttp 桥与 Coil 网络栈版本冲突 | T3 | Coil 已带 okhttp（M1 toml）；共用同版本，冲突时桥独立用 Ktor client（半天） |
| R6 逐字歌词数据在行渲染下无用武之地 | T8 | lxlyric 解析器照做（协议完备性），渲染 M4 升级卡拉OK时直接可用 |
