package com.amcentral365.service.api

import mu.KotlinLogging

import kotlin.Exception

import java.util.Arrays

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


private val logger = KotlinLogging.logger {}

private val validSchemaDefTypes = mapOf(
      "string"  to SchemaUtils.ElementType.STRING
    , "number"  to SchemaUtils.ElementType.NUMBER
    , "boolean" to SchemaUtils.ElementType.BOOLEAN
    , "map"     to SchemaUtils.ElementType.MAP
)

private const val attributeNodeName = "_attr"

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

            @JvmStatic fun fromTypeName(elmName: String, typeStr: String, forcedType: ElementType? = null): TypeDef =
                genericFrom(typeStr) { str, required, zeroplus, oneplus, indexed ->
                    if( forcedType == null && str !in validSchemaDefTypes )
                        throw StatusException(406, "$elmName defines an invalid type '$str'. " +
                                "The valid types are: ${validSchemaDefTypes.keys.joinToString(", ")}")
                    TypeDef(forcedType ?: validSchemaDefTypes[str]!!, required, zeroplus, oneplus, indexed)
                }!!

            @JvmStatic fun fromEnumValue(elmName: String, enumVal: String): TypeDef? =
                    genericFrom(enumVal) { str, required, zeroplus, oneplus, indexed ->
                        if( str.isEmpty() )
                            TypeDef(ElementType.ENUM, required, zeroplus, oneplus, indexed)
                        else
                            null
                    }

            /**
             * Clone another TypeDef object, optionally altering some of its properties
             */
            @JvmStatic fun from(
                    tpd:      TypeDef,
                    typeCode: ElementType = tpd.typeCode,
                    required: Boolean = tpd.required,
                    zeroplus: Boolean = tpd.zeroplus,
                    oneplus:  Boolean = tpd.oneplus,
                    indexed:  Boolean = tpd.indexed
            ) = TypeDef(typeCode, required, zeroplus, oneplus, indexed)

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
                    logger.info { "caching schema for role $roleName" }
                    val roleSchema = schemaUtils.loadSchemaFromDb(roleName)
                                  ?: throw StatusException(404, "role '$roleName' was not found")
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
            , onPrimitive: (name: String, value: JsonPrimitive) -> Unit =    { _,_ -> }
            , onArray:     (name: String, value: JsonArray)     -> Unit =    { _,_ -> }
            , onObject:    (name: String, value: JsonObject)    -> Boolean = { _,_ -> true }
            , beforeEach:  (name: String, value: JsonElement)   -> Unit =    { _,_ -> }
    ) {
        when {
            elm.isJsonNull      -> { beforeEach(name, elm);  onNull(name) }
            elm.isJsonPrimitive -> { beforeEach(name, elm);  onPrimitive(name, elm.asJsonPrimitive) }
            elm.isJsonArray     -> { beforeEach(name, elm);  onArray(name, elm.asJsonArray) }
            elm.isJsonObject    -> {
                beforeEach(name, elm);
                val processChildren = onObject(name, elm.asJsonObject)
                if( processChildren )
                    for(entry in elm.asJsonObject.entrySet()) { // forEach swallows exceptions, use loop
                        walkJson("$name.${entry.key}", entry.value, onNull, onPrimitive, onArray, onObject, beforeEach)
                    }
            }
        }
    }


    fun validateAndCompile(roleName: String, jsonStr: String
        , rootElmName: String = "\$"
        , seenRoles: MutableList<Pair<String, String>>? = null
        , getSchemaStrByRoleName: (roleName: String) -> String? = ::loadSchemaFromDb
    ): CompiledSchema
    {
        try {
            val elm0 = JsonParser().parse(jsonStr)
            require(elm0.isJsonObject) { "$roleName: role schema must be a Json Object (i.e. the {...} thingy)" }
            return this.validateAndCompile(roleName, elm0.asJsonObject, rootElmName, seenRoles, getSchemaStrByRoleName = getSchemaStrByRoleName)
        } catch(x: JsonParseException) {
            logger.warn { "failed while validating/compiling schema for role $roleName: ${x.message}" }
            throw StatusException(x, 406)
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
    fun validateAndCompile(roleName: String, rootJsonObj: JsonObject
        , rootElmName: String = "\$"
        , seenRoles: MutableList<Pair<String, String>>? = null
        , seenNodes: MutableMap<String, ASTNode>? = null
        , getSchemaStrByRoleName: (roleName: String) -> String? = ::loadSchemaFromDb
    ): CompiledSchema
    {
        logger.info { "validating/compiling schema for role $roleName, root $rootElmName" }
        val compiledNodes = seenNodes ?: mutableMapOf()

        try {
            walkJson(rootElmName, rootJsonObj,
                onNull   = { name -> throw StatusException(406, "$name is null, what is it supposed to define?") }
              , onObject = { name, elm ->
                    if( elm.size() == 0 )
                        throw StatusException(406, "$name: no members defined")
                    try {
                        val attrStr = elm.getAsJsonPrimitive(attributeNodeName)?.asString
                        val tpd = if(attrStr == null) TypeDef(ElementType.OBJECT)
                                  else TypeDef.fromTypeName(name, attrStr, ElementType.OBJECT)
                        compiledNodes.putIfAbsent(name, ASTNode(name, tpd))

                        if( tpd.multiple && elm != rootJsonObj ) {
                            val pluralName = "$name[]"
                            compiledNodes.put(pluralName, ASTNode(pluralName, TypeDef(ElementType.OBJECT)))
                            validateAndCompile(roleName, elm, rootElmName = pluralName,
                                    seenRoles = seenRoles, seenNodes = compiledNodes,
                                    getSchemaStrByRoleName = getSchemaStrByRoleName)
                            return@walkJson false
                        }

                    } catch(x: Exception) {
                        throw StatusException(406, "$name: processing $attributeNodeName: ${x.message}")
                    }
                    true
                }

                // ENUMs
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

                    if( name.endsWith("."+attributeNodeName) ) {
                        // do nothing, already processed when encounterd the object
                    } else if( prm.asString.startsWith('@')) {
                        val rfRoleNameWithFlags = prm.asString.substring(1)
                        if(rfRoleNameWithFlags.isBlank())
                            throw StatusException(406, "$name defines an empty reference '@'")

                        var rfRoleName = rfRoleNameWithFlags
                        val typeDef = TypeDef.genericFrom(rfRoleNameWithFlags) { stripedRoleName, required, zeroplus, oneplus, indexed ->
                            rfRoleName = stripedRoleName
                            TypeDef(ElementType.OBJECT, required, zeroplus, oneplus, indexed)
                        }!!

                        if(rfRoleName == roleName)
                            throw StatusException(406, "$name references the same role $roleName")

                        val seeenRoles = seenRoles ?: mutableListOf(Pair("$", roleName))
                        val prevIdx = seeenRoles.indexOfFirst { it.second == rfRoleName }
                        if( prevIdx != -1 )
                            throw StatusException(406, "$name: cycle in role references. Role $rfRoleName was referenced by ${seeenRoles[prevIdx].first}")

                        seeenRoles.add(Pair(name, rfRoleName))
                        val rfSchemaStr = getSchemaStrByRoleName(rfRoleName)
                                ?: throw StatusException(406, "$name references unknown role '$rfRoleName'")

                        // NB: recursive call
                        val subAttrRoot = if(typeDef.multiple) "$name[]" else name
                        compiledNodes.put(name, ASTNode(name, typeDef))
                        compiledNodes.putIfAbsent(subAttrRoot, ASTNode(subAttrRoot, TypeDef(typeDef.typeCode)))
                        val rfSchemas = validateAndCompile(rfRoleName, rfSchemaStr,
                                rootElmName = subAttrRoot, seenRoles = seeenRoles,
                                getSchemaStrByRoleName = getSchemaStrByRoleName)
                        rfSchemas.forEach() { compiledNodes.putIfAbsent(it.key, it.value) }

                    } else {
                        var typeDef = TypeDef.fromTypeName(name, prm.asString)
                        val isMap = typeDef.typeCode == ElementType.MAP

                        if( isMap ) {
                            if( typeDef.indexed )
                                throw StatusException(406, "$name: map elements can't be indexed")
                            if( !typeDef.multiple )
                                typeDef = TypeDef.from(typeDef, zeroplus = true, indexed = false)
                        }

                        compiledNodes.put(name, ASTNode(name, typeDef))

                        if( typeDef.multiple )
                            // For maps, the name[] element defines the value type, currently always STRING
                            compiledNodes.put("$name[]", ASTNode("$name[]",
                                    TypeDef(if( isMap ) ElementType.STRING else typeDef.typeCode )))
                    }
              }
            )

        } catch(x: JsonParseException) {
            logger.warn { "failed validating/compiling schema for role $roleName: ${x.message}" }
            throw StatusException(x, 406)
        }

        schemaCache.put(roleName, compiledNodes)
        logger.info { "successfully validated and cached schema for role $roleName" }
        return compiledNodes
    }



    fun checkValueType(typeCode: ElementType, elm: JsonElement) {
        when(typeCode) {
            ElementType.STRING  -> require((elm as JsonPrimitive).isString)
            ElementType.BOOLEAN -> require((elm as JsonPrimitive).isBoolean)
            ElementType.NUMBER  -> require((elm as JsonPrimitive).isNumber)
            ElementType.ENUM    -> require((elm as JsonPrimitive).isString)
            ElementType.MAP     -> require(elm.isJsonObject)
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
                checkValueType(astn.type.typeCode, prm)
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
                        throw StatusException(406, "attribute '$name' is not defined for role $roleName")

                    val astn = roleSchema[name]!!
                    val isMap = astn.type.typeCode == ElementType.MAP
                    if( astn.type.multiple && !isMap && !(e.isJsonArray || (!astn.type.required && e.isJsonNull )))
                        throw StatusException(406, "attribute '$name' must be an array")

                    unseenRequiredNames.remove(name)
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
                    //if( astn.type.required && jo.size() == 0 )
                    //    throw StatusException(406, "attribute '$name' is required, empty objects are not allowed")

                    checkWithThrow(name, astn, jo)  // ensure it isn't an array or a primitive

                    if( astn.type.typeCode == ElementType.MAP ) {
                        if( astn.type.oneplus && jo.size() == 0 )
                            throw StatusException(406, "attribute '$name' is required, empty maps are not allowed")

                        val astv = roleSchema["$name[]"]!!
                        jo.entrySet().forEachIndexed() { idx, e ->
                            try {
                                checkValueType(astv.type.typeCode, e.value)
                            } catch(x: Exception) {
                                throw StatusException(406, "$name[$idx], key '${e.key}': the value type isn't ${astv.type.typeCode}")
                            }
                        }

                        false  // don't process individual elements, we just did
                    } else {
                        addUnseenRequiredNodes(name)    // add object's mandatory attributes
                        true   // process object elements
                    }
                }
              , onArray = { name, arr ->
                    val astn = roleSchema[name]!!
                    if( !astn.type.multiple )
                        throw StatusException(406, "attribute '$name' shan't be an array")
                    if( astn.type.oneplus && arr.size() == 0 )
                        throw StatusException(406, "attribute '$name': at least one array element is required")

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
        logger.info { "successfully validated asset (TODO: print id here) for role $roleName" }
    }


    fun validateAssetValue(roleName: String, jsonStr: String) =
            this.validateAssetValue(roleName, JsonParser().parse(jsonStr))
}
