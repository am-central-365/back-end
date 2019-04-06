package com.amcentral365.service.api.catalog

import com.amcentral365.service.dao.uuidToBytes
import com.amcentral365.service.databaseStore
import java.util.UUID

class AssetRoleValues { companion object {

    fun hasRole(assetId: UUID, roleName: String): Boolean {
        databaseStore.getGoodConnection().use { conn ->
            conn.prepareStatement("select null from asset_role_values where asset_id = ? and role_name = ?").use { stmt ->
                stmt.setBytes(1, uuidToBytes(assetId))
                stmt.setString(2, roleName)
                stmt.executeQuery().use { rs ->
                    return rs.next() && !rs.next()
                }
            }
        }
    }



}}
