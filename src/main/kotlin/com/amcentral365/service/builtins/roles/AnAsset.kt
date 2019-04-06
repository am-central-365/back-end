package com.amcentral365.service.builtins.roles

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.ScriptExecutorFlow
import com.amcentral365.service.StatusException
import com.amcentral365.service.config
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetRoleValues
import com.amcentral365.service.databaseStore
import com.google.gson.GsonBuilder


open class AnAsset {
    var asset: Asset? = null
}


abstract class ExecutionTarget(
    var workDirBase: String? = null,
    var commandToCreateWorkDir: List<String>? = null,
    var commandToRemoveWorkDir: List<String>? = null
): AnAsset(), ScriptExecutorFlow {

    fun getCmdToCreateWorkDir(): List<String> {
        val w = this.workDirBase ?: config.localScriptExecBaseDir
        return this.commandToCreateWorkDir!!.map { it.replace("\$WorkDirBase", w) }
    }


    fun getCmdToRemoveWorkDir(workDirName: String): List<String> {
        return this.commandToRemoveWorkDir!!.map { it.replace("\$WorkDir", workDirName) }
    }

    companion object {
        @Deprecated("use the one from daoutils ", replaceWith = ReplaceWith("com.amcentral365.service.dao.fromDB"))
        fun fromDB(asset: Asset, roleName: String, clazz: Class<ExecutionTarget>): ExecutionTarget {
            databaseStore.getGoodConnection().use { conn ->
                val dao = AssetRoleValues(asset.assetId!!, roleName)
                val cnt = SelectStatement(dao).select(dao.allCols).byPk().run(conn)
                if( cnt != 1 )
                    throw StatusException(404, "Script asset ${asset.assetId} has no role 'script'")

                val obj = GsonBuilder().create().fromJson(dao.assetVals, clazz)
                obj.asset = asset
                return obj
            }
        }
    }

}
