package com.dsann.music

import com.dsann.music.data.innertube.InnerTubeParser
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InnerTubeGenreParserTest {

    @Test
    fun parsesPrismStyleMoodAndGenreNavigationButtons() {
        val response = JsonParser().parse(
            """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "musicNavigationButtonRenderer": {
                        "buttonText": { "runs": [{ "text": "Pop" }] },
                        "clickCommand": {
                          "browseEndpoint": {
                            "browseId": "FEmusic_moods_and_genres_category",
                            "params": "genre-params"
                          }
                        }
                      }
                    },
                    {
                      "musicNavigationButtonRenderer": {
                        "buttonText": { "simpleText": "Rock" },
                        "clickCommand": {
                          "browseEndpoint": { "browseId": "FEmusic_rock" }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        ).asJsonObject

        val categories = InnerTubeParser.parseMoodsAndGenres(response)

        assertEquals(2, categories.size)
        assertEquals("Pop", categories[0].title)
        assertEquals("FEmusic_moods_and_genres_category", categories[0].browseId)
        assertEquals("genre-params", categories[0].params)
        assertEquals("Rock", categories[1].title)
        assertEquals("FEmusic_rock", categories[1].browseId)
        assertNull(categories[1].params)
    }

    @Test
    fun fallsBackToLegacyChipCloudBrowseDestinations() {
        val response = JsonParser().parse(
            """
            {
              "chipCloudRenderer": {
                "chips": [
                  {
                    "chipCloudChipRenderer": {
                      "text": { "simpleText": "Jazz" },
                      "navigationEndpoint": {
                        "browseEndpoint": {
                          "browseId": "FEmusic_jazz",
                          "params": "jazz-params"
                        }
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        ).asJsonObject

        val categories = InnerTubeParser.parseMoodsAndGenres(response)

        assertEquals(1, categories.size)
        assertEquals("Jazz", categories.single().title)
        assertEquals("FEmusic_jazz", categories.single().browseId)
        assertEquals("jazz-params", categories.single().params)
    }
}
