package com.amcentral365.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

internal class ConfigurationTest {

    @Test
    fun testClusterFQDN() {
        val testFQDN = "127.0.0.250"
        val config = Configuration(arrayOf("--cluster-fqdn", testFQDN))
        assertEquals(Pair(testFQDN, 24941.toShort()), config.clusterFQDN)
    }

    @Test
    fun testClusterNodeNames() {
        val config = Configuration(arrayOf(
                "-n", "127.0.0.100",
                "-n", "127.0.0.101",
                "--nodes", "127.0.0.102:50001,127.0.0.103,127.0.0.104",
                "--node", "127.0.0.105",
                "--bind-port", "3232"
        ))

        assertEquals(6, config.clusterNodeNames.size)
        assertEquals(Pair("127.0.0.100",  3232.toShort()), config.clusterNodeNames[0])
        assertEquals(Pair("127.0.0.101",  3232.toShort()), config.clusterNodeNames[1])
        assertEquals(Pair("127.0.0.102", 50001.toShort()), config.clusterNodeNames[2])
        assertEquals(Pair("127.0.0.103",  3232.toShort()), config.clusterNodeNames[3])
        assertEquals(Pair("127.0.0.104",  3232.toShort()), config.clusterNodeNames[4])
        assertEquals(Pair("127.0.0.105",  3232.toShort()), config.clusterNodeNames[5])
    }

}