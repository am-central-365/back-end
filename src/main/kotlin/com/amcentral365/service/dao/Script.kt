package com.amcentral365.service.dao

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table
import java.sql.Timestamp
import java.util.UUID

@Table("scripts")
class Script: Entity() {
    enum class Uncompress { Dont, Zip }
    enum class TargetType { Site, Cluster, Host, Container }

    @Column("script_id", pkPos = 1, onInsert = Generated.OnTheClientAlways)
    var scriptId: UUID? = null

    @Column("script_name")      var scriptName:    String  = ""
    @Column("description")      var description:   String? = null
    @Column("script_store_id")  var scriptStoreId: UUID?   = null

    @Column("url")         var url:         String?     = null
    @Column("uncompress")  var uncompress:  Uncompress  = Uncompress.Dont
    @Column("script_main") var scriptMain:  String?     = null
    @Column("interpreter") var interpreter: String?     = null
    @Column("sudo")        var sudo:        Boolean     = false
    @Column("target_type") var targetType:  TargetType? = null

    @Column("created_by")  var createdBy:  String = ""
    @Column("modified_by") var modifiedBy: String = ""
    @Column("created_ts",  onInsert = Generated.OnTheDbAlways)  var createdTs:  Timestamp? = null
    @Column("modified_ts", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true)
    var modifiedTs: Timestamp? = null
}
