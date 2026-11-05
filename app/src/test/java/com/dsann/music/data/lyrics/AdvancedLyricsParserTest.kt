package com.dsann.music.data.lyrics

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
    fun legacySpatialFlowKaraokeCacheMarkersRemainReadable() {
        val legacyWordPayload =
            "SPATIALFLOW-WORDS:QRC\n[1000,2000]Hello (1000,300)world (1300,400)"
        assertTrue(AdvancedLyricsParser.recognizes(legacyWordPayload))
        val legacyLine = AdvancedLyricsParser.parse(legacyWordPayload)!!.single()
        assertEquals("Hello world", legacyLine.content)
        assertEquals(1000L, legacyLine.words.first().absoluteStartTimeMs)

        val legacyLyricsPlus =
            "SPATIALFLOW-LYRICSPLUS\n" +
                """{"type":"Word","lyrics":[{"time":"1000","duration":"800","text":"Hi","syllabus":[{"time":"1000","duration":"800","text":"Hi"}]}]}"""
        assertTrue(AdvancedLyricsParser.recognizes(legacyLyricsPlus))
        assertEquals("Hi", AdvancedLyricsParser.parse(legacyLyricsPlus)!!.single().content)
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
    @Test
    fun ttmlWithoutWordTimestampsRemainsLineSynced() {
        val lines = TtmlParser.parse(
            """<tt><body><div><p begin="1s" end="3s"><span>Hello </span><span>world</span></p></div></body></tt>"""
        )
        assertEquals(1, lines.size)
        assertEquals("Hello world", lines.single().content)
        assertFalse(lines.single().isWordByWord)
        assertTrue(lines.single().words.isEmpty())
    }

    @Test
    fun ttmlPartiallyTimedSpansDoNotPretendUntimedTextIsKaraoke() {
        val lines = TtmlParser.parse(
            """<tt><body><div><p begin="1s" end="3s"><span begin="1s" end="2s">Timed</span> <span>plain</span></p></div></body></tt>"""
        )
        val line = lines.single()
        assertFalse(line.isWordByWord)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun ttmlStartOnlyWordsUseNextTimestampAndParagraphEnd() {
        val lines = TtmlParser.parse(
            """<tt><body><div><p begin="1s" end="3s"><span begin="1s">Hello </span><span begin="1.8s">world</span></p></div></body></tt>"""
        )
        val line = lines.single()
        assertTrue(line.isWordByWord)
        assertEquals("Hello world", line.content)
        assertEquals(800L, line.words[0].durationMs)
        assertEquals(1200L, line.words[1].durationMs)
        assertEquals(0, line.words[0].charRange.first)
        assertEquals(4, line.words[0].charRange.last)
        assertEquals(6, line.words[1].charRange.first)
        assertEquals(10, line.words[1].charRange.last)
    }

    @Test
    fun qrcCharacterRangesExcludeLeadingAndTrailingTokenWhitespace() {
        val line = LrcParser.parse("[1000,2000] (1000,300)Hello (1300,400)world (1800,200)").single()
        assertEquals("Hello world", line.content)
        assertEquals(0, line.words.first().charRange.first)
        assertEquals(4, line.words.first().charRange.last)
        assertEquals(6, line.words.last().charRange.first)
        assertEquals(10, line.words.last().charRange.last)
    }

}
