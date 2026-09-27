package com.echomusic.app.core.data.sync

import com.echomusic.app.core.model.Song
import com.echomusic.app.core.model.SongSource

/**
 * 同步差集结果。
 *
 * @property toUpsert 待写入/更新的行（已并入现库的播放统计字段，直接 REPLACE 覆盖）
 * @property toDeleteIds MediaStore 已消失的现库 LOCAL 曲目 id
 */
data class SyncDiffResult(
    val toUpsert: List<Song>,
    val toDeleteIds: List<Long>,
) {
    val hasChanges: Boolean get() = toUpsert.isNotEmpty() || toDeleteIds.isNotEmpty()
}

/**
 * 库同步核心 diff（ADR-0004 D2 / BREAKDOWN T3）——纯函数，可整段 JVM 单测。
 *
 * 规则（按 BREAKDOWN T3 验收标准逐条落点）：
 *  1. **去重**：incoming 先按 `_id` 去重，重复行保留 `dateModifiedSec` 最大的一条；
 *  2. **增改**：incoming 中「现库没有」或「现库有且 DATE_MODIFIED 严格更大」的行进入 upsert；
 *     时间戳相等/更小 → 判定未变更，跳过（防重复同步误判，幂等）；
 *  3. **统计保留**：upsert 行并入现库的 `lastPlayedAtMs` / `playCount`——重新扫描不丢「最近/最常播放」；
 *  4. **删除**：现库 LOCAL 曲目 id − 本轮见到的 id 集合（[seenIds]）。
 *     `seenIds` 独立传参：增量模式下 incoming 只是变更子集，删除检测必须基于全量 id 清单
 *     （MediaStoreSource.queryAllIds）；ONLINE 行（M2）不在删除检测范围内。
 *
 * @param current   现库快照（全部来源）
 * @param incoming  本轮 MediaStore 查询结果（全量或增量批次，允许含重复 `_id`）
 * @param seenIds   本轮 MediaStore 见到的全部 `_id`（删除检测基准）
 */
fun syncDiff(
    current: List<Song>,
    incoming: List<Song>,
    seenIds: Set<Long>,
): SyncDiffResult {
    // 1. 去重：同 _id 保留 dateModifiedSec 最大的
    val incomingById = HashMap<Long, Song>()
    for (row in incoming) {
        val existing = incomingById[row.id]
        if (existing == null || row.dateModifiedSec > existing.dateModifiedSec) {
            incomingById[row.id] = row
        }
    }

    val currentById = current.associateBy { it.id }

    // 2/3. 增改判定 + 统计保留
    val toUpsert = ArrayList<Song>(incomingById.size)
    for ((id, row) in incomingById) {
        val existing = currentById[id]
        if (existing == null || row.dateModifiedSec > existing.dateModifiedSec) {
            toUpsert += row.copy(
                lastPlayedAtMs = existing?.lastPlayedAtMs,
                playCount = existing?.playCount ?: 0,
            )
        }
    }

    // 4. 删除 = 现库 LOCAL id − 本轮见到的 id
    val toDelete = current
        .filter { it.source == SongSource.LOCAL && it.id !in seenIds }
        .map { it.id }

    return SyncDiffResult(toUpsert = toUpsert, toDeleteIds = toDelete)
}
