package com.amcentral365.service.api

import mu.KotlinLogging

import spark.Request
import spark.Response

import com.amcentral365.service.StatusException
import com.amcentral365.service.StatusMessage
import com.amcentral365.service.api.catalog.AssetRoleValues
import com.amcentral365.service.api.catalog.Assets
import com.amcentral365.service.builtins.RoleName
import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.dao.Task
import com.amcentral365.service.dao.fromDB
import com.amcentral365.service.dao.stringToTs
import com.amcentral365.service.databaseStore
import com.amcentral365.service.formatResponse

private val logger = KotlinLogging.logger {}

class Tasks { companion object {

    fun list(req: Request, rsp: Response): String {
        rsp.type("application/json")
        return """{"text": "not implemented"}"""
    }


    fun submit(req: Request, rsp: Response): String {
        val thisThreadId = Thread.currentThread().name;
        return try {
            val task = Task()
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)

            task.name = paramMap.getOrElse("taskName") { throw StatusException(400, "parameter 'taskName' is required") }.trim()
            val scriptKey = paramMap.getOrElse("scriptKey") { throw StatusException(400, "parameter 'scriptKey' is required") }.trim()
            val targetKey = paramMap.get("targetKey")
            if( targetKey.isNullOrEmpty() )
                return formatResponse(rsp, StatusMessage(400, "targetKey was not provided or empty"))
            task.executorRoleName = paramMap.get("executorRole")?.trim() ?: RoleName.ScriptExecutorSSH
            task.scheduledRunTs = stringToTs(paramMap.get("scheduledTs"))
            task.scriptArgs = paramMap.get("scriptArgs")?.trim()
            task.description = paramMap.get("description")?.trim()

            val script = fromDB<Script>(scriptKey, RoleName.Script)
            val targetRoleName = script.targetRoleName ?:
                    return formatResponse(rsp, StatusMessage(400, "script ${script.name} has no attribute 'targetRoleName'"))
            val targetAsset = Assets.getAssetByKey(targetKey)
            if(!AssetRoleValues.hasRole(targetAsset.assetId!!, targetRoleName))
                return formatResponse(rsp, StatusMessage(404, "asset $targetKey has no requested target role '$targetRoleName'"))

            task.status = Task.Status.Ready
            task.scriptAssetId = script.asset!!.assetId
            task.targetAssetId = targetAsset.assetId

            val msg = databaseStore.insertObjectAsRow(task)
            logger.info { "$thisThreadId: submitted task ${task.taskId}: ${msg.msg}" }
            formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "$thisThreadId: error running script: ${x.message}" }
            formatResponse(rsp, x)
        }
    }
}}
