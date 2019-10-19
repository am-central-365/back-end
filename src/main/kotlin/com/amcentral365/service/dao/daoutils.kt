package com.amcentral365.service.dao

import com.google.gson.GsonBuilder
import java.sql.Connection
import java.util.UUID
import java.nio.ByteBuffer

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.databaseStore
import com.amcentral365.service.StatusException
import com.amcentral365.service.api.catalog.Assets
import com.amcentral365.service.builtins.RoleName
import com.amcentral365.service.builtins.roles.AnAsset
import com.amcentral365.service.schemaUtils

inline fun <reified T> getAssetObjectForRole(assetId: UUID, roleName: String, conn: Connection): T? {
    val dao = AssetRoleValues(assetId, roleName)
    val cnt = SelectStatement(dao).select(dao.allCols).byPk().run(conn)
    if( cnt != 1 )
        return null

    return GsonBuilder().create().fromJson<T>(dao.assetVals, T::class.java)
}


inline fun <reified T> loadRoleObjectFromDB(asset: Asset, roleName: String, Initializer: (obj: T)-> Unit): T {
    databaseStore.getGoodConnection().use { conn ->
        val obj = getAssetObjectForRole<T>(asset.assetId!!, roleName, conn)
            ?: throw StatusException(404, "Asset ${asset.assetId} has no role '$roleName'")
        Initializer(obj) //obj.asset = asset
        return obj
    }
}


inline fun <reified T: AnAsset> fromDB(assetId: UUID, roleName: String): T {
    databaseStore.getGoodConnection().use { conn ->
        val dao = AssetRoleValues(assetId, roleName)
        val cnt = SelectStatement(dao).select(dao.allCols).byPk().run(conn)
        if( cnt != 1 )
            throw StatusException(404, "Asset ${assetId} has no role '$roleName'")

        val elm = schemaUtils.assignDefaultValues(roleName, dao.assetVals!!)
        return GsonBuilder().create().fromJson<T>(elm, T::class.java)
    }
}

inline fun <reified T: AnAsset> fromDB(assetIdOrKey: String, roleName: String): T {
    val asset = Assets.getAssetByKey(assetIdOrKey)
    val obj = fromDB<T>(asset.assetId!!, roleName)
    obj.asset = asset
    return obj;
}


fun uuidToBytes(uuid: UUID?): ByteArray? {
    if(uuid == null)
        return null
    val bb = ByteBuffer.wrap(ByteArray(16))
    bb.putLong(uuid.mostSignificantBits)
    bb.putLong(uuid.leastSignificantBits)
    return bb.array()
}
