package com.amcentral365.service.api.catalog

import mu.KotlinLogging
import spark.Request
import spark.Response

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.StatusException
import com.amcentral365.service.StatusMessage

import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetValues
import com.amcentral365.service.databaseStore
import com.amcentral365.service.formatResponse
import com.amcentral365.service.schemaUtils
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.UUID

private val logger = KotlinLogging.logger {}


class Assets {

    /**
     * /catalog/assets
     */
    fun getAssets(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val paramMap = combineRequestParams(req)
        val asset = Asset()
        val assetValues = AssetValues()

        asset.assignFrom(paramMap)
        val roleName   = paramMap.getOrDefault("role_name", "").trim()
        logger.info { "get asset by id ${asset.assetId}, name ${asset.name}, role $roleName" }

        val selStmtAssets = SelectStatement(asset).byPresentValues()


        if( asset.assetId == null && roleName.isNotEmpty() )
            return formatResponse(rsp, 400, "parameter 'role_name' requires 'asset_id'")

        return formatResponse(rsp, 501, "Not sure what the method shoud be doing")
    }


    private fun assetIdByStr(assetIdOrName: String): UUID? {
        try {
            return UUID.fromString(assetIdOrName)
        } catch(x: IllegalArgumentException) {
            val asset = Asset(assetIdOrName)
            val cnt = SelectStatement(asset, databaseStore::getGoodConnection).select(Asset::assetId).by(Asset::name).run()
            return if( cnt == 0 ) null else asset.assetId
        }
    }

    private fun extractAssetIdOrDie(paramMap: MutableMap<String, String>): UUID {
        val pkParam = "asset_id"
        val pkIdOrName = paramMap.getOrElse(pkParam) { throw StatusException(400, "parameter '$pkParam' is required") }
        val pkId = this.assetIdByStr(pkIdOrName)
        if( pkId == null )
            throw StatusException(400, "parameter '$pkParam' is required")
        paramMap.remove(pkParam)

        return pkId
    }


    fun getAssetByIdAndRole(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val paramMap = combineRequestParams(req)

        val roleName = paramMap.getOrDefault("role_name", "").trim()
        require(roleName.isNotEmpty())

        val assetIdOrName = paramMap.getOrDefault("asset_id", "").trim()
        require(assetIdOrName.isNotEmpty())

        val assetId = this.assetIdByStr(assetIdOrName) ?:
                        return formatResponse(rsp, 404, "asset '$assetIdOrName' was not found")

        val assetValues = AssetValues(assetId, roleName)
        val cnt = SelectStatement(assetValues).byPresentValues().run()
        if( cnt == 0 )
            return formatResponse(rsp, 404, "asset '$assetIdOrName' does not have role '$roleName'")

        return assetValues.asJsonStr()
    }


    fun getAssetById(req: Request, rsp: Response): String {
        rsp.type("application/json")
        val paramMap = combineRequestParams(req)

        val roleName = paramMap.getOrDefault("role_name", "").trim()
        require(roleName.isNotEmpty())

        val assetIdOrName = paramMap.getOrDefault("asset_id", "").trim()
        require(assetIdOrName.isNotEmpty())

        val assetId = this.assetIdByStr(assetIdOrName) ?:
                        return formatResponse(rsp, 404, "asset '$assetIdOrName' was not found")

        val assetValues = AssetValues(assetId, roleName)
        val cnt = SelectStatement(assetValues).byPresentValues().run()
        if( cnt == 0 )
            return formatResponse(rsp, 404, "asset '$assetIdOrName' does not have role '$roleName'")

        return assetValues.asJsonStr()
    }


    fun createAsset(req: Request, rsp: Response): String {
        val asset = Asset()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            asset.assignFrom(paramMap)
            logger.info() { "creating asset '${asset.name}'" }

            if( asset.name == null )
                return formatResponse(rsp, 400, "parameter 'name' is required")

            val msg = databaseStore.insertObjectAsRow(asset)
            logger.info { "create asset ${asset.name}: ${msg.msg}" }
            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error creating role ${asset.name}: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }

    fun updateAsset(req: Request, rsp: Response): String {
        val asset = Asset()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)

            asset.assetId = this.extractAssetIdOrDie(paramMap)
            asset.assignFrom(paramMap)
            logger.info { "updating asset '${asset.assetId}', name '${asset.name}'" }

            val msg = databaseStore.updateObjectAsRow(asset)
            logger.info { "update asset ${asset.assetId}, name '${asset.name}' succeeded: $msg" }

            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error updating asset ${asset.assetId}, name '${asset.name}': ${x.message}" }
            return formatResponse(rsp, x)
        }
    }


    fun addAssetRole(req: Request, rsp: Response): String {
        val assetValues = AssetValues()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            assetValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetValues.assignFrom(paramMap)
            logger.info() { "adding role '${assetValues.roleName}' to asset '${assetValues.assetId}'" }

            if( assetValues.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")
            if( assetValues.assetVals == null )
                return formatResponse(rsp, 400, "parameter 'asset_vals' is required")

            schemaUtils.validateAssetValue(assetValues.roleName!!, assetValues.assetVals!!)

            val msg = databaseStore.insertObjectAsRow(assetValues)
            logger.info { "add asset role ${assetValues.roleName}: ${msg.msg}" }
            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error adding role ${assetValues.roleName}: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }

    fun updateAssetRole(req: Request, rsp: Response): String {
        val assetValues = AssetValues()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            assetValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetValues.assignFrom(paramMap)
            logger.info() { "adding role '${assetValues.roleName}' to asset '${assetValues.assetId}'" }


            if( assetValues.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")
            if( assetValues.assetVals == null )
                return formatResponse(rsp, 400, "parameter 'asset_vals' is required")

            schemaUtils.validateAssetValue(assetValues.roleName!!, assetValues.assetVals!!)
            logger.info { "updating role ${assetValues.roleName} of asset ${assetValues.assetId}" }

            val msg = databaseStore.updateObjectAsRow(assetValues)
            logger.info { "updating role ${assetValues.roleName} of asset ${assetValues.assetId} succeeded: $msg" }

            return formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.info { "error updating role ${assetValues.roleName} of asset ${assetValues.assetId} succeeded: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }

}
