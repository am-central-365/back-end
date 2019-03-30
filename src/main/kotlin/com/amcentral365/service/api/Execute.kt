package com.amcentral365.service.api

import com.amcentral365.service.ExecutionTargetAMCWorker
import com.amcentral365.service.ExecutionTargetSSHHost
import mu.KotlinLogging
import spark.Request
import spark.Response

import com.amcentral365.service.StatusException
import com.amcentral365.service.StatusMessage
import com.amcentral365.service.ScriptExecutor
import com.amcentral365.service.api.catalog.Assets
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.formatResponse


private val logger = KotlinLogging.logger {}

class Execute { companion object {

    fun list(req: Request, rsp: Response): String {
        rsp.type("application/json")
        return """{"text": "not implemented"}"""
    }


    fun start(req: Request, rsp: Response): String {
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)

            val scriptKey = paramMap.getOrElse("script_key") { throw StatusException(400, "parameter 'script_key' is required") }.trim()
            val targetKey = paramMap.getOrElse("target_key") { throw StatusException(400, "parameter 'target_key' is required") }.trim()

            val scriptAsset = Assets.getAssetByKey(scriptKey)
            val targetAsset = Assets.getAssetByKey(targetKey)

            val thisThreadId = Thread.currentThread().name;
            val script = Script.fromDB(scriptAsset)
            val target =
                if( script.runOnAmc ?: false ) ExecutionTargetAMCWorker(thisThreadId)
                else ExecutionTargetSSHHost(thisThreadId, targetAsset)  // targetAsset

            val scriptExecutor = ScriptExecutor(thisThreadId)
            scriptExecutor.run(script, target, System.out /* FIXME */)
            return formatResponse(rsp, StatusMessage.OK)  // FIXME: should contain the output parameter

        } catch(x: Exception) {
            logger.error { "error runing script ${sc} assets: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }


    fun getInfo(req: Request, rsp: Response): String {
        rsp.type("application/json")
        return """{"text": "not implemented"}"""
    }


    fun getLog(req: Request, rsp: Response): String {
        rsp.type("text/plain")
        return "not implemented"
    }


    fun getOutput(req: Request, rsp: Response): String {
        rsp.type("text/plain")
        return "not implemented"
    }
}}
