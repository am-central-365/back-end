package com.amcentral365.service.api

import mu.KotlinLogging

import kotlin.Exception

import java.util.Arrays

import com.google.gson.JsonElement
import com.google.gson.JsonParser

import com.amcentral365.service.StatusException
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore
import com.google.common.annotations.VisibleForTesting
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.gson.Gson

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

private const val compositeTypeNodeName = "type"
private const val compositeDefaultNodeName = "default"
private const val attributeNodeName = "_attr"

typealias CompiledSchema = Map<String, SchemaUtils.ASTNode>
fun CompiledSchema.nodesWithDefaultValue(): Iterable<Map.Entry<String, SchemaUtils.ASTNode>> =
    this.entries.filter { it.value.type.defaultVal != null }


/**
 * Load schema definition from the database
 *
 * @return schema as a JSON string or null if [roleName] wasn't found.
 */
fun loadSchemaFromDb(roleName: String): String? {
    val role = Role(roleName)
    val lst = databaseStore.fetchRowsAsObjects(role, limit = 1)
    require(lst.size < 2)
    return if( lst.size == 1 ) (lst[0] as Role).roleSchema else null
}


open class SchemaUtils(
        maxCacheWeight: Long = 100,
        val loadSchema: (roleName: String) -> String? = ::loadSchemaFromDb
) {

    enum class ElementType { STRING, NUMBER, BOOLEAN, MAP, ENUM, OBJECT }

    data class TypeDef(
          val typeCode: ElementType
        , val required: Boolean = false
        , val zeroplus: Boolean = false
        , val oneplus:  Boolean = false
        , val indexed:  Boolean = false
        , val defaultVal: Any? = null
    ) {
        val multiple: Boolean get() = this.zeroplus || this.oneplus


        override fun equals(other: Any?): Boolean {
            if(this === other) return true
            if(javaClass != other?.javaClass) return false

            other as TypeDef
            if(typeCode != other.typeCode) return false
            if(required != other.required) return false
            if(zeroplus != other.zeroplus) return false
            if(oneplus != other.oneplus) return false
            if(indexed != other.indexed) return false
            if(defaultVal.toString() != other.defaultVal.toString()) return false

            return true
        }

        override fun hashCode(): Int {
            var result = typeCode.hashCode()
            result = 31 * result + required.hashCode()
            result = 31 * result + zeroplus.hashCode()
            result = 31 * result + oneplus.hashCode()
            result = 31 * result + indexed.hashCode()
            result = 31 * result + (defaultVal?.hashCode() ?: 0)
            return result
        }

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
                    TypeDef(forcedType ?: validSchemaDefTypes.getValue(str), required, zeroplus, oneplus, indexed)
                }!!

            @JvmStatic fun fromEnumValue(enumVal: String): TypeDef? =
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
                    tpd:        TypeDef,
                    typeCode:   ElementType = tpd.typeCode,
                    required:   Boolean = tpd.required,
                    zeroplus:   Boolean = tpd.zeroplus,
                    oneplus:    Boolean = tpd.oneplus,
                    indexed:    Boolean = tpd.indexed,
                    defaultVal: Any?    = tpd.defaultVal
            ) = TypeDef(typeCode, required, zeroplus, oneplus, indexed, defaultVal)


            @JvmStatic fun isCompositeTypeDef(obj: JsonObject): Boolean =
                    obj.size() in 1..2
                &&  obj.has(compositeTypeNodeName)
                && (obj.size() == 1 || obj.has(compositeDefaultNodeName))
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
                beforeEach(name, elm)
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
    ): CompiledSchema
    {
        try {
            val elm0 = JsonParser().parse(jsonStr)
            require(elm0.isJsonObject) { "$roleName: role schema must be a Json Object (i.e. the {...} thingy)" }
            return this.validateAndCompile(roleName, elm0, rootElmName, seenRoles)
        } catch(x: JsonParseException) {
            logger.warn { "failed while validating/compiling schema for role $roleName: ${x.message}" }
            throw StatusException(x, 406)
        }
    }


    /**
     * Convert JSON String representation of a role schema to its compiled form
     *
     * The compiled form has all references to other schemas resolved.
     * Internally it is a [Set] of node definitions [ASTNode] with full attribute
     * names serving as keys.
     * Full attribute name comprise of the full path from the root to the node.
     */
    private fun validateAndCompile(roleName: String, rootJsonElm: JsonElement
      , rootElmName: String = "\$"
      , seenRoles: MutableList<Pair<String, String>>? = null
      , seenNodes: MutableMap<String, ASTNode>? = null
    ) : CompiledSchema
    {
        logger.info { "validating/compiling schema for role $roleName, root $rootElmName" }
        val compiledNodes = seenNodes ?: mutableMapOf()

        try {
            walkJson(rootElmName, rootJsonElm,
                onNull   = { name -> throw StatusException(406, "$name is null, what is it supposed to define?") }
              , onObject = { name, elm ->
                    if( elm.size() == 0 )
                        throw StatusException(406, "$name: no members defined")
                    try {
                        val attrStr = elm.getAsJsonPrimitive(attributeNodeName)?.asString
                        val isComposite = TypeDef.isCompositeTypeDef(elm)
                        var tpd = TypeDef(ElementType.OBJECT)
                        if( attrStr == null ) {
                            if( isComposite )
                                tpd = this.getCompositeType(roleName, name, elm, seenRoles, compiledNodes)
                        } else {
                            tpd = TypeDef.fromTypeName(name, attrStr, ElementType.OBJECT)
                        }

                        compiledNodes.putIfAbsent(name, ASTNode(name, tpd))

                        if( isComposite )
                            return@walkJson false

                        if( tpd.multiple && elm != rootJsonElm ) {
                            val pluralName = "$name[]"
                            compiledNodes[pluralName] = ASTNode(pluralName, TypeDef(ElementType.OBJECT))
                            validateAndCompile(roleName, elm, rootElmName = pluralName,
                                    seenRoles = seenRoles, seenNodes = compiledNodes)
                            return@walkJson false
                        }

                    } catch(x: Exception) {
                        throw StatusException(406, "$name: ${x.message}")
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
                            val tpd = TypeDef.fromEnumValue(enumVal)
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

                    compiledNodes[name] = ASTNode(name, typeDef, enumValues = enumValues.toTypedArray())
              }

            , onPrimitive = { name, prm ->
                    if( !prm.isString )
                        throw StatusException(406, "Wrong type of $name, should be string")

                    if( name.endsWith(".$attributeNodeName") ) {
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
                        val rfSchemaStr = this.loadSchema(rfRoleName)
                                ?: throw StatusException(406, "$name references unknown role '$rfRoleName'")

                        // NB: recursive call
                        val subAttrRoot = if(typeDef.multiple) "$name[]" else name
                        compiledNodes.put(name, ASTNode(name, typeDef))
                        compiledNodes.putIfAbsent(subAttrRoot, ASTNode(subAttrRoot, TypeDef(typeDef.typeCode)))
                        val rfSchemas = validateAndCompile(rfRoleName, rfSchemaStr,
                                rootElmName = subAttrRoot, seenRoles = seeenRoles)
                        rfSchemas.forEach { compiledNodes.putIfAbsent(it.key, it.value) }

                    } else {
                        var typeDef = TypeDef.fromTypeName(name, prm.asString)
                        val isMap = typeDef.typeCode == ElementType.MAP

                        if( isMap ) {
                            if( typeDef.indexed )
                                throw StatusException(406, "$name: map elements can't be indexed")
                            if( !typeDef.multiple )
                                typeDef = TypeDef.from(typeDef, zeroplus = true, indexed = false)
                        }

                        compiledNodes[name] = ASTNode(name, typeDef)

                        if( typeDef.multiple )
                            // For maps, the name[] element defines the value type, currently always STRING
                            compiledNodes["$name[]"] =
                                ASTNode("$name[]", TypeDef(if( isMap ) ElementType.STRING else typeDef.typeCode ))
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

    private fun getCompositeType(roleName: String, name: String, elm: JsonObject
                  , seenRoles: MutableList<Pair<String, String>>?
                  , seenNodes: MutableMap<String, ASTNode>
    ): TypeDef {
        val typeElmJson = elm[compositeTypeNodeName]
        var currNodeName = "$name.$compositeTypeNodeName"
        try {

            validateAndCompile(roleName, typeElmJson, currNodeName, seenRoles, seenNodes)
            require(seenNodes.contains(currNodeName))
                { "code bug: processed node $currNodeName, but didn't store it in compiledNodes" }
            val astNode = seenNodes.remove(currNodeName)!!

            if( astNode.type.multiple )
                seenNodes.remove("$currNodeName[]")

            if( !elm.has(compositeDefaultNodeName) )
                return astNode.type

            if( astNode.type.typeCode == ElementType.OBJECT )
                throw StatusException(406, "${astNode.attrName}: default values are not supported for OBJECT types")

            if( astNode.type.multiple && !elm[compositeDefaultNodeName].isJsonArray )
                throw StatusException(406, "${astNode.attrName} is an array. Its default value must also be an array")

            currNodeName = "$name.$compositeDefaultNodeName"

            fun checkAndGet(elm: JsonPrimitive, checker: (item: JsonPrimitive) -> Boolean, converter: (elm: JsonPrimitive) -> Any): Any =
                try {
                    if( !checker(elm) )
                        throw StatusException(406, "default value '$elm' should match the element type")
                    converter(elm)
                } catch(x: JsonParseException) {
                    throw StatusException(x, 406, "default does not match the element type")
                }

            val defaultElm = elm[compositeDefaultNodeName]
            var defaultVal: Any? = null

            when {
                defaultElm.isJsonNull -> {
                    if( astNode.type.required )
                        throw StatusException(406, "the type does not allow nulls")
                }

                defaultElm.isJsonObject ->
                    throw StatusException(406, "default values of OBJECT types are not supported")

                defaultElm.isJsonPrimitive -> {
                    val prim = defaultElm.asJsonPrimitive
                    when(astNode.type.typeCode) {
                        ElementType.NUMBER  -> defaultVal = checkAndGet(prim, { it.isNumber })  { it.asNumber  }
                        ElementType.STRING  -> defaultVal = checkAndGet(prim, { it.isString })  { it.asString  }
                        ElementType.BOOLEAN -> defaultVal = checkAndGet(prim, { it.isBoolean }) { it.asBoolean }
                        ElementType.ENUM    -> {
                            defaultVal = checkAndGet(prim, { it.isString }) { it.asString }
                            if( defaultVal !in astNode.enumValues!! )
                                throw StatusException(406, "value '$defaultVal' does not belong to the ENUM")
                        }
                        else ->
                            throw StatusException(406, "expected a composite type ")
                    }

                }

                defaultElm.isJsonArray -> {
                    val arr = defaultElm.asJsonArray
                    if( astNode.type.oneplus && arr.size() == 0)
                        throw StatusException(406, "the type prohibits empty arrays")

                    val vals = mutableListOf<Any?>()
                    for(k in 0 until arr.size()) {
                        val itemElm = arr[k]
                        if( itemElm.isJsonNull )
                            if( astNode.type.required )
                                throw StatusException(406, "[$k]: the type does not allow null array elements")
                            else {
                                vals.add(null)
                                continue
                            }

                        if( !itemElm.isJsonPrimitive )
                            throw StatusException(406, "[$k]: only primitive types are allowed as array elements")

                        val prim = itemElm.asJsonPrimitive
                        when(astNode.type.typeCode) {
                            ElementType.NUMBER  -> vals.add( checkAndGet(prim, { it.isNumber }) { it.asNumber } )
                            ElementType.STRING  -> vals.add( checkAndGet(prim, { it.isString }) { it.asString } )
                            ElementType.BOOLEAN -> vals.add( checkAndGet(prim, { it.isBoolean }) { it.asBoolean } )
                            ElementType.ENUM    -> {
                                val v = checkAndGet(prim, { it.isString }) { it.asString }
                                if(v !in astNode.enumValues!!)
                                    throw StatusException(406, "[$k]: value '$v' isn't valid for the ENUM")
                                vals.add(v)
                            }
                            else ->
                                throw StatusException(406, "[$k]: 'default' is only supported for arrays of STRING, NUMBER, BOOLEAN, or ENUM")
                        }
                    }

                    defaultVal = vals
                }
            }

            //val defaultVal = TypeDef.defaultValFromJson(currNodeName, astNode, elm[compositeDefaultNodeName])
            return TypeDef.from(astNode.type, defaultVal = defaultVal)

        } catch(x: Exception) {
            throw StatusException(406, "$currNodeName: ${x.message}")
        }
    }

    /**
     * Guava Cache of compiled role schemas, by role name.
     *
     * Cache size is --schema-cache-size-in-nodes [ASTNode]s. When value isn't found in the
     * cache or the database, a [StatusException] is thrown with value 404.
     */
    private val schemaCache = CacheBuilder.newBuilder()
            .maximumWeight(maxCacheWeight)
            .weigher { _: String, value: CompiledSchema -> value.size }
            .build(object: CacheLoader<String, CompiledSchema>() {
                override fun load(roleName: String): CompiledSchema {
                    logger.info { "caching schema for role $roleName" }
                    val roleSchema = this@SchemaUtils.loadSchema(roleName)
                            ?: throw StatusException(404, "role '$roleName' was not found")
                    return validateAndCompile(roleName, roleSchema)
                }
            })


    private fun checkValueType(typeCode: ElementType, elm: JsonElement) {
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
            attr.startsWith("$prefix.") && attr.indexOf('.', prefix.length+1) == -1

    private fun validateAssetValue(roleName: String, elm: JsonElement, roleSchema: CompiledSchema) {
        val unseenRequiredNames = mutableSetOf("$")

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

                    val astn = roleSchema.getValue(name)
                    val isMap = astn.type.typeCode == ElementType.MAP
                    if( astn.type.multiple && !isMap && !(e.isJsonArray || (!astn.type.required && e.isJsonNull )))
                        throw StatusException(406, "attribute '$name' must be an array")

                    unseenRequiredNames.remove(name)
                }
              , onNull = { name ->
                    val astn = roleSchema.getValue(name)
                    if( astn.type.required )
                        throw StatusException(406, "attribute '$name' is required, null values are not allowed")
                }
              , onPrimitive = { name, prm ->
                    val astn = roleSchema.getValue(name)
                    checkWithThrow(name, astn, prm)
                }
              , onObject = { name, jo ->
                    val astn = roleSchema.getValue(name)
                    //if( astn.type.required && jo.size() == 0 )
                    //    throw StatusException(406, "attribute '$name' is required, empty objects are not allowed")

                    checkWithThrow(name, astn, jo)  // ensure it isn't an array or a primitive

                    if( astn.type.typeCode == ElementType.MAP ) {
                        if( astn.type.oneplus && jo.size() == 0 )
                            throw StatusException(406, "attribute '$name' is required, empty maps are not allowed")

                        val astv = roleSchema.getValue("$name[]")
                        jo.entrySet().forEachIndexed { idx, e ->
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
                    val astn = roleSchema.getValue(name)
                    if( !astn.type.multiple )
                        throw StatusException(406, "attribute '$name' shan't be an array")
                    if( astn.type.oneplus && arr.size() == 0 )
                        throw StatusException(406, "attribute '$name': at least one array element is required")

                    arr.forEachIndexed { idx, e ->
                        //checkWithThrow("$name[$idx]", astn, e)
                        try {
                            checkElement("$name[]", e)
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

    fun validateAssetValue(roleName: String, elm: JsonElement) {
        val roleSchema = this.schemaCache.get(roleName)
        this.validateAssetValue(roleName, elm, roleSchema)
    }

    // ---------------------------------------- Assigning the default value
    fun assignDefaultValues(roleName: String, assetValStr: String): JsonElement =
        this.assignDefaultValues(roleName, JsonParser().parse(assetValStr))


    private fun assignDefaultValues(roleName: String, assetElm: JsonElement, roleSchemaP: CompiledSchema? = null): JsonElement {
        val roleSchema = roleSchemaP ?: this.schemaCache.get(roleName)
        this.validateAssetValue(roleName, assetElm, roleSchema)

        // Copy
        val workElm = assetElm.deepCopy()
        interestingNodes@ for( (path, node) in roleSchema.nodesWithDefaultValue()) {
            var elm = workElm
            var childName = "\$"
            val pathItems = path.split('.')
            for(k in 1 until pathItems.size) {  // skipping the first (root)
                if( !elm.isJsonObject )
                    throw StatusException(406, "According to role $roleName, attribute '${pathItems.subList(0, k).joinToString(".")}' must be an object, but it is not")

                childName = pathItems[k].removeSuffix("[]")
                val childIsPresent = elm.asJsonObject.has(childName)

                if( k == pathItems.size-1 )
                    if( childIsPresent )
                        continue@interestingNodes    // The element is present, no default substitution
                    else
                        break
                else
                    if( !childIsPresent )
                        continue@interestingNodes    // A parent of the element does not exist. We don not create
                                                     // intermediate parents for the sake of assigning the default

                elm = elm.asJsonObject[childName]
            }

            val defaultVal = node.type.defaultVal
            when(defaultVal) {
                is String  -> elm.asJsonObject.addProperty(childName, defaultVal)
                is Boolean -> elm.asJsonObject.addProperty(childName, defaultVal)
                is Number  -> elm.asJsonObject.addProperty(childName, defaultVal)
                else       -> throw NotImplementedError("still working on the array code")  // TODO: implement
            }
        }

        return workElm
    }


    // ---------------------------------------- Top level
    fun getAssetValue(roleName: String, assetValStr: String): JsonElement = this.assignDefaultValues(roleName, assetValStr)
}
