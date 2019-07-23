package com.amcentral365.service.api

import com.amcentral365.service.Configuration
import com.amcentral365.service.StatusException
import com.amcentral365.service.config
import org.junit.jupiter.api.Assertions.assertDoesNotThrow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.assertThrows

import org.junit.jupiter.api.Assertions.assertFalse


internal class SchemaUtilsTest {

    val schemaUtils: SchemaUtils

    init {
        config = Configuration(emptyArray())
        schemaUtils = SchemaUtils()
    }

    @Test fun `schema - simple, valid`() {
        val nodes = this.schemaUtils.validateAndCompile("r1",
                """{
                    "a": "boolean+",
                    "b": "map",
                    "c": "number!",
                    "d": "string",
                    "e": ["uno", "dos", "tres"],
                    "g": ["!*^", "sunday", "monday"],
                    "f": {
                      "f1": "string^",
                      "f2": ["yes", "no"],
                      "f3": "boolean"
                     },
                    "h": "map+"
                    }""".trimMargin()
        )

        assertNotNull(nodes)
        assertEquals(15, nodes.size)

        fun ast(name: String, etp: SchemaUtils.ElementType, rqd: Boolean=false, zeroplus: Boolean=false,
            oneplus: Boolean=false, idx: Boolean=false, ev: Array<String>? = null): SchemaUtils.ASTNode
        =
            SchemaUtils.ASTNode(name, SchemaUtils.TypeDef(etp, rqd, zeroplus, oneplus, idx), ev)

        //println(nodes.find { it.attrName == "$.e[]" })
        assertTrue(nodes.containsValue(ast("$",      SchemaUtils.ElementType.OBJECT)))
        assertTrue(nodes.containsValue(ast("$.a",    SchemaUtils.ElementType.BOOLEAN, oneplus=true)))
        assertTrue(nodes.containsValue(ast("$.a[]",  SchemaUtils.ElementType.BOOLEAN)))
        assertTrue(nodes.containsValue(ast("$.b",    SchemaUtils.ElementType.MAP, zeroplus = true)))
        assertTrue(nodes.containsValue(ast("$.b[]",  SchemaUtils.ElementType.STRING)))
        assertTrue(nodes.containsValue(ast("$.c",    SchemaUtils.ElementType.NUMBER, rqd=true)))
        assertTrue(nodes.containsValue(ast("$.d",    SchemaUtils.ElementType.STRING)))
        assertTrue(nodes.containsValue(ast("$.e",    SchemaUtils.ElementType.ENUM, ev=arrayOf("uno", "dos", "tres"))))
        assertTrue(nodes.containsValue(ast("$.g",    SchemaUtils.ElementType.ENUM, rqd=true, zeroplus=true, idx=true, ev= arrayOf("sunday", "monday"))))
        assertTrue(nodes.containsValue(ast("$.f",    SchemaUtils.ElementType.OBJECT)))
        assertTrue(nodes.containsValue(ast("$.f.f1", SchemaUtils.ElementType.STRING, idx=true)))
        assertTrue(nodes.containsValue(ast("$.f.f2", SchemaUtils.ElementType.ENUM, ev=arrayOf("yes", "no"))))
        assertTrue(nodes.containsValue(ast("$.f.f3", SchemaUtils.ElementType.BOOLEAN)))
        assertTrue(nodes.containsValue(ast("$.h",    SchemaUtils.ElementType.MAP, oneplus = true)))
        assertTrue(nodes.containsValue(ast("$.h[]",  SchemaUtils.ElementType.STRING)))
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
        val su = SchemaUtils { null }

        val x = assertThrows<StatusException> { su.validateAndCompile("r1", """{"a": "@r2"}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("references unknown role 'r2'"))
    }


    @Test fun `schema - ref - recursive call`() {
        val su = SchemaUtils { """{ "b": "boolean+" }""" }

        val nodes = su.validateAndCompile("r1", """{"a": "@r2!"}""")
        assertEquals(4, nodes.size)
        val node0 = nodes.get("$")
        assertNotNull(node0)
        assertEquals(SchemaUtils.ElementType.OBJECT, node0!!.type.typeCode)

        val nodea = nodes.get("$.a")
        assertNotNull(nodea)
        assertEquals("$.a", nodea!!.attrName)
        assertEquals(SchemaUtils.TypeDef(SchemaUtils.ElementType.OBJECT, required = true), nodea.type)

        val nodeb = nodes.get("$.a.b")
        assertNotNull(nodeb)
        assertEquals("$.a.b", nodeb!!.attrName)
        assertEquals(SchemaUtils.TypeDef(SchemaUtils.ElementType.BOOLEAN, oneplus = true), nodeb.type)

        val nodebp = nodes.get("$.a.b[]")
        assertNotNull(nodebp)
        assertEquals("$.a.b[]", nodebp!!.attrName)
        assertEquals(SchemaUtils.TypeDef(SchemaUtils.ElementType.BOOLEAN), nodebp.type)
    }

    @Test fun `schema - ref - circular role`() {
        val roles = mapOf(
            "r2" to """{ "b": "@r3" }""",
            "r3" to """{ "c": "@r2" }"""
        )
        val su = SchemaUtils { roleName -> roles[roleName] }

        val x = assertThrows<StatusException> { su.validateAndCompile("r1", """{"a": "@r2"}""") }
        assertEquals(406, x.code)
        assertEquals("$.a.b.c: cycle in role references. Role r2 was referenced by $.a", x.message)
    }

    private fun chk(nodes: CompiledSchema, nodeName: String, nodeType: SchemaUtils.TypeDef) {
        val node = nodes.get(nodeName)
        assertNotNull(node)
        assertEquals(nodeName, node!!.attrName)
        assertEquals(nodeType, node.type)
    }

    @Test fun `schema - anonymous ref`() {
        val nodes = this.schemaUtils.validateAndCompile("r1",
                """{
                     "a": "boolean+",
                     "b": {
                       "_attr": "!+",
                       "c": "number!",
                       "d": "string",
                       "e": {
                         "f": "number^"
                       }
                     }
                   }""".trimMargin()
        )

        assertEquals(9, nodes.size)

        chk(nodes, "$",         SchemaUtils.TypeDef(SchemaUtils.ElementType.OBJECT))
        chk(nodes, "$.a",       SchemaUtils.TypeDef(SchemaUtils.ElementType.BOOLEAN, oneplus = true))
        chk(nodes, "$.b",       SchemaUtils.TypeDef(SchemaUtils.ElementType.OBJECT, required = true, oneplus = true))
        chk(nodes, "$.b[]",     SchemaUtils.TypeDef(SchemaUtils.ElementType.OBJECT))
        chk(nodes, "$.b[].e",   SchemaUtils.TypeDef(SchemaUtils.ElementType.OBJECT))
        chk(nodes, "$.b[].e.f", SchemaUtils.TypeDef(SchemaUtils.ElementType.NUMBER, indexed = true))
    }


    @Test fun `schema - composite type - basic`() {
        val nodes = this.schemaUtils.validateAndCompile("r1", """{"a": {"type": "string"}}""")
        assertEquals(2, nodes.size)

        chk(nodes, "$",   SchemaUtils.TypeDef(SchemaUtils.ElementType.OBJECT))
        chk(nodes, "$.a", SchemaUtils.TypeDef(SchemaUtils.ElementType.STRING))
    }

    private fun chkCompositeDefault(schema: String, elmTypedef: SchemaUtils.TypeDef, defaultVal: Any?) {
        val nodes = this.schemaUtils.validateAndCompile("r1", schema)
        assertEquals(2, nodes.size)
        chk(nodes, "$", SchemaUtils.TypeDef(SchemaUtils.ElementType.OBJECT))
        chk(nodes, "$.a", SchemaUtils.TypeDef.from(elmTypedef, defaultVal = defaultVal))
    }

    private fun chkCompositeDefault(schema: String, elmType: SchemaUtils.ElementType, defaultVal: Any?) {
        chkCompositeDefault(schema, SchemaUtils.TypeDef(elmType), defaultVal)
    }

    private fun chkCompositeDefault(elmTypeJson: String, defaultStr: String, elmType: SchemaUtils.ElementType, defaultVal: Any?) =
        chkCompositeDefault("""{"a": { "type": "$elmTypeJson", "default": $defaultStr}}""", SchemaUtils.TypeDef(elmType), defaultVal)

    private fun chkCompositeDefault(elmTypeJson: String, defaultStr: String, elmTypedef: SchemaUtils.TypeDef, defaultVal: Any?) =
        chkCompositeDefault("""{"a": { "type": "$elmTypeJson", "default": $defaultStr}}""", elmTypedef, defaultVal)

    private fun chkCompositeException(elmTypeJson: String, defaultStr: String, elmTypedef: SchemaUtils.TypeDef, defaultVal: Any?) {
        val x = assertThrows<StatusException> { chkCompositeDefault(elmTypeJson, defaultStr, elmTypedef, defaultVal) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("\$.a.default: "))
        assertTrue(x.message!!.contains("default value"))
        assertTrue(x.message!!.toLowerCase().contains(defaultStr.toLowerCase()), "can't find substring '$defaultStr' in ${x.message}")
        assertTrue(x.message!!.contains("should match the element type"))
      //assertTrue(x.message!!.toLowerCase().contains(elmTypeJson.toLowerCase()), "can't find substring '$elmTypeJson' in ${x.message}")
    }

    private fun chkCompositeException(elmTypeJson: String, defaultStr: String, elmType: SchemaUtils.ElementType, defaultVal: Any?) =
            chkCompositeException(elmTypeJson, defaultStr, SchemaUtils.TypeDef(elmType), defaultVal)


    @Test fun `schema - composite type - default - string`() {
        chkCompositeDefault("string", "\"xx\"", SchemaUtils.ElementType.STRING, "xx")

        chkCompositeException("string", "true",   SchemaUtils.ElementType.STRING, true)
        chkCompositeException("string", "1234",   SchemaUtils.ElementType.STRING, 1234)
        chkCompositeException("string", "4.32",   SchemaUtils.ElementType.STRING, 4.32)
    }

    @Test fun `schema - composite type - default - number`() {
        chkCompositeDefault("number", "28647", SchemaUtils.ElementType.NUMBER, 28647)
        chkCompositeDefault("number", "51.98", SchemaUtils.ElementType.NUMBER, 51.98)
        chkCompositeDefault("number", "0.987", SchemaUtils.ElementType.NUMBER, 0.987)
        chkCompositeDefault("number", "0.9876", SchemaUtils.ElementType.NUMBER, .9876)

        chkCompositeException("number", "true",  SchemaUtils.ElementType.NUMBER, null)
        chkCompositeException("number", "xyzzy", SchemaUtils.ElementType.NUMBER, null)
    }

    @Test fun `schema - composite type - default - boolean`() {
        chkCompositeDefault("boolean",  "true", SchemaUtils.ElementType.BOOLEAN,  true)
        chkCompositeDefault("boolean", "false", SchemaUtils.ElementType.BOOLEAN, false)

        chkCompositeException("boolean", "12.34",  SchemaUtils.ElementType.BOOLEAN, null)
        chkCompositeException("boolean", "xyzzy",  SchemaUtils.ElementType.BOOLEAN, null)
    }

    @Test fun `schema - composite type - default - enum`() {
        chkCompositeDefault("""{"a": { "type": ["X", "Y", "Z"], "default": "Y"}}""", SchemaUtils.ElementType.ENUM, "Y")
        chkCompositeDefault("""{"a": { "type": ["1", "3", "5"], "default": "5"}}""", SchemaUtils.ElementType.ENUM, "5")
        chkCompositeDefault("""{"a": { "type": ["+", "a", "b", "c"], "default": ["c", "a"]}}""", SchemaUtils.TypeDef(SchemaUtils.ElementType.ENUM, oneplus = true), listOf("c", "a"))

        // should check for incorrect enum values
        var x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": { "type": ["X", "Y", "Z"], "default": "NOT-A-MEMBER"}}""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("value 'NOT-A-MEMBER' does not belong to the ENUM"), x.message)

        // should check for incorrect enum values in arrays too
        val arrJsonStr = """{"a": { "type": ["+", "a", "b", "c"], "default": ["c", "X"]}}"""
        x = assertThrows { chkCompositeDefault(arrJsonStr, SchemaUtils.TypeDef(SchemaUtils.ElementType.ENUM, oneplus = true), listOf<Any>()) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("value 'X' isn't valid for the ENUM"), x.message)
    }

    @Test fun `schema - composite type - default - null`() {
        val schemaStr = """{"a": { "type": "number!", "default": null}}"""
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", schemaStr) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("the type does not allow nulls"), x.message)
    }

    @Test fun `schema - composite type - default - object`() {
        // object types can't have default
        var schemaStr = """{"a": { "type": { "a": "number" }, "default": null}}"""
        var x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", schemaStr) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("default values are not supported for OBJECT types"), x.message)

        // and the default can't be an ebject
        schemaStr = """{"a": { "type": "number", "default": { "n": "number" }}}"""
        x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", schemaStr) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("default values of OBJECT types are not supported"), x.message)
    }

    @Test fun `schema - composite type - default - arrays`() {
        chkCompositeDefault("boolean+", "[true, true, false, true]", SchemaUtils.TypeDef(SchemaUtils.ElementType.BOOLEAN, oneplus = true), listOf(true, true, false, true))
        chkCompositeDefault("number+",  "[1, 3, -4, 7]",             SchemaUtils.TypeDef(SchemaUtils.ElementType.NUMBER,  oneplus = true), listOf(1,3,-4,7))

        // null elements are genrally allowed
        chkCompositeDefault("number+",  "[1, null, -4, 7]", SchemaUtils.TypeDef(SchemaUtils.ElementType.NUMBER,  oneplus = true), listOf(1,null,-4,7))

        // .. except for mandatory types
        var x = assertThrows<StatusException> { chkCompositeDefault("number!+", "[1, null, 2]", SchemaUtils.TypeDef(SchemaUtils.ElementType.NUMBER, oneplus = true), listOf<Any?>(1, null, 2)) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("the type does not allow null array elements"), x.message)

        // empty arrays for zero-or-more types are ok
        chkCompositeDefault("number*",  "[]", SchemaUtils.TypeDef(SchemaUtils.ElementType.NUMBER,  zeroplus = true), listOf<Any>())

        // .. but not allowed for one-or-more arrays
        x = assertThrows<StatusException> { chkCompositeDefault("number+", "[]", SchemaUtils.TypeDef(SchemaUtils.ElementType.NUMBER, oneplus = true), listOf<Any>()) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("the type prohibits empty arrays"), x.message)

        // .. also applies to enums
        var arrJsonStr = """{"a": { "type": ["+", "X", "Y", "Z"], "default": [] }}"""
        x = assertThrows { chkCompositeDefault(arrJsonStr, SchemaUtils.TypeDef(SchemaUtils.ElementType.ENUM, oneplus = true), listOf<Any>()) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("the type prohibits empty arrays"), x.message)

        // when the type is an array, the default also must be an array
        x = assertThrows<StatusException> { chkCompositeDefault("number+", "44", SchemaUtils.TypeDef(SchemaUtils.ElementType.NUMBER, oneplus = true), listOf<Any?>(44)) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("is an array. Its default value must also be an array"), x.message)

        // only primitives are allowed as array elements
        arrJsonStr = """{"a": { "type": "string+", "default": ["x", [], "y"] }}"""
        x = assertThrows { chkCompositeDefault(arrJsonStr, SchemaUtils.TypeDef(SchemaUtils.ElementType.ENUM, oneplus = true), listOf<Any>()) }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("only primitive types are allowed as array elements"), x.message)
    }

    @Test fun `schema - array - empty`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": [] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("no enum values defined"), x.message)
    }

    @Test fun `schema - array - not string`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": [true, 2] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("enum values must be strings"), x.message)
    }

    @Test fun `schema - array - dup value`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": ["x", "x"] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("'x' is already defined"), x.message)
    }

    @Test fun `schema - array - blank value`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": [" "] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("is blank"), x.message)
    }

    @Test fun `schema - array - no values`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": ["+"] }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("no enum values defined"), x.message)
    }

    @Test fun `schema - map - indexed`() {
        val x = assertThrows<StatusException> { this.schemaUtils.validateAndCompile("r1", """{"a": "map+^" }""") }
        assertEquals(406, x.code)
        assertTrue(x.message!!.contains("map elements can't be indexed"), x.message)
    }


    @Test fun isAttributeWithPrefixTest() {
        assertTrue (schemaUtils.isAttributeWithPrefix("$.a",     "$"))
        assertTrue (schemaUtils.isAttributeWithPrefix("$.a.b",   "$.a"))
        assertTrue (schemaUtils.isAttributeWithPrefix("$.a.b.c", "$.a.b"))

        assertFalse(schemaUtils.isAttributeWithPrefix("$",       "$"))
        assertFalse(schemaUtils.isAttributeWithPrefix("$.a.b",   "$"))
        assertFalse(schemaUtils.isAttributeWithPrefix("$.a.b.c", "$"))
        assertFalse(schemaUtils.isAttributeWithPrefix("$.a",     "$.a"))
        assertFalse(schemaUtils.isAttributeWithPrefix("$.a.b.c", "$.a"))
    }


    private val roleSchemaCompute = """{
            "hostname": "string!",
            "ram_mb":   "number!",
            "audio":    "boolean",
            "video":    [ "!", "trident", "radeon", "matrox", "nvida" ],
            "hdds":     "@disk_drive!+",
            "watchers": "@watchers*",
            "array0*":  "number*",
            "array1+":  "number+",
            "nested":   "@nested1",
            "map0*":    "map",
            "map1+":    "map+"
        }""".trimIndent()

    private val roleSchemaDiskDrive = """{ "size_mb": "number!", "mount_point": "string" }"""
    private val roleSchemaWatchers  = """{ "name":    "string!" }"""
    private val roleSchemaNested1   = """{ "name1":   "string!", "n2":   "@nested2+" }"""
    private val roleSchemaNested2   = """{ "name2":   "string!", "n3":   "@nested3!" }"""
    private val roleSchemaNested3   = """{ "name3":   "string!", "val3":  "number!+" }"""

    private val roles = mapOf(
        "compute"    to roleSchemaCompute,
        "disk_drive" to roleSchemaDiskDrive,
        "watchers"   to roleSchemaWatchers,
        "nested1"    to roleSchemaNested1,
        "nested2"    to roleSchemaNested2,
        "nested3"    to roleSchemaNested3
    )

    private val schemaUtils2 = object : SchemaUtils(loadSchema = { roleName -> roles[roleName] }) {
        init {
            this.validateAndCompile("nested3", roleSchemaNested3)
            this.validateAndCompile("nested2", roleSchemaNested2)
            this.validateAndCompile("nested1", roleSchemaNested1)
            this.validateAndCompile("watchers", roleSchemaDiskDrive)
            this.validateAndCompile("disk_drive", roleSchemaDiskDrive)
            this.validateAndCompile("compute", roleSchemaCompute)
        }
    }

    @Test fun `asset - validate - throws`() {
        fun check(assetJsonStr: String, exceptionMsgFragment: String) {
            val x = assertThrows<StatusException> { this.schemaUtils2.validateAssetValue("compute", assetJsonStr) }
            assertEquals(406, x.code)
            assertTrue(x.message!!.contains(exceptionMsgFragment)) { "got: ${x.message}" }
        }

        val nonRequiredElementPassedMsg = "missing 4 required attributes"

        check("""{ "hdds": [ {} ] }""",        nonRequiredElementPassedMsg)

        check("""{ "bogus": null }""",         "attribute '$.bogus' is not defined for role compute")

        check("""{ "hostname": null }""",      "hostname' is required, null values are not allowed")
        check("""{ "hostname": "x" }""",       "missing 3 required attributes")
        check("""{ "hostname": true }""",      "hostname' isn't STRING")
        check("""{ "hostname": 54.32 }""",     "hostname' isn't STRING")
        check("""{ "hostname": [] }""",        "hostname' shan't be an array")

        check("""{ "ram_mb": "very many" }""", "ram_mb' isn't NUMBER")
        check("""{ "audio": "true" }""",       "audio' isn't BOOLEAN")

        check("""{ "video": false }""",        "video' isn't ENUM")
        check("""{ "video": "sight" }""",      "isn't valid for the enum")
        check("""{ "video": [] }""",           "video' shan't be an array")

        check("""{ "hdds": 23 }""",            "hdds' must be an array")
        check("""{ "hdds": {} }""",            "hdds' must be an array")
        check("""{ "hdds": {"x": 1} }""",      "hdds' must be an array")
        check("""{ "hdds": [] }""",            "hdds': at least one array element is required")

        // TODO: This should raise "empty objects are not allowed for array elements" message.
        //       But currently there is no way to enforce that the objects must have attributes.
        check("""{ "hdds": [ {} ] }""",        nonRequiredElementPassedMsg)

        check("""{ "hdds": [ {"size_mb": true} ] }""",  "isn't NUMBER")
        check("""{ "hdds": [ {"size_mb": "44"} ] }""",  "isn't NUMBER")
        check("""{ "hdds": [ {"size_mb":  44 } ] }""",  "missing 3 required attributes")

        check("""{ "watchers": null }""",      nonRequiredElementPassedMsg)
        check("""{ "watchers": [] }""",        nonRequiredElementPassedMsg)
        check("""{ "watchers": [ {} ] }""",    "missing 5 required attributes")
        check("""{ "watchers": [ { "name": null } ] }""",    "name' is required, null values are not allowed")
        check("""{ "watchers": [ { "name": "xy" } ] }""",    nonRequiredElementPassedMsg)

        check("""{ "array0*": null }""",       nonRequiredElementPassedMsg)
        check("""{ "array0*": [] }""",         nonRequiredElementPassedMsg)
        check("""{ "array0*": [3] }""",        nonRequiredElementPassedMsg)
        check("""{ "array0*": [3,5] }""",      nonRequiredElementPassedMsg)
        check("""{ "array0*": [3,5,"x"] }""",  "isn't NUMBER")

        check("""{ "array1+": null }""",       nonRequiredElementPassedMsg)
        check("""{ "array1+": [] }""",         "array1+': at least one array element is required")
        check("""{ "array1+": [3] }""",        nonRequiredElementPassedMsg)
        check("""{ "array1+": [3,5] }""",      nonRequiredElementPassedMsg)
        check("""{ "array1+": [3,5,"x"] }""",  "isn't NUMBER")

        check("""{ "map0*": null }""",         nonRequiredElementPassedMsg)
        check("""{ "map0*": {} }""",           nonRequiredElementPassedMsg)
        check("""{ "map0*": {"a": true} }""",  "isn't STRING")
        check("""{ "map0*": {"a": []} }""",    "isn't STRING")
        check("""{ "map0*": {"a": {}} }""",    "isn't STRING")
        check("""{ "map0*": {"a": 51} }""",    "isn't STRING")
        check("""{ "map0*": {"a": "q"} }""",   nonRequiredElementPassedMsg)

        check("""{ "map1+": null }""",         nonRequiredElementPassedMsg)
        check("""{ "map1+": {} }""",           "empty maps are not allowed")
        check("""{ "map1+": {"a": true} }""",  "isn't STRING")
        check("""{ "map1+": {"a": []} }""",    "isn't STRING")
        check("""{ "map1+": {"a": {}} }""",    "isn't STRING")
        check("""{ "map1+": {"a": 51} }""",    "isn't STRING")
        check("""{ "map1+": {"a": "q"} }""",   nonRequiredElementPassedMsg)
    }

    @Test fun `asset - validate - simple`() {
        val assetJsonStr = """{
            "hostname": "compute-1",
            "ram_mb":    1024,
            "audio":     true,
            "video":     "nvida",
            "hdds":     [
                {"size_mb":  2048, "mount_point": "/"     },
                {"size_mb": 10480, "mount_point": "/data" }
            ],
            "watchers": [ { "name": "alice"}, { "name": "bob"} ],
            "array1+":  [71, 34, 2],
            "map1+":    { "one": "uno", "two": "dos", "three": "tres" }

        }""".trimIndent()

        assertDoesNotThrow { this.schemaUtils2.validateAssetValue("compute", assetJsonStr) }
    }

    @Test fun `asset - validate - nested`() {
        val assetJsonStr = """{
            "hostname": "compute-2",
            "ram_mb":    512,
            "video":    "matrox",
            "hdds":     [ {"size_mb": 1.2 } ],
            "nested":   {
               "name1":  "x1",
               "n2": [
                   { "name2": "alpha", "n3": { "name3": "n3a", "val3": [11, 12] } },
                   { "name2": "beta",  "n3": { "name3": "n3b", "val3": [21, 22] } }
                ]
            }

        }""".trimIndent()

        this.schemaUtils2.validateAssetValue("compute", assetJsonStr)
    }


    @Test fun `asset - validate - anonymous schema`() {
        val su = object : SchemaUtils() {
            init {
                this.validateAndCompile("r1", """{
                    "a": "boolean+",
                    "b": {
                       "_attr": "!+",
                       "c": "string",
                       "d": {
                         "e": "number"
                       },
                       "f": "number"
                    },
                    "g": "string!"
                   }""".trimMargin()
                )
            }
        }

        su.validateAssetValue("r1",
            """{
                 "a": [true, false, false],
                 "b": [
                    { "c": "def 1" },
                    { "c": "def 3", "d": {}, "f": 14 },
                    { "c": "def 2", "d": { "e": 4}, "f": 0.8 },
                  ],
                 "g": "24 Dec == 18 Hex"
               }""".trimIndent()
        )
    }

}
