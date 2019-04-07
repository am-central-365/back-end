package com.amcentral365.service.builtins

import java.util.UUID

val AMCClusterAssetId = UUID.fromString("000000ff-0000-0000-0000-000000000001")
val AMCWorkerAssetId  = UUID.fromString("000000ff-0000-0000-0000-000000000002")
val LocalHostAssetId  = UUID.fromString("000000ff-0000-0000-0000-00007f000001")



class RoleName(_unused: String) { companion object {
    val Script      = "script"
    val AMCCluster  = "amc-cluster"
    val AMCWorker   = "amc-worker"
    val ExecutionTarget   ="execution-target"
    val ScriptExecutorAMC ="script-executor-amc"
}}
