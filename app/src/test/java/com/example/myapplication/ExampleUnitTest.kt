package com.example.myapplication

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun parsesOkMessageForEventLogging() {
        val result = GeminiClient.parseAnalysis(
            """{"categoria":"ok","severidad":"ninguna","fragmento":""}"""
        )

        assertEquals(Category.OK, result.category)
        assertTrue(result.shouldPersist)
    }

    @Test
    fun parsesHighRiskMessageForPersistence() {
        val result = GeminiClient.parseAnalysis(
            """{"categoria":"grooming","severidad":"alta","fragmento":"no le digas a nadie"}"""
        )

        assertEquals(Category.GROOMING, result.category)
        assertEquals(Severity.ALTA, result.severity)
        assertTrue(result.shouldPersist)
        assertEquals("no le digas a nadie", result.fragment)
    }
}