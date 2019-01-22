package com.amcentral365.service

import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.UUID

import mu.KotlinLogging
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.dao.Asset
import java.io.InputStream
import kotlin.reflect.jvm.jvmName

private val logger = KotlinLogging.logger {}

class ExecutionTargetAMCWorker(private val threadId: String): ExecutionTarget {
    private var workDirName: String? = null
    private var execTimeoutSec: Long = 0
    private var idleTimeoutSec: Long = 0

    override var asset: Asset? = null
    override val name: String = if(this.asset == null || this.asset!!.name == null) "not defined" else this.asset!!.name!!

    override fun connect() = true
    override fun disconnect() {}

    override fun prepare(script: Script): Boolean {
        this.execTimeoutSec = script.scriptMain?.execTimeoutSec ?: config.defaultScriptExecTimeoutSec
        this.idleTimeoutSec = script.scriptMain?.idleTimeoutSec ?: config.defaultScriptIdleTimeoutSec
        if( script.needsWorkDir )
            this.workDirName = this.execAndGetOutput("/bin/mktemp -d -p ${config.localScriptExecBaseDir} am-central.XXXXXX")
        return true
    }

    override fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream?): StatusMessage {
        return this.realExec(script.location!!.content!!.body, outputStream = outputStream, inputStream = inputStream)
    }

    override fun cleanup(script: Script) {
        if( this.workDirName.isNullOrBlank() )
            return

        logger.debug { "removing work directory ${this.workDirName}" }
        this.execAndGetOutput("/bin/rm -r ${this.workDirName}")
    }

    private fun execAndGetOutput(vararg commands: String, inputStream: InputStream? = null): String =
        StringOutputStream().let {
            this.realExec(*commands, inputStream = inputStream, outputStream = it)
            it.getString().trimEnd('\r', '\n')
        }

    private fun realExec(vararg commands: String, inputStream: InputStream? = null, outputStream: OutputStream): StatusMessage {
        val workDirName = this.workDirName ?: config.SystemTempDirName

        try {
            val process = ProcessBuilder()
                    .directory(File(workDirName))
                    .redirectErrorStream(true)
                    .command(*commands)  // ("/bin/sh", "-c", cmd)
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

            fun ivlText(ts: Long) = "%.1f".format(System.currentTimeMillis() - ts/1000f)

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
                    val readByteCnt = process.inputStream.read(buffer, 0, Math.min(availableByteCnt, buffer.size))
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
            val msg = "completed normally in ${ivlText(execStartTs)} sec"
            logger.info { "${this.threadId}: $msg" }
            return StatusMessage(if( rc == 0 ) 200 else 500, msg)

        } catch(x: Exception) {
            logger.warn { "${this.threadId}: ${x::class.jvmName} ${x.message}" }
            throw StatusException(x, 500)
        }
    }

}


fun _devcall() {
/*
Asset: {"name": "inline-test-1", "description": "Testing inline scripts"} =>
   {"pk": {"asset_id": "40c8690c-cfb2-4c02-aee7-3a9ed9dab0aa"}, "optLock": {"modified_ts": "2019-01-18 05:45:24.0"}}

{"role_name": "script", "asset_vals":
   {"location": {"content": {"body": "hostname", "version": "1.0.0"}}, "target-role": "amc-worker-host"}}
=>
   {"pk": {"asset_id": "40c8690c-cfb2-4c02-aee7-3a9ed9dab0aa", "role_name": "script"},
       "optLock": {"modified_ts": "2019-01-18 05:51:02.0"}}

 */

    try {
        val script = Script.fromDB(UUID.fromString("40c8690c-cfb2-4c02-aee7-3a9ed9dab0aa"))
        if( script == null )
            return

        val target = ExecutionTargetAMCWorker("_devcall")
        val se = ScriptExecutor("_devcall", script, target)

        println("Running: ${script.name}")
        StringOutputStream().let {
            se.run(it)
            println("Output: ${it.getString()}")
        }

    } catch(x: java.lang.Exception) {
        println("Exception ${x::class.qualifiedName}: ${x.message}")
    }

    //val inlineLoc = LocationInline("hostname")
    //val eh = ExecutionTargetAMCWorker(Script(scriptMain, inlineLoc))
    //eh.prepare()
}
