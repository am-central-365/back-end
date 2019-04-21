package com.amcentral365.service

import mu.KotlinLogging

import com.amcentral365.service.builtins.roles.Script
import com.google.common.base.Stopwatch
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.reflect.jvm.jvmName

private val logger = KotlinLogging.logger {}

open class ScriptExecutor(private val threadId: String) {

    fun run(script: Script, target: ScriptExecutorFlow, outputStream: OutputStream, inputStream: InputStream? = null): StatusMessage {
        var connected = false
        try {
            logger.info { "${this.threadId}: connecting to target ${target.name}" }
            connected = target.connect()
            if( !connected ) {
                val msg = "${this.threadId}: failed to connect to target ${target.name}"
                logger.warn { "msg" }
                return StatusMessage(500, msg)
            }

            logger.info { "${this.threadId}: preparing script ${script.name} on target ${target.name}" }
            if( !target.prepare(script) ) {
                val msg = "${this.threadId}: failed to prepare script ${script.name} on target ${target.name}"
                logger.warn { msg }
                return StatusMessage(500, msg)
            }

            try {
                logger.info { "${this.threadId}: executing script ${script.name} on target ${target.name}" }
                val w = Stopwatch.createStarted()
                val statusMessage = target.execute(script, outputStream, inputStream)
                logger.info { "${this.threadId}: executed in ${w.elapsed(TimeUnit.MILLISECONDS)} msec" }
                return statusMessage
            } finally {
                logger.info { "${this.threadId}: cleaning up after script ${script.name} on target ${target.name}" }
                target.cleanup(script)
            }

        } catch(x: Exception) {
            logger.warn { "${this.threadId}: ${x::class.jvmName} ${x.message}" }
            throw StatusException(x, 500)
        } finally {
            if( connected )
                try {
                    logger.info { "${this.threadId}: disconnecting from target ${target.name}" }
                    target.disconnect()
                } catch(x: Exception) {
                    logger.warn { "${this.threadId}: ignoring failed disconnect: ${x::class.jvmName} ${x.message}" }
                }
        }
    }
}
