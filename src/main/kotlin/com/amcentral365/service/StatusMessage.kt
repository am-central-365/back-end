package com.amcentral365.service

import com.google.common.collect.ImmutableMap
import java.util.HashMap

import com.google.gson.JsonObject


internal class StatusMessage(val code: Int, val msg: String) {

    companion object {
        val OK = StatusMessage(200, "ok")
    }

    constructor(x: Throwable):
        this((x as? StatusException)?.code ?: 500, x.message ?: "")

    constructor(x: Throwable, prefixMsg: String):
        this((x as? StatusException)?.code ?: 500, "$prefixMsg ${x.javaClass.name} ${x.message}")

    val isOk: Boolean get() = this.code == StatusMessage.OK.code
}


class StatusException : Exception {
    internal var code: Int = 0
    private  var attr: ImmutableMap<String, String>? = null

    private fun getCode(x: Exception) = (x as? StatusException)?.code ?: 500
    private fun setCause(x: Exception) { if( this.cause == null ) this.initCause(x) }


    internal constructor(x: Exception) : super(x) { this.code = this.getCode(x);  this.setCause(x) }
    internal constructor(x: Exception, attr: ImmutableMap<String, String>) : this(x) { this.attr = attr }

    internal constructor(code: Int, msg: String) : super(msg) { this.code = code }
    internal constructor(code: Int, attr: ImmutableMap<String, String>, msg: String): this(code, msg) { this.attr = attr }


    private fun merge(otherAttr: ImmutableMap<String, String>): StatusException {
        if( this.attr == null )
            this.attr = otherAttr
        else {
            val m = HashMap(this.attr)
            m.putAll(otherAttr)
            this.attr = ImmutableMap.copyOf(m)
        }
        return this
    }

    companion object {
        internal fun from(code: Int, msg: String) = StatusException(code, msg)
        internal fun from(code: Int, attr: ImmutableMap<String, String>, msg: String) = StatusException(code, attr, msg)
        internal fun from(x: Exception) = x as? StatusException ?: StatusException(x)
        internal fun from(x: Exception, attr: ImmutableMap<String, String>) =
            (x as? StatusException)?.merge(attr)
                ?: StatusException(x, attr)  // Side effect: modifies x. But we don't care.

        internal fun asJsonObject(x: Throwable): JsonObject {
            val js = JsonObject()
            js.addProperty("message", x.message)

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