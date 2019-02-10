package com.amcentral365.service.api.catalog

import com.amcentral365.pl4kotlin.DeleteStatement
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
import com.amcentral365.service.StatusMessage
import com.amcentral365.service.combineRequestParams
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetRoleValues
import com.amcentral365.service.formatResponse
import com.amcentral365.service.toJsonArray
import com.amcentral365.service.databaseStore
import com.amcentral365.service.schemaUtils

private val logger = KotlinLogging.logger {}


class Assets { companion object {

    private fun assetIdByKey(assetIdOrName: String): UUID? {
        return try {
            UUID.fromString(assetIdOrName)
        } catch(x: IllegalArgumentException) {
            val asset = Asset(assetIdOrName)
            val cnt = SelectStatement(asset, databaseStore::getGoodConnection).select(Asset::assetId).by(Asset::name).run()
            if( cnt == 0 ) null else asset.assetId
        }
    }

    private fun extractAssetIdOrDie(paramMap: MutableMap<String, String>): UUID {
        val pkParam = "asset_key"
        val pkKey = paramMap.getOrElse(pkParam) { throw StatusException(400, "parameter '$pkParam' is required") }.trim()
        val pkId = this.assetIdByKey(pkKey) ?: throw StatusException(400, "asset with name '$pkKey' was not found")
        paramMap.remove(pkParam)

        return pkId
    }

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


    fun getAssetById(req: Request, rsp: Response): String {
        val asset = Asset()
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            asset.assetId = this.extractAssetIdOrDie(paramMap)
            asset.assignFrom(paramMap)
            logger.info { "querying asset ${asset.assetId}" }

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
        val assetRoleValues = AssetRoleValues()
        var conn: Connection? = null
        try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            assetRoleValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetRoleValues.assignFrom(paramMap)
            logger.info { "listing roles of asset '${assetRoleValues.assetId}'" }

            conn = databaseStore.getGoodConnection()
            val defs = SelectStatement(assetRoleValues)
                                        .select(AssetRoleValues::roleName).byPresentValues().iterate(conn).asSequence().toList()
            return toJsonArray(defs, "role_name")

        } catch(x: Exception) {
            logger.info { "error querying roles of asset ${assetRoleValues.assetId} succeeded: ${x.message}" }
            return formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }
    }


    fun getAssetByIdAndRole(req: Request, rsp: Response): String {
        val assetRoleValues = AssetRoleValues()
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            assetRoleValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetRoleValues.assignFrom(paramMap)
            logger.info { "querying role '${assetRoleValues.roleName}' of asset '${assetRoleValues.assetId}'" }

            if( assetRoleValues.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")

            val cnt = SelectStatement(assetRoleValues, databaseStore::getGoodConnection)
                                        .select(assetRoleValues.allCols).byPresentValues().run()
            if( cnt == 0 )
                return formatResponse(rsp, 404, "role '${assetRoleValues.roleName}' of asset '${assetRoleValues.assetId}' was not found")

            assetRoleValues.asJsonStr()
        } catch(x: Exception) {
            logger.info { "error querying role ${assetRoleValues.roleName} of asset ${assetRoleValues.assetId}: ${x.message}" }
            formatResponse(rsp, x)
        }
    }


    fun createAsset(req: Request, rsp: Response): String {
        val asset = Asset()
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            asset.assignFrom(paramMap)
            logger.info { "creating asset '${asset.name}'" }

            if( asset.name == null )
                return formatResponse(rsp, 400, "parameter 'name' is required")

            val msg = databaseStore.insertObjectAsRow(asset)
            logger.info { "create asset ${asset.name}: ${msg.msg}" }
            formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error creating role ${asset.name}: ${x.message}" }
            formatResponse(rsp, x)
        }
    }

    fun updateAsset(req: Request, rsp: Response): String {
        val asset = Asset()
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)

            asset.assetId = this.extractAssetIdOrDie(paramMap)
            asset.assignFrom(paramMap)
            logger.info { "updating asset '${asset.assetId}', name '${asset.name}'" }

            val msg = databaseStore.updateObjectAsRow(asset)
            logger.info { "update asset ${asset.assetId}, name '${asset.name}' succeeded: $msg" }

            formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error updating asset ${asset.assetId}, name '${asset.name}': ${x.message}" }
            formatResponse(rsp, x)
        }
    }


    fun addAssetRole(req: Request, rsp: Response): String {
        val assetRoleValues = AssetRoleValues()
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            assetRoleValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetRoleValues.assignFrom(paramMap)
            logger.info { "adding role '${assetRoleValues.roleName}' to asset '${assetRoleValues.assetId}'" }

            if( assetRoleValues.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")
            if( assetRoleValues.assetVals == null )
                return formatResponse(rsp, 400, "parameter 'asset_vals' is required")

            schemaUtils.validateAssetValue(assetRoleValues.roleName!!, assetRoleValues.assetVals!!)

            val msg = databaseStore.insertObjectAsRow(assetRoleValues)
            logger.info { "add asset role ${assetRoleValues.roleName}: ${msg.msg}" }
            formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.error { "error adding role ${assetRoleValues.roleName}: ${x.message}" }
            formatResponse(rsp, x)
        }
    }

    fun updateAssetRole(req: Request, rsp: Response): String {
        val assetRoleValues = AssetRoleValues()
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req, parseJsonBody = true)
            assetRoleValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetRoleValues.assignFrom(paramMap)
            logger.info { "adding role '${assetRoleValues.roleName}' to asset '${assetRoleValues.assetId}'" }

            if( assetRoleValues.roleName == null )
                return formatResponse(rsp, 400, "parameter 'role_name' is required")
            if( assetRoleValues.assetVals == null )
                return formatResponse(rsp, 400, "parameter 'asset_vals' is required")

            schemaUtils.validateAssetValue(assetRoleValues.roleName!!, assetRoleValues.assetVals!!)
            logger.info { "updating role ${assetRoleValues.roleName} of asset ${assetRoleValues.assetId}" }

            val msg = databaseStore.updateObjectAsRow(assetRoleValues)
            logger.info { "updating role ${assetRoleValues.roleName} of asset ${assetRoleValues.assetId} succeeded: $msg" }

            formatResponse(rsp, msg, jsonIfOk = true)

        } catch(x: Exception) {
            logger.info { "error updating role ${assetRoleValues.roleName} of asset ${assetRoleValues.assetId}: ${x.message}" }
            formatResponse(rsp, x)
        }
    }


    fun deleteAsset(req: Request, rsp: Response): String {
        val asset = Asset()
        var conn: Connection? = null
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            asset.assetId = this.extractAssetIdOrDie(paramMap)
            asset.assignFrom(paramMap)
            val cascade = paramMap.getOrDefault("cascade", "false").toBoolean()
            logger.info { "deleting asset '${asset.assetId}', name '${asset.name}', cascade='$cascade'" }

            if( asset.modifiedTs == null )
                return formatResponse(rsp, 400, "the OptLock value modify_ts must be supplied to Delete")

            conn = databaseStore.getGoodConnection()
            if( cascade ) {
                val assetRoleValues = AssetRoleValues(asset.assetId!!)
                val cnt = DeleteStatement(assetRoleValues, databaseStore::getGoodConnection).by(assetRoleValues::assetId).run(conn)
                logger.info { "deletrf $cnt roles of asset '${asset.assetId}', name '${asset.name}'" }
            }

            val cnt = DeleteStatement(asset).by(asset::assetId).run(conn)
            if( cnt == 0 )
                return formatResponse(rsp, 400, "asset with id ${asset.assetId} and modify_ts ${asset.modifiedTs} was not found")

            logger.info { "deleted asset '${asset.assetId}', name '${asset.name}'" }
            formatResponse(rsp, StatusMessage.OK)

        } catch(x: Exception) {
            logger.error { "error deleting asset '${asset.assetId}', name '${asset.name}': ${x.message}" }
            formatResponse(rsp, x)
        } finally {
            closeIfCan(conn)
        }
    }


    fun deleteAssetRoles(req: Request, rsp: Response): String {
        val assetRoleValues = AssetRoleValues()
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            assetRoleValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetRoleValues.assignFrom(paramMap)
            logger.info { "deleting roles of asset '${assetRoleValues.assetId}'" }

            val cnt = DeleteStatement(assetRoleValues, databaseStore::getGoodConnection).by(assetRoleValues::assetId).run()
            if( cnt == 0 )
                return formatResponse(rsp, 404, "no roles were deleted: either asset '${assetRoleValues.assetId}' does not exist, or has no roles")

            val msg = databaseStore.deleteObjectRow(assetRoleValues)
            logger.info { "deleting roles of asset '${assetRoleValues.assetId}' succeeded: $msg" }
            formatResponse(rsp, 200, "deleted $cnt role(s) of asset ${assetRoleValues.assetId}")
        } catch(x: Exception) {
            logger.error { "error deleting roles of asset '${assetRoleValues.assetId}': ${x.message}" }
            formatResponse(rsp, x)
        }
    }


    fun deleteAssetRole(req: Request, rsp: Response): String {
        val assetRoleValues = AssetRoleValues()
        var msgRoleOf = "~role/asset are yet unknown~"
        return try {
            rsp.type("application/json")
            val paramMap = combineRequestParams(req)
            assetRoleValues.assetId = this.extractAssetIdOrDie(paramMap)
            assetRoleValues.assignFrom(paramMap)
            msgRoleOf = "role '${assetRoleValues.roleName}' of asset '${assetRoleValues.assetId}'"
            logger.info { "deleting $msgRoleOf" }

            val msg = databaseStore.deleteObjectRow(assetRoleValues)
            logger.info { "deleting $msgRoleOf succeeded: $msg" }
            formatResponse(rsp, msg)
        } catch(x: Exception) {
            logger.error { "error deleting $msgRoleOf: ${x.message}" }
            formatResponse(rsp, x)
        }
    }

}}
