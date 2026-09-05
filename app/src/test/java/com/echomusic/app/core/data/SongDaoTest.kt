package com.echomusic.app.core.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.echomusic.app.core.data.db.EchoDatabase
import com.echomusic.app.core.data.db.EchoDatabase_Impl
import com.echomusic.app.core.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T0 门禁（BREAKDOWN §3.1）：Room3 KMP 化的直接红利——
 * 纯 JVM 上用内存库 + BundledSQLiteDriver 跑 DAO，不需要模拟器/Robolectric。
 * 注意：类路径上同时有 room3 的 android/jvm 变体，但两者都提供工厂 lambda 版
 * inMemoryDatabaseBuilder，此处显式传工厂，规避重载解析歧义。
 */
class SongDaoTest {

    private fun buildDb(): EchoDatabase =
        Room.inMemoryDatabaseBuilder<EchoDatabase> { EchoDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Test
    fun insertThenQueryRoundtrip() = runBlocking {
        val db = buildDb()
        val dao = db.songDao()

        dao.insertAll(
            listOf(
                SongEntity(id = 1, title = "阳关三叠"),
                SongEntity(id = 2, title = "广陵散"),
            ),
        )

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("阳关三叠", all.first { it.id == 1L }.title)
        // source 字段默认 LOCAL（D2 决策）
        assertEquals(SongEntity.SOURCE_LOCAL, all[0].source)

        db.close()
    }

    @Test
    fun replaceConflictKeepsSingleRow() = runBlocking {
        val db = buildDb()
        val dao = db.songDao()

        dao.insertAll(listOf(SongEntity(id = 1, title = "初版")))
        dao.insertAll(listOf(SongEntity(id = 1, title = "修订版")))

        assertEquals(1, dao.count())
        assertEquals("修订版", dao.observeAll().first().single().title)

        db.close()
    }
}
