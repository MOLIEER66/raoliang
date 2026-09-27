package com.echomusic.app.core.data.sync

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStore 查询包装接口（BREAKDOWN §3.2：以 Robolectric 的 ShadowContentResolver 喂
 * fixture 游标验证查询参数；业务断言仍落在 [syncDiff] 纯函数上）。
 *
 * 拆两个查询的原因：增量同步的「增改」用 `DATE_MODIFIED > lastSyncAt` 的全字段批次，
 * 「删除检测」需要全量 `_id` 清单做集合差——两条管道，一次封装。
 * 前置契约：调用方（LibraryRepository）须先确认 READ_MEDIA_AUDIO（或 26–32 的
 * READ_EXTERNAL_STORAGE）已授予，本层不做权限判断。
 */
interface MediaStoreSource {

    /** 自 [lastSyncAtSec]（含）以来新增/修改的音频行（全字段） */
    suspend fun queryUpdatedSince(lastSyncAtSec: Long): List<MediaStoreAudioRow>

    /** 当前 MediaStore 中全部音频 `_id`（删除检测基准） */
    suspend fun queryAllIds(): List<Long>
}

/**
 * [MediaStoreSource] 的 ContentResolver 实现（Context 薄封装，所有查询走 IO 调度器）。
 * 排序按 `_id` 保证输出稳定；`DATE_MODIFIED` 为 TEXT 列（秒），统一转 Long。
 *
 * 注意：列常量必须经类名（`MediaStore.Audio.Media.X`）访问——Java 静态常量在 Kotlin
 * 里不能经实例别名取，否则 Unresolved reference（T3 联调实录）。
 */
class MediaStoreSourceImpl(private val context: Context) : MediaStoreSource {

    override suspend fun queryUpdatedSince(lastSyncAtSec: Long): List<MediaStoreAudioRow> =
        withContext(Dispatchers.IO) {
            // >= 而非 >：边界文件不漏；时间戳相等的行由 syncDiff 的幂等规则跳过
            query(
                selection = "${MediaStore.Audio.Media.DATE_MODIFIED} >= ?",
                args = arrayOf(lastSyncAtSec.toString()),
            )
        }

    override suspend fun queryAllIds(): List<Long> = withContext(Dispatchers.IO) {
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            null,
            null,
            MediaStore.Audio.Media._ID,
        )?.use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        ids
    }

    private fun query(selection: String?, args: Array<String>?): List<MediaStoreAudioRow> {
        val rows = ArrayList<MediaStoreAudioRow>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            selection,
            args,
            MediaStore.Audio.Media._ID,
        )?.use { c ->
            val idx = mutableMapOf<String, Int>()
            PROJECTION.forEachIndexed { i, col -> idx[col] = i }
            while (c.moveToNext()) {
                rows += MediaStoreAudioRow(
                    id = c.getLong(idx[MediaStore.Audio.Media._ID]!!),
                    title = c.getString(idx[MediaStore.Audio.Media.TITLE]!!),
                    artist = c.getString(idx[MediaStore.Audio.Media.ARTIST]!!),
                    album = c.getString(idx[MediaStore.Audio.Media.ALBUM]!!),
                    albumId = c.getLong(idx[MediaStore.Audio.Media.ALBUM_ID]!!),
                    durationMs = c.getLong(idx[MediaStore.Audio.Media.DURATION]!!),
                    sizeBytes = c.getLong(idx[MediaStore.Audio.Media.SIZE]!!),
                    path = c.getString(idx[MediaStore.Audio.Media.DATA]!!),
                    // DATE_MODIFIED 在 Audio.Media 是 TEXT 秒级时间戳，个别 ROM 以数字返回，
                    // 两种取法都归一到 Long
                    dateModifiedSec = c.getString(idx[MediaStore.Audio.Media.DATE_MODIFIED]!!)
                        ?.toLongOrNull()
                        ?: c.getDouble(idx[MediaStore.Audio.Media.DATE_MODIFIED]!!).toLong(),
                    year = c.getInt(idx[MediaStore.Audio.Media.YEAR]!!).takeIf { it != 0 },
                    mimeType = c.getString(idx[MediaStore.Audio.Media.MIME_TYPE]!!),
                )
            }
        }
        return rows
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
        )
    }
}
