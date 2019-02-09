package com.amcentral365.service.builtins.roles.amc

import com.amcentral365.service.dao.Asset
import com.google.gson.JsonObject
import kotlin.reflect.KProperty

/*
    {
      "role_name": "amc-worker-config",
      "class": "config",
      "description": "Configuration of a single AMC Worker Node. Only non-default values need to be specified.",
      "role_schema": {
        "verbosity":  "number",
        "bind-port":  "number",
        "schema-cache-size-in-nodes":  "number",
        "local-script-exec-base-dir":  "string",
        "default-script-exec-timeout-sec": "number",
        "default-script-idle-timeout-sec": "number",
        "script-output-poll-interval-msec": "number"
      }
    }

    {
      "role_name": "jdbc-connection-config",
      "class": "config",
      "description": "Parameters to connect to a generic JDBC database",
      "role_schema": {
        "user":     "string",
        "password": "string",
        "url":      "string!"
      }
    }

    {
      "role_name": "host-port",
      "class": "address",
      "description": "host or ip, and its port",
      "role_schema": {
        "host": "string!",
        "port": "number"
      }
    }

    {
      "role_name": "amc-cluster-config",
      "class": "config",
      "description": "Configuration of an AMC Cluster. Only non-default values need to be specified.",
      "role_schema": {
        "db-connection":      "@jdbc-connection-config!",
        "cluster-node-names": "@host-port+",
        "cluster-fqdn":       "@host-port",
        "charset":            "string",
        "default-worker-config": "@amc-worker-config"
      }
    }


*/
data class WorkerConfiguration(
    var verbosity: Int? = null,
    var bindPort:  Short? = null,
    var schemaCacheSizeInNodes: Long? = null,   // how many nodes the schema cache can hold
    var localScriptExecBaseDir: String? = null,
    var defaultScriptExecTimeoutSec: Int? = null,
    var defaultScriptIdleTimeoutSec: Int? = null,
    var scriptOutputPollIntervalMsec: Int? = null
)





data class Configuration(val jsonObj: JsonObject, val parent: Configuration? = null) {

    inline fun <reified T> jsonGet(obj: JsonObject, name: String): Any? =
        when(T::class.java) {
            java.lang.Integer::class -> obj.get(name)?.asInt
            java.lang.Boolean::class -> obj.get(name)?.asBoolean
            else -> obj.get(name)?.asString
        }



    private fun jsonGetInt(json: JsonObject, name: String): Int? {
        val elm = json.get(name)
        if( elm != null && elm.isJsonPrimitive && !elm.isJsonNull )
            return elm.asInt
        return null
    }

    private fun setInt(kp: KProperty<Int>, name: String) {

    }

    private val localBindPot: Int?
    val bindPort: Int get() = this.localBindPot ?: parent?.bindPort ?: 24941

    init {
        this.localBindPot = this.jsonGet<Int>(jsonObj, "bind-port") as Int?
    }


}


data class Worker(var cluster: Asset?, var workerConfig: Asset?) {
    var asset: Asset? = null


}
