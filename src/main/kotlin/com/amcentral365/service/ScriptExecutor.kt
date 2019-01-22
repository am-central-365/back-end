package com.amcentral365.service

import mu.KotlinLogging

import com.amcentral365.service.builtins.roles.Script
import com.google.common.base.Stopwatch
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.reflect.jvm.jvmName

private val logger = KotlinLogging.logger {}

open class ScriptExecutor(val threadId: String, val script: Script, val target: ExecutionTarget) {

    fun run(outputStream: OutputStream) {
        var connected = false
        try {
            logger.info { "${this.threadId}: connecting to target ${target.name}" }
            connected = target.connect()
            if( !connected ) {
                logger.warn { "${this.threadId}: failed to connect to target ${target.name}" }
                return
            }

            logger.info { "${this.threadId}: preparing script ${script.name} on target ${target.name}" }
            if( !target.prepare(script) ) {
                logger.warn { "${this.threadId}: failed to prepare script ${script.name} on target ${target.name}" }
                return
            }

            try {
                logger.info { "${this.threadId}: executing script ${script.name} on target ${target.name}" }
                val w = Stopwatch.createStarted()
                target.execute(script, outputStream)
                logger.info { "${this.threadId}: executed in ${w.elapsed(TimeUnit.MILLISECONDS)} msec" }
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

    companion object {

        fun transferFiles(script: Script, target: ExecutionTarget) {

        }
    }

}
