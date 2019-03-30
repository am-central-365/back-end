package com.amcentral365.service.dao

import com.google.gson.GsonBuilder
import java.sql.Connection
import java.util.UUID

import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.service.databaseStore
import com.amcentral365.service.StatusException
import com.amcentral365.service.builtins.RoleName
import com.amcentral365.service.builtins.roles.Target

inline fun <reified T> getAssetObjectByRole(assetId: UUID, roleName: RoleName, conn: Connection): T? {
    val dao = AssetRoleValues(assetId, roleName.name)
    val cnt = SelectStatement(dao).select(dao.allCols).byPk().run(conn)
    if( cnt != 1 )
        return null

    return GsonBuilder().create().fromJson<T>(dao.assetVals, T::class.java)
}


inline fun <reified T> loadRoleObjectFromDB(asset: Asset, roleName: RoleName, Initializer: (obj: T)-> Unit): T {
    databaseStore.getGoodConnection().use { conn ->
        val obj = getAssetObjectByRole<T>(asset.assetId!!, roleName, conn)
            ?: throw StatusException(404, "Script asset ${asset.assetId} has no role 'script'")
        Initializer(obj) //obj.asset = asset
        return obj
    }
}


inline fun <reified T: Target> fromDB(asset: Asset, roleName: String): T {
    databaseStore.getGoodConnection().use { conn ->
        val dao = AssetRoleValues(asset.assetId!!, roleName)
        val cnt = SelectStatement(dao).select(dao.allCols).byPk().run(conn)
        if( cnt != 1 )
            throw StatusException(404, "Script asset ${asset.assetId} has no role 'script'")

        val obj = GsonBuilder().create().fromJson<T>(dao.assetVals, T::class.java)
        obj.asset = asset
        return obj
    }
}
