package com.amcentral365.service.builtins.roles


import com.amcentral365.service.TransferManager
import com.amcentral365.service.SenderOfMain
import com.amcentral365.service.SenderOfInlineContent
import com.amcentral365.service.SenderOfLocalPath
import com.amcentral365.service.StatusException
import javax.annotation.Generated

private fun quoteParam(param: String) =
    when {
        param.startsWith('\'') -> param
        param.startsWith('"')  -> param
       !param.contains(' ')    -> param
        else                   -> "'$param'"
    }


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
    var main:        String? = null,
    val interpreter: Array<String>? = null,   // e.g. ["python", "-u"]
    val sudoAs:      String? = null
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
            ret.addAll(this.interpreter.map { quoteParam(it) })

        ret.add(this.main!!)

        return ret
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
        if(sudoAs != other.sudoAs) return false

        return true
    }

    @Generated("IntelliJ")
    override fun hashCode(): Int {
        var result = main?.hashCode() ?: 0
        result = 31 * result + (interpreter?.contentHashCode() ?: 0)
        result = 31 * result + (sudoAs?.hashCode() ?: 0)
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

    init {
        // Ensure all are null, or only one is not null
        when {
            content        != null -> require(githubUrl == null && fileSystemPath == null && nexusUrl == null)
            githubUrl      != null -> require(fileSystemPath == null && nexusUrl == null)
            fileSystemPath != null -> require(nexusUrl == null)
            else -> Unit  // Either the last one is not null, or all are null, we're good either way
        }
    }

}

data class Script(
    var location:         ScriptLocation?,
    var scriptMain:       ScriptMain?,
    var scriptArgs:       Array<String>?,
    var targetRoleName:   String?,
    var execTimeoutSec:   Int? = null,
    var idleTimeoutSec:   Int? = null
): AnAsset(null) {

    val name get() = this.asset?.name
    val needsWorkDir get() = this.location?.needsWorkDir ?: false

    val hasMain get() = this.scriptMain?.main?.isNotBlank() == true

    fun getCommand(): List<String>? {
        val lst = mutableListOf<String>()
        lst.addAll(this.scriptMain?.getCommand()!!.asIterable())
        this.scriptArgs?.forEach {
            lst.add(quoteParam(it))
        }
        return lst
    }

    fun getSender(): TransferManager.Sender? {
        val loc = this.location ?: return SenderOfMain()

        return when {
            loc.content != null -> {
                if( loc.content.body.isBlank() )
                    throw StatusException(415, "Script '${this.name}': content is required, but blank")
                SenderOfInlineContent(loc.content.body)
            }

            loc.fileSystemPath != null -> SenderOfLocalPath(loc.fileSystemPath)

            loc.nexusUrl  != null -> throw StatusException(501, "Script '${this.name}': SenderOfNexusFile is not yet implemented")  // TODO
            loc.githubUrl != null -> throw StatusException(501, "Script '${this.name}': SenderOfGitHubDir is not yet implemented")  // TODO

            else ->
                throw StatusException(415, "Script '${this.name}': the Location has no known non-null properties. This shouldn't happen.")
        }
    }

    fun assignMain(main: String) {
        if( scriptMain == null )
            this.scriptMain = ScriptMain(main)
        if( this.scriptMain!!.main.isNullOrBlank() )
            this.scriptMain!!.main = main
    }

    @Generated("IntelliJ")
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(javaClass != other?.javaClass) return false

        other as Script

        if(location != other.location) return false
        if(scriptMain != other.scriptMain) return false
        if(scriptArgs != null) {
            if(other.scriptArgs == null) return false
            if(!scriptArgs!!.contentEquals(other.scriptArgs!!)) return false
        } else if(other.scriptArgs != null) return false
        if(targetRoleName != other.targetRoleName) return false
        if(execTimeoutSec != other.execTimeoutSec) return false
        if(idleTimeoutSec != other.idleTimeoutSec) return false

        return true
    }

    @Generated("IntelliJ")
    override fun hashCode(): Int {
        var result = location?.hashCode() ?: 0
        result = 31 * result + (scriptMain?.hashCode() ?: 0)
        result = 31 * result + (scriptArgs?.contentHashCode() ?: 0)
        result = 31 * result + (targetRoleName?.hashCode() ?: 0)
        result = 31 * result + (execTimeoutSec ?: 0)
        result = 31 * result + (idleTimeoutSec ?: 0)
        return result
    }

}
