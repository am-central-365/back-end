package com.amcentral365.service.api

import com.google.gson.JsonElement
import com.google.gson.JsonParser

import com.amcentral365.service.StatusException
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive


private val validSchemaDefTypes = mapOf(
      "string"  to SchemaUtils.ElementType.STRING
    , "number"  to SchemaUtils.ElementType.NUMBER
    , "boolean" to SchemaUtils.ElementType.BOOLEAN
    , "map"     to SchemaUtils.ElementType.MAP
)

private const val ROLE_DELIMITER: Char = ':'

class SchemaUtils {

    enum class ElementType { STRING, NUMBER, BOOLEAN, MAP, ENUM, OBJECT }

    data class TypeDef(
          val typeCode: ElementType
        , val required: Boolean = false
        , val multiple: Boolean = false
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
                if( workStr.contains('!') ) { required = true;  workStr = workStr.replaceFirst('!', ' ') }
                if( workStr.contains('+') ) { multiple = true;  workStr = workStr.replaceFirst('+', ' ') }
                if( workStr.contains('^') ) { indexed  = true;  workStr = workStr.replaceFirst('^', ' ') }

                return postParse(workStr.trimEnd(), required, multiple, indexed)
            }

            @JvmStatic fun fromTypeName(elmName: String, typeStr: String): TypeDef =
                genericFrom(typeStr) { str, required, multiple, indexed ->
                    if( str !in validSchemaDefTypes )
                        throw StatusException(406, "$elmName defines an invalid type '$str'. " +
                                "The valid types are: ${validSchemaDefTypes.keys.joinToString(", ")}")
                    TypeDef(validSchemaDefTypes[str]!!, required, multiple, indexed)
                }!!

            @JvmStatic fun fromEnumValue(elmName: String, enumVal: String): TypeDef? =
                    genericFrom(enumVal) { str, required, multiple, indexed ->
                        if( str.isEmpty() )
                            TypeDef(ElementType.ENUM, required, multiple, indexed)
                        else
                            null
                    }
        }

        fun normalize(): String {
            val tps = when(this.typeCode) {
                ElementType.STRING -> 's'
                ElementType.NUMBER -> 'n'
                ElementType.BOOLEAN -> 'b'
                ElementType.ENUM -> 'e'
                ElementType.MAP -> 'm'
                ElementType.OBJECT -> 'o'
            }

            val flags = 0
                + (if( this.required ) 1 else 0)
                + (if( this.multiple ) 2 else 0)
                + (if( this.indexed )  4 else 0)

            return "$tps$flags"  // returns values like s1 or b3
        }
    }

    data class ASTNode(
            val attrName:   String,   // "size"
            val type: TypeDef,
            val enumValues: Array<String>? = null   // when the type is ENUM
    )

    companion object {

        @JvmStatic
        private fun loadSchemaReference(roleName: String): String? {
            val role = Role(roleName)
            val lst = databaseStore.fetchRowsAsObjects(role, limit = 1)
            require(lst.size < 2)
            return if( lst.size == 1 ) (lst[0] as Role).roleSchema else null
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
                            walkJson("$name.${entry.key}", entry.value, onNull, onEmptyObject, onPrimitive, onArray)
                    }
                }
            }
        }


        @JvmStatic fun validateAndCompile(roleName: String, jsonStr: String
            , rootElmName: String = "\$"
            , seenRoles: MutableList<Pair<String, String>>? = null): Set<ASTNode>
        {
            val compiledNodes: MutableSet<ASTNode> = mutableSetOf<ASTNode>()

            try {
                val elm0 = JsonParser().parse(jsonStr)
                require(elm0.isJsonObject) { "Role Schema must be a Json Object (e.g. the {...} thingy)" }

                walkJson(rootElmName, elm0.asJsonObject,
                    onNull        = { name -> throw StatusException(406, "$name is null, what is it supposed to define?") }
                  , onEmptyObject = { name -> throw StatusException(406, "$name: no members defined") }

                  , onArray = { name, arr ->
                        val enumValues = mutableSetOf<String>()
                        var typeDef = TypeDef(ElementType.ENUM)

                        if( arr.size() < 1 )
                            throw StatusException(406, "$name: no enum values defined")

                        for(idx in 0 until arr.size()) {
                            val e = arr[idx]
                            if( !e.isJsonPrimitive || !e.asJsonPrimitive.isString )
                                throw StatusException(406, "$name[$idx]: enum values must be strings")

                            val enumVal = e.asJsonPrimitive.asString

                            if( idx == 0 ) {
                                val tpd = TypeDef.fromEnumValue(name, enumVal)
                                if( tpd != null ) {
                                    typeDef = tpd
                                    continue
                                }
                            }

                            if( enumVal in enumValues )
                                throw StatusException(406, "$name[$idx]: the enum value '$enumVal' is already defined")

                            enumValues.add(enumVal)
                        }

                        if( enumValues.isEmpty() )
                            throw StatusException(406, "$name: no enum values defined")

                        compiledNodes.add(ASTNode(name+"[]", typeDef, enumValues = enumValues.toTypedArray()))
                  }

                , onPrimitive = { name, prm ->
                        if( !prm.isString )
                            throw StatusException(406, "Wrong type of $name, should be string")

                        if( prm.asString.startsWith('@'))  {
                            val rfRoleName = prm.asString.substring(1)
                            if( rfRoleName.isBlank() )
                                throw StatusException(406, "$name defines an empty reference '@'")

                            if( rfRoleName == roleName )
                                throw StatusException(406, "$name references the same role $roleName")

                            val rfSchemaStr = loadSchemaReference(roleName) ?:
                                    throw StatusException(406, "$name references unknown role '$rfRoleName'")

                            // NB: recursive call
                            val rfSchemaSet = validateAndCompile(rfRoleName, rfSchemaStr, rootElmName = name)
                            compiledNodes.addAll(rfSchemaSet)

                        } else {
                            val typeDef = TypeDef.fromTypeName(name, prm.asString)
                            compiledNodes.add(ASTNode(name, typeDef))
                        }
                  }
                )

            } catch(x: JsonParseException) {
                throw StatusException(x, 406)
            }

            return compiledNodes
        }

        /**
         * Given a valid Role Schema, get its representation string suitable for comparison
         *
         */
        @JvmStatic fun normalize(schemaJson: JsonObject): String {

            val attrs = HashMap<String, String>()

            walkJson("\$", schemaJson,
                    onPrimitive = { name, prm ->
                        attrs[name] = TypeDef.fromTypeName(name, prm.asString).normalize()
                    },
                    onArray = { name, arr ->
                        var typeDef = TypeDef.fromEnumValue(name, arr[0].asString)
                        val startIndex = if(typeDef == null) 0 else 1
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
