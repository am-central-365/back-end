package com.amcentral.tbd

import spark.Request
import spark.Response

import com.google.common.io.Resources
import mu.KLogging

class WebServer {
    companion object: KLogging()

    private val DEFAULT_PORT = 16717 // 0x414D
    private val API_BASE = "/v0.1"   // must match servers.url in src/main/resources/swagger/amcentral365.yaml

    fun start(port: Int = DEFAULT_PORT) {
        spark.Spark.port(port)

        spark.Spark.staticFiles.location("swagger")

        handleCORS()

      //spark.Spark.get(API_BASE+"/publicKey-java",  fun(_,_) = SomeJavaClass.getPublicKey())
        spark.Spark.get(API_BASE+"/publicKey",   fun(req, rsp) = this.getPublicKey(req, rsp))
    }

    private fun handleCORS() {
        spark.Spark.after("*", fun(req: Request, rsp: Response) = rsp.header("Access-Control-Allow-Origin", "*"))

        // preflight
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
}
