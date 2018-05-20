package com.amcentral365.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

internal class MainKtTest {

    @org.junit.Test
    fun parseParams() {
        assertFalse(parseParams(emptyArray()))
        assertTrue(parseParams(arrayOf("x")))
    }
}
