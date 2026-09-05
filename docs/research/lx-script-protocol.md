# LX 自定义音源脚本协议剖析（Raoliang M2 前置研究）

状态：研究完成（2026-09-05） · 证据等级：A（官方文档 + 真实运行标本双重验证）
关联：[ADR-0002](../ADR-0002-source-plugin.md) · [ADR-0003-js-runtime](ADR-0003-js-runtime.md) · [PRD §6](../PRD-v0.1.md)

## 0. TL;DR

洛雪自定义源脚本是一个**单文件、全异步、回调 + Promise 风格**的插件协议：宿主向脚本注入唯一的全局对象 `globalThis.lx`，脚本顶层注册 `request` 处理器后主动发送 `inited` 握手，此后宿主按脚本声明的 `actions` 分发三类业务请求（musicUrl / lyric / pic），脚本经宿主提供的 `request()` 网络桥完成所有 HTTP。**官方文档、生态脚本之间存在 7 处已知分歧（§8），App 实现必须以"actions 声明驱动 + 双签名兜底"的容错姿态兼容，而不是照抄任何一份文档。**

## 1. 研究对象与证据

| 证据 | 说明 |
| --- | --- |
| `reference/HYWmusic_free_v1.0.0.js` | 真实发行版脚本标本（2026-08 生成，ikun 协议系），本文行号均指此文件 |
| [移动版官方文档](https://lxmusic.toside.cn/mobile/custom-source) | 自定义源脚本编写说明（API 权威来源） |
| [桌面版官方文档](https://lxmusic.toside.cn/desktop/custom-source) | 机制互通的参照系（request options / handler / sources 结构） |
| lx-music-desktop#1643 | 洛雪开发者自述：移动端自定义源运行在**基于 QuickJS 的包装依赖**上 |

标本骨架（L26–L269）：顶层解构 `globalThis.lx` → 定义每平台音质表 → 封装 `httpFetch`（对 `request` 做双签名兼容的 Promise 化）→ 注册 `on(EVENT_NAMES.request)` → 构建 `sources` → `send(EVENT_NAMES.inited)` → 异步 `checkUpdate()`。

## 2. 运行环境与安全边界（宿主必须提供的"世界"）

官方对移动版执行环境的定义，直接框定了我们沙箱的实现范围：

- **语言**：JavaScript，ES6+ 语法，UTF-8 编码，单文件自包含（标本未使用任何模块系统）。
- **引擎**："轻量级 JavaScript 引擎"——洛雪移动版实为 QuickJS 包装（官方 issue #1643 自述），即：**全量 Promise / async-await 是硬需求，DOM / Node API 一概不存在**。
- **可用宿主 API 仅 `setTimeout` / `clearTimeout`**；无 `window`、无 `require`、无文件系统。
- **内置属性被冻结**（官方："其他 JavaScript 内置属性都会被冻结"，防止篡改预加载脚本环境；允许脚本扩展自身属性）。
- **`console.log/warn/error` 可用**，日志单条 **1024 字符截断**（官方），宿主应捕获落盘供"记录自定义源日志"功能展示（对标 lx-music-mobile #635 的调试体验）。
- **HTTP 不受跨域规则限制**（官方），但唯一出口是宿主的 `request()` 桥 → 这是我们实现 ADR-0002"域名可审计"的关键卡口：**App 侧记录每次桥接的 URL，脚本自身无原生网络能力**。
- 明文 `http` 大量存在于生态（标本 `API_BASE = http://SERVER_ADDRESS_REDACTED`）→ App 的网络安全策略必须为脚本桥接流量放行明文（建议按域名白名单粒度，与审计联动）。

## 3. `globalThis.lx` 完整 API 面

脚本第一行就是 `const { EVENT_NAMES, request, on, send, env, version: LX_VERSION } = globalThis.lx`（标本 L26）——**解构失败即脚本能被"检测兼容性"，因此宿主必须在脚本执行前把对象完整挂好**。

| 成员 | 类型 | 约定（来源） |
| --- | --- | --- |
| `EVENT_NAMES` | 常量表 | 官方仅 3 个：`inited` / `request` / `updateAlert`（`updateAlert` 为源 API **v1.2.0** 新增） |
| `on(event, handler)` | 注册宿主→脚本事件 | `request` 事件的 handler **必须返回 Promise**（官方原文强调） |
| `send(event, datas)` | 脚本→宿主通信 | 见 §4 握手、§7 更新 |
| `request(url, options, callback)` | HTTP 桥 | 唯一网络出口；不受跨域限制；**返回一个取消函数**（调用可终止请求）。详见 §6 |
| `env` | `'mobile'` | 移动版固定值（桌面版 `'desktop'`）。脚本用它区分 UA / 分支逻辑 |
| `version` | string | **自定义源 API 协议版本**（非宿主 App 版本），"API 变更时此版本号将会更改"。标本将其拼入 UA |
| `currentScriptInfo` | object | 当前脚本自省信息：`name / description / version / author / homepage / rawScript`（官方移动版文档） |
| `utils` | 工具集 | 见下，移动版为桌面版缩水集 |

`lx.utils` 明细（App 兼容目标 = 移动版超集）：

| 分类 | 方法 | 备注 |
| --- | --- | --- |
| `utils.buffer` | `from` / `bufToString` | 编码仅支持 `base64` / `hex` / `utf8` |
| `utils.crypto` | `md5` / `randomBytes` / `aesEncrypt` / `rsaEncrypt` | **aesEncrypt 仅支持 aes-128-cbc、aes-128-ecb**（官方硬限制，脚本里的 AES 就是这两族） |
| `utils.zlib` | `inflate` / `deflate` | **移动版官方未实现**——App 可 P1 再补，行为对齐桌面版即可 |

## 4. 生命周期握手：`inited`

时序（全部有标本行号佐证）：

```
宿主                              脚本
 │ 解析头注释元数据(§7.1)            │
 │ 建沙箱 / isolate                 │
 │ 注入 globalThis.lx + 预置环境 ──▶ │ 顶层解构 lx (L26)
 │                                 │ on(EVENT_NAMES.request, handler) (L237)
 │ ◀── send(inited, {status, openDevTools?, sources}) (L262-266)
 │ 校验 sources → 更新音源列表 UI     │
 │                                 │ 异步 checkUpdate() (L269)
 │ ◀── send(updateAlert, {...})（可选，≤1 次）
 └─ 之后按需派发 request 事件 ──────▶ handler 返回 Promise
```

`send(EVENT_NAMES.inited, datas)` 的 `datas`：

| 字段 | 约定 |
| --- | --- |
| `status` | `true`=就绪；false/缺省=初始化失败（宿主应禁用该源并展示错误） |
| `openDevTools` | **桌面版语义，移动版官方无此字段**——标本仍会发（L264），宿主必须忽略未知字段 |
| `sources` | 音源能力声明表，结构见下 |

`sources` 结构（官方定义 + 标本 L250-259 实现）：

```js
{
  kw: {                       // key = 平台 id；官方允许 kw/kg/tx/wy/mg/local
    name: 'kw',               // 源名，官方"目前非必须"（标本直接填平台 id）
    type: 'music',            // 官方"目前固定值需为 music"（桌面版另有歌单源扩展）
    actions: ['musicUrl', 'lyric', 'pic'],
    qualitys: ['128k','320k','flac','flac24bit','hires'],
  },
  /* ...kg / tx / wy / mg */
}
```

- `actions`：**能力声明，App 必须按它分发**——官方基础文档写的是"非 local 源只支持 musicUrl，local 源可用 musicUrl/lyric/pic"，但 2025→2026 的生态（标本与 ikun 系发行版）对五大平台**全开三 action** 且在真实宿主上工作。这是协议事实上已经演化、文档未跟上的典型（详见 §8-1）。
- `qualitys`：官方枚举 `['128k','320k','flac','flac24bit']`；生态扩展 `hires`（标本 L29），其他发行版脚本还有更多档位。**App 音质档位表必须对未知值容错**：能映射就映射，不能就隐藏该档而不是崩溃。

## 5. 请求处理：`on(EVENT_NAMES.request, ({action, source, info}) => Promise)`

handler 收到 `{ source, action, info }`（`source` = 平台 id，即 sources 的 key），**必须返回 Promise**。三种 action：

### 5.1 `musicUrl`

- 入参：`info = { type, musicInfo }`；`type` = 音质（`sources.qualitys` 之一；local 源为 `null`）。
- 出参：**Promise resolve 一个 http(s) 形式的歌曲 URL 字符串**（官方："需要在 Promise 返回 HTTP 形式的歌曲 URL"）。
- 失败：reject（标本 reject 的是 `Error`，文案已中文化："鉴权失败"/"请求过速"等，L175-181）→ **宿主把 reject 消息原样透出到 UI**，这就是 ADR-0002 说的"错误提示可读"。
- 标本内部是 `GET /api/music/url?...`（L149-183），按服务端 code 分支：200→url，401/403→鉴权失败，429→请求过速，500→服务器错误。**这对 App 的启示：单源失败是常态，reject 必须轻量、可区分、可降级。**

### 5.2 `lyric`

- 入参：`info = { musicInfo }`。
- 出参：Promise resolve 歌词对象 `{ lyric, tlyric, rlyric, lxlyric }`——官方文档原文拼写为 `{lryic, tlryic, rlyric, lxlyric}`（含 typo），**以生态脚本实际字段 `lyric/tlyric` 为准**；`tlyric`=翻译、`rlyric`=罗马音、`lxlyric`=洛雪逐字格式（`[mm:ss.ms]<start,dur>text`）。缺失字段给 `null`/`''`（标本 L201-211 失败时返回空对象兜底，**永不 reject**——歌词失败不应阻断播放）。

### 5.3 `pic`

- 入参：`info = { musicInfo }`。
- 出参：Promise resolve **http(s) 封面 URL 字符串**（标本失败时 resolve `''`，同样不 reject）。

### 5.4 `musicInfo`：没有 schema 的"字段大礼包"

协议对 `musicInfo` 内部结构无强约束——它是宿主透传给脚本的歌曲元数据。脚本端做**多平台联合字段收集**（标本 L128-146 `collectMusicInfoParams`）：

- ID 族：`songmid` / `songId` / `id` / `hash` / `rid` / `musicId` / `copyrightId` / `songid`
- 元数据族：`albumAudioId` / `strMediaMid` / `mediaMid` / `albumId` / `albumMid` / `albumName` / `songname` / `songName` / `name` / `singer` / `singers` / `artist`
- 且同时查 `musicInfo.meta.*` 嵌套层（洛雪内部歌曲对象把元数据放 `meta` 下）

**App 行为约定：把搜索/歌单得到的全量字段 + `meta` 嵌套对象整体透传**，宁多勿少；ID 字段缺失时脚本会自己降级。这也意味着 App 的歌曲数据模型需要一个 `Map<String, Any?>` 型的 `sourceSpecific` 通道，不能只存自己的规范化字段。

### 5.5 未知 action

标本对未知 action `return Promise.reject('action not support: ' + action)`（L246）。宿主同理：**没在 `actions` 里声明的组合，直接快速失败**，不要发进脚本。

## 6. 网络桥接：`request(url, options, callback)`

签名（官方 + 标本 L50-78 双重验证）：

```js
const cancel = lx.request(url, options, (err, resp, body) => { ... })
// cancel: 调用此函数可终止 HTTP 请求（官方；App 必须返回函数，哪怕 no-op）
```

`options`（官方可用项全集）：

| 字段 | 说明 |
| --- | --- |
| `method` | 默认 GET（标本 L61 显式兜底 `if (!reqOptions.method) reqOptions.method = 'GET'` → 宿主也应默认 GET） |
| `headers` | 自定义请求头，含 UA、`X-Card-Key` 等 |
| `body` | 原始请求体（string/对象） |
| `form` | urlencoded 表单 |
| `formData` | multipart 表单 |
| `timeout` | **唯一超时控制手段**（官方明说），毫秒 |

`callback` 的**双签名**——本协议最著名的兼容坑（标本 L62-66 注释原文）：

- 2 参 `(err, resp)`：`resp.body` 承载响应体；
- 3 参 `(err, resp, body)`：`body` 独立传响应体（needle 风格）；
- **"部分版本 `resp.body` 为 undefined，body 在第三个参数；两者都取以兜底"**——生态脚本已经为宿主实现不齐买过单。

**App 裁决**：callback 恒以 3 参调用 `(err, resp, body)`，且保证 `resp.body === body` → 两种写法的脚本都兼容。

`resp` 结构：`{ statusCode, headers, body }`。

**body 的 JSON 语义**：桌面版底层是 needle，按 content-type 自动把 JSON 响应解析成对象。标本两头兼容（`typeof respBody === 'string'` 才 `JSON.parse`，L109-111、L162-165），但生态里大量脚本直接 `resp.body.code` 不做兜底 → **App 推荐 needle 行为：content-type 为 JSON 时给对象，否则给 string**。

**UA 约定**（生态事实标准，官方文档未强制）：`lx-music-${env}/${version}`，如 `lx-music-mobile/2.0.0`；无 env 时退 `lx-music-request/${version || '1.0.0'}`（标本 L57）。部分公益后端按 `lx-music-*` 前缀做准入校验。**App 建议：默认给桥接请求注入 `lx-music-mobile/{协议version}`，脚本显式设置的 UA 优先**（标本类脚本自己注入，裸 request 的脚本则受益于默认值）。

**自定义 header**：标本给所有请求加 `X-Card-Key: 公益版`（L59，发行版私有鉴权头，非协议成员）→ App 的 header 处理必须是**透传制 + 黑名单制**（禁 Host/Cookie 等敏感头），不能是白名单枚举，否则兼容性死的最早。

**App 侧整体超时**：官方未固化 handler 处理超时。建议 App 对每次 request 事件派发设 **10–15s 可配置超时**（单次 HTTP 的 `options.timeout` 由脚本控制，两层独立），超时按 handler reject 处理并触发降级。

## 7. 更新机制

### 7.1 头注释元数据（导入时解析）

| 字段 | 约定 |
| --- | --- |
| `@name` | 必填，**24 字符以内** |
| `@description` | 36 字符以内，可缺省 |
| `@version` / `@author` / `@homepage` | 可选 |
| `@updateUrl` | 官方文档头部字段列表未列，但生态脚本携带（标本 L8，指向发行服务 release 接口）→ **App 应解析并不强制** |

### 7.2 `updateAlert` 事件

- 触发：脚本自行比较版本后 `send(EVENT_NAMES.updateAlert, { desc, version, updateUrl })`（标本 L117-121）。
- 约束：源 API **v1.2.0 新增**；**每次运行至多调用一次**（官方）；`updateUrl` 必须是 http(s)（官方）。
- 版本比较逻辑在**脚本侧**：标本 `parseVer/cmpVer`（L90-100）把 `v` 前缀去掉、按 `[.-]` 分段转 int 逐段比较——**App 无需实现协议级版本比较**，只需接收 `updateAlert` 并处理。
- 宿主行为：展示更新提示（`desc` 标本截断至 200 字符；`version`；`updateUrl`）→ 用户同意后 GET `updateUrl` 拉取新脚本重新导入（content-type 为 JS 文本时）。`updateUrl` 域名应纳入审计展示。
- 另一条独立更新通道：`@updateUrl` 头字段 → App 可定时 GET 检查（P1）。

## 8. 官方文档 vs 生态现实：分歧清单（兼容性风险源）

| # | 分歧点 | 官方文档 | 生态现实（标本/ikun 系） | App 裁决 |
| --- | --- | --- | --- | --- |
| 1 | `actions` 范围 | 非 local 源仅 `musicUrl` | 五平台全开 `musicUrl/lyric/pic` | **按脚本 actions 声明分发，不硬编码平台→能力映射** |
| 2 | `qualitys` 枚举 | 128k/320k/flac/flac24bit | 追加 `hires` 等 | 未知档位容错（映射/隐藏，不崩） |
| 3 | `openDevTools` | 移动版无此字段 | 照发不误 | 忽略未知字段 |
| 4 | callback 签名 | `(err, resp, body)` | 脚本内做 2 参/3 参双兜底 | 恒 3 参 + `resp.body === body` |
| 5 | UA | 无约定 | `lx-music-${env}/${version}` 且后端可能校验 | 默认注入 `lx-music-mobile/*`，脚本显式值优先 |
| 6 | `utils` 能力 | 桌面全量 | 移动版 zlib 未实现、aes 仅 128 位 | 对齐移动版超集；zlib P1 |
| 7 | `@updateUrl` 头 | 头部字段列表未收录 | 广泛使用 | 解析并支持，不强制 |

## 9. App 兼容洛雪生态：必须实现的 API 清单

> P0 = 标本类脚本（HYWmusic / ikun 系）跑通全流程的最小集；P1 = 生态长尾兼容与体验。

### P0（不做就不是 LX 兼容宿主）

- [ ] **P0-1** 执行前注入完整 `globalThis.lx`：`EVENT_NAMES` / `on` / `send` / `request` / `env='mobile'` / `version`（解构不可缺成员）
- [ ] **P0-2** `EVENT_NAMES` 三常量：`inited` / `request` / `updateAlert`
- [ ] **P0-3** `on(request, handler)`：接收注册；派发 `{action, source, info}`；**必须拿到 Promise**；handler 超时（10–15s 可配置）按 reject 处理
- [ ] **P0-4** `send(inited)`：解析 `{status, openDevTools?, sources}`；`status:false` 走失败路径；未知字段忽略
- [ ] **P0-5** `sources` 解析：`name(可选)/type/actions/qualitys`；**actions 驱动能力分发**；qualitys 未知档容错
- [ ] **P0-6** 三 action 派发与出参解析：`musicUrl→url 字符串`、`lyric→{lyric,tlyric,rlyric,lxlyric}`、`pic→url 字符串`；未知 action 快速失败
- [ ] **P0-7** `musicInfo` 全字段透传（含 `meta.*` 嵌套），数据模型预留 sourceSpecific 通道
- [ ] **P0-8** `request(url, options, callback)` 桥：`method/headers/body/form/formData/timeout` 全支持；**恒 3 参 callback 且 `resp.body===body`**；JSON content-type 自动解析（needle 语义）；默认 GET；**返回取消函数**；err 为可读 Error
- [ ] **P0-9** 桥接默认 UA `lx-music-mobile/{version}`，脚本显式 headers 优先；header 透传制（黑名单封敏感头）
- [ ] **P0-10** `setTimeout` / `clearTimeout` 注入
- [ ] **P0-11** `console.log/warn/error` 捕获（1024 字符截断）+ 脚本日志面板
- [ ] **P0-12** 引擎级 Promise / async-await / ES6+（含模板串、解构、展开、可选链——生态脚本混淆后什么都用）
- [ ] **P0-13** 头注释解析 `@name/@description/@version/@author/@homepage`（@name ≤24、@description ≤36 校验）
- [ ] **P0-14** `updateAlert` 接收（≤1 次）→ 更新弹窗 → `updateUrl` 下载重导入（域名审计）
- [ ] **P0-15** 沙箱白名单：无文件/进程/原生网络能力，所有 HTTP 仅经 P0-8 桥并记录 URL；明文 http 放行策略（按脚本域名）
- [ ] **P0-16** 单源失败降级钩子：handler reject / 超时 / isolate 崩溃 → 多源自动切换（ADR-0002）

### P1（生态长尾 + 体验）

- [ ] **P1-1** `utils.buffer`：`from` / `bufToString`（utf8/hex/base64）
- [ ] **P1-2** `utils.crypto`：`md5` / `randomBytes` / `aesEncrypt`(128-cbc/ecb) / `rsaEncrypt`
- [ ] **P1-3** `currentScriptInfo` 注入（脚本自省）
- [ ] **P1-4** `request` 取消函数真实生效 + isolate 销毁时终止在途请求
- [ ] **P1-5** 多脚本并存：每脚本独立运行环境/日志/启停
- [ ] **P1-6** `@updateUrl` 定时检查 + 静默更新确认
- [ ] **P1-7** 域名审计面板：按脚本展示桥接过的 URL 列表（ADR-0002 安全承诺的 UI 落点）
- [ ] **P1-8** `utils.zlib`（inflate/deflate，对齐桌面版）
- [ ] **P1-9** 歌单源（`type: songlist`）与 `local` 源调研（桌面版扩展，标本未用）
- [ ] **P1-10** `.lxmc` 等加密脚本格式调研（生态存在，非官方公开协议）
- [ ] **P1-11** 官方文档 typo 容错：lyric 出参兼容 `lryic/tlryic` 拼写（读取侧宽容）

## 10. 参考

- 标本：`docs/research/reference/HYWmusic_free_v1.0.0.js`（本文行号引用）
- [洛雪移动版·自定义源脚本编写说明](https://lxmusic.toside.cn/mobile/custom-source)（updateAlert v1.2.0、环境限制、currentScriptInfo、utils 移动版差异）
- [洛雪桌面版·自定义源脚本编写说明](https://lxmusic.toside.cn/desktop/custom-source)（request options、handler、sources 结构、歌词逐字格式）
- [lyswhut/lx-music-mobile](https://github.com/lyswhut/lx-music-mobile) / [lx-music-desktop issue #1643](https://github.com/lyswhut/lx-music-desktop/issues/1643)（移动端引擎 = QuickJS 包装）
- [lx-music-mobile #635](https://github.com/lyswhut/lx-music-mobile/issues/635)（自定义源日志调试）
