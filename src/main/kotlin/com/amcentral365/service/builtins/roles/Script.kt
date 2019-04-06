package com.amcentral365.service.builtins.roles

import java.util.UUID

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.SenderOfFileSystemPath
import com.amcentral365.service.SenderOfInlineContent
import com.amcentral365.service.StatusException
import com.amcentral365.service.TransferManager
import com.amcentral365.service.builtins.RoleName
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.getAssetObjectForRole
import com.amcentral365.service.dao.loadRoleObjectFromDB
import com.amcentral365.service.databaseStore
import javax.annotation.Generated

/*
   Roles:
    {
      "roleName": "script-main",
      "class": "script",
      "description": "Defines script invocation details",
      "roleSchema": {
        "main":        "string!",
        "interpreter": "string+",
        "params":      "string+",
        "sudoAs":     "string",
        "workDir":     "string",
        "exec_timeout_sec": "number",
        "idle_timeout_sec": "number"
      }
    }
*/

data class ScriptMain(
    val main:        String? = null,
    val interpreter: Array<String>? = null,   // e.g. ["python", "-u"]
    val params:      Array<String>? = null,
    val sudoAs:      String? = null,
    val workDir:     String? = null
) {
    constructor(): this(null)  // used by Gson deserializer

    /**
     * sudo -u sudoAs interpreter params
     *
     * # sudo -u root /bin/sh -c 'pwd && /usr/bin/id && echo $PATH'
     *   only idis correct
     */
    fun getCommand(): List<String> {
        if( this.main.isNullOrBlank() )
            throw StatusException(412, "script 'main' was not provided")

        val ret = mutableListOf<String>()

        if( !this.sudoAs.isNullOrBlank() )
            ret.addAll(listOf("sudo", "-u", this.sudoAs))

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
           !param.contains(' ')    -> param
            else                   -> "'$param'"
        }

    @Generated("IntelliJ")
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
        if(sudoAs != other.sudoAs) return false
        if(workDir != other.workDir) return false

        return true
    }

    @Generated("IntelliJ")
    override fun hashCode(): Int {
        var result = main?.hashCode() ?: 0
        result = 31 * result + (interpreter?.contentHashCode() ?: 0)
        result = 31 * result + (params?.contentHashCode() ?: 0)
        result = 31 * result + (sudoAs?.hashCode() ?: 0)
        result = 31 * result + (workDir?.hashCode() ?: 0)
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

data class Script(
    var scriptMain:       ScriptMain?,
    var location:         ScriptLocation?,
    var executorRoleName: String?,
    var targetRoleName:   String?,
    var execTimeoutSec:   Int? = null,
    var idleTimeoutSec:   Int? = null
): AnAsset() {

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

/*
    companion object {
        fun fromDB(assetId: UUID): Script? {
            databaseStore.getGoodConnection().use { conn ->
                val scriptAsset = Asset(assetId)

                val cnt = SelectStatement(scriptAsset).select(scriptAsset.allCols).byPk().run(conn)
                if( cnt != 1 )
                    return null

                val script = getAssetObjectForRole<Script>(assetId, RoleName.Script, conn) ?: return null
                return script
            }
        }


        //fun fromDB(asset: Asset): Script = loadRoleObjectFromDB(asset, RoleName.Script) { it.asset = asset }
    }
*/
}

class ScriptBundleNode(
    val name: String,
    val content: String? = null,
    val children: List<ScriptBundleNode>? = null
)
