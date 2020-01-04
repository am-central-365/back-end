package com.amcentral365.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

internal class MainKtTest {

    @Test
    fun `irrelevant test`() {
        print("++ running IT test 'irrelevant test'")
        val auth = Authorization()
        val amcUser = AuthenticatedUser("amcentral365", "amcentral365@somemail.com", "amcentral-365 Admin")
        Assertions.assertTrue(auth.isAdmin(amcUser))
    }

    @Test
    fun topology() {
        val topo = Topology.fromDNS()
        Assumptions.assumeTrue(topo.isValid())
        print("++ topology: $topo")
    }
}
