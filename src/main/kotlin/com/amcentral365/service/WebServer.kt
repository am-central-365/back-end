package com.amcentral365.service

import com.amcentral365.pl4kotlin.Entity
import spark.Request
import spark.Response

import com.google.common.io.Resources
import mu.KLogging


class WebServer {
    companion object: KLogging()

    private val API_BASE = "/v0.1"   // must match servers.url in src/main/resources/swagger/amcentral365.yaml

    fun start(port: Int) {
        spark.Spark.port(port)

        spark.Spark.staticFiles.location("swagger")

        handleCORS()

      //spark.Spark.get(API_BASE+"/publicKey-java",  fun(_,_) = SomeJavaClass.getPublicKey())
        spark.Spark.get(API_BASE+"/publicKey",   fun(req, rsp) = this.getPublicKey(req, rsp))
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

    internal fun getPublicKey(req: Request, rsp: Response): String {
        logger.info { "getPublicKey from ${req.ip()}" }
        rsp.type("text/plain")
        return Resources.toString(Resources.getResource("ssh-key.pub"), Charsets.US_ASCII)
    }

    private fun requestMethod(caller: String, req: Request): String {
        var method = req.requestMethod()
        logger.debug { "$caller: http request method is $method" }

        val m2 = req.queryParams("_method")
        if( m2 != null ) {
            method = m2
            logger.debug( "$caller: the real http request method is $method")
        }

        return method
    }

    private fun formatResponse(rsp: Response, code: Int, message: String): String {
        rsp.status(code)
        if( code == StatusMessage.OK.code )
            logger.debug { "code: $code, messsae: $message" }
        else
            logger.error { "$code: $message" }
        return "{\"code\": $code, \"message\": \"$message\"}"
    }


    fun restCallForAPersistentObject(req: Request, rsp: Response, entity: Entity): String {
        rsp.type("application/json")
        val method = this.requestMethod("restCallForAPersistentObject", req)
        if (method.isEmpty())
            return formatResponse(rsp, 400, "no HTTP request method")

        return ""
    }
}
