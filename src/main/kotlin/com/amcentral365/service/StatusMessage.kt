package com.amcentral365.service

import com.google.gson.JsonObject


class StatusMessage(val code: Int, val msg: String) {

    companion object {
        val OK = StatusMessage(200, "ok")
    }

    constructor(x: Throwable, code: Int=500):
        this((x as? StatusException)?.code ?: code, x.message ?: "")

    constructor(x: Throwable, prefixMsg: String, code: Int=500):
        this((x as? StatusException)?.code ?: code, "$prefixMsg ${x.javaClass.name} ${x.message}")

    val isOk: Boolean get() = this.code >= 200 && this.code < 300
}


class StatusException : Exception {
    internal var code: Int = 0
    private  var attr: Map<String, String>? = null

    private fun getCode(x: Exception) = (x as? StatusException)?.code ?: 500
    private fun setCause(x: Exception) { if( this.cause == null ) this.initCause(x) }

    internal constructor(x: Exception) : super(x) { this.code = this.getCode(x);  this.setCause(x) }
    internal constructor(x: Exception, code: Int) : super(x) { this.code = code;  this.setCause(x) }
    internal constructor(x: Exception, attr: Map<String, String>) : this(x) { this.attr = attr }

             constructor(code: Int, msg: String) : super(msg) { this.code = code }
    internal constructor(code: Int, attr: Map<String, String>, msg: String): this(code, msg) { this.attr = attr }

    private fun merge(otherAttr: Map<String, String>): StatusException {
        if( this.attr == null )
            this.attr = otherAttr
        else {
            val m = HashMap(this.attr)
            m.putAll(otherAttr)
            this.attr = HashMap<String, String>(m)
        }
        return this
    }

    companion object {
        internal fun from(code: Int, msg: String) = StatusException(code, msg)
        internal fun from(code: Int, attr: Map<String, String>, msg: String) = StatusException(code, attr, msg)
        internal fun from(x: Exception) = x as? StatusException ?: StatusException(x)
        internal fun from(x: Exception, attr: Map<String, String>) =
            (x as? StatusException)?.merge(attr)
                ?: StatusException(x, attr)  // Side effect: modifies x. But we don't care.

        internal fun asJsonObject(x: Throwable): JsonObject {
            val js = JsonObject()
            js.addProperty("message", x.message ?: x.javaClass.name)

            if( x is StatusException ) {
                js.addProperty("code", x.code)
                if( x.attr != null )
                    x.attr!!.forEach{ js.addProperty(it.key, it.value) }
            } else {
                js.addProperty("code", 500)
            }

            if( x.cause != null )
                js.add("cause", StatusException.asJsonObject(x.cause!!))

            return js
        }
    }
}
