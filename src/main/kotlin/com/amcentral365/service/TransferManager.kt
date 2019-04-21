package com.amcentral365.service

import com.amcentral365.service.builtins.roles.Script
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Exception
import java.nio.charset.Charset

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

    abstract class Receiver(val script: Script, val baseDir: String? = null) {
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
            logger.warn { "$threadId: ${x::class.qualifiedName}, ${x.message}" }
            success = false
            throw StatusException(x)
        } finally {
            safeExec("receiver.end()") { receiver.end(success) }
            safeExec("sender.end()") { sender.end(success) }
        }

        return success
    }

}


class SenderOfMain(): TransferManager.Sender() {
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


class ReceiverLocalhost(script: Script, baseDir: String? = null): TransferManager.Receiver(script, baseDir) {
    private var fileCount = 0
    private var baseDirFile: File? = null

    init {
        if( this.baseDir != null )
            this.baseDirFile = File(baseDir)
    }

    private fun copy(inputStream:  InputStream, outputFile: File): Long {
            FileOutputStream(outputFile).use { outputStream ->
                val copiedByteCount = inputStream.copyTo(outputStream)
                logger.debug { "wrote $copiedByteCount to ${outputFile.getPath()}" }
                return copiedByteCount
            }
    }

    override fun apply(item: TransferManager.Item) {

        if( item.verifyPathExists ) {
            if( !File(item.pathStr).exists() )
                throw StatusException(404, "Path ${item.pathStr} does not exist on the host")

        // When there is no path, we read inputStream into inline content
        } else if( item.pathStr.isBlank() ) {
            if( script.hasMain )
                throw StatusException(412, "Ambiguity: the script both defines main and has inline content")
            if( item.inputStream == null )
                throw StatusException(412, "The path is empty and there is no input")
            val contentFile = File.createTempFile("amc_", "", this.baseDirFile)
            contentFile.deleteOnExit()
            this.copy(item.inputStream, contentFile)
            this.script.assignMain(contentFile.name)

        // When path is a directory, create it and all parent directories on the way
        } else if( item.isDirectory ) {
            if( this.baseDirFile == null )
                throw StatusException(500, "Bug: no baseDir was provided in the constructor")

            if( item.pathStr.isBlank() )
                throw StatusException(412, "Can't create an empty directory")

            val dirToCreate = File(item.pathStr)
            if( dirToCreate.isAbsolute )
                throw StatusException(412, "Absolute paths are not allowed: ${item.pathStr}")

            val dir = this.baseDirFile!!.resolve(dirToCreate)
            if( !dir.exists() ) {
                logger.debug { "Creating directory path ${dir.getPath()}" }
                if( !dir.mkdirs() )
                    throw StatusException(500, "Failed to create directory path $dir")
            }

        // Create a file and write inputStream into it
        } else {
            if( this.baseDirFile == null )
                throw StatusException(500, "Bug: no baseDir was provided in the constructor")

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

            val file = this.baseDirFile!!.resolve(relFile)
            logger.debug { "writing to ${file.getPath()}" }
            this.copy(item.inputStream, file)
        }
    }


}
