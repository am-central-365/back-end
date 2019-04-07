package com.amcentral365.service.api

import com.amcentral365.service.ExecutionTargetAMCWorker
import com.amcentral365.service.ExecutionTargetSSHHost
import mu.KotlinLogging
import spark.Request
import spark.Response

import com.amcentral365.service.StatusException
import com.amcentral365.service.StatusMessage
import com.amcentral365.service.ScriptExecutor
import com.amcentral365.service.ScriptExecutorFlow
import com.amcentral365.service.api.catalog.AssetRoleValues
import com.amcentral365.service.api.catalog.Assets
import com.amcentral365.service.builtins.RoleName
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.fromDB
import com.amcentral365.service.formatResponse


private val logger = KotlinLogging.logger {}

class Execute { companion object {

    fun list(req: Request, rsp: Response): String {
        rsp.type("application/json")
        return """{"text": "not implemented"}"""
    }


    fun start(req: Request, rsp: Response): String {
        val thisThreadId = Thread.currentThread().name;
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)

            val scriptKey = paramMap.getOrElse("script_key") { throw StatusException(400, "parameter 'script_key' is required") }.trim()
            val targetKey = paramMap.get("target_key")

            val script = fromDB<Script>(scriptKey, RoleName.Script)
            val executorRoleName = script.executorRoleName ?:
                    return formatResponse(rsp, StatusMessage(400, "script ${script.name} has no executorRoleName"))
            val targetRoleName = script.targetRoleName ?:
                    return formatResponse(rsp, StatusMessage(400, "script ${script.name} has no targetRoleName"))

            var targetAsset: Asset? = null
            if( targetKey != null && targetKey.isNotEmpty() ) {
                targetAsset = Assets.getAssetByKey(targetKey)
                if(!AssetRoleValues.hasRole(targetAsset.assetId!!, targetRoleName))
                    return formatResponse(rsp, StatusMessage(404, "asset $targetKey has no requested target role $targetRoleName"))
            }
            val scriptExecutorImplementation = getScriptExecutorImplementation(thisThreadId, executorRoleName, targetAsset)
            val outputStream = System.out  // FIXME: send to the log, to the message channel, maybe to more recipients

            val scriptExecutor = ScriptExecutor(thisThreadId)
            scriptExecutor.run(script, scriptExecutorImplementation, outputStream)
            return formatResponse(rsp, StatusMessage.OK)  // FIXME: should contain the output parameter

        } catch(x: Exception) {
            logger.error { "$thisThreadId: error running script: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }


    fun getScriptExecutorImplementation(thisThreadId: String, executorRoleName: String, targetAsset: Asset?): ScriptExecutorFlow {
        if( executorRoleName == RoleName.ScriptExecutorAMC )
            return ExecutionTargetAMCWorker(thisThreadId)

        // TODO: allow scripts run not only on the target asset, but on its closest parent, having executorRoleName
        // Say, we have an Oracle SqlPlus script. Target would be an Oracle instance,
        // but the script is executed by sqlplus on the host machine running the instance.
        // To achieve this, we set executorRole to "host-with-sqlplus". Oracle instance dousn't have it,
        // but its hosting environment (a VM, a Docker container, or whatever) does have this role.
        // we should be able to go up the parent/child chain, find it, and use as the execution asset.
        //
        // Problem: what defines the parent/child hierarchy?

        // For now, we just return the target.
        require(targetAsset != null)
        return ExecutionTargetSSHHost(thisThreadId, targetAsset)
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
