package com.amcentral365.service.dao

import java.sql.Timestamp
import java.util.UUID

import com.amcentral365.pl4kotlin.Column
import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Generated
import com.amcentral365.pl4kotlin.Table

@Table("tasks")
class Task(): Entity() {
    enum class Status(s: String) { Ready("Ready"), Processing("Processing"), Finished("Finished"), Failed("Failed") }

    @Column("task_id", pkPos = 1, restParamName = "taskId",         onInsert = Generated.OneTheClientWhenNull) var taskId:   UUID? = null
    @Column("task_status_ts",     restParamName = "taskStatusTs",   onInsert = Generated.OnTheDbAlways)        var statusTs:       Timestamp? = null
    @Column("scheduled_run_ts",   restParamName = "scheduledRunTs", onInsert = Generated.OnTheDbWhenNull)      var scheduledRunTs: Timestamp? = null

    @Column("task_name",          restParamName = "taskName")           var name:             String? = null
    @Column("task_status",        restParamName = "taskStatus")         var status:           Status? = null
    @Column("script_asset_id",    restParamName = "scriptAssetId")      var scriptAssetId:    UUID? = null
    @Column("target_asset_id",    restParamName = "targetAssetId")      var targetAssetId:    UUID? = null
    @Column("executor_role_name", restParamName = "executorRoleName")   var executorRoleName: String? = null

    @Column("script_args", restParamName = "scriptArgs")   var scriptArgs:  String? = null
    @Column("description")                                 var description: String? = null

    @Column("created_by",  restParamName = "createdBy")  var createdBy:  String? = null
    @Column("modified_by", restParamName = "modifiedBy") var modifiedBy: String? = null
    @Column("created_ts",  restParamName = "createdTs",  onInsert = Generated.OnTheDbAlways)                          var createdTs:  Timestamp? = null
    @Column("modified_ts", restParamName = "modifiedTs", onInsert = Generated.OnTheDbAlways, isOptimisticLock = true) var modifiedTs: Timestamp? = null
}
