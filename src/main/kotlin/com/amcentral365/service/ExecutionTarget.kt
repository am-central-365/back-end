package com.amcentral365.service.builtins.roles

import mu.KotlinLogging

import com.amcentral365.service.ScriptExecutorFlow
import com.amcentral365.service.StatusException
import com.amcentral365.service.StatusMessage
import com.amcentral365.service.StringOutputStream
import com.amcentral365.service.TransferManager
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.config
import com.amcentral365.service.dao.fromDB
import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

abstract class ExecutionTarget(
    asset: Asset?
): AnAsset(asset), ScriptExecutorFlow {

    protected var execTimeoutSec: Int = 0
    protected var idleTimeoutSec: Int = 0
    protected var workDirName: String? = null
    protected var targetDetails: ExecutionTargetDetails? = null

    val baseDir get() = this.targetDetails?.workDirBase

    abstract protected fun realExec(commands: List<String>, inputStream: InputStream? = null, outputStream: OutputStream): StatusMessage

    protected fun getCmdToCreateWorkDir(): List<String> {
        val w = this.baseDir
        if( w == null )
            throw StatusException(501, "the script's target role (targetRoleName) does not define 'workDirBase'")
        return this.targetDetails?.commandToCreateWorkDir?.map { it.replace("\$WorkDirBase", w) }
               ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToCreateWorkDir'")
    }

    protected fun getCmdToRemoveWorkDir(workDirName: String): List<String> =
        this.targetDetails?.commandToRemoveWorkDir?.map { it.replace("\$WorkDir", workDirName) }
        ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToRemoveWorkDir'")


    protected fun getCmdToCreateSubDir(subDir: String): List<String> {
        val w = this.targetDetails?.workDirBase ?: config.localScriptExecBaseDir   // FIXME: localScriptExecBaseDir is specific to AMC
        return this.targetDetails?.commandToCreateSubDir?.map { it.replace("\$WorkDirBase", w).replace("\$SubDirName", subDir) }
            ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToCreateSubDir'")

    }

    protected fun getCmdToCreateFile(fileName: String): List<String> =
        this.targetDetails?.commandToCreateFile?.map { it.replace("\$fileName", fileName) }
        ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToCreateFile'")

    protected fun getCmdToCreateExecutable(fileName: String): List<String> =
        this.targetDetails?.commandToCreateExecutable?.map { it.replace("\$fileName", fileName) }
        ?: this.getCmdToCreateFile(fileName)

    protected fun getCmdToRemoveFile(fileName: String): List<String> =
        this.targetDetails?.commandToRemoveFile?.map { it.replace("\$fileName", fileName) }
        ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToRemoveFile'")

    protected fun executeAndGetOutput(commands: List<String>, inputStream: InputStream? = null): String =
        StringOutputStream().let {
            this.realExec(commands, inputStream = inputStream, outputStream = it)
            it.getString().trimEnd('\r', '\n')
        }

    protected fun initTargetDetails(targetRoleName: String) {
        this.targetDetails = fromDB(this.asset!!.assetId!!, targetRoleName)
    }

    override fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream?): StatusMessage {
        this.execTimeoutSec = script.execTimeoutSec ?: config.defaultScriptExecTimeoutSec
        this.idleTimeoutSec = script.idleTimeoutSec ?: config.defaultScriptIdleTimeoutSec

        val commands = script.getCommand()!!
        return this.realExec(commands, outputStream = outputStream, inputStream = inputStream)
    }

    override fun cleanup(script: Script) {
        if( !this.workDirName.isNullOrBlank() ) {
            val commandToRemoveWorkDir = this.getCmdToRemoveWorkDir(this.workDirName!!)
            logger.debug { "removing work directory ${this.workDirName}: $commandToRemoveWorkDir" }
            executeAndGetOutput(commandToRemoveWorkDir)
        } else if( script.hasMain ) {
            val commandToRemoveMain = this.getCmdToRemoveFile(script.scriptMain!!.main!!)
            logger.debug { "removing script ${script.scriptMain!!.main}: $commandToRemoveMain" }
            executeAndGetOutput(commandToRemoveMain)
        }
    }

    protected fun transferScriptContent(threadId: String, script: Script, receiver: TransferManager.Receiver): Boolean {
        val sender = script.getSender()
        if( sender == null ) {
            logger.warn { "$threadId: script '${script.name}' has no content, nothing to do" }
            return false
        }

        if( script.needsWorkDir ) {
            val commandToCreateWorkDir = this.getCmdToCreateWorkDir()
            this.workDirName = this.executeAndGetOutput(commandToCreateWorkDir)
        }

        logger.info { "$threadId: transferring ${script.name} file to ${this.name}" }
        val transferManager = TransferManager(threadId)
        val success = transferManager.transfer(sender, receiver)

        return success
    }
/*
    companion object {
        @Deprecated("use the one from daoutils ", replaceWith = ReplaceWith("com.amcentral365.service.dao.fromDB"))
        fun fromDB(asset: Asset, roleName: String, clazz: Class<ExecutionTarget>): ExecutionTarget {
            databaseStore.getGoodConnection().use { conn ->
                val dao = AssetRoleValues(asset.assetId!!, roleName)
                val cnt = SelectStatement(dao).select(dao.allCols).byPk().run(conn)
                if( cnt != 1 )
                    throw StatusException(404, "Script asset ${asset.assetId} has no role 'script'")

                val obj = GsonBuilder().create().fromJson(dao.assetVals, clazz)
                obj.asset = asset
                return obj
            }
        }
    }
*/
}
