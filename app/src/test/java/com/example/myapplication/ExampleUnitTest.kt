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
    fun parsesGroomingMessageForEventLogging() {
        val result = GeminiClient.parseAnalysis(
            """{"categoria":"grooming","severidad":"bajo"}"""
        )

        assertEquals(Category.GROOMING, result.category)
        assertEquals(Severity.BAJO, result.severity)
        assertTrue(result.shouldPersist)
    }

    @Test
    fun parsesHighRiskMessageForPersistence() {
        val result = GeminiClient.parseAnalysis(
            """{"categoria":"acoso sexual","severidad":"alto"}"""
        )

        assertEquals(Category.ACOSO_SEXUAL, result.category)
        assertEquals(Severity.ALTO, result.severity)
        assertTrue(result.shouldPersist)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCategoryOutsideAllowedValues() {
        GeminiClient.parseAnalysis(
            """{"categoria":"contenido_sexual","severidad":"alto"}"""
        )
    }
}