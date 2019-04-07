package com.amcentral365.service.dao

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table
import java.sql.Timestamp


@Table("roles")
class Role(): Entity() {
    @Column("name", pkPos = 1, restParamName = "role_name") var roleName: String? = null
    @Column("class")                      var roleClass:     String? = null
    @Column("role_schema", isJson = true) var roleSchema:    String? = null
    @Column("description")                var description:   String? = null

    @Column("created_by")  var createdBy:  String? = null
    @Column("modified_by") var modifiedBy: String? = null
    @Column("created_ts",  onInsert = Generated.OnTheDbAlways)                          var createdTs:  Timestamp? = null
    @Column("modified_ts", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true) var modifiedTs: Timestamp? = null

    constructor(roleName: String): this() { this.roleName = roleName }
}
