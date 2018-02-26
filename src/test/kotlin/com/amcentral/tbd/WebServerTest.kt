package com.amcentral.tbd

import org.junit.Test
import org.junit.Assert.assertEquals
import io.mockk.mockk
import io.mockk.every

import com.google.common.io.Resources
import spark.Response

class WebServerTest {

    @Test
    fun `getPublicKey$production_sources_for_module_amcentral`() {
        val expectedKey = Resources.toString(Resources.getResource("ssh-key.pub"), Charsets.US_ASCII)
        val rspMock = mockk<Response>()
        every { rspMock.type("text/plain") } answers { nothing }

        val actualKey = WebServer().getPublicKey(rspMock)
        assertEquals(expectedKey, actualKey)
    }
}