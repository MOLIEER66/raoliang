# ADR-0003 · 音源脚本 JS 运行时选型

状态：**草案（Draft），待 M2 评审**（2026-09-05 初稿）
关联：[ADR-0002](../ADR-0002-source-plugin.md)（协议方向已接受）· [PRD §6/§7](../PRD-v0.1.md) · 协议剖析：[lx-script-protocol.md](lx-script-protocol.md)

## 1. 背景

应用本体是中立播放器，用户自备的洛雪（LX）格式 JS 脚本在 App 内受限环境执行（ADR-0002）。运行时是 M2 音源系统的地基，选错代价 = 迁移整个插件层。

工作负载画像（来自协议剖析 §2/§5/§6）：

- 执行**完全不可信的第三方单文件脚本**（生态脚本普遍经过压缩混淆 + 字符串加密）；
- 交互模式 = **全异步双向回调**：脚本注册 `on(request, handler)` 并返回 Promise；脚本调用 `request(url, options, callback)` 由宿主代发 HTTP；宿主再把结果回灌脚本；
- 无 DOM / 无 Node API 需求；引擎级 Promise / async-await / ES6+ 为硬需求；
- 多音源冗余（PRD §6）意味着需要**多实例并存与故障隔离**；
- 约束来自 PRD §7：targetSdk 36（Android 16），若引入原生库需验证 16KB 内存页。

## 2. 候选与 2026-09 状态核实（WebSearch / 官方文档核实）

| 候选 | 2026-09 状态（核实结果） |
| --- | --- |
| **androidx.javascriptengine**（Jetpack JS Engine） | **1.1.0 stable（2026-05-06）**；1.0.0 stable 于 2025-07-02。API 26+ 且依赖系统 WebView 支持；执行在**独立沙箱进程**；1.1.0 新增 MessagePort（对称、低开销的宿主↔isolate 双向通道，支持 string 与 ArrayBuffer）。Apache-2.0 |
| **QuickJS 系 Android 绑定** | `dokar3/quickjs-kt`：**活跃**，v1.0.14（2026-08-15），KMP（android/jvm/native），协程集成，Apache-2.0，捆绑原版 QuickJS（issue #137 请求迁移 quickjs-ng）。`HarlonWang/quickjs-wrapper`（`wang.harlon.quickjs:wrapper-android`）：活跃，已发 46 个版本，基于 **quickjs-ng**，支持 Java 类型绑定 / Promise 执行 / 字节码编译，Apache-2.0；注意 **CVE-2026-1145**（quickjs-ng ≤ 0.11.0）。`cashapp/quickjs-java`（app.cash.quickjs）：**已死**——最终版 0.9.2（2021-08），开发转入 Zipline，社区靠 `zhanghai/quickjs-java` 等 fork 续命 |
| **Mozilla Rhino** | **活跃**：1.9.1（2026-02-15，修复 1.9.0 回归）；1.9.0（2025-12-22）性能 +10–30%；纯 Java，jar ≈ 1.6MB；ES6 为默认语言层级，支持 async/await；**1.9.x 要求 JDK 17**；2.0.0-SNAPSHOT 开发中。MPL-2.0 |
| **无头 WebView** | 系统 WebView（Chromium，Play 持续更新），JS 能力全量；`addJavascriptInterface` 注入宿主对象 + `evaluateJavascript` 原生处理 Promise；`shouldInterceptRequest` 可审计网络 |

生态旁证：洛雪移动版官方自述自定义源跑在"**基于 QuickJS 的包装依赖**"上（lx-music-desktop#1643）——进程内 QuickJS 是被生态验证过的路线，因此 QuickJS 作为备选不是纸面方案。

## 3. 对比矩阵

| 维度 | androidx.javascriptengine | QuickJS 系绑定 | Rhino | 无头 WebView |
| --- | --- | --- | --- | --- |
| **沙箱隔离强度** | ★★★★★ 独立沙箱进程：脚本 OOM/崩溃只杀 isolate，主进程无恙；崩溃可控可回调 | ★★★☆☆ 进程内原生引擎：仅"能力型"隔离（未绑定即不存在）；但 QuickJS 系**内存安全漏洞直接暴露在应用进程**（CVE-2026-1145 即实例）；需自建 `:sandbox` 进程 + IPC 才能补齐 | ★★☆☆☆ 进程内 JVM：需 ClassShutter + sealed object 手工封禁，历史泄漏面大；引擎漏洞同样在应用进程 | ★★★☆☆ 渲染进程崩溃不伤主进程；但 `addJavascriptInterface` 反射面 + 多脚本共享实例时无 per-script 隔离 |
| **注入 globalThis.lx + request 桥** | 无宿主对象注入 API；需 ~200 行 JS 预置胶水（定义 lx，经 **MessagePort** 回调宿主）+ Kotlin 端口分发器；一次性成本，胶水可单测 | ★ 最顺：原生绑定一等公民，Kotlin 函数直接暴露，request 桥即一个绑定方法 | 最灵活也最危险：Java 对象直接塞进脚本作用域 | 顺：`addJavascriptInterface` 即宿主对象；UA/请求可由宿主拦截审计 |
| **Promise 支持度** | 引擎原生支持；`evaluateJavaScriptAsync` 的 Promise 返回值由 `JS_FEATURE_PROMISE_RETURN` 特性门控（设备 WebView 版本相关）。**纯 MessagePort 桥架构不依赖该特性**：Promise 全部在 JS 侧消化 | 完整（quickjs-ng 对齐最新 ES；quickjs-kt 协程化任务泵） | 支持 async/await；长生命周期回调场景的微任务泵需要 spike 验证 | 完整（evaluateJavascript 原生等待 Promise） |
| **包体积** | ≈ 0（引擎在 WebView Provider 进程，不进 APK） | +2–4MB（.so × ABI，quickjs-ng 略小） | ≈ 1.6MB 纯 Java jar，无 ABI 问题 | ≈ 0 APK 体积；运行时每实例 30–100MB 内存 |
| **维护活跃度（2026-09）** | Jetpack 官方节奏健康（2025-07 → 2026-05 双 stable） | quickjs-kt / quickjs-wrapper 活跃但均为**单人主维护**；CashApp 前车之鉴（2021 停更） | Mozilla 官方，活跃 | 系统 Google Play 更新 |
| **Android 16 / targetSdk 36** | 无 NDK → **16KB 内存页风险清零**（PRD §7）；风险是可用性波动：WebView 版本不足/被禁用时报 "Unable to obtain a JavascriptEngine"（2026 年 ADMob 在 Android 16 上的同类事故佐证）→ 必须 `isSupported()` 探测 + 降级 | NDK .so 必须 **16KB 对齐**，且要逐版本验证各库发布物 | 纯 Java，但 1.9.x 面向 JDK 17 字节码 → 需验证 D8/脱糖链路 | 无 APK 层风险；WebView 生命周期/线程纪律（须在带 Looper 线程创建） |
| **许可证** | Apache-2.0 | Apache-2.0（捆绑 MIT/BSD 引擎） | MPL-2.0（合规简单） | 系统组件 |

## 4. 决策

**主运行时采用 `androidx.javascriptengine` 1.1.0**；同时在工程上定义 `LxRuntime` 内部接口（注入 lx / 派发 request / 接收 inited / 销毁），把 **QuickJS（dokar3/quickjs-kt）实现为可插拔备选**，用于设备不支持 JS Engine 时的降级与 A/B 验证。

架构落点：

```
Player → SourceManager（多源冗余/降级，ADR-0002）
             └─ LxRuntime（接口）
                  ├─ JsEngineRuntime  ← 主选：每脚本一个 JavaScriptIsolate
                  └─ QuickJsRuntime   ← 备选：独立 :js 沙箱进程
             └─ LxBridge：request 桥（OkHttp 代发 + UA 注入 + 域名审计 + header 黑名单）
```

JS 侧预置胶水（`lx-prelude.js`，按 [lx-script-protocol.md](lx-script-protocol.md) §3–§7 实现）：在脚本代码前求值，构建 `globalThis.lx`；`request()` 把 `{callId, url, options}` 经 MessagePort 发给宿主，宿主 OkHttp 执行后回灌 `{callId, err, resp, body}`，胶水侧完成 3 参 callback 派发与 Promise 化。**该架构不依赖 `JS_FEATURE_PROMISE_RETURN`**（Promise 均在胶水与用户脚本之间闭环），只依赖 MessagePort；端口不可用时按 §6-R3 降级。

## 5. 理由

1. **信任边界第一**：脚本是不可信第三方代码，JS Engine 是四案中唯一"开箱即得进程级故障/内存隔离"的方案。QuickJS/Rhino 要达到同等强度需自建 `:sandbox` 进程 + IPC——等于把官方库重造一遍，而那正是"面试作品"里最容易被追问穿的部分。
2. **Android 16 零负担**：无 NDK 依赖，PRD §7 的 16KB 页专项对本模块自动豁免；Jetpack 双 stable（2025-07 / 2026-05）背书可讲。
3. **协议天然适配**：LX 协议本就是"回调 + Promise"的全异步模型，与 MessagePort 桥一一对应；桥接胶水写一次，同时就是协议文档的可执行规格（TDD 友好）。
4. **失败模式恰好服务产品**：isolate 粒度 = 脚本粒度，脚本崩溃只杀自己 → 直接落实 ADR-0002"单源失败自动降级"。
5. **体积与审计双赢**：零 APK 增量；HTTP 收敛到 Kotlin OkHttp 桥，域名审计/明文白名单/header 黑名单全在宿主侧实现，比 WebView 的 `shouldInterceptRequest` 粒度更细。

## 6. 风险与缓解

| # | 风险 | 等级 | 缓解 |
| --- | --- | --- | --- |
| R1 | 设备可用性波动：API 26 以下、WebView 过旧/被禁用时沙箱不可得（同类事故见 2026 年 ADMob on Android 16） | 中 | 启动 `isSupported()` 探测；`LxRuntime` 抽象 + QuickJsRuntime 降级实现（P1 完成）；最低支持线以上不可用设备给出可读提示 |
| R2 | 无宿主对象注入 API，桥接靠 JS 胶水 + 端口，工程量高于 QuickJS 绑定 | 中 | 胶水控制在 ~200 行并按协议文档单测（标本脚本全流程用例）；这是一次性成本 |
| R3 | `JS_FEATURE_MESSAGE_PORT` 为特性门控（依赖 WebView 版本） | 中 | 特性探测；不可用时降级为"evaluate 字符串信道"（性能差但功能等价）；再不行触发备选切换评审 |
| R4 | IPC 往返延迟（每次 evaluate/端口消息过 Binder） | 低 | 网络请求（百 ms 级）占绝对主导；spike 中测桥接 p95 < 50ms（不含网络）；大 body（如歌词）远小于 Binder 限制，超限走 named data |
| R5 | 调试体验弱于进程内引擎 | 低 | console callback + 脚本日志面板（对标 lx-music-mobile"记录自定义源日志"）；胶水内建异常转发 |
| R6 | QuickJS 备选自身的 16KB 对齐与 CVE 跟踪 | 中（仅启用备选时） | 启用前逐 release 验证 .so 对齐；quickjs-wrapper 路线锁定 quickjs-ng > 0.11.0（CVE-2026-1145） |

## 7. 备选与切换触发条件

| 备选 | 定位 | 切换触发 |
| --- | --- | --- |
| **dokar3/quickjs-kt**（首选备选） | KMP + 协程，全功能确定性强，不依赖设备 WebView 状态 | Spike 中 JS Engine 在 ≥1 台目标测试机不可用，或端口/降级信道性能不达标；或 Jetpack 库出现长期停更信号 |
| HarlonWang/quickjs-wrapper（次选备选） | 基于 quickjs-ng，跟踪引擎上游快，Java 绑定齐全 | quickjs-kt 停更/需 NG 特性 |
| Mozilla Rhino | 纯 Java、零 NDK、MPL-2.0 | 仅当放弃对高混淆生态脚本的兼容承诺（现代语法/微任务泵风险，不推荐） |
| 无头 WebView | 全 Chromium 能力 + 拦截审计 | 仅当出现 DOM 级能力需求（LX 协议没有），否则内存与线程纪律不划算 |
| （反面教材）cashapp/quickjs-java | — | 已停更（2021-08）且官方转向 Zipline；任何 QuickJS 选型前先核维护状态 |

## 8. M2 Spike 验收（go / no-go）

用 `reference/HYWmusic_free_v1.0.0.js` 作为验收脚本集，全部通过即 go：

1. **可用性矩阵**：Android 8 / 12 / 16 各 ≥1 台，`createConnectedInstanceAsync` 成功率 3/3；
2. **特性矩阵**：`JS_FEATURE_MESSAGE_PORT`、`JS_FEATURE_PROMISE_RETURN`、`JS_FEATURE_ISOLATE_TERMINATION` 在测机上的实际支持情况（记录降级路径实际走哪条）；
3. **协议全流程**：inited(sources 解析含 hires) → musicUrl(128k/320k/flac) → lyric(四字段) → pic → updateAlert 弹窗；
4. **对抗样本**：死循环脚本、`while(1) malloc` 内存巨兽、原型污染脚本 → isolate 被终止/超时，主进程与 UI 无感，且其他 isolate 不受影响；
5. **性能**：桥接往返 p95 < 50ms（不含网络），冷启动（建沙箱 + 注入胶水 + inited）< 1.5s；
6. **审计**：桥接 URL 全量落盘可查。

任一测机 1/3 失败且 R3 降级通道也不可用 → 触发备选切换评审（quickjs-kt）。

## 9. 参考

- [Jetpack JavaScriptEngine 发布页](https://developer.android.com/jetpack/androidx/releases/javascriptengine)（1.0.0 stable 2025-07；1.1.0 stable 2026-05-06；MessagePort 于 1.1.0-alpha02 引入）
- [官方使用指南](https://developer.android.com/develop/ui/views/layout/webapps/jsengine)（沙箱进程模型、isolate、JS_FEATURE_* 门控、provideNamedData、console callback）
- [dokar3/quickjs-kt](https://github.com/dokar3/quickjs-kt)（v1.0.14，2026-08-15）· [issue #137](https://github.com/dokar3/quickjs-kt/issues/137)（NG 迁移诉求）
- [HarlonWang/quickjs-wrapper](https://github.com/HarlonWang/quickjs-wrapper) · [MavenCentral wrapper-android](https://central.sonatype.com/artifact/wang.harlon.quickjs/wrapper-android) · [CVE-2026-1145](https://nvd.nist.gov/vuln/detail/CVE-2026-1145)（quickjs-ng ≤ 0.11.0）
- [cashapp/quickjs-java（Maven Central 停更证据）](https://central.sonatype.com/artifact/app.cash.quickjs/quickjs-android) · 继任者 [cashapp/zipline](https://github.com/cashapp/zipline)
- [mozilla/rhino releases](https://github.com/mozilla/rhino/releases)（1.9.1，2026-02-15）· [ES 兼容矩阵](https://mozilla.github.io/rhino/engines.html)
- [ADMob on Android 16 "Unable to obtain a JavascriptEngine" 社区报告](https://www.reddit.com/r/admob/comments/1qh0uig/unable_to_obtain_javascript_engine_android_16/)（R1 依据）
- [洛雪移动版自定义源文档](https://lxmusic.toside.cn/mobile/custom-source) · [lx-music-desktop#1643](https://github.com/lyswhut/lx-music-desktop/issues/1643)（官方移动端 = QuickJS 包装）
