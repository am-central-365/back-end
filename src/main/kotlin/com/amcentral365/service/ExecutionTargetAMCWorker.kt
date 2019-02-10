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
    override var asset: Asset? = null

    private var workDirName: String? = null
    private var content: String? = null
    private var execTimeoutSec: Int = 0
    private var idleTimeoutSec: Int = 0

    override fun connect() = true
    override fun disconnect() {}

    override fun prepare(script: Script): Boolean {
        this.execTimeoutSec = script.scriptMain?.execTimeoutSec ?: config.defaultScriptExecTimeoutSec
        this.idleTimeoutSec = script.scriptMain?.idleTimeoutSec ?: config.defaultScriptIdleTimeoutSec

        val sender = script.getSender()
        if( sender == null ) {
            logger.warn { "${this.threadId}: script '${script.name}' has no content, nothing to do" }
            return false
        }

        if( script.needsWorkDir )
            this.workDirName = this.execAndGetOutput(listOf(
                    "/bin/mktemp", "-dp", config.localScriptExecBaseDir, "amc.XXXXXX"))

        val receiver = ReceiverLocalhost(this.workDirName)

        logger.info { "${this.threadId}: transfering ${script.name} file to ${this.name}" }
        val transferManager = TransferManager(this.threadId)
        val success = transferManager.transfer(sender, receiver)
        this.content = receiver.content

        return success
    }

    override fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream?): StatusMessage {
        if( this.content != null )
            return this.realExec(listOf(this.content!!), outputStream = outputStream, inputStream = inputStream)

        val commands = script.getCommand()!!
        return this.realExec(commands, outputStream = outputStream, inputStream = inputStream)
    }

    override fun cleanup(script: Script) {
        if( this.workDirName.isNullOrBlank() )
            return

        logger.debug { "removing work directory ${this.workDirName}" }
        this.execAndGetOutput(listOf("/bin/rm", "-r", this.workDirName!!))
    }

    private fun execAndGetOutput(commands: List<String>, inputStream: InputStream? = null): String =
        StringOutputStream().let {
            this.realExec(commands, inputStream = inputStream, outputStream = it)
            it.getString().trimEnd('\r', '\n')
        }

    private fun realExec(commands: List<String>, inputStream: InputStream? = null, outputStream: OutputStream): StatusMessage {
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
Asset:
{"name": "script-test-inline-1", "description": "Testing pwd"} =>
   {"pk": {"asset_id": "8b0f7f5e-569d-462a-baac-f2f16e982c2a"}, "optLock": {"modified_ts": "2019-01-26 17:36:30.0"}}


pwd script:
{"role_name": "script", "asset_vals": {
    "location":     { "content": {"body": "pwd", "version": "1.0.0"}},
    "target-role": "script-target-host"}}
=>
   {"pk": {"asset_id": "8b0f7f5e-569d-462a-baac-f2f16e982c2a", "role_name": "script"}, "optLock": {"modified_ts": "2019-01-26 17:40:43.0"}}

cat :
Asset:  {"name": "script-test-fs-1", "description": "Testing /usr/bin/id"} =>
   {"pk": {"asset_id": "6d7169ca-d6b0-47ba-b298-1f3a45bbcea1"}, "optLock": {"modified_ts": "2019-01-27 18:08:30.0"}}

Script:
    {"role_name": "script", "asset_vals": {
        "location":   {"fileSystemPath": "/usr/bin/id"},
        "scriptMain", {"main": "/usr/bin/id", "params": ["-G", "n"], "sudo_as": "root" },
        "target-role": "script-target-host"}}
    =>
       {"pk": {"asset_id": "8b0f7f5e-569d-462a-baac-f2f16e982c2a", "role_name": "script"}, "optLock": {"modified_ts": "2019-01-26 17:40:43.0"}}

*/

    try {
        val script: Script = Script.fromDB(UUID.fromString("8b0f7f5e-569d-462a-baac-f2f16e982c2a"))!!
        val target = ExecutionTargetAMCWorker("_devcall")
        val se = ScriptExecutor("_devcall")

        println("++ Running: ${script.name}")
        StringOutputStream().let {
            se.run(script, target, it)
            println("++ Output: ${it.getString()}")
        }

    } catch(x: java.lang.Exception) {
        println("++ Exception ${x::class.qualifiedName}: ${x.message}")
    }

    //val inlineLoc = LocationInline("hostname")
    //val eh = ExecutionTargetAMCWorker(Script(scriptMain, inlineLoc))
    //eh.prepare()
}
