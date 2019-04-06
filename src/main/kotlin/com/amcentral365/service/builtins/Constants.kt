package com.amcentral365.service.builtins

import java.util.UUID

val AMCClusterAssetId = UUID.fromString("000000ff-0000-0000-0000-000000000001")
val AMCWorkerAssetId  = UUID.fromString("000000ff-0000-0000-0000-000000000002")
val LocalHostAssetId  = UUID.fromString("000000ff-0000-0000-0000-00007f000001")



enum class RoleName(_unused: String) {
    Script      ("script"),
    AMCCluster  ("amc-cluster"),
    AMCWorker   ("amc-worker"),
    ExecutionTarget("execution-target"),
    ScriptExecutorAMC("script-executor-amc"),
}
