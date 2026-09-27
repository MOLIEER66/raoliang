package com.echomusic.app.core.data.sync

/**
 * 库同步进度（BREAKDOWN T3「同步进度可观测」）：供 SCREENS §1 扫描态的顶部 2dp 线性进度
 * 与实时计数消费。UI 波次经 `LibraryRepository.syncState`（StateFlow）订阅。
 */
data class SyncState(
    val phase: Phase = Phase.IDLE,
    /** 本轮 MediaStore 命中的曲目数 */
    val found: Int = 0,
    /** 已写入库的曲目数（分批写入期间实时增长） */
    val upserted: Int = 0,
    /** 本轮删除的失效曲目数 */
    val deleted: Int = 0,
    /** 失败时的可读信息（错误三件套的「细节」位，SCREENS §1 错误态） */
    val error: String? = null,
) {
    enum class Phase { IDLE, SCANNING, SAVING, DONE, FAILED }

    val isInProgress: Boolean get() = phase == Phase.SCANNING || phase == Phase.SAVING

    companion object {
        val IDLE = SyncState()
    }
}
