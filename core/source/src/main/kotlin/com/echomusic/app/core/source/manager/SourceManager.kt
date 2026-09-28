package com.echomusic.app.core.source.manager

import com.echomusic.app.core.source.LxInitResult
import com.echomusic.app.core.source.LxProtocol
import com.echomusic.app.core.source.LxResult
import com.echomusic.app.core.source.LxRuntime
import com.echomusic.app.core.source.LxScriptEvent
import com.echomusic.app.core.source.LxScriptMeta
import com.echomusic.app.core.source.LxSourceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * 多音源管理器（T5 · ADR-0002 技术要点）。
 *
 * 职责：
 * - 持有已导入脚本（[StoredScript]）与各自的 [LxRuntime] 实例；
 * - 提供业务入口 [resolveMusicUrl] / [fetchLyric] / [fetchPic]——内部按用户排序的启用源
 *   走 [FallbackPolicy] 降级（reject/超时/崩溃 → 自动切下一源，P0-16）；
 * - 未声明 action 的（源,平台,action）组合直接跳过该源（协议 §5.5 快速失败）。
 *
 * 依赖注入姿态（ADR-0004 D3 依赖方向 js → source，这里不 import :core:js）：
 * [runtimeFactory] 由 app 装配层提供（真身 JsEngineRuntime，测试假体直造）。
 * [scriptStore] 由 Android 层实现（filesDir/sources/ 落盘），接口纯 JVM 可假体。
 */
class SourceManager(
    private val runtimeFactory: suspend (scriptCode: String, meta: LxScriptMeta?) -> LxRuntime?,
    private val scriptStore: LxScriptStore,
) {

    /** 已导入脚本的登记项（store 条目 + 运行时句柄，纯内存）。 */
    data class ManagedSource(
        val meta: LxScriptMeta,
        val file: String,
        val enabled: Boolean,
        val order: Int,
    ) {
        val displayName: String get() = meta.name
    }

    /** inited 握手后的能力面（sources 表），按平台 id 索引。 */
    data class SourceCaps(
        val scriptName: String,
        val platforms: Map<String, LxSourceInfo>,
    )

    private val mutex = Mutex()

    private val _sources = MutableStateFlow<List<ManagedSource>>(emptyList())
    val sources: StateFlow<List<ManagedSource>> = _sources.asStateFlow()

    private val _caps = MutableStateFlow<Map<String, SourceCaps>>(emptyMap())
    /** 脚本名 → 能力面（UI 的"音源详情"数据源之一）。 */
    val caps: StateFlow<Map<String, SourceCaps>> = _caps.asStateFlow()

    /** 运行时实例池：脚本名 → runtime（失败装载的脚本不入池）。 */
    private val runtimes = mutableMapOf<String, LxRuntime>()

    // ------------------------------------------------------------------ 装载

    /**
     * 启动：读 store 全量脚本 → 逐个建 runtime + 握手。
     * 握手失败（status:false / 崩溃）的源保留登记但标记不可用（caps 缺位），
     * UI 展示可读错误；不阻断其他源（故障隔离，P0-15/16）。
     */
    suspend fun loadAll() {
        mutex.withLock {
            val stored = scriptStore.readAll()
            _sources.value = stored.map { ManagedSource(it.meta, it.file, it.enabled, it.order) }
            stored.filter { it.enabled }.forEach { entry ->
                launchScript(entry.file, entry.meta, entry.code)
            }
        }
    }

    private suspend fun launchScript(file: String, meta: LxScriptMeta, code: String?) {
        if (code == null) return
        val rt = runtimeFactory(code, meta) ?: return
        when (val init = rt.load(code, meta)) {
            is LxInitResult.Ready -> {
                runtimes[meta.name] = rt
                _caps.value = _caps.value + (meta.name to SourceCaps(
                    scriptName = meta.name,
                    platforms = init.sources,
                ))
                relayEvents(meta.name, rt)
            }
            is LxInitResult.Failed -> rt.close()
        }
    }

    // ------------------------------------------------------------------ 业务入口（降级）

    /** musicUrl 解析（在线播放的取流入口，T6 由 ResolvingDataSource 调用）。 */
    suspend fun resolveMusicUrl(
        sourceSpecific: JsonElement,
        platform: String,
        quality: String?,
    ): ResolutionOutcome = dispatch(LxProtocol.Actions.MUSIC_URL, platform, sourceSpecific, quality)

    /** 歌词获取（T8 歌词页数据源）。 */
    suspend fun fetchLyric(sourceSpecific: JsonElement, platform: String): ResolutionOutcome =
        dispatch(LxProtocol.Actions.LYRIC, platform, sourceSpecific, null)

    /** 封面获取。 */
    suspend fun fetchPic(sourceSpecific: JsonElement, platform: String): ResolutionOutcome =
        dispatch(LxProtocol.Actions.PIC, platform, sourceSpecific, null)

    data class ResolutionOutcome(
        val data: JsonElement?,
        val viaSource: String?,
        val failureSummary: String?,
    ) {
        val isSuccess: Boolean get() = failureSummary == null
    }

    private suspend fun dispatch(
        action: String,
        platform: String,
        musicInfo: JsonElement,
        quality: String?,
    ): ResolutionOutcome {
        val candidates = enabledOrdered()
            .filter { capsOf(it.meta.name)?.platforms?.get(platform)?.supports(action) == true }
        if (candidates.isEmpty()) {
            return ResolutionOutcome(null, null, "没有启用的音源支持 $platform 的 $action")
        }
        val info = when (action) {
            LxProtocol.Actions.MUSIC_URL ->
                com.echomusic.app.core.source.wire.LxWire.encodeMusicUrlInfo(quality, musicInfo)
            else -> com.echomusic.app.core.source.wire.LxWire.encodeMusicInfo(musicInfo)
        }
        val result = FallbackPolicy.resolve(
            candidates.map { it.meta.name to it.displayName },
        ) { name ->
            val rt = runtimes[name]
            if (rt == null || !rt.isAlive) {
                LxResult.Error(LxResult.Error.Kind.RUNTIME_DEAD, "runtime unavailable")
            } else {
                rt.dispatchRequest(platform, action, info)
            }
        }
        return when (result) {
            is FallbackPolicy.Resolution.Success ->
                ResolutionOutcome(result.data, candidates[result.sourceIndex].meta.name, null)
            is FallbackPolicy.Resolution.AllFailed ->
                ResolutionOutcome(null, null, FallbackPolicy.summarize(result))
        }
    }

    // ------------------------------------------------------------------ 导入与增删

    /**
     * 导入新脚本（SAF 流程的落点）：头注释解析 → store 落盘 → 启动握手。
     * 返回导入结果（name 冲突时拒绝——同名脚本视为重复导入）。
     */
    suspend fun import(code: String): ImportResult {
        val meta = com.echomusic.app.core.source.protocol.LxHeaderParser.parse(code)
            ?: return ImportResult.Rejected("脚本没有可识别的头注释（@name 缺失）")
        var storedFile: String? = null
        mutex.withLock {
            if (_sources.value.any { it.meta.name == meta.name }) {
                return ImportResult.Duplicate(meta)
            }
            val order = (_sources.value.maxOfOrNull { it.order } ?: -1) + 1
            val file = scriptStore.write(meta.name, code)
            scriptStore.upsert(meta, file, order)
            _sources.value = _sources.value + ManagedSource(meta, file, enabled = true, order)
            storedFile = file
        }
        launchScript(file = storedFile ?: return ImportResult.Rejected("存储失败"), meta = meta, code = code)
        val ready = runtimes[meta.name]
        return if (ready?.isAlive == true) ImportResult.Ready(meta)
        else ImportResult.InitedFailed(meta)
    }

    sealed interface ImportResult {
        data class Ready(val meta: LxScriptMeta) : ImportResult
        data class Duplicate(val meta: LxScriptMeta) : ImportResult
        data class InitedFailed(val meta: LxScriptMeta) : ImportResult
        data class Rejected(val reason: String) : ImportResult
    }

    /** 删除源：关 runtime → 删 store 条目与文件 → 刷新列表。 */
    suspend fun delete(name: String) {
        mutex.withLock {
            runtimes.remove(name)?.close()
            scriptStore.delete(name)
            _sources.value = _sources.value.filterNot { it.meta.name == name }
            _caps.value = _caps.value - name
        }
    }

    /** 启停源（停用即关 runtime；启用即重新握手）。 */
    suspend fun setEnabled(name: String, enabled: Boolean) {
        val entry = _sources.value.firstOrNull { it.meta.name == name } ?: return
        scriptStore.setEnabled(name, enabled)
        if (!enabled) {
            runtimes.remove(name)?.close()
        } else {
            scriptStore.read(name)?.let { launchScript(it.file, it.meta, it.code ?: scriptStore.read(name)?.code) }
        }
        _sources.value = _sources.value.map {
            if (it.meta.name == name) it.copy(enabled = enabled) else it
        }
    }

    // ------------------------------------------------------------------ 内部

    private fun enabledOrdered(): List<ManagedSource> = _sources.value.filter { it.enabled }.sortedBy { it.order }

    private fun capsOf(name: String): SourceCaps? = _caps.value[name]

    /** 旁路事件总线（console/updateAlert），launchScript 时把各 runtime 的 events 汇入。 */
    private val _scriptEvents = MutableSharedFlow<LxScriptEvent>(extraBufferCapacity = 64)
    val scriptEvents: Flow<LxScriptEvent> = _scriptEvents.asSharedFlow()

    private fun relayEvents(name: String, rt: LxRuntime) {
        // scope 由构造方注入（见 init 协作约定）；M2 测试用注入 scope，生产用 app 级 scope
        eventsScope?.launch {
            rt.events.collect { _scriptEvents.emit(it) }
        }
    }

    /** 事件转发协程作用域（构造后注入；null = 不转发，测试可省）。 */
    internal var eventsScope: kotlinx.coroutines.CoroutineScope? = null
}
