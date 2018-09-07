package com.amcentral365.service

import com.amcentral365.pl4kotlin.Entity
import com.google.gson.*
import spark.Request
import spark.Response
import java.util.TreeMap


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

internal fun quoteJsonChars(s: String) = s.replace("\"", "\\\"")

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

/**
 * Combine all Request parameters into a single uber map
 *
 * Since we combine all parameters, the order we parse them is important, as the later overwrite the former.
 * - We parse the body
 * - Then the form, which looks to me same as body
 * - Then the query parameters
 * - And finally the path.
 *
 * The goal for this order is to make the target resource visible and immutable.
 * E.g. when we POST to /.../resourceId (which is pretty visible), the id can't be overwritten in the request body
 * (which is not shown in the browser).
 *
 */
internal fun combineRequestParams(req: Request, parseJsonBody: Boolean=false): TreeMap<String, String> {
    // API converts parameter names to lowercase, we prefer mixed
    val p = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)

    if( parseJsonBody ) {
        val jsonRoot = JsonParser().parse(req.body()).asJsonObject  // throws subclasses of JsonParseException
        jsonRoot.keySet().forEach { key ->
            // asString isn't working for Json children, but toString() does
            val v = jsonRoot[key]
            when {
                v.isJsonPrimitive -> p[key.toLowerCase()] = v.asString
                v.isJsonNull -> throw StatusException(400, "null values are not yet supported, check parameter '$key'")
                v.isJsonObject -> p[key.toLowerCase()] = v.toString()
                v.isJsonArray -> p[key.toLowerCase()] = v.toString()
            }
        }
    }

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
