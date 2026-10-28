package com.codetrio.spatialflow.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedLyricsParserTest {
    @Test
    fun qrcKeepsWordTimings() {
        val line = LrcParser.parse("[1000,2000]Hello (1000,300)world (1300,400)").single()
        assertEquals("Hello world", line.content)
        assertEquals(2, line.words.size)
        assertEquals(1000L, line.words[0].absoluteStartTimeMs)
        assertEquals(1300L, line.words[1].absoluteStartTimeMs)
    }

    @Test
    fun yrcKeepsAbsoluteWordTimings() {
        val line = LrcParser.parse("[1000,2000](1000,300,0)你(1300,400,0)好").single()
        assertEquals("你好", line.content)
        assertEquals(2, line.words.size)
        assertEquals(1000L, line.words[0].absoluteStartTimeMs)
        assertEquals(1300L, line.words[1].absoluteStartTimeMs)
    }

    @Test
    fun krcAddsLineRelativeOffsets() {
        val line = LrcParser.parse("[1000,1000]<0,300,0>你<300,400,0>好").single()
        assertEquals("你好", line.content)
        assertEquals(2, line.words.size)
        assertEquals(1000L, line.words[0].absoluteStartTimeMs)
        assertEquals(1300L, line.words[1].absoluteStartTimeMs)
    }

    @Test
    fun lyricsPlusRetainsSyllableTimingWithoutSpaces() {
        val wrapped = AdvancedLyricsParser.wrapLyricsPlus(
            """{"type":"Word","lyrics":[{"time":"1000","duration":"800","text":"你好","syllabus":[{"time":"1000","duration":"300","text":"你"},{"time":"1300","duration":"500","text":"好"}]}]}"""
        )
        val line = AdvancedLyricsParser.parse(wrapped)!!.single()
        assertEquals("你好", line.content)
        assertEquals(2, line.words.size)
        assertEquals(1000L, line.words[0].absoluteStartTimeMs)
        assertEquals(1300L, line.words[1].absoluteStartTimeMs)
    }

    @Test
    fun wholeSecondAndSingleDecimalLrcAreSynced() {
        assertTrue(LyricsResult.looksSynchronized("[01:23]line"))
        assertTrue(LyricsResult.looksSynchronized("[01:23.4]line"))
        assertTrue(LyricsResult.looksSynchronized("[01:23.456]line"))
    }

    @Test
    fun differentUnicodeTitlesGetDifferentCacheKeys() {
        val first = TrackMetadata(cleanedTitle = "गीत", cleanedArtist = "कलाकार", durationMs = 123000L)
        val second = TrackMetadata(cleanedTitle = "रात", cleanedArtist = "कलाकार", durationMs = 123000L)
        assertNotEquals(first.getCacheKey(), second.getCacheKey())
        assertFalse(first.getCacheKey().contains("||"))
    }

    @Test
    fun legacyCacheMigrationIsDisabledForUnicodeArtistOrTitle() {
        val nonLatinTitle = TrackMetadata(cleanedTitle = "गीत", cleanedArtist = "Artist", durationMs = 123000L)
        val nonLatinArtist = TrackMetadata(cleanedTitle = "Hello", cleanedArtist = "歌手", durationMs = 123000L)
        val asciiTrack = TrackMetadata(cleanedTitle = "Hello", cleanedArtist = "Artist", durationMs = 123000L)
        assertEquals(null, nonLatinTitle.getLegacyCacheKey())
        assertEquals(null, nonLatinArtist.getLegacyCacheKey())
        assertNotEquals(null, asciiTrack.getLegacyCacheKey())
    }
}
