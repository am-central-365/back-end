package com.amcentral365.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.net.MalformedURLException

internal class TransferManagerSenderOfGitHubTest {
    val api = "api/v3"
    val host = "host-name"
    val repo = "repo-name"
    val branch = "branch-name"
    val path = "p1/p2/fname"
    val query = "k1=v1&k2=v2&k3=v3"
    val fragment = "fragment-piece"

    val baseInput  = "http://$host/$repo/blob/$branch/$path"
    val baseResult = "http://$host/$api/repos/$repo/contents/$path?ref=$branch"

    @Test fun `simple path` () {
        val v = TransferManagerSenderOfGitHub(baseInput)
        Assertions.assertEquals(baseResult, v.url.toExternalForm())
    }

    @Test fun `alread converted` () {
        val converted = "http://some-host/$api/~shoud-be-unchanged~"
        val v = TransferManagerSenderOfGitHub(converted)
        Assertions.assertEquals(converted, v.url.toExternalForm())
    }

    @Test fun `with query` () {
        val v = TransferManagerSenderOfGitHub("$baseInput?$query")
        Assertions.assertEquals("$baseResult&$query", v.url.toExternalForm())
    }

    @Test fun `with fragment` () {
        val v = TransferManagerSenderOfGitHub("$baseInput#$fragment")
        Assertions.assertEquals("$baseResult#$fragment", v.url.toExternalForm())
    }

    @Test fun `with query and fragment` () {
        val v = TransferManagerSenderOfGitHub("$baseInput?$query#$fragment")
        Assertions.assertEquals("$baseResult&$query#$fragment", v.url.toExternalForm())
    }

    @Test fun `no protocol` () {
        assertThrows<MalformedURLException> { TransferManagerSenderOfGitHub(baseInput.replace("http://", "junk://")) }
    }

    @Test fun `no authority` () {
        val e = assertThrows<StatusException> { TransferManagerSenderOfGitHub(baseInput.replace(host, "")) }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue(e.message!!.contains("url has no host[:port] part")) { "${e.message}" }
    }

    @Test fun `no file` () {
        val e = assertThrows<StatusException> { TransferManagerSenderOfGitHub("http://$host") }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue(e.message!!.contains("url has no path")) { "${e.message}" }
    }

    @Test fun `no parse` () {
        val e = assertThrows<StatusException> { TransferManagerSenderOfGitHub(baseInput.replace("blob", "bulb")) }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue(e.message!!.contains("url couldn't be parsed")) { "${e.message}" }
    }
}
