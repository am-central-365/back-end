package com.alderprogs.amcentral

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

internal class MainKtTest {

    @org.junit.Test
    fun parseParams() {
        assertFalse(parseParams(emptyArray()))
        assertTrue(parseParams(arrayOf("x")))
    }
}
