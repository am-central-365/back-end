package com.amcentral365.service

import com.amcentral365.service.builtins.roles.Script
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Exception
import java.nio.file.Paths
import java.security.SecureRandom
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

class TransferManager(private val threadId: String) {
    data class Item(
            val pathStr:      String,
            val inputStream:  InputStream? = null,
            val isDirectory:  Boolean = false,
            val verifyPathExists: Boolean = false
    )

    abstract class Sender {
        open fun begin() {}
        open fun end(successful: Boolean) {}

        abstract fun getIterator(): Iterator<Item>
    }

    abstract class Receiver(val script: Script) {
        open fun begin() {}
        open fun end(successful: Boolean) {}

        abstract fun apply(item: Item)
    }

    private fun safeExec(blockName: String, block: () -> Unit): Boolean {
        try {
            block()
        } catch(x: Exception) {
            logger.warn { "$threadId: ignoring failed $blockName: ${x::class.qualifiedName}, ${x.message}" }
            return false
        }
        return true
    }

    fun transfer(sender: Sender, receiver: Receiver): Boolean {
        if( !safeExec("sender.begin()") { sender.begin() } )
            return false

        if( !safeExec("receiver.begin()") { receiver.begin() } ) {
            safeExec("sender.end(false)") { sender.end(false) }
            return false
        }

        var success = true
        try {
            sender.getIterator().forEach { item ->
                receiver.apply(item)
            }

        } catch(x: Exception) {
            logger.warn(x) { "$threadId: ${x::class.qualifiedName}, ${x.message}" }
            success = false
            throw StatusException(x)
        } finally {
            safeExec("receiver.end()") { receiver.end(success) }
            safeExec("sender.end()") { sender.end(success) }
        }

        return success
    }

}


class SenderOfMain: TransferManager.Sender() {
    override fun getIterator(): Iterator<TransferManager.Item> = emptyList<TransferManager.Item>().iterator()
}


class SenderOfInlineContent(private val content: String): TransferManager.Sender() {
    override fun getIterator(): Iterator<TransferManager.Item> = listOf(
            TransferManager.Item(pathStr = "", inputStream = ByteArrayInputStream(content.toByteArray(config.charSet)))
        ).iterator()
}


class SenderOfFileSystemPath(private val pathStr: String): TransferManager.Sender() {
    override fun getIterator(): Iterator<TransferManager.Item> = listOf(
            TransferManager.Item(pathStr = this.pathStr, verifyPathExists = true)
        ).iterator()
}


class ReceiverLocalhost(script: Script): TransferManager.Receiver(script) {
    private var fileCount = 0

    private fun copy(inputStream:  InputStream, outputFile: File): Long {
            FileOutputStream(outputFile).use { outputStream ->
                val copiedByteCount = inputStream.copyTo(outputStream)
                logger.debug { "wrote $copiedByteCount to ${outputFile.path}" }
                return copiedByteCount
            }
    }

    override fun apply(item: TransferManager.Item) {
        val baseDirFile = File(config.localScriptExecBaseDir)

        if( item.verifyPathExists ) {
            if( !File(item.pathStr).exists() )
                throw StatusException(404, "Path ${item.pathStr} does not exist on the host")

        // When there is no path, we read inputStream into inline content
        } else if( item.pathStr.isBlank() ) {
            if( script.hasMain )
                throw StatusException(412, "Ambiguity: the script both defines main and has inline content")
            if( item.inputStream == null )
                throw StatusException(412, "The path is empty and there is no input")
            val contentFile = File.createTempFile("amc_", "", baseDirFile)
            contentFile.deleteOnExit()
            this.copy(item.inputStream, contentFile)
            this.script.assignMain(contentFile.name)

        // When path is a directory, create it and all parent directories on the way
        } else if( item.isDirectory ) {
            if( item.pathStr.isBlank() )
                throw StatusException(412, "Can't create an empty directory")

            val dirToCreate = File(item.pathStr)
            if( dirToCreate.isAbsolute )
                throw StatusException(412, "Absolute paths are not allowed: ${item.pathStr}")

            val dir = baseDirFile.resolve(dirToCreate)
            if( !dir.exists() ) {
                logger.debug { "Creating directory path ${dir.path}" }
                if( !dir.mkdirs() )
                    throw StatusException(500, "Failed to create directory path $dir")
            }

        // Create a file and write inputStream into it
        } else {
            if( item.pathStr.isBlank() )
                throw StatusException(400, "There is no path to the destination file")
            if( item.inputStream == null )
                throw StatusException(400, "The input stream is required, got null")

            if( !this.script.hasMain ) {
                if( this.fileCount > 0 )
                    throw StatusException(400, "Ambiguity: the script has more than one file and no 'main' defined")
                this.script.assignMain(item.pathStr)
            }

            val relFile = File(item.pathStr)
            if( relFile.isAbsolute )
                throw StatusException(412, "Absolute paths are not allowed: ${item.pathStr}")

            val file = baseDirFile.resolve(relFile)
            logger.debug { "writing to ${file.path}" }
            this.copy(item.inputStream, file)
        }
    }
}


class ReceiverRemotehost(script: Script, private val targetHost: ExecutionTargetSSHHost): TransferManager.Receiver(script) {
    private var fileCount = 0

    companion object {
        private val rnd = SecureRandom()
        fun genTempFileName(prefix: String, suffix: String): String {
            var middle = rnd.nextLong()
            if( middle == Long.MIN_VALUE )
                middle = 0
            return prefix + abs(middle) + suffix
        }
    }

    override fun apply(item: TransferManager.Item) {
        val baseDirFile = targetHost.baseDir?.let { File(it) }

        if( item.verifyPathExists ) {
            if( !this.targetHost.exists(item.pathStr) )
                throw StatusException(404, "Path ${item.pathStr} does not exist on the host")

        // When path is a directory, create it and all parent directories on the way
        } else if( item.isDirectory ) {
            if( baseDirFile == null )
                throw StatusException(500, "The target host does not define base directory")

            if( item.pathStr.isBlank() )
                throw StatusException(412, "Can't create an empty directory")

            val dirToCreate = File(item.pathStr)
            if( dirToCreate.isAbsolute )
                throw StatusException(412, "Absolute paths are not allowed: ${item.pathStr}")

            val dir = baseDirFile.resolve(dirToCreate)
            logger.debug { "Creating directory path ${dir.path}" }
            this.targetHost.createDirectories(dir.path)

        // When there is no path, we read inputStream into inline content
        } else if( item.pathStr.isBlank() ) {
            if( this.fileCount > 0 )
                throw StatusException(412, "Ambiguity: the script defines both inline and regular files")
            if( script.hasMain )
                throw StatusException(412, "Ambiguity: the script defines both main and the inline content")
            if( item.inputStream == null )
                throw StatusException(412, "The path is empty and there is no input")
            var contentFileName = genTempFileName("amc_", "")
            contentFileName = Paths.get(targetHost.baseDir!!, contentFileName).toString()
            targetHost.copyExecutableFile(item.inputStream, contentFileName)
            this.script.assignMain(contentFileName)

        // Create a file and write inputStream into it
        } else {
            if( baseDirFile == null )
                throw StatusException(500, "The target host does not define the base directory")

            if( item.inputStream == null )
                throw StatusException(400, "The input stream is required, got null")

            if( !this.script.hasMain ) {
                if( this.fileCount > 0 )
                    throw StatusException(400, "Ambiguity: the script has more than one file and no 'main' defined")
            }

            val relFile = File(item.pathStr)
            if( relFile.isAbsolute )
                throw StatusException(412, "Absolute paths are not allowed: ${item.pathStr}")

            val file = baseDirFile.resolve(relFile)
            logger.debug { "writing ${file.path}" }

            targetHost.copyFile(item.inputStream, file.name)
            logger.debug { "done" }
            this.fileCount++
        }
    }
}
