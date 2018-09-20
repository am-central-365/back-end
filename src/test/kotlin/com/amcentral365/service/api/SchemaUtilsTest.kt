package com.amcentral365.service.api

import com.amcentral365.service.StatusException

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.assertThrows

import org.junit.jupiter.api.Assertions.assertFalse


internal class SchemaUtilsTest {

    var schemaUtils = SchemaUtils()

    @Test fun `schema - simple, good`() {
        val nodes = this.schemaUtils.validateAndCompile("r1",
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
        assertTrue(nodes.containsValue(ast("$.a", SchemaUtils.ElementType.BOOLEAN, mul=true)))
        assertTrue(nodes.containsValue(ast("$.b", SchemaUtils.ElementType.MAP)))
        assertTrue(nodes.containsValue(ast("$.c", SchemaUtils.ElementType.NUMBER, rqd=true)))
        assertTrue(nodes.containsValue(ast("$.d", SchemaUtils.ElementType.STRING)))
        assertTrue(nodes.containsValue(ast("$.e[]", SchemaUtils.ElementType.ENUM, ev=arrayOf("uno", "dos", "tres"))))
        assertTrue(nodes.containsValue(ast("$.g[]", SchemaUtils.ElementType.ENUM, rqd=true, mul=true, idx=true, ev= arrayOf("sunday", "monday"))))
        assertTrue(nodes.containsValue(ast("$.f.f1", SchemaUtils.ElementType.STRING, idx=true)))
        assertTrue(nodes.containsValue(ast("$.f.f2[]", SchemaUtils.ElementType.ENUM, ev=arrayOf("yes", "no"))))
        assertTrue(nodes.containsValue(ast("$.f.f3", SchemaUtils.ElementType.BOOLEAN)))
    }

    @Test fun `schema - bad -json`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": }""") }
        assertTrue(x.message!!.contains("MalformedJsonException"))
    }

    @Test fun `schema - no nulls`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": null}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.startsWith("$.a is null"))
    }

    @Test fun `schema - no empty objects`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": {}}""") }
        assertEquals(406, x.code)
        assertEquals("$.a: no members defined", x.message)
    }

    @Test fun `schema - bad type`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": "xyz"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("defines an invalid type 'xyz'"))
    }

    @Test fun `schema - only string type`() {
        var x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": 52}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("should be string"))

        x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": false}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("should be string"))
    }

    @Test fun `schema - ref - empty`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": "@"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("empty reference '@'"))
    }


    @Test fun `schema - ref - same role`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": "@r1"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("references the same role r1"))
    }

    @Test fun `schema - ref - unknown role`() {
        val su = object : SchemaUtils() {
            override fun loadSchemaFromDb(roleName: String): String? = null
        }

        val x = assertThrows<StatusException> { su.validateAndCompile("r1", """{"a": "@r2"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("references unknown role 'r2'"))
    }


    @Test fun `schema - ref - recursive call`() {
        val su = object : SchemaUtils() {
            override fun loadSchemaFromDb(roleName: String): String? = """{ "b": "boolean+" }"""
        }

        val nodes = su.validateAndCompile("r1", """{"a": "@r2!"}""")
        assertEquals(2, nodes.size)
        val nodea = nodes.get("$.a")
        assertNotNull(nodea)
        assertEquals("$.a", nodea!!.attrName)
        assertEquals(SchemaUtils.ElementType.OBJECT, nodea.type.typeCode)
        assertTrue(nodea.type.required)
        assertFalse(nodea.type.multiple)
        assertFalse(nodea.type.indexed)

        val node = nodes.get("$.a.b")
        assertEquals("$.a.b", node!!.attrName)
        assertEquals(SchemaUtils.ElementType.BOOLEAN, node.type.typeCode)
        assertTrue(node.type.multiple)
        assertFalse(node.type.required)
        assertFalse(node.type.indexed)
    }

    @Test fun `schema - ref - circular role`() {
        val su = object : SchemaUtils() {
            val roles = mapOf(
                "r2" to """{ "b": "@r3" }""",
                "r3" to """{ "c": "@r2" }"""
            )

            override fun loadSchemaFromDb(roleName: String): String? = roles[roleName]
        }

        val x = assertThrows<StatusException> { su.validateAndCompile("r1", """{"a": "@r2"}""") }
        assertEquals(406, x.code)
        assertEquals("$.a.b.c: cycle in role references. Role r2 was referenced by $.a", x.message)
    }

    @Test fun `schema - array - empty`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": [] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("no enum values defined"))
    }

    @Test fun `schema - array - not string`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": [true, 2] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("enum values must be strings"))
    }

    @Test fun `schema - array - dup value`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": ["x", "x"] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("'x' is already defined"))
    }

    @Test fun `schema - array - blank value`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": [" "] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("is blank"))
    }

    @Test fun `schema - array - no values`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": ["+"] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("no enum values defined"))
    }



    val roleSchemaCompute = """{
            "hostname": "string!",
            "ram_mb":   "number!",
            "audio":    "boolean",
            "video":    [ "!", "trident", "radeon", "matrox", "nvida" ],
            "hdds":     "@disk_drive!+"
        }""".trimIndent()

    val roleSchemaDiskDrive = """{
            "size_mb":     "number!",
            "mount_point": "string"
        }""".trimIndent()

    @Test fun `asset - validate - null`() {
        val su = object : SchemaUtils() {
            val roles = mapOf(
                "compute"    to roleSchemaCompute,
                "disk_drive" to roleSchemaDiskDrive
            )

            override fun loadSchemaFromDb(roleName: String): String? = roles[roleName]
        }

        su.validateAndCompile("disk_drive", this.roleSchemaDiskDrive)
        su.validateAndCompile("compute", this.roleSchemaCompute)

        val x = assertThrows<StatusException> {  su.validateAssetValue("compute", """{ "name": null }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("'hostname' is required, null values are not allowed"))
    }
}
