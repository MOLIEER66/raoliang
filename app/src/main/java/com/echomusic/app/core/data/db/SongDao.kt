package com.echomusic.app.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * T0 冒烟 DAO：全部为 suspend / Flow（Room3 coroutines-only，无挂起即编译报错）。
 * 正式 songs DAO 的统计聚合、排序查询在 T2 扩展。
 */
@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs ORDER BY id")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int
}
