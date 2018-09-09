package com.amcentral365.service.dao

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table
import java.sql.Timestamp

import com.amcentral365.service.api.catalog.RoleSchemas

@Table("role_schemas")
class RoleSchema(): Entity() {
    @Column("role_name",   pkPos = 1)     var roleName:   String? = null
    @Column("schema_ver",  pkPos = 2)     var schemaVer:  Int = 0
    @Column("role_schema", isJson = true) var roleSchema: String? = null
    @Column("schema_crc")                 var schemaCRC:  Int = 0

    @Column("created_by")  var createdBy:  String? = null
    @Column("modified_by") var modifiedBy: String? = null
    @Column("created_ts",  onInsert = Generated.OnTheDbAlways)                          var createdTs:  Timestamp? = null
    @Column("modified_ts", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true) var modifiedTs: Timestamp? = null

    constructor(schema: RoleSchemas.Schema): this() {
        this.roleName = schema.roleName
        this.schemaVer = schema.schemaVer
    }

}