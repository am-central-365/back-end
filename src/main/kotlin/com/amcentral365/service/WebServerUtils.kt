package com.amcentral365.service

import com.amcentral365.pl4kotlin.Entity
import com.google.gson.GsonBuilder
import spark.Request
import spark.Response
import java.util.TreeMap
import com.google.gson.FieldAttributes
import com.google.gson.ExclusionStrategy


class DAOExclusionStrategy constructor(private val baseTypeToSkip: Class<*>) : ExclusionStrategy {
    override fun shouldSkipClass(clazz: Class<*>): Boolean = clazz == baseTypeToSkip

    override fun shouldSkipField(f: FieldAttributes?): Boolean {
        // The most accurate method would be to check if the field has Column annotation.
        // But somehow annotations get lost on the way and always empty.
        //return f?.getAnnotation(Column::class.java) != null

        // Less accurate: field belongs to a class derived from Entity
        return f?.declaringClass?.superclass != baseTypeToSkip
    }
}

internal val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .setExclusionStrategies(DAOExclusionStrategy(Entity::class.java))
        .create()  // thread-safe

internal fun requestMethod(caller: String, req: Request): String {
    var method = req.requestMethod()
    WebServer.logger.debug { "$caller: http request method is $method" }

    // For clients not supporting PUT, PATCH, etc allow supplying the real method in the headers
    // NB: GET should never allow overrides, limit to POST only.
    if( method == "POST" && req.headers().contains("X-HTTP-Method-Override") ) {
        val m2 = req.headers("X-HTTP-Method-Override")
        if( m2 != method ) {
            method = m2
            WebServer.logger.debug( "$caller: the real http request method is $method")
        }
    }

    return method
}

internal fun quoteJsonChars(s: String) = s.replace("""["']""", "\\$1")

internal fun formatResponse(rsp: Response, msg: StatusMessage): String = formatResponse(rsp, msg.code, msg.msg)

internal fun formatResponse(rsp: Response, code: Int, message: String): String {
    if( code == StatusMessage.OK.code ) WebServer.logger.debug { "$code: $message" }
    else                                WebServer.logger.error { "$code: $message" }
    val jsonMsg = "{\"code\": $code, \"message\": \"${quoteJsonChars(message)}\"}"
    rsp.status(code)
    rsp.body(jsonMsg)
    return jsonMsg
}


internal fun formatResponse(rsp: Response, x: Throwable): String {
    WebServer.logger.warn(x) { "in WebServer" }
    val js = StatusException.asJsonObject(x)
    rsp.status(js.getAsJsonPrimitive("code").asInt)
    return gson.toJson(js)
}


internal fun combineRequestParams(req: Request): TreeMap<String, String> {
    // API converts parameter names to lowercase, we prefer mixed
    val p = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
    req.params().forEach { key, value ->
        if( value.isNotBlank() && "*" != value )
            p[(if (key.length > 1 && key[0] == ':') key.substring(1) else key).toLowerCase()] = value
    }

    req.queryParams().forEach { qpn ->
        val vals = req.queryParamsValues(qpn)
        if( vals.isNotEmpty() && vals[0].isNotBlank() )
            p[qpn.toLowerCase()] = vals[0]
    }

    return p
}


internal fun toJsonArray(lst: List<Entity>, singleItemColumnName: String? = null): String =
    lst.joinToString(", ", prefix = "[", postfix = "]") { ent ->
        if( singleItemColumnName == null )
            ent.asJsonStr()
        else
            ent.allCols.first { it.columnName == singleItemColumnName }.asJsonValue()
    }
