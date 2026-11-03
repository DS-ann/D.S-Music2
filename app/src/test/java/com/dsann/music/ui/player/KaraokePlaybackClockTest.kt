package com.dsann.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class KaraokePlaybackClockTest {

    @Test
    fun firstFrameAnchorsToMedia3Position() {
        val clock = KaraokePlaybackClock()

        assertEquals(
            12_345,
            clock.update(
                frameTimeNanos = 1_000_000_000L,
                actualPositionMs = 12_345,
                playbackSpeed = 1f,
                isPlaying = true
            )
        )
    }

    @Test
    fun advancesAtNormalPlaybackSpeed() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 1f, true)

        val result = clock.update(1_016_666_667L, 10_017, 1f, true)

        assertEquals(10_017, result)
    }

    @Test
    fun advancesAtDoublePlaybackSpeed() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 2f, true)

        val result = clock.update(1_016_666_667L, 10_033, 2f, true)

        assertEquals(10_033, result)
    }

    @Test
    fun correctsSmallPhaseErrorWithinAFewFrames() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 1f, true)

        val result = clock.update(1_016_666_667L, 10_022, 1f, true)

        // The 5 ms sample error is corrected promptly without snapping the sweep.
        assertEquals(10_019, result)
    }

    @Test
    fun forwardSeekReanchorsImmediately() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 1f, true)

        val result = clock.update(1_016_666_667L, 15_017, 1f, true)

        assertEquals(15_017, result)
    }

    @Test
    fun shortSeekReanchorsImmediately() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 1f, true)

        val result = clock.update(1_016_666_667L, 10_067, 1f, true)

        assertEquals(10_067, result)
    }

    @Test
    fun backwardSeekReanchorsImmediately() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 30_000, 1f, true)

        val result = clock.update(1_016_666_667L, 8_017, 1f, true)

        assertEquals(8_017, result)
    }

    @Test
    fun pausedSeekIsReflectedImmediatelyAndPlaybackReanchors() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 1f, true)

        assertEquals(
            7_500,
            clock.update(1_100_000_000L, 7_500, 1f, false)
        )
        assertEquals(
            7_500,
            clock.update(1_200_000_000L, 7_500, 1f, true)
        )
    }

    @Test
    fun longFrameGapReanchorsInsteadOfInventingElapsedAudioTime() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 1f, true)

        val result = clock.update(1_250_000_000L, 10_250, 1f, true)

        assertEquals(10_250, result)
    }

    @Test
    fun invalidPlaybackSpeedFallsBackToNormalSpeed() {
        val clock = KaraokePlaybackClock()
        clock.update(1_000_000_000L, 10_000, 1f, true)

        val result = clock.update(1_016_666_667L, 10_017, Float.NaN, true)

        assertEquals(10_017, result)
    }

    @Test
    fun wordSweepProgressIsLinearInSourceTime() {
        assertEquals(0f, calculateKaraokeWordProgress(1_000L, 400L, 999L), 0f)
        assertEquals(0.25f, calculateKaraokeWordProgress(1_000L, 400L, 1_100L), 0.0001f)
        assertEquals(0.5f, calculateKaraokeWordProgress(1_000L, 400L, 1_200L), 0.0001f)
        assertEquals(0.75f, calculateKaraokeWordProgress(1_000L, 400L, 1_300L), 0.0001f)
        assertEquals(1f, calculateKaraokeWordProgress(1_000L, 400L, 1_400L), 0f)
    }

    @Test
    fun zeroWordDurationRemainsNumericallySafe() {
        assertEquals(0f, calculateKaraokeWordProgress(1_000L, 0L, 999L), 0f)
        assertEquals(0f, calculateKaraokeWordProgress(1_000L, 0L, 1_000L), 0f)
        assertEquals(1f, calculateKaraokeWordProgress(1_000L, 0L, 1_001L), 0f)
    }

    @Test
    fun negativePositionIsNeverReturned() {
        val clock = KaraokePlaybackClock()

        assertEquals(
            0,
            clock.update(
                frameTimeNanos = 1_000_000_000L,
                actualPositionMs = -500,
                playbackSpeed = 1f,
                isPlaying = true
            )
        )
    }
}
