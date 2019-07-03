package com.amcentral365.service.mergedata

import com.amcentral365.service.Configuration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

internal class MergeRolesTest {

    private val ROLES_DIR = "test_roles"

    private fun makeFile(relName: String) = File("$MERGE_DATA_ROOT_DIR/$ROLES_DIR/$relName")

    @Test
    public fun findRoleFileTest() {
        val aL0 = makeFile("a.json")
        val bL0 = makeFile("b.json")
        val aL1 = makeFile("L1/a.yml")
        val bL1 = makeFile("L1/b.yml")
        val aL2 = makeFile("L1/L2/a.xml")
        val bL2 = makeFile("L1/L2/b.xml")

        val files = listOf(aL2, bL0, aL1, bL1, aL0, bL2)

        com.amcentral365.service.config = Configuration(arrayOf())
        val merger = MergeAssets(ROLES_DIR)

        Assertions.assertNull(merger.findRoleFile("x", files))
        Assertions.assertNull(merger.findRoleFile("x.y", files))
        Assertions.assertNull(merger.findRoleFile("x.y.z", files))

        Assertions.assertEquals(aL0, merger.findRoleFile("a", files))
        Assertions.assertEquals(aL1, merger.findRoleFile("L1.a", files))
        Assertions.assertEquals(aL2, merger.findRoleFile("L1.L2.a", files))

        Assertions.assertEquals(bL0, merger.findRoleFile("b", files))
        Assertions.assertEquals(bL1, merger.findRoleFile("L1.b", files))
        Assertions.assertEquals(bL2, merger.findRoleFile("L1.L2.b", files))
    }
}
