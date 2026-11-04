package com.dsann.music.data.innertube

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCategoryCacheParserTest {

    @Test
    fun parsesCurrentWrappedGenreCacheIntoTypedCategories() {
        val categories = HomeCategoryCacheParser.parse(
            """
            {
              "categories": [
                { "title": "Pop", "browseId": "FEmusic_pop", "params": "pop-params" },
                { "title": "Rock", "browseId": "FEmusic_rock" }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                HomeCategory("Pop", "FEmusic_pop", "pop-params"),
                HomeCategory("Rock", "FEmusic_rock", null)
            ),
            categories
        )
        assertEquals(HomeCategory::class.java, categories.first().javaClass)
    }

    @Test
    fun acceptsLegacyArrayAndSkipsMalformedOrDuplicateEntries() {
        val categories = HomeCategoryCacheParser.parse(
            """
            [
              { "title": "Jazz", "browseId": "FEmusic_jazz" },
              { "title": "Broken", "browseId": "" },
              { "title": "Jazz duplicate", "browseId": "FEmusic_jazz" },
              { "title": "Ambient", "browseId": "FEmusic_ambient", "params": "ambient" },
              "not-an-object"
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf(
                HomeCategory("Jazz", "FEmusic_jazz", null),
                HomeCategory("Ambient", "FEmusic_ambient", "ambient")
            ),
            categories
        )
        assertEquals(true, categories.all { it.javaClass == HomeCategory::class.java })
    }
}
