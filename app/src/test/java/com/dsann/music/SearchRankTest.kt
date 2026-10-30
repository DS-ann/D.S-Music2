package com.dsann.music

import com.dsann.music.data.innertube.OnlineAlbum
import com.dsann.music.data.innertube.OnlineArtist
import com.dsann.music.data.innertube.OnlineArtistRef
import com.dsann.music.data.innertube.OnlinePlaylist
import com.dsann.music.data.innertube.OnlineSong
import com.dsann.music.data.innertube.SearchFilter
import com.dsann.music.data.innertube.SearchItem
import com.dsann.music.data.innertube.SearchRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankTest {
    @Test
    fun exactArtistWinsOverSameNameSongForAmbiguousSearch() {
        val song = SearchItem.Song(
            OnlineSong(videoId = "song-lisa", title = "LISA", artist = "Various Artists")
        )
        val artist = SearchItem.Artist(
            OnlineArtist(
                browseId = "UC-lisa",
                title = "LISA",
                subscriberCount = "Artist • 25M monthly audience"
            )
        )

        val result = SearchRank.rank(
            "lisa",
            listOf(
                SearchFilter.SONGS to listOf(song),
                SearchFilter.ARTISTS to listOf(artist)
            )
        )

        assertEquals("UC-lisa", (result.topResult as SearchItem.Artist).artist.browseId)
    }

    @Test
    fun exactWholeWordSongBeatsArtistNameThatOnlyStartsWithQuery() {
        val song = SearchItem.Song(
            OnlineSong(videoId = "halo-song", title = "Halo", artist = "Beyoncé")
        )
        val artist = SearchItem.Artist(
            OnlineArtist(browseId = "UC-halocene", title = "Halocene", subscriberCount = "300K subscribers")
        )

        val result = SearchRank.rank(
            "halo",
            listOf(
                SearchFilter.SONGS to listOf(song),
                SearchFilter.ARTISTS to listOf(artist)
            )
        )

        assertEquals("halo-song", (result.topResult as SearchItem.Song).song.videoId)
    }

    @Test
    fun trustedTopCardFindsArtistWhenTypedSearchMissesIt() {
        val literalAlbum = SearchItem.Album(
            OnlineAlbum(
                browseId = "MPREb-literal",
                title = "RHCP",
                artists = listOf(OnlineArtistRef("Unknown Artist"))
            )
        )
        val band = SearchItem.Artist(
            OnlineArtist("UC-rhcp", "Red Hot Chili Peppers", subscriberCount = "40.8M monthly audience")
        )

        val result = SearchRank.rank(
            "rhcp",
            listOf(SearchFilter.ALBUMS to listOf(literalAlbum)),
            card = band
        )

        assertEquals("UC-rhcp", (result.topResult as SearchItem.Artist).artist.browseId)
    }

    @Test
    fun typoTopCardCanWinWhenCategorySearchDoesNotFindIt() {
        val unrelatedSong = SearchItem.Song(
            OnlineSong(videoId = "other", title = "A Day", artist = "Someone Else")
        )
        val card = SearchItem.Artist(
            OnlineArtist("UC-taylor", "Taylor Swift", subscriberCount = "440M monthly audience")
        )

        val result = SearchRank.rank(
            "taylor swfit",
            listOf(SearchFilter.SONGS to listOf(unrelatedSong)),
            card = card
        )

        assertEquals("UC-taylor", (result.topResult as SearchItem.Artist).artist.browseId)
    }

    @Test
    fun remixCardDoesNotDisplaceTheOriginalSong() {
        val remix = SearchItem.Song(
            OnlineSong(
                videoId = "flip",
                title = "Blinding Lights (Reggae Flip)",
                artist = "Unknown Cover Act"
            )
        )
        val original = SearchItem.Song(
            OnlineSong(videoId = "original", title = "Blinding Lights", artist = "The Weeknd")
        )

        val result = SearchRank.rank(
            "blinding lights",
            listOf(SearchFilter.SONGS to listOf(remix, original)),
            card = remix
        )

        assertEquals("original", (result.topResult as SearchItem.Song).song.videoId)
    }

    @Test
    fun matchedAudioRecordingWinsOverItsVideoCard() {
        val video = SearchItem.Song(
            OnlineSong(videoId = "video", title = "HUMBLE.", artist = "Kendrick Lamar", isVideo = true)
        )
        val song = SearchItem.Song(
            OnlineSong(videoId = "track", title = "HUMBLE.", artist = "Kendrick Lamar")
        )

        val result = SearchRank.rank(
            "humble kendrick",
            listOf(
                SearchFilter.SONGS to listOf(song),
                SearchFilter.VIDEOS to listOf(video)
            ),
            card = video
        )

        assertEquals("track", (result.topResult as SearchItem.Song).song.videoId)
    }

    @Test
    fun liveResultsRemainLastWhenMusicResultsExist() {
        val song = SearchItem.Song(OnlineSong("track", "A Day", "Someone Else"))
        val live = SearchItem.Song(OnlineSong("live", "Lofi radio", "Lofi Girl", isVideo = true, isLive = true))

        val result = SearchRank.rank(
            "A Day",
            listOf(SearchFilter.SONGS to listOf(song), SearchFilter.LIVE to listOf(live))
        )

        assertEquals("track", (result.topResult as SearchItem.Song).song.videoId)
        assertEquals(SearchFilter.LIVE, result.sections.last().filter)
        assertTrue(result.sections.last().items.isNotEmpty())
    }
}
