package com.amcentral365.service.api

import com.google.gson.JsonElement
import com.google.gson.JsonParser

import com.amcentral365.service.StatusException
import com.amcentral365.service.config
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore
import com.amcentral365.service.schemaUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import java.lang.Exception
import java.util.Arrays


private val validSchemaDefTypes = mapOf(
      "string"  to SchemaUtils.ElementType.STRING
    , "number"  to SchemaUtils.ElementType.NUMBER
    , "boolean" to SchemaUtils.ElementType.BOOLEAN
    , "map"     to SchemaUtils.ElementType.MAP
)

typealias CompiledSchema = Map<String, SchemaUtils.ASTNode>


open class SchemaUtils {

    enum class ElementType { STRING, NUMBER, BOOLEAN, MAP, ENUM, OBJECT }

    data class TypeDef(
          val typeCode: ElementType
        , val required: Boolean = false
        , val zeroplus: Boolean = false
        , val oneplus:  Boolean = false
        , val indexed:  Boolean = false
    ) {
        val multiple: Boolean get() = this.zeroplus || this.oneplus

        companion object {

            @JvmStatic
            fun genericFrom(strToParse: String,
                postParse: (s: String, required: Boolean, zeroplus: Boolean, oneplus: Boolean, indexed: Boolean) -> TypeDef?
            ): TypeDef? {
                var workStr = strToParse
                var required = false
                var zeroplus = false
                var oneplus  = false
                var indexed  = false

                // NB: we only replace first occurrence not allowing double definitions
                if( workStr.contains('!') ) { required = true;  workStr = workStr.replaceFirst('!', ' ') }
                if( workStr.contains('*') ) { zeroplus = true;  workStr = workStr.replaceFirst('*', ' ') }
                if( workStr.contains('+') ) { oneplus  = true;  workStr = workStr.replaceFirst('+', ' ') }
                if( workStr.contains('^') ) { indexed  = true;  workStr = workStr.replaceFirst('^', ' ') }

                return postParse(workStr.trimEnd(), required, zeroplus, oneplus, indexed)
            }

            @JvmStatic fun fromTypeName(elmName: String, typeStr: String): TypeDef =
                genericFrom(typeStr) { str, required, zeroplus, oneplus, indexed ->
                    if( str !in validSchemaDefTypes )
                        throw StatusException(406, "$elmName defines an invalid type '$str'. " +
                                "The valid types are: ${validSchemaDefTypes.keys.joinToString(", ")}")
                    TypeDef(validSchemaDefTypes[str]!!, required, zeroplus, oneplus, indexed)
                }!!

            @JvmStatic fun fromEnumValue(elmName: String, enumVal: String): TypeDef? =
                    genericFrom(enumVal) { str, required, zeroplus, oneplus, indexed ->
                        if( str.isEmpty() )
                            TypeDef(ElementType.ENUM, required, zeroplus, oneplus, indexed)
                        else
                            null
                    }
        }
    }

    data class ASTNode(
            val attrName:   String,   // "size"
            val type: TypeDef,
            val enumValues: Array<String>? = null   // when the type is ENUM
    ) {
        override fun equals(other: Any?): Boolean {  // auto-generated
            if(this === other) return true
            if(javaClass != other?.javaClass) return false

            other as ASTNode

            if(attrName != other.attrName) return false
            if(type != other.type) return false
            if(!Arrays.equals(enumValues, other.enumValues)) return false

            return true
        }

        override fun hashCode(): Int {  // auto-generated
            var result = attrName.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + (enumValues?.let { Arrays.hashCode(it) } ?: 0)
            return result
        }
    }


    /**
     * Guava Cache of compiled role schemas, by role name.
     *
     * Cache size is --schema-cache-size-in-nodes [ASTNode]s. When value isn't found in the
     * cache or the database, a [StatusException] is thrown with value 404.
     */
    val schemaCache = CacheBuilder.newBuilder()
            .maximumWeight(config.schemaCacheSizeInNodes)
            .weigher { key: String, value: CompiledSchema -> value.size }
            .build(object: CacheLoader<String, CompiledSchema>() {
                override fun load(roleName: String): CompiledSchema {
                    val roleSchema = schemaUtils.loadSchemaFromDb(roleName)
                    if( roleSchema == null )
                        throw StatusException(404, "role '$roleName' was not found")
                    return schemaUtils.validateAndCompile(roleName, roleSchema)
                }
            })


    /**
     * Load schema definition from the database
     *
     * @return schema as a JSON string or null if [roleName] wasn't found.
     */
    @VisibleForTesting open fun loadSchemaFromDb(roleName: String): String? {
        val role = Role(roleName)
        val lst = databaseStore.fetchRowsAsObjects(role, limit = 1)
        require(lst.size < 2)
        return if( lst.size == 1 ) (lst[0] as Role).roleSchema else null
    }


    /**
     * Walk schema json top down, calling handlers.
     */
    private fun walkJson(
              name: String
            , elm: JsonElement
            , onNull:      (name: String) -> Unit = {}
            , onPrimitive: (name: String, value: JsonPrimitive) -> Unit = { _,_ -> }
            , onArray:     (name: String, value: JsonArray)     -> Unit = { _,_ -> }
            , onObject:    (name: String, value: JsonObject)    -> Unit = { _,_ -> }
            , beforeEach:  (name: String, value: JsonElement) -> String = { x,_ -> x }
    ) {
        when {
            elm.isJsonNull      -> { onNull(beforeEach(name, elm)) }
            elm.isJsonPrimitive -> { onPrimitive(beforeEach(name, elm), elm.asJsonPrimitive) }
            elm.isJsonArray     -> { onArray(beforeEach(name, elm), elm.asJsonArray) }
            elm.isJsonObject    -> {
                onObject(beforeEach(name, elm), elm.asJsonObject)
                for(entry in elm.asJsonObject.entrySet()) { // forEach swallows exceptions, use loop
                    val mangledname = beforeEach("$name.${entry.key}", entry.value)
                    walkJson(mangledname, entry.value, onNull, onPrimitive, onArray, onObject, beforeEach)
                }
            }
        }
    }


    /**
     * Convert JSON String representation of a role schema to compiled form
     *
     * The compiled form has all references to other schemas resolved.
     * Internally it is a [Set] of node definitions [ASTNode] with full attribute
     * names serving as keys.
     * Full attribute name comprise of the full path from the root to the node.
     */
    fun validateAndCompile(roleName: String, jsonStr: String
        , rootElmName: String = "\$"
        , seenRoles: MutableList<Pair<String, String>>? = null): CompiledSchema
    {
        val compiledNodes: MutableMap<String, ASTNode> = mutableMapOf()

        try {
            val elm0 = JsonParser().parse(jsonStr)
            require(elm0.isJsonObject) { "Role Schema must be a Json Object (e.g. the {...} thingy)" }

            walkJson(rootElmName, elm0.asJsonObject,
                onNull   = { name -> throw StatusException(406, "$name is null, what is it supposed to define?") }
              , onObject = { name, elm ->
                    if( elm.size() == 0 )
                        throw StatusException(406, "$name: no members defined")
                    compiledNodes.put(name, ASTNode(name, TypeDef(ElementType.OBJECT)))
                }

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

                        if( enumVal.isBlank() )
                            throw StatusException(406, "$name: enum value at index $idx is blank")

                        if( idx == 0 ) {
                            val tpd = TypeDef.fromEnumValue(name, enumVal)
                            if( tpd != null ) {
                                typeDef = tpd
                                continue
                            }
                        }

                        if( enumVal in enumValues )
                            throw StatusException(406, "${name.substringAfterLast("[")}[$idx]: the enum value '$enumVal' is already defined")

                        enumValues.add(enumVal)
                    }

                    if( enumValues.isEmpty() )
                        throw StatusException(406, "$name: no enum values defined")

                    // walkJson appends '[]' tp array name, but while in the schema definition enums are arrays,
                    // in the asset they are single values. Strip out the trailing [] from the name.
                    val scalarName = name.substringBeforeLast('[')
                    compiledNodes.put(name, ASTNode(name, typeDef, enumValues = enumValues.toTypedArray()))
              }

            , onPrimitive = { name, prm ->
                    if( !prm.isString )
                        throw StatusException(406, "Wrong type of $name, should be string")

                    if( prm.asString.startsWith('@'))  {
                        val rfRoleNameWithFlags = prm.asString.substring(1)
                        if( rfRoleNameWithFlags.isBlank() )
                            throw StatusException(406, "$name defines an empty reference '@'")

                        var rfRoleName = rfRoleNameWithFlags
                        val typeDef = TypeDef.genericFrom(rfRoleNameWithFlags) { stripedRoleName, required, zeroplus, oneplus, indexed ->
                            rfRoleName = stripedRoleName
                            TypeDef(ElementType.OBJECT, required, zeroplus, oneplus, indexed)
                        }!!

                        if( rfRoleName == roleName )
                            throw StatusException(406, "$name references the same role $roleName")

                        val seeenRoles = seenRoles ?: mutableListOf(Pair("$", roleName))
                        val prevIdx = seeenRoles.indexOfFirst { it.second == rfRoleName }
                        if( prevIdx != -1 )
                            throw StatusException(406, "$name: cycle in role references. Role $rfRoleName was referenced by ${seeenRoles[prevIdx].first}")

                        seeenRoles.add(Pair(name, rfRoleName))
                        val rfSchemaStr = loadSchemaFromDb(rfRoleName) ?:
                                throw StatusException(406, "$name references unknown role '$rfRoleName'")

                        // NB: recursive call
                        val subAttrRoot = "$name[]"
                        compiledNodes.put(name, ASTNode(name, typeDef))
                      //compiledNodes.put(subAttrRoot, ASTNode(subAttrRoot, TypeDef(typeDef.typeCode)))
                        val rfSchemaSet = validateAndCompile(rfRoleName, rfSchemaStr, rootElmName = subAttrRoot, seenRoles = seeenRoles)
                        compiledNodes.putAll(rfSchemaSet) //.minus(subAttrRoot))

                    } else {
                        val typeDef = TypeDef.fromTypeName(name, prm.asString)
                        compiledNodes.put(name, ASTNode(name, typeDef))
                    }
              }
            )

        } catch(x: JsonParseException) {
            throw StatusException(x, 406)
        }

        schemaCache.put(roleName, compiledNodes)
        return compiledNodes
    }


    fun checkValueType(astn: ASTNode, elm: JsonElement) {
        when(astn.type.typeCode) {
            ElementType.STRING  -> require((elm as JsonPrimitive).isString)
            ElementType.BOOLEAN -> require((elm as JsonPrimitive).isBoolean)
            ElementType.NUMBER  -> require((elm as JsonPrimitive).isNumber)
            ElementType.ENUM    -> require((elm as JsonPrimitive).isString)
            ElementType.MAP     -> require(elm.isJsonObject) // FIXME
            ElementType.OBJECT  -> require(elm.isJsonObject)
        }
    }


    @VisibleForTesting fun isAttributeWithPrefix(attr: String, prefix: String) =
            attr.startsWith(prefix+".") && attr.indexOf('.', prefix.length+1) == -1


    fun validateAssetValue(roleName: String, elm: JsonElement) {
        val roleSchema = this.schemaCache.get(roleName)
        val unseenRequiredNames = mutableSetOf<String>("$")

        fun checkWithThrow(name: String, astn: ASTNode, prm: JsonElement) {
            try {
                checkValueType(astn, prm)
            } catch(x: Exception) {
                throw StatusException(406, "type of attribute '$name' isn't ${astn.type.typeCode}")
            }

            if( astn.type.typeCode == ElementType.ENUM )
                if( prm.asString !in astn.enumValues!! )
                    throw StatusException(406, "value '${prm.asString}' of attribute '$name' isn't valid for the enum")
        }

        fun addUnseenRequiredNodes(prefix: String) =
            unseenRequiredNames.addAll(roleSchema.filter
                { it.value.type.required && isAttributeWithPrefix(it.key, prefix) }.keys)

        fun checkElement(rootElmName: String, jsonElement: JsonElement) {
            this.walkJson(rootElmName, jsonElement,
                beforeEach = { name, e ->
                    if( !roleSchema.containsKey(name) )
                        throw StatusException(406, "attribute '$name' is not defined in the schema")

                    val astn = roleSchema[name]!!
                    if( astn.type.multiple && !e.isJsonArray )
                        throw StatusException(406, "attribute '$name' must be an array")

                    unseenRequiredNames.remove(name)
                    name
                }
              , onNull = { name ->
                    val astn = roleSchema[name]!!
                    if( astn.type.required )
                        throw StatusException(406, "attribute '$name' is required, null values are not allowed")
                }
              , onPrimitive = { name, prm ->
                    val astn = roleSchema[name]!!
                    checkWithThrow(name, astn, prm)
                }
              , onObject = { name, jo ->
                    val astn = roleSchema[name]!!
                    if( astn.type.required && jo.size() == 0 )
                        throw StatusException(406, "attribute '$name' is required, empty objects are not allowed")

                    checkWithThrow(name, astn, jo)  // ensure it isn't an array or a primitive

                    addUnseenRequiredNodes(name)    // add object's mandatory attributes
                }
              , onArray = { name, arr ->
                    val astn = roleSchema[name]!!
                    if( !astn.type.multiple )
                        throw StatusException(406, "attribute '$name' shan't be an array")
                    if( astn.type.oneplus && arr.size() == 0 )
                        throw StatusException(406, "attribute '$name': at least one array elements is required")

                    arr.forEachIndexed { idx, e ->
                        //checkWithThrow("$name[$idx]", astn, e)
                        try {
                            checkElement(name+"[]", e)
                        } catch(x: StatusException) {
                            throw StatusException(x.code, "$name[$idx]: ${x.message}")
                        }
                    }
                }
            )
        }

        checkElement("$", elm)

        if( unseenRequiredNames.isNotEmpty() )
            throw StatusException(406, "missing ${unseenRequiredNames.size} required attributes: ${unseenRequiredNames.joinToString(", ")}")
    }


    fun validateAssetValue(roleName: String, jsonStr: String) =
            this.validateAssetValue(roleName, JsonParser().parse(jsonStr))
}
