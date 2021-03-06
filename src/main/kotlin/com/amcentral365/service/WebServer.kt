package com.amcentral365.service

import mu.KLogging

import java.sql.Connection
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

import spark.Request
import spark.Response

import com.google.common.annotations.VisibleForTesting

import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.closeIfCan

import com.amcentral365.service.api.Execute
import com.amcentral365.service.api.Tasks
import com.amcentral365.service.api.catalog.Assets
import com.amcentral365.service.api.catalog.Roles

import com.amcentral365.service.dao.Meta
import com.amcentral365.service.dao.Role


class WebServer {
    companion object: KLogging()

    private val API_BASE = "/v0.1"   // must match servers.url in src/main/resources/swagger/amcentral365.yml

    fun start(port: Int) {
        spark.Spark.port(port)

        if( config.inDevelopment )
            // With --devel flag, we read API specs from directory. This allows changing files while the program is running
            spark.Spark.staticFiles.externalLocation("src/main/resources/swagger")
        else
            spark.Spark.staticFiles.location("swagger")

        handleCORS()

        // TODO: Always gzip responses
        // spark.Spark.after("*", fun(_: Request, rsp: Response) = rsp.header("Content-Encoding", "gzip"))

        // development-time call to initiate dev functionality. Remove it.
        //spark.Spark.get("$API_BASE/devcall", fun(req, rsp) = this._devcall(req, rsp))

        // ------------------------------- the API
        spark.Spark.get("$API_BASE/publicKey", fun(req, rsp) = this.getPublicKey(req, rsp))

        // --- Admin Data API: for each DAO we define GET/POST/PUT/DELETE
        val apiBaseForAdminData = "$API_BASE/admin/data"
        spark.Spark.get(apiBaseForAdminData) { _, _ -> this.listDaoEntities() }

        Meta.entities.forEach {
            val tn = Meta.tableName(it)
            spark.Spark.get   ("$apiBaseForAdminData/$tn", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, it))
            spark.Spark.post  ("$apiBaseForAdminData/$tn", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, it))
            spark.Spark.put   ("$apiBaseForAdminData/$tn", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, it))
            spark.Spark.delete("$apiBaseForAdminData/$tn", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, it))
        }

        spark.Spark.get   ("$API_BASE/catalog/roles",           fun(req, rsp) = Roles.listRoles(req, rsp))
        spark.Spark.post  ("$API_BASE/catalog/roles",           fun(req, rsp) = Roles.createRole(req, rsp))
        spark.Spark.get   ("$API_BASE/catalog/roles/:roleName", fun(req, rsp) = Roles.listRoles(req, rsp))
        spark.Spark.post  ("$API_BASE/catalog/roles/:roleName", fun(req, rsp) = Roles.updateRole(req, rsp))
        spark.Spark.delete("$API_BASE/catalog/roles/:roleName", fun(req, rsp) = Roles.deleteRole(req, rsp))

        spark.Spark.get("$API_BASE/catalog/assets",                            fun(req, rsp) = Assets.listAssets(req, rsp))
        spark.Spark.get("$API_BASE/catalog/assets/:assetKey",                  fun(req, rsp) = Assets.getAssetById(req, rsp))
        spark.Spark.get("$API_BASE/catalog/assets/:assetKey/roles",            fun(req, rsp) = Assets.listAssetRoles(req, rsp))
        spark.Spark.get("$API_BASE/catalog/assets/:assetKey/roles/:roleName",  fun(req, rsp) = Assets.getAssetByIdAndRole(req, rsp))

        spark.Spark.post  ("$API_BASE/catalog/assets",                           fun(req, rsp) = Assets.createAsset(req, rsp))
        spark.Spark.post  ("$API_BASE/catalog/assets/:assetKey",                 fun(req, rsp) = Assets.updateAsset(req, rsp))
        spark.Spark.post  ("$API_BASE/catalog/assets/:assetKey/roles",           fun(req, rsp) = Assets.addAssetRole(req, rsp))
        spark.Spark.post  ("$API_BASE/catalog/assets/:assetKey/roles/:roleName", fun(req, rsp) = Assets.updateAssetRole(req, rsp))

        spark.Spark.delete("$API_BASE/catalog/assets/:assetKey",                 fun(req, rsp) = Assets.deleteAsset(req, rsp))
        spark.Spark.delete("$API_BASE/catalog/assets/:assetKey/roles",           fun(req, rsp) = Assets.deleteAssetRoles(req, rsp))
        spark.Spark.delete("$API_BASE/catalog/assets/:assetKey/roles/:roleName", fun(req, rsp) = Assets.deleteAssetRole(req, rsp))

        spark.Spark.get ("$API_BASE/executes",                   fun(req, rsp) = Execute.list(req, rsp))
        spark.Spark.post("$API_BASE/executes",                   fun(req, rsp) = Execute.start(req, rsp))
        spark.Spark.get ("$API_BASE/executes/:executeId",        fun(req, rsp) = Execute.getInfo(req, rsp))
        spark.Spark.get ("$API_BASE/executes/:executeId/log",    fun(req, rsp) = Execute.getLog(req, rsp))
        spark.Spark.get ("$API_BASE/executes/:executeId/output", fun(req, rsp) = Execute.getOutput(req, rsp))

        spark.Spark.get ("$API_BASE/tasks",                   fun(req, rsp) = Tasks.list(req, rsp))
        spark.Spark.post("$API_BASE/tasks",                   fun(req, rsp) = Tasks.submit(req, rsp))
    }

    private fun handleCORS() {
        spark.Spark.after("*", fun(_: Request, rsp: Response) = rsp.header("Access-Control-Allow-Origin", "*"))

        // pre-flight
        spark.Spark.options("*", fun(req: Request, rsp: Response) {
            fun copyHeaderBack(header: String) {
                val headerVal = req.headers(header)
                if( !headerVal.isNullOrBlank() )
                    rsp.header(header, headerVal)
            }

            copyHeaderBack("Access-Control-Request-Method")
            copyHeaderBack("Access-Control-Request-Headers")
        })
    }

    @VisibleForTesting
    internal fun getPublicKey(req: Request, rsp: Response): String {
        logger.info { "getPublicKey from ${req.ip()}" }
        rsp.type("text/plain")
        return getFileOrResource(config.sshPublicKeyFile).toString(Charsets.US_ASCII)  // NB: not the configured charset. ASCII.
    }


    @VisibleForTesting
    internal fun listDaoEntities() = gson.toJson(Meta.entities.map { Meta.tableName(it) })

    @VisibleForTesting
    internal fun restCallForPersistentObject(req: Request, rsp: Response, entityClass: KClass<out Entity>): String {
        rsp.type("application/json")
        val method = requestMethod("restCallForPersistentObject", req)
        if( method.isEmpty() )
            return formatResponse(rsp, 400, "no HTTP request method")

        try {
            val paramMap = combineRequestParams(req)
            val inputInstance: Entity = entityClass.primaryConstructor!!.call()
            inputInstance.assignFrom(paramMap)

            when(method) {
                "GET" -> {
                    val limit = paramMap.getOrDefault("limit", "0").toInt()
                    val defs = databaseStore.fetchRowsAsObjects(inputInstance, limit = limit)
                    logger.info { "get[${inputInstance.tableName}]: returning ${defs.size} items" }
                    return toJsonArray(defs)
                }

                "PUT", "POST" -> {
                    val msg = databaseStore.mergeObjectAsRow(inputInstance)
                    return formatResponse(rsp, msg)
                }

                "DELETE" -> {
                    val msg = databaseStore.deleteObjectRow(inputInstance)
                    return formatResponse(rsp, msg)
                }

                else ->
                    return formatResponse(rsp, 405, "request method $method is unsupported, valid methods are GET, PUT, POST, and DELETE")
            }

        } catch(x: Exception) {
            return formatResponse(rsp, x)
        }
    }


    @VisibleForTesting
    internal fun restCallForRoles(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val method = requestMethod("restCallForRoles", req)
        if( method.isEmpty() )
            return formatResponse(rsp, 400, "no HTTP request method")

        var conn: Connection? = null
        try {
            val paramMap = combineRequestParams(req)
            val role = Role()
            role.assignFrom(paramMap)

            when(method) {
                "GET" -> {
                    val limit = paramMap.getOrDefault("limit", "0").toInt()
                    val justnames = paramMap.getOrDefault("justnames", "false").toBoolean()

                    val selStmt = SelectStatement(role).byPresentValues()
                    if( justnames ) selStmt.select(role::roleName)
                    else            selStmt.select(role.allCols)

                    val fetchLimit = if( limit > 0 ) limit else Int.MAX_VALUE
                    conn = databaseStore.getGoodConnection()
                    val defs = selStmt.iterate(conn).asSequence().take(fetchLimit).toList()
                    logger.info { "get[${role.tableName}]: returning ${defs.size} items" }
                    return toJsonArray(defs, if( justnames ) "name" else null)
                }

                else ->
                    return formatResponse(rsp, 405, "request method $method is unsupported, the valid methods are: GET")
            }

        } catch(x: Exception) {
            return formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }

    }

    @VisibleForTesting
    internal fun restCallForRole(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val method = requestMethod("restCallForRole", req)
        if( method.isEmpty() )
            return formatResponse(rsp, 400, "no HTTP request method")

        var conn: Connection? = null
        try {
            val paramMap = combineRequestParams(req)
            val role = Role()
            role.roleName = paramMap["role_name"]
            conn = databaseStore.getGoodConnection()
            val recs: Int = SelectStatement(role).select(role.allCols).byPk().run(conn)
            closeIfCan(conn); conn = null

            when(method) {
                "GET" -> return role.asJsonStr()

                "PUT", "POST" -> {
                    val msg = databaseStore.mergeObjectAsRow(role)
                    return formatResponse(rsp, msg)
                }

                "DELETE" -> {
                    val msg = databaseStore.deleteObjectRow(role)
                    return formatResponse(rsp, msg)
                }

                else ->
                    return formatResponse(rsp, 405, "request method $method is unsupported, the valid methods are GET, PUT, POST, DELETE")
            }

        } catch(x: Exception) {
            return formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }
    }

/*
    internal fun _devcall(req: Request, rsp: Response): String {
        logger.debug { "devcall from ${req.ip()}" }
        rsp.type("text/plain")
        _devcall()
        return "Da nada"
    }
*/
}
