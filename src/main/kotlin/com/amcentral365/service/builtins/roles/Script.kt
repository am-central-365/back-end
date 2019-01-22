package com.amcentral365.service.builtins.roles

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetRoleValues
import com.amcentral365.service.databaseStore
import com.google.gson.GsonBuilder
import java.sql.Connection
import java.util.UUID

/*
   Roles:
    {
      "role_name": "script-main",
      "class": "script",
      "description": "Defines script invocation details",
      "role_schema": {
        "main":        "string",
        "interpreter": "string",
        "sudo_as":     "string",
        "workdir":     "string",
        "exec_timeout_sec": "number",
        "idle_timeout_sec": "number"
      }
    }
*/

data class ScriptMain(
    val main:        String? = null,
    val interpreter: String? = null,
    val sudo_as:     String? = null,
    val workdir:     String? = null,
    val execTimeoutSec: Long? = null,
    val idleTimeoutSec: Long? = null
) {
    constructor(): this(null)
}

/*
    {
          "role_name": "script-location",
          "class": "location",
          "description": "Where the script files are located. Only one attribute must be present",
          "role_schema": {
            "content": {
               "body":    "string!",
               "version": "string!"
             },
            "githubUrl":       "string",
            "fileSystemPath":  "string",
            "nexusUrl":        "string"
          }
    }
*/

// NB: the values are mutually exclusive. One of the values must be present.
class ScriptLocation
{
    val content:        Content? = null;
    val githubUrl:      String?  = null;
    val fileSystemPath: String?  = null;
    val nexusUrl:       String?  = null // follows 307 redirects, see https://repository.sonatype.org/nexus-restlet1x-plugin/default/docs/path__artifact_maven_redirect.html
    val nexusUnpack:    Boolean? = null

    data class Content(var body: String, var version: String) { constructor():this("", "1.0.0") }

    val needsWorkDir = content == null && fileSystemPath == null
/*
    init {
        if( content != null )
            require(githubUrl == null && fileSystemPath == null && nexusUrl == null)
        else if( githubUrl != null )
            require(fileSystemPath == null && nexusUrl == null)
        else if( fileSystemPath != null )
            require(nexusUrl == null)
        else
            require(nexusUrl != null)
    }
*/
}


/*
    {
          "role_name": "script",
          "class": "script",
          "description": "Code executable on a host or another target",
          "role_schema": {
            "scriptMain":  "@script-main",
            "location":    "@script-location",
            "target-role": "string!"
          }
    }
*/

data class Script(
    var scriptMain:     ScriptMain?,
    var location:       ScriptLocation?,
    var targetRoleName: String?
) {
    var asset: Asset? = null

    val name = this.asset?.name
    val needsWorkDir = this.location?.needsWorkDir ?: false

    companion object {
        inline fun <reified T> getAssetObjectByRole(assetId: UUID, roleName: String, conn: Connection): T? {
            val dao = AssetRoleValues(assetId, roleName)
            val cnt = SelectStatement(dao).select(dao.allCols).byPk().run(conn)
            if( cnt != 1 )
                return null

            return GsonBuilder().create().fromJson<T>(dao.assetVals, T::class.java)
        }

        fun fromDB(assetId: UUID): Script? {
            val script1 = Script(null, null, null)

            databaseStore.getGoodConnection().use { conn ->
                script1.asset = Asset(assetId)
                val cnt = SelectStatement(script1.asset!!).select(script1.asset!!.allCols).byPk().run(conn)
                if( cnt != 1 )
                    return null

                val script2 = getAssetObjectByRole<Script>(assetId, "script", conn)
                if( script2 == null )
                    return null

                script1.targetRoleName = script2.targetRoleName
                script1.location = script2.location
                script1.scriptMain = script2.scriptMain
                return script1
            }

        }
    }
}

class ScriptBundleNode(
    val name: String,
    val content: String? = null,
    val children: List<ScriptBundleNode>? = null
) {

}
