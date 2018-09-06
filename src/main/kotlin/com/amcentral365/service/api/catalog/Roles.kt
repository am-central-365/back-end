package com.amcentral365.service.api.catalog

import spark.Request
import spark.Response

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.closeIfCan
import com.amcentral365.service.*

import com.amcentral365.service.dao.Role
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.sql.Connection

class Roles {
    private val restToColDef = Role().allCols.map { it.restParamName to it }.toMap()

    companion object {
        fun validate(jsonStr: String): Boolean {
            return true

            //val rootObj = JsonParser().parse(jsonStr).asJsonObject
            //rootObj.
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

        if( field.isNotEmpty() ) {

            require( this.restToColDef.contains(field))
                { throw StatusException(400, "parameter '$field' is not a valid REST parameter for Role") }
            selStmt.select(this.restToColDef.getValue(field).columnName)

        } else if( fields.isNotEmpty() ) {

            selStmt.select(
                fields.split(',').map { restParam ->
                    require(this.restToColDef.contains(restParam))
                        { throw StatusException(400, "parameter '$restParam' is not a valid REST parameter for Role") }
                    this.restToColDef.getValue(restParam)
                }
            )

        } else {

            selStmt.select(role.allCols)

        }

        var conn: Connection? = null
        return try {

            conn = databaseStore.getGoodConnection()
            val defs = selStmt.iterate(conn).asSequence().filterIndexed{k, _ -> k >= skipCount}.take(fetchLimit).toList()
            toJsonArray(defs, if( field.isNotEmpty() ) field else null)

        } catch(x: Exception) {
            formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }

    }

    fun mergeRole(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val paramMap = combineRequestParams(req)
        val role = Role()

        if( paramMap.containsKey("role_schema") )
            Roles.validate(paramMap.get("role_schema")!!)

        role.assignFrom(paramMap)
        if( role.roleName == null )
            return formatResponse(rsp, 400, "parameters 'role_name' is required")

        val msg = databaseStore.mergeObjectAsRow(role)
        return formatResponse(rsp, msg)
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