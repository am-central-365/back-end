package com.alderprogs.amcentral

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

internal class MainKtTest {

    @org.junit.jupiter.api.Test
    fun parseParams() {
        assertFalse(parseParams(emptyArray()))
        assertTrue(parseParams(arrayOf("x")))
    }
}
