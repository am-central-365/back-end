package com.amcentral365.service

import com.amcentral365.pl4kotlin.Entity
import spark.Request
import spark.Response

import com.google.common.io.Resources
import mu.KLogging

import com.amcentral365.service.dao.ScriptStore
import com.google.common.annotations.VisibleForTesting
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor


class WebServer {
    companion object: KLogging()

    private val API_BASE = "/v0.1"   // must match servers.url in src/main/resources/swagger/amcentral365.yaml

    fun start(port: Int) {
        spark.Spark.port(port)

        spark.Spark.staticFiles.location("swagger")

        handleCORS()

      //spark.Spark.get(API_BASE+"/publicKey-java",  fun(_,_) = SomeJavaClass.getPublicKey())
        spark.Spark.get("$API_BASE/publicKey",   fun(req, rsp) = this.getPublicKey(req, rsp))

        spark.Spark.get   ("$API_BASE/admin/data/scriptStores", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, ScriptStore::class))
        spark.Spark.post  ("$API_BASE/admin/data/scriptStores", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, ScriptStore::class))
        spark.Spark.put   ("$API_BASE/admin/data/scriptStores", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, ScriptStore::class))
        spark.Spark.delete("$API_BASE/admin/data/scriptStores", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, ScriptStore::class))
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
    internal fun restCallForPersistentObject(req: Request, rsp: Response, entityClass: KClass<out Entity>): String {
        rsp.type("application/json")
        val method = requestMethod("restCallForPersistentObject", req)
        if (method.isEmpty())
            return formatResponse(rsp, 400, "no HTTP request method")

        try {
            val paramMap = combineRequestParams(req)
            val inputInstance: Entity = entityClass.primaryConstructor!!.call()
            inputInstance.assignFrom(paramMap)

            val msg: StatusMessage?
            when(method) {
                "GET" -> {
                    val limit = paramMap.getOrDefault("limit", "0").toInt()
                    val defs = databaseStore.fetchRowsAsObjects(inputInstance, limit = limit)
                    logger.info { "get[${inputInstance.tableName}]: returning ${defs.size} items" }
                    return toJsonStr(defs)
                }

                "PUT", "POST" ->
                    msg = databaseStore.mergeObjectAsRow(inputInstance)

                "DELETE" ->
                    msg = databaseStore.deleteObjectRow(inputInstance)

                else ->
                    return formatResponse(rsp, 405, "request method $method is unsupported, valid methods are GET, PUT, POST, and DELETE")
            }

            return formatJsonResponse(rsp, msg)

        } catch(x: Exception) {
            return formatResponse(rsp, x)
        }
    }
}
