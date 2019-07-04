package com.amcentral365.service.dao

import java.sql.Timestamp
import java.util.UUID

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table


@Table("asset_role_values")
class AssetRoleValues(): Entity() {
    @Column("asset_id",  pkPos = 1, restParamName = "assetId")        var assetId:   UUID? = null
    @Column("role_name", pkPos = 2, restParamName = "roleName")       var roleName:  String? = null
    @Column("asset_vals", isJson = true, restParamName = "assetVals") var assetVals: String? = null  // JSON

    @Column("created_by",  restParamName = "createdBy")  var createdBy:  String? = null
    @Column("modified_by", restParamName = "modifiedBy") var modifiedBy: String? = null
    @Column("created_ts",  restParamName = "createdTs",  onInsert = Generated.OnTheDbAlways)                          var createdTs:  Timestamp? = null
    @Column("modified_ts", restParamName = "modifiedTs", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true) var modifiedTs: Timestamp? = null

    constructor(assetId: UUID): this() { this.assetId = assetId }
    constructor(assetId: UUID, roleName: String): this() { this.assetId = assetId;  this.roleName = roleName }
    constructor(assetId: UUID, roleName: String, assetVals: String): this(assetId, roleName) { this.assetVals = assetVals }
}
