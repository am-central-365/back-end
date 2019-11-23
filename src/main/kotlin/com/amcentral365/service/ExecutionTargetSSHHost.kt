package com.amcentral365.service

import com.amcentral365.service.builtins.roles.ExecutionTarget
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.builtins.roles.TargetSSH
import com.google.common.base.Preconditions
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import mu.KotlinLogging
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.reflect.jvm.jvmName

private val logger = KotlinLogging.logger {}

open class ExecutionTargetSSHHost(threadId: String, private val target: TargetSSH): ExecutionTarget(threadId, target.asset) {

    init {
        Preconditions.checkNotNull(target.hostname as String)
        Preconditions.checkArgument((target.hostname as String).isNotEmpty())
        Preconditions.checkNotNull(target.port as Int)
        Preconditions.checkArgument((target.port as Int) > 0)
        Preconditions.checkNotNull(target.loginUser as String)
        Preconditions.checkArgument((target.loginUser as String).isNotEmpty())
    }

    private val jsch: JSch by lazy {
        val sshPvtKey = getFileOrResource(config.sshPrivateKeyFile)
        val sshPubKey = getFileOrResource(config.sshPublicKeyFile)
        JSch.setConfig("StrictHostKeyChecking", "no")
        JSch().also {
          it.addIdentity("internal", sshPvtKey, sshPubKey, null)
        }
    }

    private val sftpChannel: ChannelSftp by lazy {
        (this.session!!.openChannel("sftp") as ChannelSftp).also {
              it.connect()
              it.cd(this.workDirName)
        }
    }

    var session: Session? = null

    val S_IXUSR = 1 shl 6       // "u+x" for chmod

    override fun connect(): Boolean {
        try {
            val sess = jsch.getSession(target.loginUser, target.hostname, target.port!!)
            sess.connect()
            sess.timeout = 0                // TODO: make it a parameter
            this.session = sess
            return true
        } catch(e: JSchException) {
            throw StatusException.from(e)
        }
    }

    override fun disconnect() {
        this.session?.disconnect()
    }


    private fun copy0(contentStream: InputStream, fileName: String, permissionsToAdd: Int = 0): Long {
        Preconditions.checkArgument(fileName.isNotBlank())
        sftpChannel.put(contentStream, fileName)
        if( permissionsToAdd != 0) {
            val sftpATTRS = sftpChannel.stat(fileName)
            sftpChannel.chmod(sftpATTRS.permissions or permissionsToAdd, fileName)
        }

        return 0
    }

    override fun copyExecutableFile(contentStream: InputStream, fileName: String): Long =
        this.copy0(contentStream, fileName, S_IXUSR)

    override fun copyFile(contentStream: InputStream, fileName: String): Long =
        this.copy0(contentStream, fileName)

    override fun createDirectories(dirPath: String) {
        Preconditions.checkArgument(dirPath.isNotBlank())
        this.sftpChannel.mkdir(dirPath)
    }

    override fun exists(pathStr: String): Boolean {
        Preconditions.checkArgument(pathStr.isNotBlank())
        try {
            this.sftpChannel.stat(pathStr)
            return true
        } catch(x: SftpException) {
            return false
        }
    }

    override fun cleanup(script: Script) {
        if( !this.workDirName.isBlank() ) {
            val commandToRemoveWorkDir = getCmdToRemoveWorkDir()
            logger.debug { "removing work directory ${this.workDirName}: $commandToRemoveWorkDir" }
            executeAndGetOutput(commandToRemoveWorkDir)
        } else {
            throw StatusException(500, "workDirName is blank: '${this.workDirName}'")
            /*val commandToRemoveMain = this.getCmdToRemoveFile(script.scriptMain!!.main!!)
            logger.debug { "removing script ${script.scriptMain!!.main}: $commandToRemoveMain" }
            executeAndGetOutput(commandToRemoveMain)*/
        }
    }

    override fun prepare(script: Script): Boolean {
        initTargetDetails(script.targetRoleName!!)
        initWorkDir()
        return transferScriptContent(this.threadId, script, ReceiverHost(script, this))
    }

    private fun initWorkDir() {
        val commandToCreateWorkDir = getCmdToCreateWorkDir()
        this.workDirName = this.executeAndGetOutput(commandToCreateWorkDir)
    }

    override fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream?): StatusMessage =
        super.customizeAndExecuteCommands(script, outputStream, inputStream) {
            // script.getCommand() gives us the command to run, but we need to switch to the working dir
            // targetDetails.commandToExecuteMain has a template to customize for that.
            val details = this.targetDetails    // cache to make Kotlin happy
            if( details?.commandToExecuteMain == null || details.commandToExecuteMain.isEmpty() )
                throw StatusException(501, "the script's target role (targetRoleName) does not define 'commandToExecuteMain'")
            val cmds = mutableListOf<String>()
            details.commandToExecuteMain.forEach { detail ->
                if( detail == "<commands>" )
                    cmds.addAll(script.getCommand()!!)
                else
                    cmds.add(detail.replace("\$WorkDir", this.workDirName))
            }
            cmds
        }

    /**
     * Execute a command on the remote host and stream the output
     *
     * @return unlike in other functions, [StatusMessage.code] represents rc from the process
     */
    override fun realExec(commands: List<String>, inputStream: InputStream?, outputStream: OutputStream): StatusMessage {
        val workDirName = this.workDirName ?: config.SystemTempDirName
        logger.debug { "${this.threadId}: workdir $workDirName" }

        val session = this.session!!
        val command = commands.joinToString(" ")

        try {
            val channel = session.openChannel("exec")
            with(channel as ChannelExec) {
                setInputStream(inputStream, false)   // close on completion
                setOutputStream(outputStream, true)
                setErrStream(outputStream, true)
                setCommand(command)
            }

            val remoteStdout = channel.inputStream        // must cache, it returns a new object each time
            channel.connect()

            logger.info { "${this.threadId}: started running $command" }

            inputStream?.run {
                val remoteStdin = channel.outputStream
                val copied = this.copyTo(remoteStdin)
                logger.debug { "$threadId: copied $copied bytes to the remote stdin" }
                remoteStdin.close()
/*
                val bytes = inputStream.readBytes()
                logger.debug { "$threadId: read ${bytes.size} bytes" }
                // channel.outputStream
                //val remoteStdin = channel.outputStream
                remoteStdin.run {  // process.outputStream is the process's stdin
                    write(bytes)
                    close()
                }
*/
            }

            fun ivlText(ts: Long) = "%.1f".format((System.currentTimeMillis() - ts)/1000f)

            var isAlive = true
            var exitReason = "execution completed"

            val buffer = ByteArray(1024)
            val execStartTs = System.currentTimeMillis()
            var idleStartTs = System.currentTimeMillis()
            do {
                if( this.execTimeoutSec > 0 && System.currentTimeMillis() - execStartTs > this.execTimeoutSec*1000L ) {
                    exitReason = "execution time ${ivlText(execStartTs)} has exceeded timeout ${this.execTimeoutSec}"
                    break
                }

                if( this.idleTimeoutSec > 0 && System.currentTimeMillis() - idleStartTs > this.idleTimeoutSec*1000L ) {
                    exitReason = "idle time ${ivlText(idleStartTs)} has exceeded timeout ${this.idleTimeoutSec}"
                    break
                }

                // cache the values to avoid race condition when process exits while we are fetching its output
                isAlive = !channel.isClosed
                var availableByteCnt = remoteStdout.available()

                if( availableByteCnt == 0 && isAlive ) {
                    TimeUnit.MILLISECONDS.sleep(config.scriptOutputPollIntervalMsec)
                    continue
                }

                while(availableByteCnt > 0) {
                    val readByteCnt = remoteStdout.read(buffer, 0, availableByteCnt.coerceAtMost(buffer.size))
                    if(readByteCnt <= 0)
                        break

                    outputStream.write(buffer, 0, readByteCnt)
                    outputStream.flush()
                    availableByteCnt -= readByteCnt
                    idleStartTs = System.currentTimeMillis()
                }

            } while( isAlive )

            if( isAlive ) {
                val msg = "aborted: $exitReason"
                logger.warn { "${this.threadId}: $msg" }
                channel.disconnect()
                return StatusMessage(408, msg)
            }

            val rc = channel.exitStatus
            val msg = "completed with return code $rc in ${ivlText(execStartTs)} sec"
            logger.info { "${this.threadId}: $msg" }
            return StatusMessage(rc, msg)

        } catch(x: Exception) {
            logger.warn { "${this.threadId}: ${x::class.jvmName} ${x.message}" }
            throw StatusException(x, 500)
        }
    }
}
