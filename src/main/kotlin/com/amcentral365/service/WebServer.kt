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
        spark.Spark.get(API_BASE+"/publicKey",   fun(req, rsp) = this.getPublicKey(req, rsp))

        spark.Spark.get(API_BASE+"/admin/data/scriptStores", fun(req, rsp) = this.restCallForPersistentObject(req, rsp, ScriptStore::class))
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
            val filterInstance: Entity = entityClass.primaryConstructor!!.call()
            filterInstance.assignFrom(paramMap)

            when(method) {
                "GET" -> {
                    val defs = databaseStore.fetchRowsAsObjects(filterInstance)
                    logger.info { "get[${filterInstance.tableName}]: returning ${defs.size} items" }
                    val jsonStr = toJsonStr(defs)
                }
            }


            return "FIXME"

        } catch(x: Exception) {
            return formatResponse(rsp, x)
        }
    }
}
