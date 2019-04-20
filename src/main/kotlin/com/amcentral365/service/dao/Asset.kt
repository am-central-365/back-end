package com.amcentral365.service.dao

import java.sql.Timestamp
import java.util.UUID

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table


@Table("assets")
open class Asset(): Entity() {
    @Column("asset_id", pkPos = 1, restParamName = "assetId", onInsert = Generated.OneTheClientWhenNull) var assetId: UUID? = null
    @Column("name")        var name:        String? = null
    @Column("description") var description: String? = null

    @Column("created_by",  restParamName = "createdBy")  var createdBy:  String? = null
    @Column("modified_by", restParamName = "modifiedBy") var modifiedBy: String? = null
    @Column("created_ts",  restParamName = "createdTs",  onInsert = Generated.OnTheDbAlways)                          var createdTs:  Timestamp? = null
    @Column("modified_ts", restParamName = "modifiedTs", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true) var modifiedTs: Timestamp? = null

    constructor(assetId: UUID): this() { this.assetId = assetId }
    constructor(name: String):  this() { this.name = name }
}
