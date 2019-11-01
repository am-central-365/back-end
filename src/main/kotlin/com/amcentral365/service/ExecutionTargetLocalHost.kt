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

class ExecutionTargetLocalHost(private val threadId: String, asset: Asset): ExecutionTarget(asset) {

    private fun copy0(inputStream:  InputStream, outputFile: File): Long {
        FileOutputStream(outputFile).use { outputStream ->
            val copiedByteCount = inputStream.copyTo(outputStream)
            logger.debug { "wrote $copiedByteCount to ${outputFile.path}" }
            return copiedByteCount
        }
    }

    override fun exists(pathStr: String): Boolean = File(pathStr).exists()

    override fun createDirectories(dirPath: String) {
        val dir = File(dirPath)
        if( !dir.exists() ) {
            logger.debug { "Creating directory path ${dir.path}" }
            if(!dir.mkdirs())
                throw StatusException(500, "Failed to create directory path ${dir.path}")
        }
    }
    override fun copyFile(contentStream: InputStream, fileName: String): Long = copy0(contentStream, File(fileName))

    override fun copyExecutableFile(contentStream: InputStream, fileName: String): Long {
        val attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr--r--"))
        val file = Files.createFile(File(fileName).toPath(), attr).toFile()
        return copy0(contentStream, file)
    }


    override fun connect() = true
    override fun disconnect() {}

    override fun prepare(script: Script): Boolean =
        transferScriptContent(this.threadId, script, ReceiverHost(script, this))

    override fun realExec(commands: List<String>, inputStream: InputStream?, outputStream: OutputStream): StatusMessage {
        val workDirName = this.workDirName ?: config.SystemTempDirName
        logger.debug { "${this.threadId}: workdir $workDirName" }

        try {
            val process = ProcessBuilder()
                    .directory(File(workDirName))
                    .redirectErrorStream(true)
                    .command(commands)  // ("/bin/sh", "-c", cmd)
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
                if( this.execTimeoutSec > 0 && System.currentTimeMillis() - execStartTs < this.execTimeoutSec*1000L ) {
                    exitReason = "execution time ${ivlText(execStartTs)} has exceeded timeout ${this.execTimeoutSec}"
                    break
                }

                if( this.idleTimeoutSec > 0 && System.currentTimeMillis() - idleStartTs < this.idleTimeoutSec*1000L ) {
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
