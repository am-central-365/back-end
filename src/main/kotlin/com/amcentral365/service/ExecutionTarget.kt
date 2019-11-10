package com.amcentral365.service.builtins.roles

import com.amcentral365.service.ReceiverHost
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
import java.lang.IllegalStateException

private val logger = KotlinLogging.logger {}

abstract class ExecutionTarget(
    val threadId: String,
    asset: Asset?
): AnAsset(asset), ScriptExecutorFlow {

    protected var execTimeoutSec: Int = 0
    protected var idleTimeoutSec: Int = 0
    protected var targetDetails: ExecutionTargetDetails? = null

    protected var workDirName: String = "."
//FIXME open val baseDir get() = this.targetDetails?.workDirBase

    abstract protected fun realExec(commands: List<String>, inputStream: InputStream? = null, outputStream: OutputStream): StatusMessage
    abstract fun exists(pathStr: String): Boolean
    abstract fun createDirectories(dirPath: String)
    abstract fun copyFile(contentStream: InputStream, fileName: String): Long
    abstract fun copyExecutableFile(contentStream: InputStream, fileName: String): Long

    protected fun getCmdToCreateWorkDir(): List<String> {
        val w = this.targetDetails?.workDirBase
            ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'workDirBase'")
        return this.targetDetails?.commandToCreateWorkDir?.map { it.replace("\$WorkDirBase", w) }
            ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToCreateWorkDir'")
    }

    protected fun getCmdToRemoveWorkDir(): List<String> =
        this.targetDetails?.commandToRemoveWorkDir?.map { it.replace("\$WorkDir", this.workDirName) }
        ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToRemoveWorkDir'")


    protected fun getCmdToCreateSubDir(subDir: String): List<String> {
//TODO: remove        val w = this.targetDetails?.workDirBase ?: config.localScriptExecBaseDir   // FIXME: localScriptExecBaseDir is specific to AMC
        return this.targetDetails?.commandToCreateSubDir?.map { it.replace("\$WorkDir", this.workDirName).replace("\$SubDirName", subDir) }
            ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToCreateSubDir'")
    }

    protected fun getCmdToCreateFile(fileName: String): List<String> =
        this.targetDetails?.commandToCreateFile?.map { it.replace("\$WorkDir", this.workDirName).replace("\$fileName", fileName) }
        ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToCreateFile'")

    protected fun getCmdToCreateExecutable(fileName: String): List<String> =
        this.targetDetails?.commandToCreateExecutable?.map { it.replace("\$WorkDir", this.workDirName).replace("\$fileName", fileName) }
        ?: this.getCmdToCreateFile(fileName)

    @Deprecated("all content, even inline, is now created in a temp dir", ReplaceWith("getCmdToRemoveWorkDir"), DeprecationLevel.WARNING)
    protected fun getCmdToRemoveFile(fileName: String): List<String> =
        this.targetDetails?.commandToRemoveFile?.map { it.replace("\$WorkDir", this.workDirName).replace("\$fileName", fileName) }
        ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToRemoveFile'")

    protected fun getCmdToVerifyFileExists(fileName: String): List<String> =
        this.targetDetails?.commandToVerifyFileExists?.map { it.replace("\$WorkDir", this.workDirName).replace("\$fileName", fileName) }
        ?: throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToVerifyFileExists'")

    protected fun executeAndGetOutput(commands: List<String>, inputStream: InputStream? = null): String =
        StringOutputStream().let {
            val statusMsg = this.realExec(commands, inputStream = inputStream, outputStream = it)
            require(200 == translateRealExecMsg(statusMsg).code)
            it.getString().trimEnd('\r', '\n')
        }

    /**
     * [customizeCommands] is used to tweak script commands to switch to the work dir when running remotely.
     */
    fun customizeAndExecuteCommands(script: Script, outputStream: OutputStream, inputStream: InputStream?, customizeCommands: (List<String>) -> List<String>): StatusMessage {
        this.execTimeoutSec = script.execTimeoutSec ?: config.defaultScriptExecTimeoutSec
        this.idleTimeoutSec = script.idleTimeoutSec ?: config.defaultScriptIdleTimeoutSec

        val commands = customizeCommands(script.getCommand()!!)

        val msg = this.realExec(commands, outputStream = outputStream, inputStream = inputStream)
        return translateRealExecMsg(msg)
    }

    protected fun initTargetDetails(targetRoleName: String) {
        this.targetDetails = fromDB(this.asset!!.assetId!!, targetRoleName)
    }

    private fun translateRealExecMsg(statusMsg: StatusMessage): StatusMessage =
        when {
            statusMsg.code < 0 -> throw StatusException(500, "code ${statusMsg.code}, ${statusMsg.msg}")
            statusMsg.code in 1..255 -> throw StatusException(500, statusMsg.msg)
            else -> StatusMessage(200, statusMsg.msg)
        }

    fun transferScriptContent(threadId: String, script: Script, receiver: TransferManager.Receiver): Boolean {
        val sender = script.getSender()
        if( sender == null ) {
            logger.warn { "$threadId: script '${script.name}' has no content, nothing to do" }
            return false
        }

        logger.info { "$threadId: transferring ${script.name} file to ${this.name}" }
        val transferManager = TransferManager(threadId)
        val success = transferManager.transfer(sender, receiver)

        return success
    }

}
