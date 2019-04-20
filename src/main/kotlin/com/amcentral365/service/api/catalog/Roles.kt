package com.amcentral365.service.api.catalog

import mu.KotlinLogging

import spark.Request
import spark.Response
import java.sql.Connection

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.closeIfCan
import com.amcentral365.service.formatResponse
import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.StatusException
import com.amcentral365.service.toJsonArray
import com.amcentral365.service.databaseStore
import com.amcentral365.service.schemaUtils

import com.amcentral365.service.dao.Role

private val logger = KotlinLogging.logger {}


class Roles { companion object {

    private val restToColDef = Role().allCols.map { it.restParamName to it }.toMap()

    fun listRoles(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val paramMap = combineRequestParams(req)
        val role = Role()

        val field  = paramMap.getOrDefault("field",  "")
        val fields = paramMap.getOrDefault("fields", "")
        logger.debug { "field $field, fields: $fields" }
        if( field.isNotEmpty() && fields.isNotEmpty() )
            return formatResponse(rsp, 400, "parameters 'field' and 'fields' are mutually exclusive")

        val skipCount = paramMap.getOrDefault("skip", "0").toInt()
        val limit = paramMap.getOrDefault("limit", "0").toInt()
        val fetchLimit = if( limit > 0 ) limit else Int.MAX_VALUE
        logger.debug { "skipCount $skipCount, limit: $limit, fetchLimit: $fetchLimit" }

        role.assignFrom(paramMap)
        logger.debug { "roleName: ${role.roleName}" }
        val selStmt = SelectStatement(role).byPresentValues()

        when {
            field.isNotEmpty() -> {

                require( this.restToColDef.contains(field) )
                    { throw StatusException(400, "parameter '$field' is not a valid REST parameter for Role") }
                selStmt.select(this.restToColDef.getValue(field).columnName)

            }
            fields.isNotEmpty() -> selStmt.select(
                    fields.split(',').map { restParam ->
                        require( this.restToColDef.contains(restParam) )
                            { throw StatusException(400, "parameter '$restParam' is not a valid REST parameter for Role") }
                        this.restToColDef.getValue(restParam)
                    }
            )
            else -> selStmt.select(role.allCols)
        }

        logger.debug { "roleName ${role.roleName}, stmt: ${selStmt.build()}" }
        var conn: Connection? = null
        return try {

            conn = databaseStore.getGoodConnection()
            val defs = selStmt.iterate(conn).asSequence().filterIndexed { k, _ -> k >= skipCount }.take(fetchLimit).toList()

            if( role.roleName != null ) {  // A concrete role GET
                if( defs.isEmpty() )
                    return formatResponse(rsp, 404, "role '${role.roleName}' was not found")
                logger.info { "roleName: ${role.roleName}, returning the found record" }
                return defs[0].asJsonStr()
            }

            logger.info { "roleName: ${role.roleName}, returning ${defs.size} items" }
            return toJsonArray(defs, if( field.isNotEmpty() ) this.restToColDef.getValue(field).columnName else null)

        } catch(x: Exception) {
            logger.error { "error querying role ${role.roleName}: ${x.message}" }
            formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }

    }


    fun createRole(req: Request, rsp: Response): String {
        val role = Role()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            role.assignFrom(paramMap);
            logger.info { "creating role '${role.roleName}'" }

            if( role.roleName == null )
                return formatResponse(rsp, 400, "parameter 'roleName' is required")
            if( role.roleSchema != null )
                schemaUtils.validateAndCompile(role.roleName!!, role.roleSchema!!)

            val msg = databaseStore.insertObjectAsRow(role)
            logger.info { "created role ${role.roleName}: ${msg.msg}" }
            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error creating role ${role.roleName}: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }


    fun updateRole(req: Request, rsp: Response): String {
        val role = Role()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            role.assignFrom(paramMap)
            logger.info { "updating role '${role.roleName}'" }

            if( role.roleName == null )
                return formatResponse(rsp, 400, "parameter 'roleName' is required")
            if( role.roleSchema != null )
                schemaUtils.validateAndCompile(role.roleName!!, role.roleSchema!!)

            val msg = databaseStore.updateObjectAsRow(role)
            logger.info { "update role ${role.roleName} succeeded: $msg" }
            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error updating role ${role.roleName}: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }


    fun deleteRole(req: Request, rsp: Response): String {
        val role = Role()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            role.assignFrom(paramMap)
            if( role.roleName == null )
                return formatResponse(rsp, 400, "parameters 'roleName' is required")

            logger.info { "deleting role '${role.roleName}'" }
            val msg = databaseStore.deleteObjectRow(role)
            logger.info { "delete role ${role.roleName} succeeded: $msg" }
            return formatResponse(rsp, msg)
        } catch(x: Exception) {
            logger.error { "error deleting role '${role.roleName}': ${x.message}" }
            return formatResponse(rsp, x)
        }
    }
}}
