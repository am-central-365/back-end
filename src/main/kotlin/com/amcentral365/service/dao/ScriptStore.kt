package com.amcentral365.service.dao

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table
import com.amcentral365.service.authUser
import java.sql.Timestamp
import java.util.UUID

@Table("script_stores")
class ScriptStore: Entity() {
    enum class StoreType { LocalFile, GitHub, Nexus }

    @Column("script_store_id", pkPos = 1, onInsert = Generated.OnTheClientAlways)
    var scriptStoreId: UUID? = null

    @Column("store_type")  var storeType:  StoreType = StoreType.GitHub
    @Column("created_by")  var createdBy:  String = authUser.userId
    @Column("modified_by") var modifiedBy: String = authUser.userId

    @Column("created_ts",  onInsert = Generated.OnTheDbAlways)  var createdTs:  Timestamp? = null
    @Column("modified_ts", onInsert = Generated.OnTheDbAlways)  var modifiedTs: Timestamp? = null
}