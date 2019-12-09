package com.amcentral365.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.net.MalformedURLException

internal class TransferManagerSenderOfGitHubTest {
    val host = "host-name"
    val repo = "user-name/repo-name"
    val branch = "branch-name";
    val path = "p1/p2/fname"
    val query = "k1=v1&k2=v2&k3=v3"
    val fragment = "fragment-piece"

    val baseInput  = "https://$host/$repo/blob/$branch/$path"
    val baseResult = "https://api.$host/repos/$repo/contents/$path?ref=$branch"

    @Test fun `simple path` () {
        val v = SenderOfGitHub(baseInput)
        Assertions.assertEquals(baseResult, v.topUrl.toExternalForm())
    }

    @Test fun `already converted` () {
        fun check_unchanged(url: String) {
            val v = SenderOfGitHub(url)
            Assertions.assertEquals(url, v.topUrl.toExternalForm())
        }

        check_unchanged("https://api.whatever/repos/~shoud-be-ignored~")
        check_unchanged("https://raw.whatever/~shoud-be-ignored~")
    }

    @Test fun `with query` () {
        val v = SenderOfGitHub("$baseInput?$query")
        Assertions.assertEquals("$baseResult&$query", v.topUrl.toExternalForm())
    }

    @Test fun `with fragment` () {
        val v = SenderOfGitHub("$baseInput#$fragment")
        Assertions.assertEquals("$baseResult#$fragment", v.topUrl.toExternalForm())
    }

    @Test fun `with query and fragment` () {
        val v = SenderOfGitHub("$baseInput?$query#$fragment")
        Assertions.assertEquals("$baseResult&$query#$fragment", v.topUrl.toExternalForm())
    }

    @Test fun `no protocol` () {
        assertThrows<MalformedURLException> { SenderOfGitHub(baseInput.replace("https://", "junks://")) }
    }

    @Test fun `no authority` () {
        val e = assertThrows<StatusException> { SenderOfGitHub(baseInput.replace(host, "")) }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue(e.message!!.contains("url has no host[:port] part")) { "${e.message}" }
    }

    @Test fun `no file` () {
        val e = assertThrows<StatusException> { SenderOfGitHub("http://$host") }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue(e.message!!.contains("url couldn't be parsed")) { "${e.message}" }
    }

    @Test fun `no parse` () {
        val e = assertThrows<StatusException> { SenderOfGitHub(baseInput.replace("blob", "bulb")) }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue(e.message!!.contains("url couldn't be parsed")) { "${e.message}" }
    }
}
