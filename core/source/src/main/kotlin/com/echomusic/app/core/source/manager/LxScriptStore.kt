package com.echomusic.app.core.source.manager

import com.echomusic.app.core.source.LxScriptMeta

/**
 * 脚本存储接口（T5 · 导入管线的落盘点）。
 *
 * Android 实现落 `filesDir/sources/`（M2 T7 波次接入 DataStore 登记元数据）；
 * 接口保持纯 JVM——store 单测与 SourceManager 单测全用内存假体。
 */
interface LxScriptStore {

    /** store 单条记录：元数据 + 文件名 + 启用态 + 排序 + 源码（read 时才带）。 */
    data class Entry(
        val meta: LxScriptMeta,
        val file: String,
        val enabled: Boolean,
        val order: Int,
        val code: String? = null,
    )

    /** 全量读（不含源码体的轻量形态由调用方决定；M2 简化为带码读）。 */
    suspend fun readAll(): List<Entry>

    /** 单条读（含源码）。 */
    suspend fun read(name: String): Entry?

    /** 落盘新脚本：返回存储文件名（实现方决定命名，如 name 哈希）。 */
    suspend fun write(name: String, code: String): String

    /** 登记/更新元数据（write 后调用）。 */
    suspend fun upsert(meta: LxScriptMeta, file: String, order: Int)

    suspend fun setEnabled(name: String, enabled: Boolean)

    suspend fun delete(name: String)

    /** 内存假体（单测用）。 */
    class Fake : LxScriptStore {
        val entries = mutableMapOf<String, Entry>()
        val written = mutableMapOf<String, String>()

        override suspend fun readAll(): List<Entry> = entries.values.sortedBy { it.order }

        override suspend fun read(name: String): Entry? = entries[name]

        override suspend fun write(name: String, code: String): String {
            written[name] = code
            return "$name.js"
        }

        override suspend fun upsert(meta: LxScriptMeta, file: String, order: Int) {
            entries[meta.name] = Entry(meta, file, enabled = true, order = order, code = written[meta.name])
        }

        override suspend fun setEnabled(name: String, enabled: Boolean) {
            entries[name] = entries[name]?.copy(enabled = enabled) ?: return
        }

        override suspend fun delete(name: String) {
            entries.remove(name)
            written.remove(name)
        }
    }
}
