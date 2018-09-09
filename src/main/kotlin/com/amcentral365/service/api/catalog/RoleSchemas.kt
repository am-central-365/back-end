package com.amcentral365.service.api.catalog

import com.google.gson.JsonElement
import com.google.gson.JsonParser

import com.amcentral365.service.StatusException
import com.amcentral365.service.databaseStore

import com.amcentral365.service.dao.RoleSchema
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import java.util.TreeMap


private val validSchemaDefTypes = mapOf(
      "string"  to RoleSchemas.ElementType.STRING
    , "number"  to RoleSchemas.ElementType.NUMBER
    , "boolean" to RoleSchemas.ElementType.BOOLEAN
    , "map"     to RoleSchemas.ElementType.MAP
)

private const val ROLE_DELIMITER: Char = ':'

class RoleSchemas {

    enum class ElementType { STRING, NUMBER, BOOLEAN, MAP, ENUM, OBJECT }

    data class TypeDef(
          val typeCode: ElementType
        , val required: Boolean = false
        , val multiple: Boolean = true
        , val indexed:  Boolean = false
    ) {
        companion object {

            @JvmStatic
            private fun genericFrom(strToParse: String,
                              postParse: (s: String, required: Boolean, multiple: Boolean, indexed: Boolean) -> TypeDef?
            ): TypeDef? {
                var workStr = strToParse
                var required = false
                var multiple = false
                var indexed  = false

                // NB: we only replace first occurrence not allowing double definitions
                if( workStr.contains('!') ) { required = true;   workStr = workStr.replaceFirst('!', ' ') }
                if( workStr.contains('+') ) { multiple = false;  workStr = workStr.replaceFirst('+', ' ') }
                if( workStr.contains('^') ) { indexed  = false;  workStr = workStr.replaceFirst('^', ' ') }

                return postParse(workStr.trimEnd(), required, multiple, indexed)
            }

            @JvmStatic fun fromTypeName(elmName: String, typeStr: String): TypeDef =
                TypeDef.genericFrom(typeStr) { str, required, multiple, indexed ->
                    if( str !in validSchemaDefTypes )
                        throw StatusException(406, "$elmName defines an invalid type '$str'. "+
                                "The valid types are: ${validSchemaDefTypes.keys.joinToString(", ")}")
                    TypeDef(validSchemaDefTypes[str]!!, required, multiple, indexed)
                }!!

            @JvmStatic fun fromEnumValue(elmName: String, enumVal: String): TypeDef? =
                TypeDef.genericFrom(enumVal) { str, required, multiple, indexed ->
                    if( str.isEmpty() )
                        TypeDef(ElementType.STRING, required, multiple, indexed)
                    else
                        null
                }
        }

        fun normalize(): String {
            val tps = when(this.typeCode) {
                ElementType.STRING  -> 's'
                ElementType.NUMBER  -> 'n'
                ElementType.BOOLEAN -> 'b'
                ElementType.ENUM    -> 'e'
                ElementType.MAP     -> 'm'
                ElementType.OBJECT  -> 'o'
            }

            val flags = 0
                + (if( this.required ) 1 else 0)
                + (if( this.multiple ) 2 else 0)
                + (if( this.indexed )  4 else 0)

            return "$tps$flags"  // returns values like s1 or b3
        }
    }

    data class Schema(val roleName: String, val schemaVer: Int) {
        init {
            require(schemaVer > 0)
        }

        constructor(qualifiedRoleName: String):
            this(
                qualifiedRoleName.substringBefore(ROLE_DELIMITER),
                qualifiedRoleName.substringAfter(ROLE_DELIMITER).toInt()
            )
    }

    data class ASTNode(
        val attrName:   String,
        val type:       TypeDef,
        val enumValues: Array<String>? = null,
        val children:   TreeMap<String, ASTNode>? = null,
        val walkData:   Any? = null
    )

    companion object {

        @JvmStatic
        private fun loadSchemaReference(schema: Schema): RoleSchema? {
            val lst = databaseStore.fetchRowsAsObjects(RoleSchema(schema), limit = 1)
            require(lst.size < 2)
            return if (lst.size == 1) lst[0] as RoleSchema else null
        }


        @JvmStatic   // throws StatusException 406 (Not Acceptable)
        fun validateSchema(roleName: String, jsonStr: String) {

            fun validateRoleReference(elmName: String, referencedRoleName: String) {
                if (referencedRoleName != roleName) {  // self references are allowed
                    if (RoleSchemas.loadSchemaReference(Schema(roleName, 1 /* FIXME: shall not assume 1 */)) == null)
                        throw StatusException(406, "$elmName references unknown role '$referencedRoleName'")
                }
            }

            fun validateAndStripAttributes(typeName: String): String =
                // we only replace one occurrence, preventing repetition  of suffixes
                typeName.replaceFirst('!', ' ').replaceFirst('+', ' ').replaceFirst('^', ' ').trimEnd(' ')

            fun process(name: String, elm: JsonElement) {
                when {
                    elm.isJsonNull ->
                        throw StatusException(406, "$name is null, what is it supposed to define?")

                    elm.isJsonPrimitive -> {
                        if (!elm.asJsonPrimitive.isString)
                            throw StatusException(406, "Wrong type of $name, should be a string")
                        var defTypeName = elm.asJsonPrimitive.asString

                        defTypeName = validateAndStripAttributes(defTypeName)

                        if (defTypeName.startsWith('@')) {
                            if (defTypeName.length < 2)
                                throw StatusException(406, "$name defines an empty reference '@'")

                            validateRoleReference(name, defTypeName.substring(1))

                        } else
                            RoleSchemas.TypeDef.fromTypeName(name, defTypeName)  // throws StatusException on error
                    }

                    elm.isJsonArray -> {  // this is Enum values. They must be strings
                        val enumValues = mutableSetOf<String>()
                        if( elm.asJsonArray.size() == 0 )
                            throw StatusException(406, "$name: no enum values defined")
                        elm.asJsonArray.forEachIndexed { idx, e ->
                            if( !e.isJsonPrimitive || !e.asJsonPrimitive.isString )
                                throw StatusException(406, "$name[$idx]: must be a string denoting the enum value")
                            val enumVal = e.asJsonPrimitive.asString

                            // FIXME: the logic catches ["A", "!"], but passes ["A", "!!"].
                            if( TypeDef.fromEnumValue(name, enumVal) != null ) {
                                if( idx > 0 )
                                    throw StatusException(406, "$name[$idx]: attributes may only appear as the first member of the enum")
                                if( elm.asJsonArray.size() <= 1 )
                                    throw StatusException(406, "$name: no enum values defined")
                            } else {
                                if( enumVal in enumValues )
                                    throw StatusException(406, "$name[$idx]: the enum value '$enumVal' is already defined")
                                enumValues.add(enumVal)
                            }
                        }
                    }

                    elm.isJsonObject -> {
                        if( elm.asJsonObject.size() == 0 )
                            throw StatusException(406, "$name: no members defined")
                        for(entry in elm.asJsonObject.entrySet())  // forEach swallows exceptions, use loop
                            process("$name.${entry.key}", entry.value)
                    }

                    else ->
                        throw StatusException(406, "Found something new in sex! $name type isn't a Json null,primitive, array, or object")
                }

            }

            try {
                val rootElm = JsonParser().parse(jsonStr)
                require(rootElm.isJsonObject) { "Role Schema must be a Json Object (e.g. the {...} thingy)" }
                process("\$", rootElm.asJsonObject)
            } catch(x: JsonParseException) {
                throw StatusException(x, 406)
            }
        }


        @JvmStatic private fun walkJson(
                name: String
                , elm: JsonElement
                , onNull:        (name: String) -> Unit = {}
                , onEmptyObject: (name: String) -> Unit = {}
                , onPrimitive:   (name: String, value: JsonPrimitive) -> Unit = { _: String, _: Any -> }
                , onArray:       (name: String, value: JsonArray)     -> Unit = { _: String, _: Any -> }
        ) {
            when {
                elm.isJsonNull      -> onNull(name)
                elm.isJsonPrimitive -> onPrimitive(name, elm.asJsonPrimitive)
                elm.isJsonArray     -> onArray(name, elm.asJsonArray)
                elm.isJsonObject    -> {
                    if( elm.asJsonObject.entrySet().isEmpty() )
                        onEmptyObject(name)
                    else {
                        for(entry in elm.asJsonObject.entrySet())  // forEach swallows exceptions, use loop
                            walkJson("$name.${entry.key}", entry.value)
                    }
                }
            }
        }


        /**
         * Given a valid Role Schema, get its representation string suitable for comparison
         *
         */
        @JvmStatic fun normalize(schemaJson: JsonObject): String {

            val attrs = HashMap<String, String>()

            RoleSchemas.walkJson(
                "\$", schemaJson,
                onPrimitive = { name, prm ->
                    attrs[name] = TypeDef.fromTypeName(name, prm.asString).normalize()
                },
                onArray = { name, arr ->
                    var typeDef = TypeDef.fromEnumValue(name, arr[0].asString)
                    val startIndex = if( typeDef == null ) 0 else 1
                    if( typeDef == null )
                        typeDef = TypeDef(ElementType.STRING)

                    attrs[name] = typeDef.normalize() + ':' +
                                  arr.filterIndexed { index, _ -> index >= startIndex }
                                     .sortedBy { elm -> elm.asJsonPrimitive.asString }
                                     .joinToString("|") { it.asJsonPrimitive.asString }
                }
            )

            return attrs.keys.sorted().joinToString(";") { key -> "$key:${attrs[key]}" }
        }
    }

}