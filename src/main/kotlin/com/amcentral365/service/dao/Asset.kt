package com.amcentral365.service.dao

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table
import java.sql.Timestamp


@Table("assets")
class Asset(): Entity() {
    @Column("asset_id", pkPos = 1) var assetId:     Long? = null
    @Column("name")                var name:        String? = null
    @Column("description")         var description: String? = null

    @Column("created_by")  var createdBy:  String? = null
    @Column("modified_by") var modifiedBy: String? = null
    @Column("created_ts",  onInsert = Generated.OnTheDbAlways)                          var createdTs:  Timestamp? = null
    @Column("modified_ts", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true) var modifiedTs: Timestamp? = null

    constructor(assetId: Long): this() { this.assetId = assetId }
    constructor(name: String):  this() { this.name = name }
}
