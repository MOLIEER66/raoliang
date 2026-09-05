package com.echomusic.app.core.data.db

import com.echomusic.app.core.model.PlayMode
import com.echomusic.app.core.model.Song
import com.echomusic.app.core.model.SongSource
import com.echomusic.app.core.model.ThemeMode

/**
 * Entity ↔ 领域模型映射（core.model 零 Android 依赖，映射本身纯 JVM 可测）。
 */

fun SongEntity.toModel(): Song = Song(
    id = id,
    source = runCatching { SongSource.valueOf(source) }.getOrDefault(SongSource.LOCAL),
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    path = path,
    dateModifiedSec = dateModifiedSec,
    year = year,
    mimeType = mimeType,
    sourceSongId = onlineKey,
    lastPlayedAtMs = lastPlayedAtMs,
    playCount = playCount,
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    source = source.name,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    path = path,
    dateModifiedSec = dateModifiedSec,
    year = year,
    mimeType = mimeType,
    onlineKey = sourceSongId,
    lastPlayedAtMs = lastPlayedAtMs,
    playCount = playCount,
)

fun List<SongEntity>.toModels(): List<Song> = map { it.toModel() }

fun ThemeMode.toKey(): String = name

fun themeModeFromKey(key: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == key } ?: ThemeMode.SYSTEM

fun PlayMode.toKey(): String = name

fun playModeFromKey(key: String?): PlayMode =
    PlayMode.entries.firstOrNull { it.name == key } ?: PlayMode.REPEAT_ALL
