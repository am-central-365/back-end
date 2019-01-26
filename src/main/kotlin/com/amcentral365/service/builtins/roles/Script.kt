package com.amcentral365.service.builtins.roles

import java.sql.Connection
import java.util.UUID
import com.google.gson.GsonBuilder

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.StatusException
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetRoleValues
import com.amcentral365.service.databaseStore

/*
   Roles:
    {
      "role_name": "script-main",
      "class": "script",
      "description": "Defines script invocation details",
      "role_schema": {
        "main":        "string!",
        "interpreter": "string+",
        "params":      "string+",
        "sudo_as":     "string",
        "workdir":     "string",
        "exec_timeout_sec": "number",
        "idle_timeout_sec": "number"
      }
    }
*/

data class ScriptMain(
    val main:        String? = null,
    val interpreter: Array<String>? = null,   // e.g. ["python", "-u"]
    val params:      Array<String>? = null,
    val sudo_as:     String? = null,
    val workdir:     String? = null,
    val execTimeoutSec: Long? = null,
    val idleTimeoutSec: Long? = null
) {
    constructor(): this(null)

    /**
     * sudo -u sudo_as interpreter params
     *
     * # sudo -u root /bin/sh -c 'pwd && /usr/bin/id && echo $PATH'
     *   only idis correct
     */
    fun getCommand(): List<String> {
        if( this.main.isNullOrBlank() )
            throw StatusException(412, "script 'main' was not provided")

        val ret = mutableListOf<String>()

        if( !this.sudo_as.isNullOrBlank() )
            ret.addAll(listOf("sudo", "-u", this.sudo_as))

        if( this.interpreter != null )
            ret.addAll(this.interpreter.map { this.quoteParam(it) })

        ret.add(this.main)
        if( this.params != null )
            ret.addAll(this.params.map { this.quoteParam(it) })

        return ret
    }

    private fun quoteParam(param: String) =
        when {
            param.startsWith('\'') -> param
            param.startsWith('"')  -> param
            !param.contains(' ')   -> param
            else -> "'$param'"
        }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(javaClass != other?.javaClass) return false

        other as ScriptMain

        if(main != other.main) return false
        if(interpreter != null) {
            if(other.interpreter == null) return false
            if(!interpreter.contentEquals(other.interpreter)) return false
        } else if(other.interpreter != null) return false
        if(params != null) {
            if(other.params == null) return false
            if(!params.contentEquals(other.params)) return false
        } else if(other.params != null) return false
        if(sudo_as != other.sudo_as) return false
        if(workdir != other.workdir) return false
        if(execTimeoutSec != other.execTimeoutSec) return false
        if(idleTimeoutSec != other.idleTimeoutSec) return false

        return true
    }

    override fun hashCode(): Int {
        var result = main?.hashCode() ?: 0
        result = 31 * result + (interpreter?.contentHashCode() ?: 0)
        result = 31 * result + (params?.contentHashCode() ?: 0)
        result = 31 * result + (sudo_as?.hashCode() ?: 0)
        result = 31 * result + (workdir?.hashCode() ?: 0)
        result = 31 * result + (execTimeoutSec?.hashCode() ?: 0)
        result = 31 * result + (idleTimeoutSec?.hashCode() ?: 0)
        return result
    }
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
    val content:        Content? = null
    val fileSystemPath: String?  = null
    val githubUrl:      String?  = null
    val nexusUrl:       String?  = null // follows 307 redirects, see https://repository.sonatype.org/nexus-restlet1x-plugin/default/docs/path__artifact_maven_redirect.html

    data class Content(var body: String, var version: String) { constructor():this("", "1.0.0") }

    val needsWorkDir get() = content == null && fileSystemPath == null
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

    {
      "role_name": "script-target-host",
      "class": "script-target",
      "description": "A host, capable of executing scripts",
      "role_schema": {
        "baseExecDir":  "string!"
      }
    }

*/

data class Script(
    var scriptMain:     ScriptMain?,
    var location:       ScriptLocation?,
    var targetRoleName: String?
) {
    var asset: Asset? = null

    val name get() = this.asset?.name
    val needsWorkDir get() = this.location?.needsWorkDir ?: false

    fun getCommand(): List<String>? = this.scriptMain?.getCommand()

    fun getSender(): TransferManager.Sender? {
        val loc = this.location ?: return null

        return when {
            loc.content != null -> {
                if( loc.content.body.isBlank() )
                    throw StatusException(415, "Script '${this.name}': content is required, but blank")
                SenderOfInlineContent(loc.content.body)
            }

            loc.fileSystemPath != null -> SenderOfFileSystemPath(loc.fileSystemPath)

            loc.nexusUrl  != null -> throw StatusException(501, "Script '${this.name}': SenderOfNexusFile is not yet implemented")  // TODO
            loc.githubUrl != null -> throw StatusException(501, "Script '${this.name}': SenderOfGitHubDir is not yet implemented")  // TODO

            else ->
                throw StatusException(415, "Script '${this.name}': the Location has no known non-null properties. This shouldn't happen.")
        }
    }


    companion object {
        private inline fun <reified T> getAssetObjectByRole(assetId: UUID, roleName: String, conn: Connection): T? {
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

                val script2 = getAssetObjectByRole<Script>(assetId, "script", conn) ?: return null

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
)