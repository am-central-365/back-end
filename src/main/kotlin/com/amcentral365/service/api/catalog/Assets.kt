package com.amcentral365.service.api.catalog

import java.lang.Exception
import java.lang.IllegalArgumentException
import java.sql.Connection
import java.util.UUID

import mu.KotlinLogging
import spark.Request
import spark.Response

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.closeIfCan

import com.amcentral365.service.StatusException
import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetValues
import com.amcentral365.service.formatResponse
import com.amcentral365.service.toJsonArray
import com.amcentral365.service.databaseStore
import com.amcentral365.service.schemaUtils

private val logger = KotlinLogging.logger {}


class Assets {

    /**
     * /catalog/assets
     */
    fun listAssets(req: Request, rsp: Response): String {
        val asset = Asset()
        var conn: Connection? = null
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)

            val nameLike  = paramMap.getOrDefault("name_like", "").trim()
            val skipCount = paramMap.getOrDefault("skip",  "0").toInt()
            val limit     = paramMap.getOrDefault("limit", "0").toInt()
            val fetchLimit = if( limit > 0 ) limit else Int.MAX_VALUE
            logger.debug { "name pattern '$nameLike', skipCount $skipCount, limit: $limit, fetchLimit: $fetchLimit" }

            val selStmt = SelectStatement(asset, databaseStore::getGoodConnection)
                    .select(Asset::assetId)
                    .select(Asset::name)
                    .select(Asset::modifiedTs)
                    .orderBy(Asset::name)
            if( nameLike.isNotEmpty() )
                selStmt.by("name like ?", nameLike)

            conn = databaseStore.getGoodConnection()
            val defs = selStmt.iterate(conn).asSequence().filterIndexed{k, _ -> k >= skipCount}.take(fetchLimit).toList()
            return toJsonArray(defs)

        } catch(x: Exception) {
            logger.error { "error querying assets: ${x.message}" }
            return formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }
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
        val pkIdOrName = paramMap.getOrElse(pkParam) { throw StatusException(400, "parameter '$pkParam' is required") }.trim()
        val pkId = this.assetIdByStr(pkIdOrName)
        if( pkId == null )
            throw StatusException(400, "asset with name '$pkIdOrName' was not found")
        paramMap.remove(pkParam)

        return pkId
    }


    fun getAssetById(req: Request, rsp: Response): String {
        val asset = Asset()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            asset.assetId = this.extractAssetIdOrDie(paramMap)
            asset.assignFrom(paramMap)
            logger.info() { "querying asset ${asset.assetId}" }

            val cnt = SelectStatement(asset, databaseStore::getGoodConnection)
                                        .select(asset.allCols).byPresentValues().run()
            if( cnt == 0 )
                return formatResponse(rsp, 404, "asset '${asset.assetId}' was not found")

            return asset.asJsonStr()

        } catch(x: Exception) {
            logger.error { "error querying ${asset.name}: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }


    fun listAssetRoles(req: Request, rsp: Response): String {
        val assetValues = AssetValues()
        var conn: Connection? = null
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            assetValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetValues.assignFrom(paramMap)
            logger.info() { "listing roles of asset '${assetValues.assetId}'" }


            conn = databaseStore.getGoodConnection()
            val defs = SelectStatement(assetValues)
                                        .select(AssetValues::roleName).byPresentValues().iterate(conn).asSequence().toList()
            return toJsonArray(defs, "role_name")

        } catch(x: Exception) {
            logger.info { "error querying roles of asset ${assetValues.assetId} succeeded: ${x.message}" }
            return formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }
    }


    fun getAssetByIdAndRole(req: Request, rsp: Response): String {
        val assetValues = AssetValues()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            assetValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetValues.assignFrom(paramMap)
            logger.info() { "querying role '${assetValues.roleName}' of asset '${assetValues.assetId}'" }

            if( assetValues.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")

            val cnt = SelectStatement(assetValues, databaseStore::getGoodConnection)
                                        .select(assetValues.allCols).byPresentValues().run()
            if( cnt == 0 )
                return formatResponse(rsp, 404, "role '${assetValues.roleName}' of asset '${assetValues.assetId}' was not found")

            return assetValues.asJsonStr()
        } catch(x: Exception) {
            logger.info { "error querying role ${assetValues.roleName} of asset ${assetValues.assetId}: ${x.message}" }
            return formatResponse(rsp, x)
        }
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
            logger.info { "error updating role ${assetValues.roleName} of asset ${assetValues.assetId}: ${x.message}" }
            return formatResponse(rsp, x)
        }
    }

}
