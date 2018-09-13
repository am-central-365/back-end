package com.amcentral365.service.api

import com.amcentral365.service.Configuration
import com.amcentral365.service.DatabaseStore
import com.amcentral365.service.StatusException
import com.amcentral365.service.config
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows

import io.mockk.mockk
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import org.junit.jupiter.api.BeforeAll


internal class SchemaUtilsTest {

    companion object {
        @BeforeAll @JvmStatic fun init() {
            config = Configuration(emptyArray())
        }
    }

    @Test fun `schema - no nulls`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": null}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.startsWith("$.a is null"))
    }

    @Test fun `schema - no empty objects`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": {}}""") }
        assertEquals(406, x.code)
        assertEquals("$.a: no members defined", x.message)
    }

    @Test fun `schema - string type name`() {
        var x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": 52}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("should be string"))

        x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": false}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("should be string"))
    }

    @Test fun `schema - ref - empty`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": "@"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("empty reference '@'"))
    }


    @Test fun `schema - ref - same role`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": "@r1"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("references the same role r1"))
    }

    /*@Test*/ fun `schema - ref - unknown role`() {
        val dbs = mockk<DatabaseStore>()
        every { dbs.fetchRowsAsObjects(Role("r2"), null, 1) } returns emptyList()

        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": "@r2"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("references unknown role 'r2'"))
    }


    /*@Test*/ fun `schema - ref - recursive call`() {
        val dbs = mockk<DatabaseStore>()

        val r2 = Role("r2")
        r2.roleSchema = """{ "b": "boolean" }"""
        every { dbs.fetchRowsAsObjects(r2, null, 1) } returns listOf(r2)

        val nodes = SchemaUtils.validateAndCompile("r1", """{"a": "@r2"}""")
        assertEquals(1, nodes.size)
        val node = nodes.iterator().next()
        assertEquals("$.a.b", node.attrName)
        assertEquals(SchemaUtils.ElementType.BOOLEAN, node.type)
    }

}
