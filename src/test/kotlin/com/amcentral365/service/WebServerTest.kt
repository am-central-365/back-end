package com.amcentral365.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

import io.mockk.mockk
import io.mockk.every
import io.mockk.just
import io.mockk.Runs

import com.google.common.io.Resources
import spark.Request
import spark.Response

internal class WebServerTest {

    @Test
    fun `getPublicKey$production_sources_for_module_amcentral`() {
        val expectedKey = Resources.toString(Resources.getResource("ssh-key.pub"), Charsets.US_ASCII)

        val reqMock = mockk<Request>()
        val rspMock = mockk<Response>()
        every { reqMock.ip() } returns "mock-ip"
        every { rspMock.type("text/plain") } just Runs

        val actualKey = WebServer().getPublicKey(reqMock, rspMock)
        assertEquals(expectedKey, actualKey)
    }
}