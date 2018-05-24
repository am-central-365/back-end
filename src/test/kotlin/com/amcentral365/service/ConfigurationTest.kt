package com.amcentral365.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

internal class ConfigurationTest {

    @Test
    fun testClusterFQDN() {
        val testFQDN = "127.0.0.250"
        val config = Configuration(arrayOf("--cluster-fqdn", testFQDN))
        assertEquals(testFQDN, config.clusterFQDN)
    }

    @Test
    fun testClusterNodeNames() {
        val config = Configuration(arrayOf(
                "-n", "127.0.0.100",
                "-n", "127.0.0.101",
                "--nodes", "127.0.0.102,127.0.0.103,127.0.0.104",
                "--node", "127.0.0.105"
        ))

        assertEquals(6, config.clusterNodeNames.size)
        assertEquals("127.0.0.100", config.clusterNodeNames[0])
        assertEquals("127.0.0.101", config.clusterNodeNames[1])
        assertEquals("127.0.0.102", config.clusterNodeNames[2])
        assertEquals("127.0.0.103", config.clusterNodeNames[3])
        assertEquals("127.0.0.104", config.clusterNodeNames[4])
        assertEquals("127.0.0.105", config.clusterNodeNames[5])
    }

}