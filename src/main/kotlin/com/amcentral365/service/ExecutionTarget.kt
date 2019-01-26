package com.amcentral365.service

import com.amcentral365.service.builtins.roles.Script
import java.io.InputStream

import com.amcentral365.service.dao.Asset
import java.io.ByteArrayOutputStream
import java.io.OutputStream

interface ExecutionTarget {
    var asset: Asset?
    val name: String

    /**
     * ssh to the remote host, connect to the database, or the like.
     * The connection is assumed to be reused by the other calls ([prepare], [execute])
     * but that isn't enforced. It is up to the implementation.
     *
     * @return success. On failure, no other steps are executed.
     */
    fun connect(): Boolean

    /**
     * Prepare the target for script execution (connect, create directories, deploy the files, unpack, etc)
     * @return success state. When false, the other steps are not performed except [disconnect]
     */
    fun prepare(script: Script): Boolean

    /**
     * Run the script commands
     * All input from [inputStream] is fed as one operation.
     * The output is streamed to [outputStream] as it is produced, without buffering.
     * @return standard HTTP code: 200 for success, other as appropriate.
     */
    fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream? = null): StatusMessage

    /**
     * Remove the created files, drop temp tables. or whatever else makes sense.
     * The method is guaranteed to be called if execution was called.
     */
    fun cleanup(script: Script)

    /**
     * Close ssh, database, or whatever connections.
     * The function is guaranteed to be called if [connect] succeeded.
     */
    fun disconnect()
}


class StringOutputStream: OutputStream() {
    private val byteStream = ByteArrayOutputStream()

    override fun write(b: Int) = byteStream.write(b)

    fun getString() = this.byteStream.toString(config.charSetName)!!
}
