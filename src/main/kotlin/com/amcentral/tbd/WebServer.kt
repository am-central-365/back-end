package com.amcentral.tbd

import spark.Request
import spark.Response

import com.google.common.io.Resources

const val DEFAULT_PORT = 16717 // 0x414D

class WebServer {

    fun start(port: Int = DEFAULT_PORT) {
        spark.Spark.port(port)

        handleCORS()

      //spark.Spark.get("/publicKey-java",  fun(_,_) = SomeJavaClass.getPublicKey())
        spark.Spark.get("/publicKey",   fun(_, rsp) = this.getPublicKey(rsp))
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

    private fun getPublicKey(rsp: Response): String {
        rsp.type("text/plain")
        return Resources.toString(Resources.getResource("ssh-key.pub"), Charsets.US_ASCII)
    }
}