package com.amcentral365.service

import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.service.dao.Meta
import com.amcentral365.service.dao.Role
import spark.Request
import spark.Response

import com.google.common.io.Resources
import mu.KLogging

import com.google.common.annotations.VisibleForTesting
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor


class WebServer {
    companion object: KLogging()

    private val API_BASE = "/v0.1"   // must match servers.url in src/main/resources/swagger/amcentral365.yaml

    fun start(port: Int) {
        spark.Spark.port(port)

        if( config.devel )
            // With --devel flag, we read API specs from directory. This allows changing files while the program is running
            spark.Spark.staticFiles.externalLocation("src/main/resources/swagger");
        else
            spark.Spark.staticFiles.location("swagger")

        handleCORS()

        spark.Spark.get("$API_BASE/publicKey",   fun(req, rsp) = this.getPublicKey(req, rsp))

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

        spark.Spark.get("$API_BASE/catalog/roles", fun(req, rsp) = this.restCallForRoles(req, rsp))
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
        return Resources.toString(Resources.getResource("ssh-key.pub"), Charsets.US_ASCII)
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
                    return toJsonStr(defs)
                }

                "PUT", "POST" -> {
                    val msg = databaseStore.mergeObjectAsRow(inputInstance)
                    return if( msg.isOk ) formatJsonResponse(rsp, msg) else formatResponse(rsp, msg)
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
        try {
            val paramMap = combineRequestParams(req)
            val role = Role()
            role.assignFrom(paramMap)

            when(method) {
                "GET" -> {
                    val limit = paramMap.getOrDefault("limit", "0").toInt()
                    val defs = databaseStore.fetchRowsAsObjects(role, limit = limit)
                    logger.info { "get[${role.tableName}]: returning ${defs.size} items" }
                    return toJsonStr(defs)
                }

                "HEAD"        -> return formatResponse(rsp, 501, "Coming soon")
                "PUT", "POST" -> return formatResponse(rsp, 501, "Coming soon")

                else ->
                    return formatResponse(rsp, 405, "request method $method is unsupported, valid methods are HEAD, GET, PUT, POST")
            }

        } catch(x: Exception) {
            return formatResponse(rsp, x)
        }

    }
}
