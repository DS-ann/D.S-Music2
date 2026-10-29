package com.dsann.music.viewmodel.lyrics

import com.dsann.music.data.lyrics.LyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerLyricsStateControllerTest {

    @Test
    fun karaokeLyricsOutrankLineSyncedLyrics() {
        val controller = PlayerLyricsStateController("LyricsTest")
        val results = mapOf(
            "KuGou" to LyricsResult(
                syncedLyrics = "[00:01.00]KuGou line",
                providerName = "KuGou",
                confidence = 0.99f,
                isSynced = true
            ),
            "QQ Music" to LyricsResult(
                syncedLyrics = "SPATIALFLOW-WORDS:QRC\n[1000,1000]Hello (1000,300)world (1300,400)",
                providerName = "QQ Music",
                confidence = 0.78f,
                isSynced = true,
                isWordByWord = true
            )
        )

        assertEquals("QQ Music", controller.determineBestResult(results)?.providerName)
    }

    @Test
    fun kugouIsDefaultWhenQualityTierIsEqual() {
        val controller = PlayerLyricsStateController("LyricsTest")
        val results = mapOf(
            "LRCLIB" to LyricsResult(
                syncedLyrics = "[00:01.00]LRCLIB line",
                providerName = "LRCLIB",
                confidence = 0.97f,
                isSynced = true
            ),
            "KuGou" to LyricsResult(
                syncedLyrics = "[00:01.00]KuGou line",
                providerName = "KuGou",
                confidence = 0.71f,
                isSynced = true
            )
        )

        assertEquals("KuGou", controller.determineBestResult(results)?.providerName)
    }

    @Test
    fun emptyProviderFailureSentinelsNeverBecomeLyrics() {
        val controller = PlayerLyricsStateController("LyricsTest")
        val results = mapOf(
            "KuGou" to LyricsResult(providerName = "KuGou", confidence = -1f),
            "LRCLIB" to LyricsResult(providerName = "LRCLIB", confidence = -1f)
        )

        assertNull(controller.determineBestResult(results))
    }
}
