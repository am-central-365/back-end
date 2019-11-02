package com.amcentral365.service

import mu.KotlinLogging
import kotlin.reflect.jvm.jvmName
import java.util.concurrent.TimeUnit

import com.amcentral365.service.builtins.roles.ExecutionTarget
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.builtins.roles.TargetSSH
import com.google.common.base.Preconditions

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session

import java.io.InputStream
import java.io.OutputStream

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


    var session: Session? = null

    override fun connect(): Boolean {
        try {
            val sess = jsch.getSession(target.loginUser, target.hostname, target.port!!)
            sess.connect()
            this.session = sess
            return true
        } catch(e: JSchException) {
            throw StatusException.from(e)
        }
    }

    override fun disconnect() {
        this.session?.disconnect()
    }

    private fun copy0(contentStream: InputStream, fileName: String, remoteCmd: List<String>): Long {
        Preconditions.checkArgument(fileName.isNotBlank())

        val outputStream = StringOutputStream()
        val statusMsg = realExec(remoteCmd, contentStream, outputStream)
        val errMsg = outputStream.getString().trimEnd('\r', '\n')
        if( statusMsg.code != 0 || errMsg.isNotEmpty() )
            throw StatusException(300, "failed to copy file $fileName: ${statusMsg.code} ${statusMsg.msg} -- $errMsg")

        return 0
    }

    override fun copyExecutableFile(contentStream: InputStream, fileName: String): Long =
        this.copy0(contentStream, fileName, getCmdToCreateExecutable(fileName))

    override fun copyFile(contentStream: InputStream, fileName: String): Long =
        this.copy0(contentStream, fileName, getCmdToCreateFile(fileName))

    override fun createDirectories(dirPath: String) {
        Preconditions.checkArgument(dirPath.isNotBlank())
        val cmd = getCmdToCreateSubDir(dirPath)
        val outputStream = StringOutputStream()
        val statusMsg = realExec(cmd, null, outputStream)
        val errMsg = outputStream.getString().trimEnd('\r', '\n')
        if( statusMsg.code != 0 || errMsg.isNotEmpty() )
            throw StatusException(300, "failed to create directory path $dirPath: ${statusMsg.code} ${statusMsg.msg} -- $errMsg")
    }

    override fun exists(pathStr: String): Boolean {
        Preconditions.checkArgument(pathStr.isNotBlank())
        val cmd = getCmdToVerifyFileExists(pathStr)
        val statusMsg = realExec(cmd, null, NullOutputStream())
        return statusMsg.code == 0
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

            val remoteStdout = channel.inputStream        // must cache, otherwise it won't work
            channel.connect()

            logger.info { "${this.threadId}: started running $command" }

            inputStream?.run {
                channel.outputStream.run {  // process.outputStream is the process's stdin
                    write(inputStream.readBytes())
                    close()
                }
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
