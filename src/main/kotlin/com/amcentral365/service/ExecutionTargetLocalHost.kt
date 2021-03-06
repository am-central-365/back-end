package com.amcentral365.service

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

import kotlin.reflect.jvm.jvmName
import mu.KotlinLogging

import com.amcentral365.service.builtins.roles.ExecutionTarget
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.dao.Asset
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions


private val logger = KotlinLogging.logger {}

class ExecutionTargetLocalHost(threadId: String, asset: Asset): ExecutionTarget(threadId, asset) {

    private fun copy0(inputStream:  InputStream, outputFile: File): Long {
        FileOutputStream(outputFile).use { outputStream ->
            val copiedByteCount = inputStream.copyTo(outputStream)
            outputStream.close()
            logger.debug { "wrote $copiedByteCount to ${outputFile.path}" }
            return copiedByteCount
        }
    }

    override fun exists(pathStr: String): Boolean = File(workDirName).resolve(pathStr).exists()

    override fun createDirectories(dirPath: String) {
        val dir = File(dirPath)
        val fullPath = File(workDirName).resolve(dir)
        if( !fullPath.exists() ) {
            logger.debug { "Creating directory path ${fullPath.path}" }
            if(!fullPath.mkdirs())
                throw StatusException(500, "Failed to create directory path ${fullPath.path}")
        }
    }

    override fun copyFile(contentStream: InputStream, fileName: String): Long =
            copy0(contentStream, File(workDirName).resolve(fileName))

    override fun copyExecutableFile(contentStream: InputStream, fileName: String): Long {
        val fullFilePath = File(workDirName).resolve(fileName)
        val attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr--r--"))
        val file = Files.createFile(fullFilePath.toPath(), attr).toFile()
        return copy0(contentStream, file)
    }

    override fun cleanup(script: Script) {
        if( !this.workDirName.isBlank() ) {
            logger.debug { "removing work directory ${this.workDirName}" }
            File(workDirName).deleteRecursively()
        } else {
            throw StatusException(500, "localHost: workDirName is blank: '${this.workDirName}'")
        }
    }

    override fun prepare(script: Script): Boolean {
        initWorkDir()
        return transferScriptContent(this.threadId, script, ReceiverHost(script, this))
    }

    override fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream?): StatusMessage =
        super.customizeAndExecuteCommands(script, outputStream, inputStream) { it }

    override fun connect() = true
    override fun disconnect() {}

    private fun initWorkDir() {
        val path = Files.createTempDirectory("amc.")
        this.workDirName = path.toString()
    }

    override fun realExec(commands: List<String>, inputStream: InputStream?, outputStream: OutputStream): StatusMessage {
        logger.info { "${this.threadId}: workdir ${this.workDirName}" }

        try {
            val process = ProcessBuilder()
                    .directory(File(this.workDirName))
                    .redirectErrorStream(true)
                    .command(commands)
                    .start()
            logger.info { "${this.threadId}: started running ${commands.joinToString(" ")}" }

            inputStream?.run {
                process.outputStream.run {  // process.outputStream is the process's stdin
                    write(inputStream.readBytes())
                    close()
                }
            }

            val buffer = ByteArray(1024)
            val execStartTs = System.currentTimeMillis()
            var idleStartTs = System.currentTimeMillis()

            fun ivlText(ts: Long) = "%.1f".format((System.currentTimeMillis() - ts)/1000f)

            var exitReason = "execution completed"
            var isAlive = true
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
                isAlive = process.isAlive
                var availableByteCnt = process.inputStream.available()

                if( availableByteCnt == 0 && isAlive ) {
                    TimeUnit.MILLISECONDS.sleep(config.scriptOutputPollIntervalMsec)
                    continue
                }

                while(availableByteCnt > 0) {
                    val readByteCnt = process.inputStream.read(buffer, 0, availableByteCnt.coerceAtMost(buffer.size))
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
                process.destroyForcibly()
                return StatusMessage(408, msg)
            }

            val rc = process.exitValue()
            val msg = "completed with return code $rc in ${ivlText(execStartTs)} sec"
            logger.info { "${this.threadId}: $msg" }
            return StatusMessage(rc, msg)

        } catch(x: Exception) {
            logger.warn { "${this.threadId}: ${x::class.jvmName} ${x.message}" }
            throw StatusException(x, 500)
        }
    }

}
