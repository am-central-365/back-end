package com.amcentral365.service.dao

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table
import com.amcentral365.service.authUser
import java.sql.Timestamp
import java.util.UUID

@Deprecated("gone", level = DeprecationLevel.ERROR)
@Table("script_stores")
class ScriptStore: Entity() {
    enum class StoreType { LocalFile, GitHub, Nexus }

    @Column("script_store_id", pkPos = 1, onInsert = Generated.OnTheClientAlways)
    var scriptStoreId: UUID? = null

    @Column("store_name")  var storeName:   String = ""
    @Column("store_type")  var storeType:   StoreType? = null
    @Column("description") var description: String? = null

    @Column("created_by")  var createdBy:  String = ""
    @Column("modified_by") var modifiedBy: String = ""
    @Column("created_ts",  onInsert = Generated.OnTheDbAlways)  var createdTs:  Timestamp? = null

    @Column("modified_ts", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true)
    var modifiedTs: Timestamp? = null
}
