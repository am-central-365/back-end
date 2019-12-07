package com.amcentral365.service

import com.amcentral365.service.builtins.roles.ExecutionTarget
import com.amcentral365.service.builtins.roles.Script
import com.google.common.base.Preconditions
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
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

class SenderOfLocalPath(pathStr: String): TransferManager.Sender() {
    val basePath: String

    init {
        Preconditions.checkNotNull(pathStr)
        this.basePath = pathStr
    }

    override fun getIterator(): Iterator<TransferManager.Item> {
        val baseFile = File(this.basePath)        // NB: the top directory is also walked
        val seq = baseFile.walkTopDown().map { file ->
            val relativePathStr = file.path     // already relative to the top dir
            if( file.isDirectory )
                TransferManager.Item(pathStr = relativePathStr, isDirectory = true)
            else {
                val inputStream = FileInputStream(file)
                TransferManager.Item(pathStr = relativePathStr, isDirectory = false, inputStream = inputStream)
            }
        }
        return seq.iterator()
    }
}


class ReceiverHost(script: Script, private val targetHost: ExecutionTarget): TransferManager.Receiver(script) {
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
        if( item.verifyPathExists ) {
            if( !this.targetHost.exists(item.pathStr) )
                throw StatusException(404, "Path ${item.pathStr} does not exist on the host")

        // When path is a directory, create it and all parent directories on the way
        } else if( item.isDirectory ) {
            if( item.pathStr.isBlank() )
                throw StatusException(412, "Can't create an empty directory")

            val dirToCreate = File(item.pathStr)
            if( dirToCreate.isAbsolute )
                throw StatusException(412, "Absolute paths are not allowed: ${item.pathStr}")

            logger.debug { "Creating directory path ${dirToCreate.path}" }
            this.targetHost.createDirectories(dirToCreate.path)

        // When there is no path, we read inputStream into inline content
        } else if( item.pathStr.isBlank() ) {
            if( this.fileCount > 0 )
                throw StatusException(412, "Ambiguity: the script defines both inline and regular files")
            if( script.hasMain )
                throw StatusException(412, "Ambiguity: the script defines both main and the inline content")
            if( item.inputStream == null )
                throw StatusException(412, "The path is empty and there is no input")
            val contentFileName = genTempFileName("amc_", "")
            targetHost.copyExecutableFile(item.inputStream, contentFileName)
            this.script.assignMain(contentFileName)

        // Create a file and write inputStream into it
        } else {
            if( item.inputStream == null )
                throw StatusException(400, "The input stream is required, got null")

            if( !this.script.hasMain ) {
                if( this.fileCount > 0 )
                    throw StatusException(400, "Ambiguity: the script has more than one file and no 'main' defined")
            }

            val relFile = File(item.pathStr)
            if( relFile.isAbsolute )
                throw StatusException(412, "Absolute paths are not allowed: ${item.pathStr}")

            logger.debug { "writing ${relFile.path}" }

            targetHost.copyFile(item.inputStream, relFile.path)
            logger.debug { "done" }
            this.fileCount++
        }
    }
}
