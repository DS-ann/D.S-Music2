package com.dsann.music.desktop

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

internal data class DesktopSavedSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val audioPath: String,
    val coverPath: String? = null,
    val lyricsPath: String? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)

internal data class DesktopPlaylist(
    val id: String,
    val title: String,
    val songIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val songs: List<DesktopSavedSong> = emptyList()
)

internal object DesktopLibraryStore {
    private val gson = Gson()
    val root: File by lazy {
        val roaming = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        File(roaming ?: System.getProperty("user.home"), "SpatialFlow").apply { mkdirs() }
    }
    val downloadsDirectory: File get() = File(root, "Downloads").apply { mkdirs() }
    val cacheDirectory: File get() = File(root, "Cache").apply { mkdirs() }
    private val favoritesFile get() = File(root, "favorites.json")
    private val playlistsFile get() = File(root, "playlists.json")
    private val downloadsFile get() = File(root, "downloads.json")
    private val cacheFile get() = File(root, "cache.json")
    private val favoriteSongsFile get() = File(root, "favorite-songs.json")

    @Synchronized
    fun favorites(): Set<String> = readSet(favoritesFile)

    @Synchronized
    fun setFavorite(videoId: String, enabled: Boolean) {
        val values = favorites().toMutableSet()
        if (enabled) values.add(videoId) else values.remove(videoId)
        favoritesFile.writeText(gson.toJson(values))
    }

    @Synchronized
    fun favoriteSongs(): List<DesktopSavedSong> = readSongs(favoriteSongsFile)

    @Synchronized
    fun setFavorite(song: com.dsann.music.data.innertube.OnlineSong, enabled: Boolean) {
        val current = favoriteSongs().filterNot { it.videoId == song.videoId }.toMutableList()
        if (enabled) {
            current.add(
                DesktopSavedSong(
                    videoId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    album = song.albumName.orEmpty(),
                    durationMs = song.durationMs,
                    thumbnailUrl = song.thumbnailUrl,
                    audioPath = ""
                )
            )
        }
        favoriteSongsFile.writeText(gson.toJson(current.sortedBy { it.title.lowercase() }))
    }

    @Synchronized
    fun removeDownload(videoId: String) {
        downloadsFile.writeText(gson.toJson(downloads().filterNot { it.videoId == videoId }))
        cacheFile.writeText(gson.toJson(readSongs(cacheFile).filterNot { it.videoId == videoId }))
    }

    @Synchronized
    fun playlists(): List<DesktopPlaylist> = readList(playlistsFile)

    @Synchronized
    fun createPlaylist(title: String): DesktopPlaylist {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "Enter a playlist name." }
        val all = playlists().toMutableList()
        val item = DesktopPlaylist(id = "pl-${System.currentTimeMillis()}", title = clean)
        all.add(0, item)
        playlistsFile.writeText(gson.toJson(all))
        return item
    }

    @Synchronized
    fun addToPlaylist(playlistId: String, videoId: String) {
        val all = playlists().map { playlist ->
            if (playlist.id == playlistId && videoId !in playlist.songIds) {
                playlist.copy(songIds = playlist.songIds + videoId)
            } else playlist
        }
        playlistsFile.writeText(gson.toJson(all))
    }

    @Synchronized
    fun addToPlaylist(playlistId: String, song: com.dsann.music.data.innertube.OnlineSong) {
        val all = playlists().map { playlist ->
            if (playlist.id == playlistId && song.videoId !in playlist.songIds) {
                val saved = DesktopSavedSong(
                    videoId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    album = song.albumName.orEmpty(),
                    durationMs = song.durationMs,
                    thumbnailUrl = song.thumbnailUrl,
                    audioPath = ""
                )
                playlist.copy(songIds = playlist.songIds + song.videoId, songs = playlist.songs + saved)
            } else playlist
        }
        playlistsFile.writeText(gson.toJson(all))
    }

    @Synchronized
    fun removeFromPlaylist(playlistId: String, videoId: String) {
        val updated = playlists().map { playlist ->
            if (playlist.id != playlistId) playlist
            else playlist.copy(
                songIds = playlist.songIds.filterNot { it == videoId },
                songs = playlist.songs.filterNot { it.videoId == videoId }
            )
        }
        playlistsFile.writeText(gson.toJson(updated))
    }

    @Synchronized
    fun deletePlaylist(playlistId: String) {
        playlistsFile.writeText(gson.toJson(playlists().filterNot { it.id == playlistId }))
    }

    @Synchronized
    fun downloads(): List<DesktopSavedSong> = readSongs(downloadsFile)

    @Synchronized
    fun cache(): List<DesktopSavedSong> = readSongs(cacheFile).filter { item ->
        File(item.audioPath).isFile &&
            !item.coverPath.isNullOrBlank() && File(item.coverPath).isFile &&
            !item.lyricsPath.isNullOrBlank() && File(item.lyricsPath).isFile
    }

    @Synchronized
    fun recordDownload(song: DesktopSavedSong, cached: Boolean) {
        val downloads = downloads().filterNot { it.videoId == song.videoId } + song
        downloadsFile.writeText(gson.toJson(downloads.sortedByDescending { it.downloadedAt }))
        if (cached) {
            val cache = readSongs(cacheFile).filterNot { it.videoId == song.videoId } + song
            cacheFile.writeText(gson.toJson(cache.sortedByDescending { it.downloadedAt }))
        }
    }

    private fun readSet(file: File): Set<String> = runCatching {
        if (!file.isFile) emptySet()
        else gson.fromJson<Set<String>>(file.readText(), object : TypeToken<Set<String>>() {}.type) ?: emptySet()
    }.getOrDefault(emptySet())

    private fun readList(file: File): List<DesktopPlaylist> = runCatching {
        if (!file.isFile) emptyList()
        else gson.fromJson<List<DesktopPlaylist>>(file.readText(), object : TypeToken<List<DesktopPlaylist>>() {}.type) ?: emptyList()
    }.getOrDefault(emptyList())

    private fun readSongs(file: File): List<DesktopSavedSong> = runCatching {
        if (!file.isFile) emptyList()
        else gson.fromJson<List<DesktopSavedSong>>(file.readText(), object : TypeToken<List<DesktopSavedSong>>() {}.type) ?: emptyList()
    }.getOrDefault(emptyList())
}
