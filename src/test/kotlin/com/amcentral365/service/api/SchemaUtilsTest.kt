package com.amcentral365.service.api

import com.amcentral365.service.Configuration
import com.amcentral365.service.DatabaseStore
import com.amcentral365.service.StatusException
import com.amcentral365.service.config
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore
import com.google.gson.JsonSyntaxException

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll

import io.mockk.mockk
import io.mockk.every
import io.mockk.just
import io.mockk.Runs


internal class SchemaUtilsTest {

    companion object {
        @BeforeAll @JvmStatic fun init() {
            //config = Configuration(emptyArray())
        }
    }

    @Test fun `schema - simple, good`() {
        val nodes = SchemaUtils.validateAndCompile("r1",
                """{
                    "a": boolean+,
                    "b": map,
                    "c": "number!",
                    "d": "string",
                    "e": ["uno", "dos", "tres"],
                    "g": ["!+^", "sunday", "monday"],
                    "f": {
                      "f1": "string^",
                      "f2": ["yes", "no"],
                      "f3": "boolean"
                     }
                    }""".trimMargin()
        )

        assertNotNull(nodes)
        assertEquals(9, nodes.size)

        fun ast(name: String, etp: SchemaUtils.ElementType, rqd: Boolean=false, mul: Boolean=false,
            idx: Boolean=false, ev: Array<String>? = null): SchemaUtils.ASTNode
        =
            SchemaUtils.ASTNode(name, SchemaUtils.TypeDef(etp, rqd, mul, idx), ev)

        //println(nodes.find { it.attrName == "$.e[]" })
        assertTrue(nodes.contains(ast("$.a", SchemaUtils.ElementType.BOOLEAN, mul=true)))
        assertTrue(nodes.contains(ast("$.b", SchemaUtils.ElementType.MAP)))
        assertTrue(nodes.contains(ast("$.c", SchemaUtils.ElementType.NUMBER, rqd=true)))
        assertTrue(nodes.contains(ast("$.d", SchemaUtils.ElementType.STRING)))
        assertTrue(nodes.contains(ast("$.e[]", SchemaUtils.ElementType.ENUM, ev=arrayOf("uno", "dos", "tres"))))
        assertTrue(nodes.contains(ast("$.g[]", SchemaUtils.ElementType.ENUM, rqd=true, mul=true, idx=true, ev= arrayOf("sunday", "monday"))))
        assertTrue(nodes.contains(ast("$.f.f1", SchemaUtils.ElementType.STRING, idx=true)))
        assertTrue(nodes.contains(ast("$.f.f2[]", SchemaUtils.ElementType.ENUM, ev=arrayOf("yes", "no"))))
        assertTrue(nodes.contains(ast("$.f.f3", SchemaUtils.ElementType.BOOLEAN)))
    }

    @Test fun `schema - bad -json`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": }""") }
        assertTrue(x.message!!.contains("MalformedJsonException"))
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

    @Test fun `schema - bad type`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": "xyz"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("defines an invalid type 'xyz'"))
    }

    @Test fun `schema - only string type`() {
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


    @Test fun `schema - array - empty`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": [] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("no enum values defined"))
    }

    @Test fun `schema - array - not string`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": [true, 2] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("enum values must be strings"))
    }

    @Test fun `schema - array - dup value`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": ["x", "x"] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("'x' is already defined"))
    }

    @Test fun `schema - array - blank value`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": [" "] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("is blank"))
    }

    @Test fun `schema - array - no values`() {
        val x = assertThrows<StatusException> { SchemaUtils.validateAndCompile("r1", """{"a": ["+"] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("no enum values defined"))
    }
}
