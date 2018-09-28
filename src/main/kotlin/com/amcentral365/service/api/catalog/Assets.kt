package com.amcentral365.service.api.catalog

import mu.KotlinLogging
import spark.Request
import spark.Response

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.StatusException

import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetValues
import com.amcentral365.service.databaseStore
import com.amcentral365.service.formatResponse

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

        return "Will implement later"
    }


    private fun assetIdByStr(assetIdOrName: String): Long? {
        val assetId = assetIdOrName.toLongOrNull()
        if( assetId != null )
            return assetId

        val asset = Asset(assetIdOrName)
        val cnt = SelectStatement(asset).by(Asset::name).run()
        return if( cnt == 0 ) null else asset.assetId
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

        } catch(x: StatusException) {
            logger.error { "error creating role ${asset.name}: ${x.message}" }
            return formatResponse(rsp, x)
        }

    }
}
