package com.echomusic.app.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.echomusic.app.core.model.PlayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 播放偏好存取（BREAKDOWN T6「播放模式状态存 PlaybackPreferences」/ ADR-0004 D4）。
 *
 * 独立最小存储：M1 只存播放模式，不与 T4 将来的 SettingsRepository 共用 DataStore 文件
 * （DataStore 单文件单实例，分文件互不阻塞）；T4 落地后若要归并，迁移到统一文件即可。
 * 队列本身不持久化（M4 playback resumption 才恢复队列，BREAKDOWN P0-11）。
 */
interface PlaybackSettings {

    /** 当前播放模式（DataStore 冷流，变更即发射） */
    val playMode: Flow<PlayMode>

    /** 写入播放模式（原子落盘，UI 切换与恢复会话共用） */
    suspend fun setPlayMode(mode: PlayMode)

    companion object {
        val DEFAULT_PLAY_MODE: PlayMode = PlayMode.REPEAT_ALL
    }
}

/**
 * 进程级单例 DataStore（文件 `playback_settings.preferences_pb`）。
 * DataStore 要求同一文件同进程仅一个实例，故用 delegate 收敛；T4 的设置文件另起名。
 */
internal val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_settings")

/**
 * [PlaybackSettings] 的 Preferences DataStore 实现。
 *
 * 主构造直接吃 `DataStore<Preferences>`（纯 JVM 单测可自建临时文件实例），
 * DI 便捷构造走 [Context.playbackDataStore] 单例。
 */
class PlaybackPreferences(private val store: DataStore<Preferences>) : PlaybackSettings {

    constructor(context: Context) : this(context.playbackDataStore)

    override val playMode: Flow<PlayMode> =
        store.data.map { prefs -> playModeFromStored(prefs[KEY_PLAY_MODE]) }

    override suspend fun setPlayMode(mode: PlayMode) {
        store.edit { it[KEY_PLAY_MODE] = mode.name }
    }

    companion object {

        private val KEY_PLAY_MODE = stringPreferencesKey("play_mode")

        /** 反序列化：脏数据/枚举更名时回退默认值，不抛异常 */
        fun playModeFromStored(raw: String?): PlayMode =
            raw?.let { stored -> runCatching { PlayMode.valueOf(stored) }.getOrNull() }
                ?: PlaybackSettings.DEFAULT_PLAY_MODE
    }
}
