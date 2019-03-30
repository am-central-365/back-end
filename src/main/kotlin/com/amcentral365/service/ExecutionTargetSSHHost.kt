package com.amcentral365.service

import com.amcentral365.service.builtins.roles.ExecutionTarget
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.dao.Asset
import mu.KotlinLogging

import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

class ExecutionTargetSSHHost(private val threadId: String, target: Asset): ExecutionTarget() {
    override fun prepare(script: Script): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream?): StatusMessage {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun cleanup(script: Script) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun disconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun connect(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
