package com.amcentral365.service.api.catalog

import spark.Request
import spark.Response

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.closeIfCan
import com.amcentral365.service.formatResponse
import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.StatusException
import com.amcentral365.service.StatusMessage
import com.amcentral365.service.databaseStore
import com.amcentral365.service.toJsonArray

import com.amcentral365.service.dao.Role
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.sql.Connection

class Roles {
    private val restToColDef = Role().allCols.map { it.restParamName to it }.toMap()

    companion object {
        private val validSchemaDefTypes = arrayOf("string", "number", "boolean", "map")
        private val validAttributeCombinations = arrayOf("!", "+", "+!", "!+")

        fun validate(roleName: String, jsonStr: String) {   // throws StatusException 406 (Not Acceptable)

            fun validateRoleReference(elmName: String, referencedRoleName: String) {
                if( referencedRoleName != roleName ) {  // self references are allowed
                    val roleList = databaseStore.fetchRowsAsObjects(Role(referencedRoleName), limit = 1)
                    if( roleList.isEmpty() )
                        throw StatusException(406, "$elmName references unknown role '$referencedRoleName'")
                }
            }

            fun validateAndStripAttributes(typeName: String): String =
                    // we only replace one occurrence, preventing repetition  of suffixes
                    typeName.replaceFirst('!', ' ').replaceFirst('+', ' ').trimEnd(' ')

            fun process(name: String, elm: JsonElement) {
                when {
                    elm.isJsonNull ->
                        throw StatusException(406, "$name is null, what is it supposed to define?")

                    elm.isJsonPrimitive -> {
                        if( !elm.asJsonPrimitive.isString )
                            throw StatusException(406, "Wrong type of $name, should be a string")
                        var defTypeName = elm.asJsonPrimitive.asString

                        defTypeName = validateAndStripAttributes(defTypeName)

                        if( defTypeName.startsWith('@') ) {
                            if( defTypeName.length < 2 )
                                throw StatusException(406, "$name defines an empty reference '@'")

                            validateRoleReference(name, defTypeName.substring(1))

                        } else if( defTypeName !in validSchemaDefTypes )
                            throw StatusException(406, "$name defines an invalid type '$defTypeName'. "+
                                            "The valid types are: ${validSchemaDefTypes.joinToString(", ")}")
                    }

                    elm.isJsonArray -> {  // this is Enum values. They must be strings
                        val enumVals = mutableSetOf<String>()
                        if( elm.asJsonArray.size() == 0 )
                            throw StatusException(406, "$name: no enum values defined")
                        elm.asJsonArray.forEachIndexed { idx, e ->
                            if( !e.isJsonPrimitive )  // used to require a 'string', but relaxed to allow number
                                throw StatusException(406, "$name[$idx]: must be a primitive denoting an enum value")
                            val enumVal = e.asJsonPrimitive.asString

                            // TODO: the logic catches ["A", "!"], but passes ["A", "!!"].
                            if( enumVal in validAttributeCombinations ) {
                                if( idx > 0 )
                                    throw StatusException(406, "$name[$idx]: attributes may only appear as the first member of the enum")
                            } else {
                                if( enumVal in enumVals )
                                    throw StatusException(406, "$name[$idx]: the enum value '$enumVal' is already defined")
                                enumVals.add(enumVal)
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

            val rootElm = JsonParser().parse(jsonStr)
            require(rootElm.isJsonObject) { "Role Schema must be a Json Object (e.g. the {...} thingy)" }
            process("\$", rootElm.asJsonObject)
        }
    }

    fun getRoles(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val paramMap = combineRequestParams(req)
        val role = Role()

        val field  = paramMap.getOrDefault("field",  "")
        val fields = paramMap.getOrDefault("fields", "")
        if( field.isNotEmpty() && fields.isNotEmpty() )
            return formatResponse(rsp, 400, "parameters 'field' and 'fields' are mutually exclusive")

        val skipCount = paramMap.getOrDefault("skip", "0").toInt()
        val limit = paramMap.getOrDefault("limit", "0").toInt()
        val fetchLimit = if( limit > 0 ) limit else Int.MAX_VALUE

        role.assignFrom(paramMap)
        val selStmt = SelectStatement(role).byPresentValues()

        when {
            field.isNotEmpty() -> {

                require( this.restToColDef.contains(field))
                { throw StatusException(400, "parameter '$field' is not a valid REST parameter for Role") }
                selStmt.select(this.restToColDef.getValue(field).columnName)

            }
            fields.isNotEmpty() -> selStmt.select(
                    fields.split(',').map { restParam ->
                        require(this.restToColDef.contains(restParam))
                        { throw StatusException(400, "parameter '$restParam' is not a valid REST parameter for Role") }
                        this.restToColDef.getValue(restParam)
                    }
            )
            else -> selStmt.select(role.allCols)
        }

        var conn: Connection? = null
        return try {

            conn = databaseStore.getGoodConnection()
            val defs = selStmt.iterate(conn).asSequence().filterIndexed{k, _ -> k >= skipCount}.take(fetchLimit).toList()

            if( role.roleName != null ) {  // A concrete role GET
                if (defs.isEmpty() )
                    return formatResponse(rsp, 404, "role '${role.roleName}' was not found")
                return defs[0].asJsonStr()
            }

            return toJsonArray(defs, if( field.isNotEmpty() ) this.restToColDef.getValue(field).columnName else null)

        } catch(x: Exception) {
            formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }

    }


    fun createRole(req: Request, rsp: Response): String {
        try {

            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            val role = Role()
            role.assignFrom(paramMap)

            if( role.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")
            if( role.roleSchema != null )
                Roles.validate(role.roleName!!, role.roleSchema!!)

            val msg = databaseStore.insertObjectAsRow(role)
            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: JsonParseException) {
            return formatResponse(rsp, StatusMessage(x, 400))
        } catch(x: StatusException) {
            return formatResponse(rsp, x)
        }
    }


    fun updateRole(req: Request, rsp: Response): String {
        try {

            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            val role = Role()
            role.assignFrom(paramMap)

            if( role.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")
            if( role.roleSchema != null )
                Roles.validate(role.roleName!!, role.roleSchema!!)

            val msg = databaseStore.updateObjectAsRow(role)
            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: JsonParseException) {
            return formatResponse(rsp, StatusMessage(x, 400))
        } catch(x: StatusException) {
            return formatResponse(rsp, x)
        }
    }


    fun deleteRole(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val paramMap = combineRequestParams(req)
        val role = Role()
        role.assignFrom(paramMap)
        if( role.roleName == null )
            return formatResponse(rsp, 400, "parameters 'role_name' is required")

        val msg = databaseStore.deleteObjectRow(role)
        return formatResponse(rsp, msg)
    }
}